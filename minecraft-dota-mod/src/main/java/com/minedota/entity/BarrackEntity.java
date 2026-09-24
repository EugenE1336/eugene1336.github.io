package com.minedota.entity;

import com.minedota.map.DotaMap;
import com.minedota.match.MatchManager;
import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Lane barracks — spawn point for creeps. Protected until T4 on the same lane is down.
 */
public class BarrackEntity extends IronGolemEntity implements TeamComponent.TeamHolder {
	public enum Kind {
		MELEE,
		RANGED;

		public String getRussianName() {
			return this == MELEE ? "ближних" : "дальников";
		}
	}

	private DotaTeam team = DotaTeam.NONE;
	private DotaMap.Lane lane = DotaMap.Lane.MID;
	private Kind kind = Kind.MELEE;

	public BarrackEntity(EntityType<? extends IronGolemEntity> entityType, World world) {
		super(entityType, world);
		this.setPlayerCreated(true);
		this.experiencePoints = 0;
	}

	public static DefaultAttributeContainer.Builder createBarrackAttributes() {
		return IronGolemEntity.createIronGolemAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 200.0)
				.add(EntityAttributes.GENERIC_ARMOR, 10.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 4.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.0);
	}

	public DotaMap.Lane getLane() {
		return lane;
	}

	public Kind getKind() {
		return kind;
	}

	public void setBarrackMeta(DotaMap.Lane lane, Kind kind) {
		this.lane = lane == null ? DotaMap.Lane.MID : lane;
		this.kind = kind == null ? Kind.MELEE : kind;
		refreshNameplate();
	}

	public boolean isInvulnerableToAttack() {
		if (this.getWorld().isClient || this.getWorld().getServer() == null) {
			return false;
		}
		return !MatchManager.get(this.getWorld().getServer()).canDamageBarrack(this);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
	}

	public void clearFootprint(ServerWorld world) {
		int ax = (int) Math.floor(this.getX());
		int az = (int) Math.floor(this.getZ());
		int sy = DotaMap.surfaceY(ax, az);
		for (BlockPos p : BlockPos.iterate(new BlockPos(ax - 2, sy - 1, az - 2), new BlockPos(ax + 2, sy + 5, az + 2))) {
			if (DotaMap.isTreeBlock(world.getBlockState(p))
					|| world.getBlockState(p).isOf(Blocks.VINE)
					|| world.getBlockState(p).isOf(Blocks.SNOW)
					|| world.getBlockState(p).isOf(Blocks.GRASS)
					|| world.getBlockState(p).isOf(Blocks.TALL_GRASS)
					|| world.getBlockState(p).isOf(Blocks.FERN)) {
				world.setBlockState(p, Blocks.AIR.getDefaultState());
			}
		}
		BlockState pad = kind == Kind.MELEE ? Blocks.COBBLESTONE.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState();
		world.setBlockState(new BlockPos(ax, sy, az), pad);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient || !this.isAlive()) {
			return;
		}
		this.setVelocity(Vec3d.ZERO);
		this.velocityModified = true;
		if (this.age % 5 == 0) {
			refreshNameplate();
		}
	}

	@Override
	public boolean tryAttack(Entity target) {
		return false;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean collidesWith(Entity other) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void pushAway(Entity entity) {
	}

	@Override
	public boolean isFireImmune() {
		return true;
	}

	@Override
	public DotaTeam getDotaTeam() {
		return team;
	}

	@Override
	public void setDotaTeam(DotaTeam team) {
		this.team = team == null ? DotaTeam.NONE : team;
		refreshNameplate();
	}

	private void refreshNameplate() {
		int hp = Math.max(0, Math.round(this.getHealth()));
		int max = Math.max(1, Math.round(this.getMaxHealth()));
		String inv = isInvulnerableToAttack() ? " §b[защита]" : "";
		String kindTag = kind == Kind.MELEE ? "Melee" : "Ranged";
		this.setCustomName(team.getDisplayName().copy()
				.append(net.minecraft.text.Text.literal(" " + lane.getTag() + " Barracks " + kindTag + " "
						+ hp + "/" + max + inv)));
		this.setCustomNameVisible(true);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (isInvulnerableToAttack()) {
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString(TeamComponent.NBT_KEY, team.getId());
		nbt.putString("Lane", lane.name());
		nbt.putString("Kind", kind.name());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains(TeamComponent.NBT_KEY)) {
			setDotaTeam(DotaTeam.fromId(nbt.getString(TeamComponent.NBT_KEY)));
		}
		if (nbt.contains("Lane")) {
			try {
				lane = DotaMap.Lane.valueOf(nbt.getString("Lane"));
			} catch (IllegalArgumentException ignored) {
				lane = DotaMap.Lane.MID;
			}
		}
		if (nbt.contains("Kind")) {
			try {
				kind = Kind.valueOf(nbt.getString("Kind"));
			} catch (IllegalArgumentException ignored) {
				kind = Kind.MELEE;
			}
		}
		refreshNameplate();
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (!this.getWorld().isClient && this.getWorld().getServer() != null) {
			MatchManager.get(this.getWorld().getServer()).onBarrackDestroyed(this);
		}
	}
}
