package com.mechabio;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.entity.IronGolemEntityRenderer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * MECHA-BIO — mod de arquivo único (Fabric 1.21.1).
 *
 * Mistura peças mecânicas (cobre + redstone) com peças biológicas (Guardian / Warden):
 *  - Laser de Redstone e Laser de Guardião
 *  - Calculadora de Redstone e Comunicador Bio (antena do Warden) — comunicação à distância
 *  - Golem Mecânico (laser, protege o dono) e Golem Bio (regenera, veneno, cura o dono)
 */
public class MechaBioMod implements ModInitializer {

    public static final String MOD_ID = "mechabio";

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    // ---------- Itens ----------
    public static Item REDSTONE_LASER;
    public static Item GUARDIAN_LASER;
    public static Item GUARDIAN_EYE;
    public static Item WARDEN_ANTENNA;
    public static Item MECH_CALCULATOR;
    public static Item BIO_COMMUNICATOR;
    public static Item GOLEM_CORE;
    public static Item BIO_CORE;

    // ---------- Entidades ----------
    public static EntityType<MechaGolemEntity> MECHA_GOLEM;
    public static EntityType<MechaGolemEntity> BIO_GOLEM;

    static <T extends Item> T reg(String name, T item) {
        return Registry.register(Registries.ITEM, id(name), item);
    }

