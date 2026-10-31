package com.agentmod;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

public class AgentEntity extends PathAwareEntity {
    /** false = senta debaixo de um teto/copa de árvore. true = tenta subir em cima. */
    private static final boolean ROOST_ON_TOP = false;
    public static final int SLOTS = 9; // mínimo pedido: 3
    private static final int LIGHT_LEVEL = 12;

    public enum Mode { FOLLOW, STAY, COLLECT, PATROL }
    private enum Fetch { NONE, TO_CHEST, TO_PLAYER }

    private static final TrackedData<Boolean> SITTING =
            DataTracker.registerData(AgentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> CARRYING =
            DataTracker.registerData(AgentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private final SimpleInventory inventory = new SimpleInventory(SLOTS);
    private UUID owner;
    private Mode mode = Mode.FOLLOW;
    private long[] path = new long[0];
    private int pathIndex;

    private boolean powered = true;
    private int unpoweredTicks;
    private BlockPos roost;
    private BlockPos lightPos;

    private Fetch fetch = Fetch.NONE;
    private Item fetchItem;
    private int fetchCount;
    private UUID fetchPlayer;
    private BlockPos fetchChest;
    private int fetchTimeout;

    public AgentEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAgentAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SITTING, false);
        builder.add(CARRYING, false);
    }

    public boolean isSitting() { return dataTracker.get(SITTING); }
    private void setSitting(boolean b) { dataTracker.set(SITTING, b); }
    public boolean isCarrying() { return dataTracker.get(CARRYING); }
    public UUID getOwnerUuid() { return owner; }
    public void setMode(Mode m) { mode = m; if (m == Mode.STAY) getNavigation().stop(); }
    public Mode getMode() { return mode; }

