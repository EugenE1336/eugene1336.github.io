package com.minedota.entity;

import com.minedota.map.DotaMap;
import com.minedota.match.MatchManager;
import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class AncientEntity extends IronGolemEntity implements TeamComponent.TeamHolder {
	private static final double RANGE = 14.0;
	private static final int FIRE_INTERVAL = 40;
	private static final float ANCIENT_DAMAGE = 10.0f;

	private DotaTeam team = DotaTeam.NONE;
	private int fireCooldown;

	public AncientEntity(EntityType<? extends IronGolemEntity> entityType, World world) {
		super(entityType, world);
		this.setPlayerCreated(true);
		this.experiencePoints = 0;
	}

	public static DefaultAttributeContainer.Builder createAncientAttributes() {
		return IronGolemEntity.createIronGolemAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 400.0)
				.add(EntityAttributes.GENERIC_ARMOR, 12.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, RANGE)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, ANCIENT_DAMAGE);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, (float) RANGE));
		this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::isEnemyUnit));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, CreepEntity.class, 10, true, false, this::isEnemyUnit));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, RangedCreepEntity.class, 10, true, false, this::isEnemyUnit));
	}

	private boolean isEnemyUnit(LivingEntity entity) {
		if (team == DotaTeam.NONE || !entity.isAlive() || entity == this) {
			return false;
		}
		if (entity instanceof TowerEntity || entity instanceof AncientEntity || entity instanceof BarrackEntity) {
			return false;
		}
		return TeamComponent.getTeam(entity) == team.opposite();
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

		if (fireCooldown > 0) {
			fireCooldown--;
		}

		LivingEntity target = this.getTarget();
		if (target == null || !isValidTarget(target)) {
			target = findNearestEnemy();
			this.setTarget(target);
		}

		if (fireCooldown > 0 || target == null || !isValidTarget(target)) {
			return;
		}

		fireAt(target);
		fireCooldown = FIRE_INTERVAL;
	}

	private boolean isValidTarget(LivingEntity target) {
		return isEnemyUnit(target)
				&& this.squaredDistanceTo(target) <= RANGE * RANGE
				&& hasClearShot(target);
	}

	private boolean hasClearShot(LivingEntity target) {
		Vec3d from = this.getEyePos();
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.55, 0);
		World world = this.getWorld();

		BlockHitResult hit = world.raycast(new RaycastContext(
				from, to, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, this));
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos bp = hit.getBlockPos();
			BlockState st = world.getBlockState(bp);
			if (DotaMap.isTreeBlock(st) || !st.getCollisionShape(world, bp).isEmpty()) {
				return false;
			}
		}

		Vec3d delta = to.subtract(from);
		double len = delta.length();
		if (len < 0.05) {
			return true;
		}
		int steps = Math.max(6, (int) (len * 5));
		BlockPos self = this.getBlockPos();
		for (int i = 1; i < steps; i++) {
			Vec3d p = from.add(delta.multiply(i / (double) steps));
			BlockPos bp = BlockPos.ofFloored(p.x, p.y, p.z);
			if (bp.equals(self) || bp.getManhattanDistance(self) == 0) {
				continue;
			}
			BlockState st = world.getBlockState(bp);
			if (DotaMap.isTreeBlock(st)) {
				return false;
			}
			if (!st.getCollisionShape(world, bp).isEmpty() && st.isSolidBlock(world, bp)) {
				return false;
			}
		}
		return true;
	}

	private LivingEntity findNearestEnemy() {
		Box box = this.getBoundingBox().expand(RANGE);
		LivingEntity best = null;
		double bestDist = RANGE * RANGE;
		for (LivingEntity e : this.getWorld().getEntitiesByClass(LivingEntity.class, box, this::isEnemyUnit)) {
			if (!hasClearShot(e)) {
				continue;
			}
			double d = this.squaredDistanceTo(e);
			if (d < bestDist) {
				bestDist = d;
				best = e;
			}
		}
		return best;
	}

	private void fireAt(LivingEntity target) {
		if (!hasClearShot(target)) {
			return;
		}
		ServerWorld world = (ServerWorld) this.getWorld();
		Vec3d vel = target.getVelocity();
		boolean hit = target.damage(world.getDamageSources().mobAttack(this), ANCIENT_DAMAGE);
		target.setVelocity(vel);
		target.velocityModified = true;
		this.playSound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.55f);
		if (!hit) {
			target.setHealth(Math.max(0.5f, target.getHealth() - ANCIENT_DAMAGE * 0.5f));
		}

		Vec3d from = this.getEyePos();
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
		Vec3d delta = to.subtract(from);
		int steps = Math.max(3, (int) (delta.length() * 2));
		for (int i = 1; i <= steps; i++) {
			double t = i / (double) steps;
			Vec3d p = from.add(delta.multiply(t));
			world.spawnParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		world.spawnParticles(ParticleTypes.LAVA, from.x, from.y, from.z, 3, 0.15, 0.15, 0.15, 0.01);
	}

	@Override
	public boolean tryAttack(Entity target) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
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
		boolean invuln = false;
		if (!this.getWorld().isClient && this.getWorld().getServer() != null) {
			invuln = !MatchManager.get(this.getWorld().getServer()).canDamageAncient(this);
		}
		String inv = invuln ? " §b[защита]" : "";
		this.setCustomName(team.getDisplayName().copy()
				.append(net.minecraft.text.Text.literal(" Ancient " + hp + "/" + max + inv)));
		this.setCustomNameVisible(true);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (!this.getWorld().isClient && this.getWorld().getServer() != null
				&& !MatchManager.get(this.getWorld().getServer()).canDamageAncient(this)) {
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString(TeamComponent.NBT_KEY, team.getId());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains(TeamComponent.NBT_KEY)) {
			setDotaTeam(DotaTeam.fromId(nbt.getString(TeamComponent.NBT_KEY)));
		}
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (!this.getWorld().isClient && this.getWorld().getServer() != null) {
			MatchManager.get(this.getWorld().getServer()).onAncientDestroyed(this);
		}
	}
}
