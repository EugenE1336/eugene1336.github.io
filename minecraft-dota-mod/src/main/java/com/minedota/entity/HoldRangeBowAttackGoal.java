package com.minedota.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BowItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.EnumSet;

/**
 * Approach until {@code maxRange}, then stand still and shoot.
 * No strafe / no walk-back (vanilla BowAttackGoal dances in and out).
 */
public class HoldRangeBowAttackGoal<T extends HostileEntity & RangedAttackMob> extends Goal {
	private final T actor;
	private final double speed;
	private final int intervalTicks;
	private final float maxRange;
	private int seeTime;
	private int cooldown = -1;
	private int combatTicks = -1;

	public HoldRangeBowAttackGoal(T actor, double speed, int intervalTicks, float maxRange) {
		this.actor = actor;
		this.speed = speed;
		this.intervalTicks = intervalTicks;
		this.maxRange = maxRange;
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		LivingEntity target = actor.getTarget();
		return target != null && target.isAlive() && isHoldingBow();
	}

	@Override
	public boolean shouldContinue() {
		return canStart() || !actor.getNavigation().isIdle();
	}

	@Override
	public void start() {
		super.start();
		actor.setAttacking(true);
	}

	@Override
	public void stop() {
		actor.setAttacking(false);
		seeTime = 0;
		cooldown = -1;
		combatTicks = -1;
		actor.clearActiveItem();
		actor.getNavigation().stop();
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = actor.getTarget();
		if (target == null) {
			return;
		}
		double distSq = actor.squaredDistanceTo(target.getX(), target.getY(), target.getZ());
		boolean canSee = actor.getVisibilityCache().canSee(target);
		if (canSee) {
			seeTime++;
		} else {
			seeTime = 0;
		}

		double maxSq = maxRange * maxRange;
		boolean inRange = distSq <= maxSq && seeTime >= 3;

		if (!inRange) {
			// Too far or no LOS — close the gap, no circling
			actor.getNavigation().startMovingTo(target, speed);
			combatTicks = -1;
		} else {
			// In range — plant feet and shoot
			actor.getNavigation().stop();
			combatTicks++;
		}

		actor.getLookControl().lookAt(target, 30.0f, 30.0f);

		if (actor.isUsingItem()) {
			if (!canSee && actor.getItemUseTime() >= 20) {
				actor.clearActiveItem();
			} else if (canSee) {
				int use = actor.getItemUseTime();
				if (use >= 20) {
					actor.clearActiveItem();
					actor.attack(target, BowItem.getPullProgress(use));
					cooldown = intervalTicks;
				}
			}
		} else if (--cooldown <= 0 && inRange && canSee) {
			actor.setCurrentHand(ProjectileUtil.getHandPossiblyHolding(actor, Items.BOW));
			cooldown = intervalTicks;
		}
	}

	private boolean isHoldingBow() {
		return actor.isHolding(Items.BOW);
	}
}