    // ------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (age % 10 == 0) think();
        if (age % 5 == 0) pickUp();
        if (age % 4 == 0) updateLight();
    }

    private void think() {
        if (age % 20 == 0) powered = checkPower();
        double speed = powered ? 1.0 : 0.35;      // sem redstone = bem mais lento
        getNavigation().setSpeed(speed);
        dataTracker.set(CARRYING, !inventory.isEmpty());

        if (powered) {
            unpoweredTicks = 0;
            roost = null;
            if (isSitting()) setSitting(false);
        } else {
            unpoweredTicks += 10;
            if (unpoweredTicks > 100) { roostBehaviour(speed); return; } // 5 s lento -> senta
        }

        if (fetch != Fetch.NONE) { fetchBehaviour(speed); return; }

        PlayerEntity o = getOwner();
        switch (mode) {
            case FOLLOW -> followOwner(o, speed);
            case STAY -> { }
            case COLLECT -> {
                if (isFull() && o != null) { deliver(o, speed); break; }
                ItemEntity target = nearestItem();
                if (target != null) getNavigation().startMovingTo(target, speed);
                else followOwner(o, speed);
            }
            case PATROL -> {
                if (path.length == 0) { mode = Mode.FOLLOW; break; }
                BlockPos t = BlockPos.fromLong(path[pathIndex % path.length]);
                if (squaredDistanceTo(Vec3d.ofBottomCenter(t)) < 2.25) pathIndex = (pathIndex + 1) % path.length;
                else getNavigation().startMovingTo(t.getX() + 0.5, t.getY(), t.getZ() + 0.5, speed);
            }
        }
    }

    private PlayerEntity getOwner() { return owner == null ? null : getWorld().getPlayerByUuid(owner); }

    private void followOwner(PlayerEntity o, double speed) {
        if (o == null) return;
        double d = squaredDistanceTo(o);
        if (d > 1024) refreshPositionAndAngles(o.getX(), o.getY(), o.getZ(), getYaw(), getPitch());
        else if (d > 16) getNavigation().startMovingTo(o, speed);
        else if (d < 9) getNavigation().stop();
    }

    private void deliver(PlayerEntity o, double speed) {
        if (squaredDistanceTo(o) < 9) { giveAllTo(o); say(o, "Entreguei tudo."); }
        else getNavigation().startMovingTo(o, speed);
    }

    // ------------------------------------------------------------ redstone
    private boolean checkPower() {
        BlockPos o = getBlockPos();
        for (int dx = -3; dx <= 3; dx++)
            for (int dy = -1; dy <= 2; dy++)
                for (int dz = -3; dz <= 3; dz++)
                    if (getWorld().getReceivedRedstonePower(o.add(dx, dy, dz)) > 0) return true;
        return false;
    }

    // ------------------------------------------------------------ sentar no teto
    private void roostBehaviour(double speed) {
        if (isSitting()) { getNavigation().stop(); return; }
        if (roost == null) roost = findRoost();
        if (roost == null || squaredDistanceTo(Vec3d.ofBottomCenter(roost)) < 1.5) { sit(); return; }
        getNavigation().startMovingTo(roost.getX() + 0.5, roost.getY(), roost.getZ() + 0.5, speed);
        if (getNavigation().isIdle()) sit();
    }

    private void sit() { getNavigation().stop(); setSitting(true); }

    private BlockPos findRoost() {
        World w = getWorld();
        BlockPos origin = getBlockPos();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int dy = -4; dy <= 4; dy++)
            for (int dx = -12; dx <= 12; dx++)
                for (int dz = -12; dz <= 12; dz++) {
                    BlockPos p = origin.add(dx, dy, dz);
                    if (!w.getBlockState(p).getCollisionShape(w, p).isEmpty()) continue;
                    if (!w.getBlockState(p.up()).getCollisionShape(w, p.up()).isEmpty()) continue;
                    BlockPos below = p.down();
                    if (!w.getBlockState(below).isSideSolidFullSquare(w, below, Direction.UP)) continue;
                    boolean ok = ROOST_ON_TOP ? (dy > 0 && w.isSkyVisible(p)) : hasCeiling(w, p);
                    if (!ok) continue;
                    double d = p.getSquaredDistance(origin);
                    if (d < bd) { bd = d; best = p; }
                }
        return best;
    }

    private boolean hasCeiling(World w, BlockPos p) {
        for (int h = 2; h <= 5; h++) {
            BlockPos c = p.up(h);
            BlockState s = w.getBlockState(c);
            if (s.isAir()) continue;
            return s.isIn(BlockTags.LEAVES) || s.isSideSolidFullSquare(w, c, Direction.DOWN);
        }
        return false;
    }

    // ------------------------------------------------------------ coleta
    private ItemEntity nearestItem() {
        ItemEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ItemEntity e : getWorld().getEntitiesByClass(ItemEntity.class, getBoundingBox().expand(14),
                i -> i.isAlive() && inventory.canInsert(i.getStack()))) {
            double d = squaredDistanceTo(e);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private void pickUp() {
        if (isSitting() || fetch != Fetch.NONE || !(mode == Mode.COLLECT || mode == Mode.PATROL)) return;
        for (ItemEntity it : getWorld().getEntitiesByClass(ItemEntity.class, getBoundingBox().expand(1.0, 0.5, 1.0),
                i -> i.isAlive() && !i.cannotPickup())) {
            ItemStack rest = inventory.addStack(it.getStack().copy());
            if (rest.isEmpty()) it.discard(); else it.setStack(rest);
            playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.3f, 1.0f);
        }
    }

    private boolean isFull() {
        for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i).isEmpty()) return false;
        return true;
    }

    // ------------------------------------------------------------ buscar item em baú
    public boolean startFetch(UUID player, Item item, int count) {
        BlockPos chest = findChestWith(item);
        if (chest == null) return false;
        fetch = Fetch.TO_CHEST;
        fetchItem = item; fetchCount = count; fetchPlayer = player; fetchChest = chest; fetchTimeout = 0;
        return true;
    }

    private Inventory containerAt(BlockPos p) {
        World w = getWorld();
        BlockState s = w.getBlockState(p);
        if (!s.hasBlockEntity()) return null;
        if (s.getBlock() instanceof ChestBlock cb) return ChestBlock.getInventory(cb, s, w, p, true);
        BlockEntity be = w.getBlockEntity(p);
        return be instanceof Inventory i ? i : null;
    }

    private BlockPos findChestWith(Item item) {
        BlockPos o = getBlockPos(), best = null;
        double bd = Double.MAX_VALUE;
        for (int dy = -6; dy <= 6; dy++)
            for (int dx = -16; dx <= 16; dx++)
                for (int dz = -16; dz <= 16; dz++) {
                    BlockPos p = o.add(dx, dy, dz);
                    Inventory inv = containerAt(p);
                    if (inv != null && inv.count(item) > 0) {
                        double d = p.getSquaredDistance(o);
                        if (d < bd) { bd = d; best = p.toImmutable(); }
                    }
                }
        return best;
    }

    private void fetchBehaviour(double speed) {
        fetchTimeout += 10;
        if (fetchTimeout > 1200) { fetch = Fetch.NONE; return; }
        if (fetch == Fetch.TO_CHEST) {
            Vec3d c = Vec3d.ofCenter(fetchChest);
            if (squaredDistanceTo(c) < 6.25) {
                int taken = takeFromChest();
                if (taken == 0) { fetch = Fetch.NONE; return; }
                fetchCount = taken; fetch = Fetch.TO_PLAYER; fetchTimeout = 0;
            } else getNavigation().startMovingTo(c.x, fetchChest.getY(), c.z, speed);
        } else {
            PlayerEntity p = getWorld().getPlayerByUuid(fetchPlayer);
            if (p == null) { fetch = Fetch.NONE; return; }
            if (squaredDistanceTo(p) < 9) {
                giveItem(p, fetchItem, fetchCount);
                say(p, "Aqui está: " + fetchCount + "x " + fetchItem.getName().getString());
                fetch = Fetch.NONE;
            } else getNavigation().startMovingTo(p, speed);
        }
    }

    private int takeFromChest() {
        Inventory inv = containerAt(fetchChest);
        if (inv == null) return 0;
        int need = fetchCount;
        for (int i = 0; i < inv.size() && need > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isOf(fetchItem)) continue;
            int n = Math.min(need, s.getCount());
            ItemStack rest = inventory.addStack(s.copyWithCount(n));
            int moved = n - rest.getCount();
            s.decrement(moved);
            need -= moved;
            if (!rest.isEmpty()) break; // inventário do agente cheio
        }
        inv.markDirty();
        return fetchCount - need;
    }

    private void giveItem(PlayerEntity p, Item item, int count) {
        int left = count;
        for (int i = 0; i < inventory.size() && left > 0; i++) {
            ItemStack s = inventory.getStack(i);
            if (!s.isOf(item)) continue;
            int n = Math.min(left, s.getCount());
            p.getInventory().offerOrDrop(s.copyWithCount(n));
            s.decrement(n);
            left -= n;
        }
        inventory.markDirty();
    }

    public void giveAllTo(PlayerEntity p) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack s = inventory.getStack(i);
            if (!s.isEmpty()) { p.getInventory().offerOrDrop(s.copy()); inventory.setStack(i, ItemStack.EMPTY); }
        }
    }

    // ------------------------------------------------------------ luz dos olhos
    private void updateLight() {
        World w = getWorld();
        BlockPos head = BlockPos.ofFloored(getEyePos());
        if (head.equals(lightPos)) return;
        BlockState at = w.getBlockState(head);
        if (!at.isAir()) return;
        clearLight();
        w.setBlockState(head, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, LIGHT_LEVEL));
        lightPos = head;
    }

    private void clearLight() {
        if (lightPos != null) {
            if (getWorld().getBlockState(lightPos).isOf(Blocks.LIGHT))
                getWorld().setBlockState(lightPos, Blocks.AIR.getDefaultState());
            lightPos = null;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!getWorld().isClient) clearLight();
        super.remove(reason);
    }

    // ------------------------------------------------------------ interação
    private static void say(PlayerEntity p, String msg) { p.sendMessage(Text.literal(msg), true); }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (getWorld().isClient) return ActionResult.SUCCESS;
        ItemStack held = player.getStackInHand(hand);
        if (owner == null) { owner = player.getUuid(); say(player, "Agente vinculado a você."); return ActionResult.SUCCESS; }
        if (!player.getUuid().equals(owner)) { say(player, "Este agente não é seu."); return ActionResult.FAIL; }

        if (held.getItem() instanceof PathWandItem) {
            long[] pts = PathWandItem.getPoints(held);
            if (pts.length == 0) say(player, "A varinha não tem pontos.");
            else { path = pts; pathIndex = 0; mode = Mode.PATROL; say(player, "Patrulhando " + pts.length + " pontos."); }
            return ActionResult.SUCCESS;
        }
        if (held.isEmpty()) {
            if (player.isSneaking()) {
                mode = switch (mode) { case FOLLOW -> Mode.STAY; case STAY -> Mode.COLLECT; default -> Mode.FOLLOW; };
                say(player, "Modo: " + mode.name());
            } else { giveAllTo(player); say(player, "Aqui estão seus itens."); }
            return ActionResult.SUCCESS;
        }
        ItemStack rest = inventory.addStack(held.copy());
        if (rest.getCount() != held.getCount()) {
            if (!player.getAbilities().creativeMode) held.setCount(rest.getCount());
            say(player, "Guardado.");
        } else say(player, "Inventário cheio.");
        return ActionResult.SUCCESS;
    }

    // ------------------------------------------------------------ salvar/carregar
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("Inv", inventory.toNbtList(getRegistryManager()));
        nbt.putInt("Mode", mode.ordinal());
        nbt.putLongArray("Path", path);
        if (owner != null) nbt.putUuid("Owner", owner);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        inventory.readNbtList(nbt.getList("Inv", NbtElement.COMPOUND_TYPE), getRegistryManager());
        mode = Mode.values()[Math.min(nbt.getInt("Mode"), Mode.values().length - 1)];
        path = nbt.getLongArray("Path");
        if (nbt.containsUuid("Owner")) owner = nbt.getUuid("Owner");
    }

    @Override
    protected void dropInventory() {
        super.dropInventory();
        ItemScatterer.spawn(getWorld(), getBlockPos(), inventory);
    }

    @Override public boolean canImmediatelyDespawn(double d) { return false; }
}
