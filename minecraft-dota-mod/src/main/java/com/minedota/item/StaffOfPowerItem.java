package com.minedota.item;

import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

/**
 * Basic hero nuke: right-click deals AoE damage to enemy-team entities.
 */
public class StaffOfPowerItem extends Item {
	private static final float DAMAGE = 8.0f;
	private static final double RADIUS = 5.0;
	private static final int COOLDOWN_TICKS = 40;

	public StaffOfPowerItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) {
			return TypedActionResult.success(stack);
		}

		if (user.getItemCooldownManager().isCoolingDown(this)) {
			return TypedActionResult.fail(stack);
		}

		DotaTeam team = TeamComponent.getTeam(user);
		if (team == DotaTeam.NONE) {
			user.sendMessage(Text.literal("Сначала вступи в команду: /dota join radiant|dire"), false);
			return TypedActionResult.fail(stack);
		}

		ServerWorld serverWorld = (ServerWorld) world;
		Box box = user.getBoundingBox().expand(RADIUS);
		List<LivingEntity> targets = serverWorld.getEntitiesByClass(
				LivingEntity.class,
				box,
				entity -> entity.isAlive()
						&& entity != user
						&& TeamComponent.getTeam(entity) == team.opposite());

		for (LivingEntity target : targets) {
			target.damage(serverWorld.getDamageSources().magic(), DAMAGE);
		}

		serverWorld.spawnParticles(
				ParticleTypes.SOUL_FIRE_FLAME,
				user.getX(), user.getY() + 1.0, user.getZ(),
				40, RADIUS * 0.4, 0.6, RADIUS * 0.4, 0.02);
		serverWorld.playSound(
				null, user.getX(), user.getY(), user.getZ(),
				SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.8f);

		user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
		user.sendMessage(Text.literal("Power Strike! Попаданий: " + targets.size()), true);
		return TypedActionResult.success(stack);
	}
}