    @Override
    public void onInitialize() {
        // Itens
        REDSTONE_LASER = reg("redstone_laser", new LaserItem(new Item.Settings().maxDamage(250), false));
        GUARDIAN_LASER = reg("guardian_laser", new LaserItem(new Item.Settings().maxDamage(400), true));
        GUARDIAN_EYE = reg("guardian_eye", new Item(new Item.Settings()));
        WARDEN_ANTENNA = reg("warden_antenna", new Item(new Item.Settings()));
        MECH_CALCULATOR = reg("mechanical_calculator", new CommunicatorItem(new Item.Settings().maxCount(1), false));
        BIO_COMMUNICATOR = reg("bio_communicator", new CommunicatorItem(new Item.Settings().maxCount(1), true));
        GOLEM_CORE = reg("golem_core", new CoreItem(new Item.Settings().maxCount(16), false));
        BIO_CORE = reg("bio_core", new CoreItem(new Item.Settings().maxCount(16), true));

        // Entidades
        MECHA_GOLEM = Registry.register(Registries.ENTITY_TYPE, id("mecha_golem"),
                EntityType.Builder.<MechaGolemEntity>create((t, w) -> new MechaGolemEntity(t, w, false), SpawnGroup.MISC)
                        .dimensions(1.4f, 2.7f).maxTrackingRange(10).build("mecha_golem"));
        BIO_GOLEM = Registry.register(Registries.ENTITY_TYPE, id("bio_golem"),
                EntityType.Builder.<MechaGolemEntity>create((t, w) -> new MechaGolemEntity(t, w, true), SpawnGroup.MISC)
                        .dimensions(1.4f, 2.7f).maxTrackingRange(10).build("bio_golem"));

        FabricDefaultAttributeRegistry.register(MECHA_GOLEM,
                IronGolemEntity.createIronGolemAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 70.0));
        FabricDefaultAttributeRegistry.register(BIO_GOLEM,
                IronGolemEntity.createIronGolemAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 55.0));

        // Aba criativa
        ItemGroup group = FabricItemGroup.builder()
                .icon(() -> new ItemStack(REDSTONE_LASER))
                .displayName(Text.translatable("itemGroup.mechabio.main"))
                .entries((ctx, entries) -> {
                    entries.add(REDSTONE_LASER);
                    entries.add(GUARDIAN_LASER);
                    entries.add(GUARDIAN_EYE);
                    entries.add(WARDEN_ANTENNA);
                    entries.add(MECH_CALCULATOR);
                    entries.add(BIO_COMMUNICATOR);
                    entries.add(GOLEM_CORE);
                    entries.add(BIO_CORE);
                }).build();
        Registry.register(Registries.ITEM_GROUP, id("main"), group);

        // Drops biológicos: Olho do Guardião e Antena do Warden
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            World w = entity.getWorld();
            if (w.isClient()) return;
            Random r = w.random;
            if (entity instanceof ElderGuardianEntity) {
                drop(entity, new ItemStack(GUARDIAN_EYE, 1 + r.nextInt(2)));
            } else if (entity instanceof GuardianEntity) {
                if (r.nextFloat() < 0.35f) drop(entity, new ItemStack(GUARDIAN_EYE));
            } else if (entity instanceof WardenEntity) {
                drop(entity, new ItemStack(WARDEN_ANTENNA, 2));
            }
        });

        // Comandos da calculadora: /calc freq <n> | /calc say <msg> | /calc ping
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("calc")
                        .then(CommandManager.literal("freq")
                                .then(CommandManager.argument("n", IntegerArgumentType.integer(0, 999))
                                        .executes(c -> Comms.cmdFreq(c.getSource().getPlayerOrThrow(),
                                                IntegerArgumentType.getInteger(c, "n")))))
                        .then(CommandManager.literal("say")
                                .then(CommandManager.argument("msg", StringArgumentType.greedyString())
                                        .executes(c -> Comms.cmdSay(c.getSource().getPlayerOrThrow(),
                                                StringArgumentType.getString(c, "msg")))))
                        .then(CommandManager.literal("ping")
                                .executes(c -> Comms.cmdPing(c.getSource().getPlayerOrThrow())))));
    }

    static void drop(Entity e, ItemStack stack) {
        ItemEntity item = new ItemEntity(e.getWorld(), e.getX(), e.getY() + 0.5, e.getZ(), stack);
        e.getWorld().spawnEntity(item);
    }

    static boolean takeRedstone(PlayerEntity p) {
        if (p.isCreative()) return true;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(Items.REDSTONE)) {
                s.decrement(1);
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    //  LASER
    // =====================================================================
    public static class Laser {
        public static boolean isFriend(Entity shooter, Entity target) {
            if (target instanceof MechaGolemEntity g) {
                UUID o = g.getOwnerUuid();
                if (o != null && shooter.getUuid().equals(o)) return true;
                if (o != null && shooter instanceof MechaGolemEntity s && o.equals(s.getOwnerUuid())) return true;
            }
            if (shooter instanceof MechaGolemEntity s) {
                if (s.getOwnerUuid() != null && target.getUuid().equals(s.getOwnerUuid())) return true;
                if (!(target instanceof Monster)) return true; // golem não fura aldeões/animais
            }
            return false;
        }

        /** Dispara um raio. Retorna quantas entidades foram atingidas. */
        public static int fire(ServerWorld w, LivingEntity shooter, Vec3d from, Vec3d dir,
                               double range, float damage, boolean guardian) {
            Vec3d end = from.add(dir.multiply(range));
            BlockHitResult hit = w.raycast(new RaycastContext(from, end,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter));
            double len = hit.getType() == HitResult.Type.MISS ? range : hit.getPos().distanceTo(from);

            ParticleEffect particle = guardian ? ParticleTypes.END_ROD : DustParticleEffect.DEFAULT;
            DamageSource src = shooter instanceof PlayerEntity pl
                    ? w.getDamageSources().playerAttack(pl)
                    : w.getDamageSources().mobAttack(shooter);

            Set<Entity> hurt = new HashSet<>();
            for (double d = 0.5; d <= len; d += 0.5) {
                Vec3d pt = from.add(dir.multiply(d));
                w.spawnParticles(particle, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                List<Entity> found = w.getOtherEntities(shooter, new Box(pt, pt).expand(0.4),
                        x -> x instanceof LivingEntity && x.isAlive() && !isFriend(shooter, x));
                for (Entity e : found) {
                    if (hurt.add(e)) {
                        LivingEntity le = (LivingEntity) e;
                        le.damage(src, damage);
                        if (guardian) {
                            le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
                        } else {
                            le.setOnFireFor(2);
                        }
                    }
                }
            }
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3d p = hit.getPos();
                w.spawnParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 6, 0.1, 0.1, 0.1, 0.01);
            }
            return hurt.size();
        }
    }

    public static class LaserItem extends Item {
        private final boolean guardian;

        public LaserItem(Settings settings, boolean guardian) {
            super(settings);
            this.guardian = guardian;
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (!(world instanceof ServerWorld sw)) {
                return TypedActionResult.success(stack, true);
            }
            // O laser de redstone gasta 1 redstone por disparo; o do guardião só usa durabilidade.
            if (!guardian && !takeRedstone(user)) {
                user.sendMessage(Text.translatable("msg.mechabio.no_redstone"), true);
                return TypedActionResult.fail(stack);
            }
            Vec3d look = user.getRotationVec(1.0f);
            Vec3d from = user.getEyePos().add(look.multiply(0.5)).add(0, -0.15, 0);
            Laser.fire(sw, user, from, look, guardian ? 48 : 32, guardian ? 10f : 6f, guardian);

            if (guardian) {
                sw.playSound(null, user.getX(), user.getY(), user.getZ(),
                        SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.PLAYERS, 1.0f, 1.2f);
            } else {
                sw.playSound(null, user.getX(), user.getY(), user.getZ(),
                        SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.8f, 1.6f);
            }
            EquipmentSlot slot = hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            stack.damage(1, user, slot);
            user.getItemCooldownManager().set(this, guardian ? 15 : 8);
            return TypedActionResult.success(stack, false);
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable(guardian ? "tooltip.mechabio.guardian_laser" : "tooltip.mechabio.redstone_laser")
                    .formatted(Formatting.GRAY));
        }
    }

    // =====================================================================
    //  COMUNICAÇÃO (calculadora de redstone / comunicador bio com antena do Warden)
    // =====================================================================
    public static class CommunicatorItem extends Item {
        public final boolean bio;

        public CommunicatorItem(Settings settings, boolean bio) {
            super(settings);
            this.bio = bio;
        }

        public static int getFreq(ItemStack s) {
            NbtComponent c = s.get(DataComponentTypes.CUSTOM_DATA);
            return c == null ? 0 : c.copyNbt().getInt("freq");
        }

        public static void setFreq(ItemStack s, int f) {
            NbtComponent old = s.get(DataComponentTypes.CUSTOM_DATA);
            NbtCompound n = old == null ? new NbtCompound() : old.copyNbt();
            n.putInt("freq", f);
            s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack st = user.getStackInHand(hand);
            if (!world.isClient()) {
                if (user.isSneaking()) { // "aperta a tecla": +1 na frequência
                    int f = (getFreq(st) + 1) % 1000;
                    setFreq(st, f);
                    user.sendMessage(Text.translatable("msg.mechabio.freq_set", f), true);
                    world.playSound(null, user.getX(), user.getY(), user.getZ(),
                            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.6f, 1.4f);
                } else {
                    user.sendMessage(Text.translatable("msg.mechabio.freq_show", getFreq(st)), true);
                    user.sendMessage(Text.translatable("msg.mechabio.help"), false);
                }
            }
            return TypedActionResult.success(st, world.isClient());
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable("tooltip.mechabio.freq", getFreq(stack)).formatted(Formatting.AQUA));
            tooltip.add(Text.translatable(bio ? "tooltip.mechabio.bio_comm" : "tooltip.mechabio.mech_comm")
                    .formatted(Formatting.GRAY));
        }
    }

    public static class Comms {
        static final double MECH_RANGE = 1500.0;

        static ItemStack held(ServerPlayerEntity p) {
            if (p.getMainHandStack().getItem() instanceof CommunicatorItem) return p.getMainHandStack();
            if (p.getOffHandStack().getItem() instanceof CommunicatorItem) return p.getOffHandStack();
            return null;
        }

        /** Procura um dispositivo com a frequência dada; prefere o bio. */
        static ItemStack findDevice(ServerPlayerEntity p, int freq) {
            ItemStack found = null;
            PlayerInventory inv = p.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (s.getItem() instanceof CommunicatorItem c && CommunicatorItem.getFreq(s) == freq) {
                    if (c.bio) return s;
                    if (found == null) found = s;
                }
            }
            return found;
        }

        static boolean isBio(ItemStack s) {
            return s.getItem() instanceof CommunicatorItem c && c.bio;
        }

        static boolean reaches(ServerPlayerEntity a, boolean aBio, ServerPlayerEntity b, boolean bBio) {
            if (aBio && bBio) return true; // antena do Warden: sem limite, entre dimensões
            if (a.getWorld() != b.getWorld()) return false;
            return a.squaredDistanceTo(b) <= MECH_RANGE * MECH_RANGE;
        }

        static int cmdFreq(ServerPlayerEntity p, int n) {
            ItemStack d = held(p);
            if (d == null) {
                p.sendMessage(Text.translatable("msg.mechabio.no_device"), false);
                return 0;
            }
            CommunicatorItem.setFreq(d, n);
            p.sendMessage(Text.translatable("msg.mechabio.freq_set", n), false);
            return 1;
        }

        static int cmdSay(ServerPlayerEntity p, String msg) {
            ItemStack d = held(p);
            if (d == null) {
                p.sendMessage(Text.translatable("msg.mechabio.no_device"), false);
                return 0;
            }
            boolean bio = isBio(d);
            int freq = CommunicatorItem.getFreq(d);
            if (!bio && !takeRedstone(p)) {
                p.sendMessage(Text.translatable("msg.mechabio.no_redstone"), false);
                return 0;
            }
            int count = 0;
            for (ServerPlayerEntity other : p.getServer().getPlayerManager().getPlayerList()) {
                if (other == p) continue;
                ItemStack od = findDevice(other, freq);
                if (od == null || !reaches(p, bio, other, isBio(od))) continue;
                other.sendMessage(Text.translatable("msg.mechabio.received", freq, p.getName(), msg), false);
                other.getWorld().playSound(null, other.getX(), other.getY(), other.getZ(),
                        SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.8f, 1.5f);
                count++;
            }
            p.sendMessage(Text.translatable("msg.mechabio.sent", count), false);
            return count;
        }

        static int cmdPing(ServerPlayerEntity p) {
            ItemStack d = held(p);
            if (d == null) {
                p.sendMessage(Text.translatable("msg.mechabio.no_device"), false);
                return 0;
            }
            boolean bio = isBio(d);
            int freq = CommunicatorItem.getFreq(d);
            String[] dirs = {"L", "SE", "S", "SO", "O", "NO", "N", "NE"};
            int count = 0;
            for (ServerPlayerEntity other : p.getServer().getPlayerManager().getPlayerList()) {
                if (other == p) continue;
                ItemStack od = findDevice(other, freq);
                if (od == null || !reaches(p, bio, other, isBio(od))) continue;
                count++;
                if (p.getWorld() != other.getWorld()) {
                    p.sendMessage(Text.translatable("msg.mechabio.ping_dim", other.getName(),
                            other.getWorld().getRegistryKey().getValue().toString()), false);
                    continue;
                }
                double dx = other.getX() - p.getX();
                double dz = other.getZ() - p.getZ();
                int idx = ((int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 45.0) + 8) % 8;
                int dist = (int) Math.sqrt(dx * dx + dz * dz);
                if (bio) { // a antena do Warden revela as coordenadas exatas
                    p.sendMessage(Text.translatable("msg.mechabio.ping_bio", other.getName(), dist, dirs[idx],
                            other.getBlockX(), other.getBlockY(), other.getBlockZ()), false);
                } else {
                    p.sendMessage(Text.translatable("msg.mechabio.ping", other.getName(), dist, dirs[idx]), false);
                }
            }
            if (count == 0) p.sendMessage(Text.translatable("msg.mechabio.nobody"), false);
            return count;
        }
    }

    // =====================================================================
    //  NÚCLEOS (criam os golens)
    // =====================================================================
    public static class CoreItem extends Item {
        private final boolean bio;

        public CoreItem(Settings settings, boolean bio) {
            super(settings);
            this.bio = bio;
        }

        private static boolean isCopperBlock(Block b) {
            return b == Blocks.COPPER_BLOCK || b == Blocks.EXPOSED_COPPER
                    || b == Blocks.WEATHERED_COPPER || b == Blocks.OXIDIZED_COPPER
                    || b == Blocks.WAXED_COPPER_BLOCK || b == Blocks.WAXED_EXPOSED_COPPER
                    || b == Blocks.WAXED_WEATHERED_COPPER || b == Blocks.WAXED_OXIDIZED_COPPER;
        }

        @Override
        public ActionResult useOnBlock(ItemUsageContext ctx) {
            World w = ctx.getWorld();
            BlockPos pos = ctx.getBlockPos();
            BlockState st = w.getBlockState(pos);
            boolean ok = bio ? st.isOf(Blocks.MOSS_BLOCK) : isCopperBlock(st.getBlock());
            PlayerEntity player = ctx.getPlayer();
            if (!ok || player == null) return ActionResult.PASS;
            if (!(w instanceof ServerWorld sw)) return ActionResult.SUCCESS;

            if (!w.getBlockState(pos.up()).isAir() || !w.getBlockState(pos.up(2)).isAir()) {
                player.sendMessage(Text.translatable("msg.mechabio.no_space"), true);
                return ActionResult.FAIL;
            }
            w.removeBlock(pos, false);
            MechaGolemEntity golem = (bio ? BIO_GOLEM : MECHA_GOLEM).create(sw);
            if (golem == null) return ActionResult.FAIL;
            golem.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), 0);
            golem.setOwner(player.getUuid());
            golem.setPlayerCreated(true);
            golem.setPersistent();
            sw.spawnEntity(golem);

            sw.spawnParticles(bio ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.ELECTRIC_SPARK,
                    pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 25, 0.5, 0.8, 0.5, 0.05);
            sw.playSound(null, pos, bio ? SoundEvents.ENTITY_SLIME_SQUISH : SoundEvents.BLOCK_ANVIL_USE,
                    SoundCategory.BLOCKS, 1.0f, 1.0f);
            ctx.getStack().decrementUnlessCreative(1, player);
            return ActionResult.CONSUME;
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable(bio ? "tooltip.mechabio.bio_core" : "tooltip.mechabio.golem_core")
                    .formatted(Formatting.GRAY));
        }
    }

    // =====================================================================
    //  GOLEM (mecânico = laser | bio = regenera, veneno e cura o dono)
    // =====================================================================
    public static class MechaGolemEntity extends IronGolemEntity {
        private final boolean bio;
        private UUID ownerUuid;

        public MechaGolemEntity(EntityType<? extends IronGolemEntity> type, World world, boolean bio) {
            super(type, world);
            this.bio = bio;
        }

        public boolean isBio() { return bio; }
        public UUID getOwnerUuid() { return ownerUuid; }
        public void setOwner(UUID u) { this.ownerUuid = u; }

        public PlayerEntity getOwner() {
            return ownerUuid == null ? null : getWorld().getPlayerByUuid(ownerUuid);
        }

        @Override
        protected void initGoals() {
            super.initGoals();
            this.goalSelector.add(3, new FollowOwnerGoal(this, 1.15, 7.0f, 3.0f));
        }

        @Override
        public void tick() {
            super.tick();
            if (!(getWorld() instanceof ServerWorld sw)) return;
            PlayerEntity owner = getOwner();

            // Defende o dono: ataca quem o feriu ou quem ele está atacando
            if (owner != null && age % 20 == 0 && getTarget() == null) {
                LivingEntity a = owner.getAttacker();
                LivingEntity t = owner.getAttacking();
                LivingEntity pick = (a != null && a.isAlive()) ? a : t;
                if (pick != null && pick.isAlive() && pick != this && !Laser.isFriend(this, pick)
                        && squaredDistanceTo(pick) < 24 * 24) {
                    setTarget(pick);
                }
            }

            if (!bio) {
                // Golem mecânico: dispara o laser de redstone nos inimigos a média distância
                if (age % 30 == 0) {
                    LivingEntity t = getTarget();
                    if (t != null && t.isAlive() && canSee(t)) {
                        double d2 = squaredDistanceTo(t);
                        if (d2 > 9 && d2 <= 14 * 14) {
                            Vec3d from = getEyePos();
                            Vec3d dir = t.getEyePos().subtract(0, 0.4, 0).subtract(from).normalize();
                            Laser.fire(sw, this, from, dir, 16, 4f, false);
                            sw.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_SHOOT,
                                    SoundCategory.NEUTRAL, 0.8f, 1.8f);
                        }
                    }
                }
            } else {
                // Golem bio: regenera sozinho e cura o dono por perto
                if (age % 40 == 0) heal(1.0f);
                if (owner != null && age % 100 == 0 && owner.getHealth() < owner.getMaxHealth()
                        && squaredDistanceTo(owner) < 100) {
                    owner.heal(2.0f);
                    sw.spawnParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.8, owner.getZ(),
                            3, 0.3, 0.3, 0.3, 0.0);
                }
            }
        }

        @Override
        public boolean tryAttack(Entity target) {
            boolean r = super.tryAttack(target);
            if (bio && r && target instanceof LivingEntity le) {
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 1));
            }
            return r;
        }

        @Override
        public ActionResult interactMob(PlayerEntity player, Hand hand) {
            ItemStack s = player.getStackInHand(hand);
            Item healItem = bio ? Items.SLIME_BALL : Items.COPPER_INGOT;
            if (s.isOf(healItem)) {
                if (getHealth() < getMaxHealth()) {
                    if (!getWorld().isClient()) {
                        heal(25.0f);
                        playSound(SoundEvents.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 1.0f);
                        s.decrementUnlessCreative(1, player);
                    }
                    return ActionResult.success(getWorld().isClient());
                }
                return ActionResult.PASS;
            }
            return super.interactMob(player, hand);
        }

        @Override
        public void writeCustomDataToNbt(NbtCompound nbt) {
            super.writeCustomDataToNbt(nbt);
            if (ownerUuid != null) nbt.putUuid("MechaOwner", ownerUuid);
        }

        @Override
        public void readCustomDataFromNbt(NbtCompound nbt) {
            super.readCustomDataFromNbt(nbt);
            if (nbt.containsUuid("MechaOwner")) ownerUuid = nbt.getUuid("MechaOwner");
        }
    }

    public static class FollowOwnerGoal extends Goal {
        private final MechaGolemEntity golem;
        private final double speed;
        private final float startDist;
        private final float stopDist;
        private PlayerEntity owner;

        public FollowOwnerGoal(MechaGolemEntity golem, double speed, float startDist, float stopDist) {
            this.golem = golem;
            this.speed = speed;
            this.startDist = startDist;
            this.stopDist = stopDist;
            setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            owner = golem.getOwner();
            return owner != null && !owner.isSpectator() && golem.getTarget() == null
                    && golem.squaredDistanceTo(owner) > startDist * startDist;
        }

        @Override
        public boolean shouldContinue() {
            return owner != null && owner.isAlive() && golem.getTarget() == null
                    && golem.squaredDistanceTo(owner) > stopDist * stopDist;
        }

        @Override
        public void start() {
            golem.getNavigation().startMovingTo(owner, speed);
        }

        @Override
        public void stop() {
            golem.getNavigation().stop();
        }

        @Override
        public void tick() {
            golem.getLookControl().lookAt(owner, 10.0f, golem.getMaxLookPitchChange());
            if (golem.age % 10 == 0) {
                if (golem.squaredDistanceTo(owner) > 30 * 30) {
                    golem.refreshPositionAfterTeleport(owner.getX(), owner.getY(), owner.getZ());
                    golem.getNavigation().stop();
                } else {
                    golem.getNavigation().startMovingTo(owner, speed);
                }
            }
        }
    }

    // =====================================================================
    //  CLIENTE (os golens usam o modelo do Golem de Ferro)
    // =====================================================================
    @Environment(EnvType.CLIENT)
    public static class Client implements ClientModInitializer {
        @Override
        public void onInitializeClient() {
            EntityRendererRegistry.<IronGolemEntity>register(MECHA_GOLEM, IronGolemEntityRenderer::new);
            EntityRendererRegistry.<IronGolemEntity>register(BIO_GOLEM, IronGolemEntityRenderer::new);
        }
    }
}
