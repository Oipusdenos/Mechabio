package com.agentmod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Arrays;

/** Clique em blocos para marcar pontos do caminho. Agache + use no ar para limpar.
 *  Clique com a varinha no Agente para ele patrulhar os pontos. */
public class PathWandItem extends Item {
    public static final int MAX_POINTS = 32;

    public PathWandItem(Settings settings) { super(settings); }

    public static long[] getPoints(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getLongArray("Points");
    }

    private static void setPoints(ItemStack stack, long[] pts) {
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        nbt.putLongArray("Points", pts);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        if (ctx.getWorld().isClient) return ActionResult.SUCCESS;
        ItemStack stack = ctx.getStack();
        long[] old = getPoints(stack);
        if (old.length >= MAX_POINTS) return ActionResult.FAIL;
        long[] pts = Arrays.copyOf(old, old.length + 1);
        BlockPos p = ctx.getBlockPos().up();
        pts[old.length] = p.asLong();
        setPoints(stack, pts);
        PlayerEntity pl = ctx.getPlayer();
        if (pl != null) pl.sendMessage(Text.literal("Ponto " + pts.length + ": " + p.toShortString()), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSneaking()) {
            if (!world.isClient) {
                setPoints(stack, new long[0]);
                user.sendMessage(Text.literal("Caminho limpo."), true);
            }
            return TypedActionResult.success(stack);
        }
        return TypedActionResult.pass(stack);
    }
}
