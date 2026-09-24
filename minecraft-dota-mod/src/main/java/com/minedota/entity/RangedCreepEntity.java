package com.minedota.entity;

import com.minedota.match.MatchManager;
import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Ranged lane creep (skeleton + bow). */
public class RangedCreepEntity extends SkeletonEntity implements TeamComponent.TeamHolder {
	public static final double RANGED_HP = 30.0;
	public static final double RANGED_DAMAGE = 4.0;

	private DotaTeam team = DotaTeam.NONE;
	private final List<BlockPos> path = new ArrayList<>();
	private int pathIndex;
	private boolean superCreep;
	private final CreepStuckAssist.State stuck = new CreepStuckAssist.State();

	public RangedCreepEntity(EntityType<? extends SkeletonEntity> entityType, World world) {
		super(entityType, world);
		this.experiencePoints = 0;
		this.setPersistent();
	}

	public static DefaultAttributeContainer.Builder createRangedAttributes() {
		return SkeletonEntity.createAbstractSkeletonAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, RANGED_HP)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, RANGED_DAMAGE)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 18.0)
				.add(EntityAttributes.GENERIC_ARMOR, 0.0);
	}

	/** Custom arrows ignore allied heroes/creeps so they can hit towers behind them. */
	@Override
	protected PersistentProjectileEntity createArrowProjectile(ItemStack arrow, float damageModifier) {
		AllyPassArrowEntity proj = new AllyPassArrowEntity(this.getWorld(), this);
		proj.initFromStack(arrow);
		double base = this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		proj.setDamage(base * Math.max(0.1f, damageModifier));
		return proj;
	}

	@Override
	public net.minecraft.entity.EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty,
			net.minecraft.entity.SpawnReason spawnReason, net.minecraft.entity.EntityData entityData,
			NbtCompound entityNbt) {
		net.minecraft.entity.EntityData data = super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
		this.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		this.setEquipmentDropChance(net.minecraft.entity.EquipmentSlot.MAINHAND, 0f);
		return data;
	}

	public void setLanePath(List<BlockPos> waypoints) {
		path.clear();
		if (waypoints != null) {
			path.addAll(waypoints);
		}
		pathIndex = 0;
	}

	public void advancePath() {
		if (pathIndex < path.size() - 1) {
			pathIndex++;
		}
	}

	/**
	 * After fighting a tower the creep often stands past the frozen waypoint.
	 * Snap index forward to the closest remaining point so we never walk back up-lane.
	 */
	public void snapPathForward() {
		if (path.isEmpty() || pathIndex >= path.size() - 1) {
			return;
		}
		int best = pathIndex;
		double bestDist = squaredDistXZ(path.get(pathIndex));
		for (int i = pathIndex + 1; i < path.size(); i++) {
			double d = squaredDistXZ(path.get(i));
			if (d < bestDist) {
				bestDist = d;
				best = i;
			}
		}
		pathIndex = best;
		while (pathIndex < path.size() - 1
				&& squaredDistXZ(path.get(pathIndex + 1)) <= squaredDistXZ(path.get(pathIndex))) {
			pathIndex++;
		}
	}

	private double squaredDistXZ(BlockPos pos) {
		double dx = pos.getX() + 0.5 - this.getX();
		double dz = pos.getZ() + 0.5 - this.getZ();
		return dx * dx + dz * dz;
	}

	public BlockPos currentWaypoint() {
		if (path.isEmpty()) {
			return null;
		}
		return path.get(Math.min(pathIndex, path.size() - 1));
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(2, new HoldRangeBowAttackGoal<>(this, 1.0, 20, 12.0f));
		this.goalSelector.add(5, new FollowLanePathGoal(this));
		this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		this.goalSelector.add(8, new LookAroundGoal(this));

		this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::isEnemy));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, LivingEntity.class, 10, true, false,
				entity -> entity instanceof TeamComponent.TeamHolder && isEnemy(entity)
						&& !(entity instanceof TowerEntity) && !(entity instanceof AncientEntity)
						&& !(entity instanceof BarrackEntity)));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, TowerEntity.class, 10, true, false,
				e -> isEnemy(e) && !((TowerEntity) e).isInvulnerableToAttack()));
		this.targetSelector.add(4, new ActiveTargetGoal<>(this, BarrackEntity.class, 10, true, false,
				e -> isEnemy(e) && !((BarrackEntity) e).isInvulnerableToAttack()));
		this.targetSelector.add(5, new ActiveTargetGoal<>(this, AncientEntity.class, 10, true, false,
				e -> isEnemy(e) && isAncientAttackable((AncientEntity) e)));
	}

	private boolean isEnemy(LivingEntity entity) {
		if (team == DotaTeam.NONE || !entity.isAlive() || entity == this) {
			return false;
		}
		return TeamComponent.getTeam(entity) == team.opposite();
	}

	@Override
	public boolean canTarget(LivingEntity target) {
		return isEnemy(target);
	}

	@Override
	public void attack(LivingEntity target, float pullProgress) {
		if (!isEnemy(target)) {
			this.setTarget(null);
			return;
		}
		super.attack(target, pullProgress);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void pushAway(net.minecraft.entity.Entity entity) {
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient || !this.isAlive()) {
			return;
		}
		if (this.age % 5 == 0) {
			refreshNameplate();
		}
		retargetPreferUnits();
		Box box = this.getBoundingBox().expand(1.2);
		for (TowerEntity tower : this.getWorld().getEntitiesByClass(TowerEntity.class, box, this::isEnemy)) {
			if (tower.isInvulnerableToAttack()) {
				CreepStuckAssist.pushAwayFrom(this, tower.getX(), tower.getZ(), 2.4);
				continue;
			}
			if (this.getTarget() == null || this.getTarget() == tower
					|| this.getTarget() instanceof TowerEntity || this.getTarget() instanceof AncientEntity
					|| this.getTarget() instanceof BarrackEntity) {
				LivingEntity unit = findNearbyEnemyUnit(12.0);
				if (unit != null) {
					this.setTarget(unit);
				} else {
					LivingEntity structure = findPreferredEnemyStructure(14.0);
					this.setTarget(structure != null ? structure : tower);
				}
			}
		}
		for (TowerEntity tower : this.getWorld().getEntitiesByClass(TowerEntity.class, box,
				t -> TeamComponent.getTeam(t) == team)) {
			CreepStuckAssist.pushAwayFrom(this, tower.getX(), tower.getZ(), 2.2);
		}
		for (BarrackEntity b : this.getWorld().getEntitiesByClass(BarrackEntity.class, box,
				ent -> isEnemy(ent) && ent.isInvulnerableToAttack())) {
			CreepStuckAssist.pushAwayFrom(this, b.getX(), b.getZ(), 2.4);
		}
		LivingEntity t = this.getTarget();
		if (t != null && !isEnemy(t)) {
			this.setTarget(null);
		}
		boolean fightingDamageable = t != null && !(t instanceof TowerEntity tw && tw.isInvulnerableToAttack())
				&& !(t instanceof BarrackEntity br && br.isInvulnerableToAttack())
				&& !(t instanceof AncientEntity an && !isAncientAttackable(an));
		boolean wantsMove = !path.isEmpty() && !fightingDamageable;
		CreepStuckAssist.tick(this, stuck, wantsMove, () -> {
			snapPathForward();
			for (int i = 0; i < 2 && pathIndex < path.size() - 1; i++) {
				advancePath();
			}
			BlockPos wp = currentWaypoint();
			if (wp != null) {
				getNavigation().startMovingTo(wp.getX() + 0.5, wp.getY(), wp.getZ() + 0.5, 1.05);
			}
		});
	}

	private void retargetPreferUnits() {
		LivingEntity current = this.getTarget();
		LivingEntity unit = findNearbyEnemyUnit(14.0);
		if (unit != null) {
			if (current == null || current instanceof TowerEntity || current instanceof AncientEntity
					|| current instanceof BarrackEntity
					|| this.squaredDistanceTo(unit) + 4.0 < this.squaredDistanceTo(current)) {
				this.setTarget(unit);
			}
			return;
		}
		if (current instanceof TowerEntity t && t.isInvulnerableToAttack()) {
			this.setTarget(null);
			current = null;
		} else if (current instanceof BarrackEntity b && b.isInvulnerableToAttack()) {
			this.setTarget(null);
			current = null;
		} else if (current instanceof AncientEntity a && !isAncientAttackable(a)) {
			this.setTarget(null);
			current = null;
		}
		if (current == null || current instanceof TowerEntity || current instanceof BarrackEntity
				|| current instanceof AncientEntity) {
			LivingEntity structure = findPreferredEnemyStructure(16.0);
			if (structure != null && (current == null
					|| this.squaredDistanceTo(structure) + 1.0 < this.squaredDistanceTo(current))) {
				this.setTarget(structure);
			}
		}
	}

	LivingEntity findNearbyEnemyUnit(double range) {
		Box box = this.getBoundingBox().expand(range);
		LivingEntity best = null;
		double bestDist = range * range;
		for (LivingEntity e : this.getWorld().getEntitiesByClass(LivingEntity.class, box, this::isEnemy)) {
			if (e instanceof TowerEntity || e instanceof AncientEntity || e instanceof BarrackEntity) {
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

	LivingEntity findPreferredEnemyStructure(double range) {
		Box box = this.getBoundingBox().expand(range);
		LivingEntity best = null;
		double bestDist = range * range;
		for (TowerEntity t : this.getWorld().getEntitiesByClass(TowerEntity.class, box, this::isEnemy)) {
			if (!t.isAlive() || t.isInvulnerableToAttack()) {
				continue;
			}
			double d = this.squaredDistanceTo(t);
			if (d < bestDist) {
				bestDist = d;
				best = t;
			}
		}
		for (BarrackEntity b : this.getWorld().getEntitiesByClass(BarrackEntity.class, box, this::isEnemy)) {
			if (!b.isAlive() || b.isInvulnerableToAttack()) {
				continue;
			}
			double d = this.squaredDistanceTo(b);
			if (d < bestDist) {
				bestDist = d;
				best = b;
			}
		}
		for (AncientEntity a : this.getWorld().getEntitiesByClass(AncientEntity.class, box, this::isEnemy)) {
			if (!a.isAlive() || !isAncientAttackable(a)) {
				continue;
			}
			double d = this.squaredDistanceTo(a);
			if (d < bestDist) {
				bestDist = d;
				best = a;
			}
		}
		return best;
	}

	boolean isAncientAttackable(AncientEntity ancient) {
		if (this.getWorld().isClient || this.getWorld().getServer() == null) {
			return false;
		}
		return MatchManager.get(this.getWorld().getServer()).canDamageAncient(ancient);
	}

	TowerEntity findBlockingEnemyTower(double range) {
		Box box = this.getBoundingBox().expand(range);
		TowerEntity best = null;
		double bestDist = range * range;
		for (TowerEntity t : this.getWorld().getEntitiesByClass(TowerEntity.class, box, this::isEnemy)) {
			if (!t.isAlive() || t.isInvulnerableToAttack()) {
				continue;
			}
			double d = this.squaredDistanceTo(t);
			if (d < bestDist) {
				bestDist = d;
				best = t;
			}
		}
		return best;
	}

	BarrackEntity findBlockingEnemyBarrack(double range) {
		Box box = this.getBoundingBox().expand(range);
		BarrackEntity best = null;
		double bestDist = range * range;
		for (BarrackEntity b : this.getWorld().getEntitiesByClass(BarrackEntity.class, box, this::isEnemy)) {
			if (!b.isAlive() || b.isInvulnerableToAttack()) {
				continue;
			}
			double d = this.squaredDistanceTo(b);
			if (d < bestDist) {
				bestDist = d;
				best = b;
			}
		}
		return best;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public boolean isFireImmune() {
		return true;
	}

	public boolean isSuperCreep() {
		return superCreep;
	}

	public void applySuperStats() {
		superCreep = true;
		var hp = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		var dmg = this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		var arm = this.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		if (hp != null) {
			hp.setBaseValue(RANGED_HP * 2.0);
		}
		if (dmg != null) {
			dmg.setBaseValue(RANGED_DAMAGE * 2.0);
		}
		if (arm != null) {
			arm.setBaseValue(2.0);
		}
		this.setHealth(this.getMaxHealth());
		equipDiamondArmor();
		equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		setEquipmentDropChance(net.minecraft.entity.EquipmentSlot.MAINHAND, 0f);
		refreshNameplate();
	}

	private void equipDiamondArmor() {
		equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		equipStack(net.minecraft.entity.EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{
				net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
				net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET}) {
			setEquipmentDropChance(slot, 0f);
		}
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
		String label = superCreep ? " Super Ranged " : " Ranged Creep ";
		this.setCustomName(team.getDisplayName().copy().append(label + hp + "/" + max));
		this.setCustomNameVisible(true);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString(TeamComponent.NBT_KEY, team.getId());
		nbt.putInt("PathIndex", pathIndex);
		nbt.putBoolean("SuperCreep", superCreep);
		NbtList list = new NbtList();
		for (BlockPos p : path) {
			list.add(NbtLong.of(p.asLong()));
		}
		nbt.put("LanePath", list);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains(TeamComponent.NBT_KEY)) {
			setDotaTeam(DotaTeam.fromId(nbt.getString(TeamComponent.NBT_KEY)));
		}
		pathIndex = nbt.getInt("PathIndex");
		if (nbt.getBoolean("SuperCreep")) {
			applySuperStats();
		}
		path.clear();
		if (nbt.contains("LanePath", NbtElement.LIST_TYPE)) {
			NbtList list = nbt.getList("LanePath", NbtElement.LONG_TYPE);
			for (int i = 0; i < list.size(); i++) {
				path.add(BlockPos.fromLong(((NbtLong) list.get(i)).longValue()));
			}
		}
	}

	private static class FollowLanePathGoal extends Goal {
		private final RangedCreepEntity creep;
		private int recalcCooldown;

		FollowLanePathGoal(RangedCreepEntity creep) {
			this.creep = creep;
			this.setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return !creep.path.isEmpty() && creep.getTarget() == null && !nearBlockingEnemyTower();
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void tick() {
			if (nearBlockingEnemyTower()) {
				creep.getNavigation().stop();
				LivingEntity unit = creep.findNearbyEnemyUnit(12.0);
				if (unit != null) {
					creep.setTarget(unit);
				} else {
					LivingEntity structure = creep.findPreferredEnemyStructure(14.0);
					if (structure != null) {
						creep.setTarget(structure);
					}
				}
				return;
			}
			creep.snapPathForward();
			BlockPos wp = creep.currentWaypoint();
			if (wp == null) {
				return;
			}
			while (wp != null && nearAlliedTower(wp) && creep.pathIndex < creep.path.size() - 1) {
				creep.advancePath();
				wp = creep.currentWaypoint();
			}
			if (wp == null) {
				return;
			}
			double dist = creep.squaredDistXZ(wp);
			if (dist < 4.0) {
				creep.advancePath();
				wp = creep.currentWaypoint();
				if (wp == null) {
					return;
				}
			}
			if (recalcCooldown-- > 0) {
				return;
			}
			recalcCooldown = 10;
			creep.getNavigation().startMovingTo(wp.getX() + 0.5, wp.getY(), wp.getZ() + 0.5, 1.0);
		}

		private boolean nearBlockingEnemyTower() {
			return creep.findPreferredEnemyStructure(10.0) != null;
		}

		private boolean nearAlliedTower(BlockPos wp) {
			Box box = new Box(wp).expand(3.5);
			return !creep.getWorld().getEntitiesByClass(TowerEntity.class, box,
					t -> TeamComponent.getTeam(t) == creep.getDotaTeam()).isEmpty();
		}
	}
}
