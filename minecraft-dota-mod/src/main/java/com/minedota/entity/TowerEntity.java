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

public class TowerEntity extends IronGolemEntity implements TeamComponent.TeamHolder {
	/** ~14 blocks — lane control, not jungle snipe. */
	private static final double RANGE = 14.0;
	private static final int FIRE_INTERVAL = 40; // was 20 — half attack speed
	private static final float TOWER_DAMAGE = 8.0f;

	private DotaTeam team = DotaTeam.NONE;
	private DotaMap.Lane lane = DotaMap.Lane.MID;
	private int tier = 1;
	private int fireCooldown;

	public TowerEntity(EntityType<? extends IronGolemEntity> entityType, World world) {
		super(entityType, world);
		this.setPlayerCreated(true);
		this.experiencePoints = 0;
	}

	public DotaMap.Lane getLane() {
		return lane;
	}

	public int getTier() {
		return tier;
	}

	public void setTowerMeta(DotaMap.Lane lane, int tier) {
		this.lane = lane == null ? DotaMap.Lane.MID : lane;
		this.tier = Math.max(1, Math.min(4, tier));
		applyTierStats();
		refreshNameplate();
	}

	/** T1→T4 HP 150/180/225/250; ATK & armor ≈ +20% each tier. */
	private void applyTierStats() {
		double hp = switch (tier) {
			case 2 -> 180.0;
			case 3 -> 225.0;
			case 4 -> 250.0;
			default -> 150.0;
		};
		double scale = Math.pow(1.20, tier - 1);
		double armor = 8.0 * scale;
		double atk = TOWER_DAMAGE * scale;
		var maxHp = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		var arm = this.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		var dmg = this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (maxHp != null) {
			maxHp.setBaseValue(hp);
			this.setHealth((float) hp);
		}
		if (arm != null) {
			arm.setBaseValue(armor);
		}
		if (dmg != null) {
			dmg.setBaseValue(atk);
		}
	}

	private float towerDamage() {
		var dmg = this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		return dmg != null ? (float) dmg.getValue() : TOWER_DAMAGE;
	}

	public boolean isInvulnerableToAttack() {
		if (this.getWorld().isClient || this.getWorld().getServer() == null) {
			return false;
		}
		return !MatchManager.get(this.getWorld().getServer()).canDamageTower(this);
	}

