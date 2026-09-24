package com.minedota.item;

import com.minedota.map.DotaTrees;
import com.minedota.worldgen.ModWorldgen;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * DotA Tango: RMB → destroy ONE tree (one trunk column + its leaves), regen.
 */
public class TangoItem extends Item {
	private static final double REACH = 6.0;

	public TangoItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!ModWorldgen.isDotaWorld(world)) {
			return TypedActionResult.pass(stack);
		}
		BlockHitResult hit = raycastTree(world, user);
		if (hit.getType() != HitResult.Type.BLOCK) {
			if (!world.isClient) {
				user.sendMessage(Text.literal("Смотри на дерево и жми ПКМ."), true);
			}
			return TypedActionResult.fail(stack);
		}
		if (!world.isClient) {
			if (!consumeOneTree(world, hit.getBlockPos(), user, stack)) {
				user.sendMessage(Text.literal("Tango работает только на деревьях."), true);
				return TypedActionResult.fail(stack);
			}
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();
		ItemStack stack = context.getStack();
		if (!ModWorldgen.isDotaWorld(world) || player == null) {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			if (!consumeOneTree(world, context.getBlockPos(), player, stack)) {
				player.sendMessage(Text.literal("Tango работает только на деревьях."), true);
				return ActionResult.FAIL;
			}
		}
		return ActionResult.success(world.isClient);
	}

	private static BlockHitResult raycastTree(World world, PlayerEntity user) {
		return world.raycast(new RaycastContext(
				user.getEyePos(),
				user.getEyePos().add(user.getRotationVec(1f).multiply(REACH)),
				RaycastContext.ShapeType.OUTLINE,
				RaycastContext.FluidHandling.NONE,
				user));
	}

	private static boolean consumeOneTree(World world, BlockPos pos, PlayerEntity player, ItemStack stack) {
		if (!DotaTrees.eatOneTree(world, pos, player)) {
			return false;
		}
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 10, 1));
		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		return true;
	}
}
