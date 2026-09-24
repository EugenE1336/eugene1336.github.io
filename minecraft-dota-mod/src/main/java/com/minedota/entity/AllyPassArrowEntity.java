package com.minedota.entity;

import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.world.World;

/**
 * Ranged-creep arrow: passes through allied living entities so it can hit towers behind heroes.
 */
public class AllyPassArrowEntity extends ArrowEntity {
	public AllyPassArrowEntity(EntityType<? extends ArrowEntity> type, World world) {
		super(type, world);
	}

	public AllyPassArrowEntity(World world, LivingEntity owner) {
		super(ModEntities.CREEP_ARROW, world);
		this.setOwner(owner);
		this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
	}

	@Override
	protected boolean canHit(Entity entity) {
		Entity owner = this.getOwner();
		if (owner != null && entity instanceof LivingEntity) {
			DotaTeam a = TeamComponent.getTeam(owner);
			DotaTeam b = TeamComponent.getTeam(entity);
			if (a != DotaTeam.NONE && a == b) {
				return false;
			}
		}
		return super.canHit(entity);
	}
}