	public static DefaultAttributeContainer.Builder createTowerAttributes() {
		return IronGolemEntity.createIronGolemAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 150.0)
				.add(EntityAttributes.GENERIC_ARMOR, 8.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, RANGE)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, TOWER_DAMAGE);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, (float) RANGE));
		// checkVisibility=true — vanilla canSee; we also enforce tree/solid LOS below
		this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::isEnemyUnit));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, CreepEntity.class, 10, true, false, this::isEnemyUnit));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, RangedCreepEntity.class, 10, true, false, this::isEnemyUnit));
		this.targetSelector.add(4, new ActiveTargetGoal<>(this, BotHeroEntity.class, 10, true, false, this::isEnemyUnit));
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

	/** Clear plaza around tower: trees, walls, stone clutter — creeps need air. */
	public void clearFootprint(ServerWorld world) {
		int ax = (int) Math.floor(this.getX());
		int az = (int) Math.floor(this.getZ());
		int sy = DotaMap.surfaceY(ax, az);
		final int r = 4; // was 3 — +1 free block
		for (BlockPos p : BlockPos.iterate(new BlockPos(ax - r, sy - 1, az - r), new BlockPos(ax + r, sy + 6, az + r))) {
			BlockState st = world.getBlockState(p);
			boolean centerPad = p.getX() == ax && p.getZ() == az && p.getY() == sy;
			if (centerPad) {
				continue;
			}
			if (p.getY() > sy) {
				if (DotaMap.isTreeBlock(st)
						|| st.isOf(Blocks.VINE) || st.isOf(Blocks.SNOW)
						|| st.isOf(Blocks.GRASS) || st.isOf(Blocks.TALL_GRASS) || st.isOf(Blocks.FERN)
						|| st.isOf(Blocks.STONE_BRICK_WALL) || st.isOf(Blocks.COBBLESTONE_WALL)
						|| st.isOf(Blocks.OAK_FENCE) || st.isOf(Blocks.IRON_BARS)
						|| st.isOf(Blocks.STONE) || st.isOf(Blocks.STONE_BRICKS)
						|| st.isOf(Blocks.COBBLESTONE) || st.isOf(Blocks.MOSSY_COBBLESTONE)
						|| st.isOf(Blocks.ANDESITE) || st.isOf(Blocks.DIORITE) || st.isOf(Blocks.GRANITE)
						|| st.isOf(Blocks.DIRT) || st.isOf(Blocks.GRASS_BLOCK)
						|| st.isOf(Blocks.LIME_BANNER) || st.isOf(Blocks.RED_BANNER)) {
					world.setBlockState(p, Blocks.AIR.getDefaultState());
				}
			} else if (p.getY() == sy) {
				if (DotaMap.isTreeBlock(st)
						|| st.isOf(Blocks.STONE_BRICK_WALL) || st.isOf(Blocks.COBBLESTONE_WALL)
						|| st.isOf(Blocks.OAK_FENCE) || st.isOf(Blocks.IRON_BARS)
						|| st.isOf(Blocks.STONE) || st.isOf(Blocks.STONE_BRICKS)
						|| st.isOf(Blocks.COBBLESTONE) || st.isOf(Blocks.IRON_BLOCK)
						|| st.isOf(Blocks.OBSIDIAN)) {
					world.setBlockState(p, Blocks.DIRT_PATH.getDefaultState());
				}
			}
		}
		world.setBlockState(new BlockPos(ax, sy, az), Blocks.IRON_BLOCK.getDefaultState());
		// Banner marker above (air column)
		world.setBlockState(new BlockPos(ax, sy + 4, az),
				team == DotaTeam.DIRE ? Blocks.RED_BANNER.getDefaultState() : Blocks.LIME_BANNER.getDefaultState());
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient || !this.isAlive()) {
			return;
		}

		this.setVelocity(Vec3d.ZERO);
		this.velocityModified = true;

		if (!this.getWorld().isClient && this.age % 5 == 0) {
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

	/**
	 * Blocks (incl. leaves/logs — leaves have no collider) must stop shots.
	 */
	private boolean hasClearShot(LivingEntity target) {
		Vec3d from = this.getEyePos();
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.55, 0);
		World world = this.getWorld();

		// Outline ray hits leaves; also scan voxels for tree blocks (leaves often empty COLLIDER)
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
		// Save velocity — mobAttack applies knockback; towers must not shove units
		Vec3d vel = target.getVelocity();
		float dmg = towerDamage();
		boolean hit = target.damage(world.getDamageSources().mobAttack(this), dmg);
		target.setVelocity(vel);
		target.velocityModified = true;
		this.playSound(SoundEvents.ENTITY_ARROW_SHOOT, 1.0f, 0.65f);
		if (!hit) {
			target.setHealth(Math.max(0.5f, target.getHealth() - dmg * 0.5f));
		}

		Vec3d from = this.getEyePos();
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
		Vec3d delta = to.subtract(from);
		int steps = Math.max(3, (int) (delta.length() * 2));
		for (int i = 1; i <= steps; i++) {
			double t = i / (double) steps;
			Vec3d p = from.add(delta.multiply(t));
			world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		world.spawnParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 4, 0.1, 0.1, 0.1, 0.01);
	}

	@Override
	public boolean tryAttack(Entity target) {
		return false;
	}

	@Override
	public boolean isCollidable() {
		// Creeps path through footprint instead of wedging inside
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
		// no-op — don't shove creeps into walls
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
		this.setCustomName(team.getDisplayName().copy()
				.append(net.minecraft.text.Text.literal(" " + lane.getTag() + " T" + tier + " " + hp + "/" + max + inv)));
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
		nbt.putInt("Tier", tier);
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
		if (nbt.contains("Tier")) {
			tier = Math.max(1, Math.min(4, nbt.getInt("Tier")));
		}
		applyTierStats();
		refreshNameplate();
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (!this.getWorld().isClient && this.getWorld().getServer() != null) {
			MatchManager.get(this.getWorld().getServer()).onTowerDestroyed(this);
		}
	}
}
