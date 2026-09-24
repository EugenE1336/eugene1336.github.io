package com.minedota.entity;

import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Melee lane creep (zombie model). */
public class CreepEntity extends ZombieEntity implements TeamComponent.TeamHolder {
	public static final double MELEE_HP = 40.0; // ~5+ hits from Axe L1 (7 dmg)
	public static final double MELEE_DAMAGE = 3.0;

	private DotaTeam team = DotaTeam.NONE;
	private final List<BlockPos> path = new ArrayList<>();
	private int pathIndex;
	private boolean superCreep;

	public CreepEntity(EntityType<? extends ZombieEntity> entityType, World world) {
		super(entityType, world);
		this.experiencePoints = 0;
		this.setPersistent();
	}

	public static DefaultAttributeContainer.Builder createCreepAttributes() {
		return ZombieEntity.createZombieAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, MELEE_HP)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, MELEE_DAMAGE)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0)
				.add(EntityAttributes.GENERIC_ARMOR, 0.0)
				.add(EntityAttributes.ZOMBIE_SPAWN_REINFORCEMENTS, 0.0);
	}

	public void setLanePath(List<BlockPos> waypoints) {
		path.clear();
		if (waypoints != null) {
			path.addAll(waypoints);
		}
		pathIndex = 0;
	}

	public List<BlockPos> getLanePath() {
		return path;
	}

	public int getPathIndex() {
		return pathIndex;
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
		// Also skip if next is already closer (standing between two points, past current)
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
		this.goalSelector.add(2, new MeleeAttackGoal(this, 1.1, false));
		this.goalSelector.add(5, new FollowLanePathGoal(this));
		this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		this.goalSelector.add(8, new LookAroundGoal(this));

		this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::isEnemy));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, LivingEntity.class, 10, true, false,
				entity -> entity instanceof TeamComponent.TeamHolder && isEnemy(entity)
						&& !(entity instanceof TowerEntity) && !(entity instanceof AncientEntity)
						&& !(entity instanceof BarrackEntity)));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, TowerEntity.class, 10, true, false, this::isEnemy));
		this.targetSelector.add(4, new ActiveTargetGoal<>(this, BarrackEntity.class, 10, true, false, this::isEnemy));
		this.targetSelector.add(5, new ActiveTargetGoal<>(this, AncientEntity.class, 10, true, false, this::isEnemy));
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
	public boolean tryAttack(net.minecraft.entity.Entity target) {
		if (target instanceof LivingEntity living && !isEnemy(living)) {
			return false;
		}
		return super.tryAttack(target);
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
		// Unstick only from ALLIED towers; enemy towers = attack target
		Box box = this.getBoundingBox().expand(1.2);
		for (TowerEntity tower : this.getWorld().getEntitiesByClass(TowerEntity.class, box, this::isEnemy)) {
			if (this.getTarget() == null || this.getTarget() == tower
					|| this.getTarget() instanceof TowerEntity || this.getTarget() instanceof AncientEntity) {
				LivingEntity unit = findNearbyEnemyUnit(10.0);
				if (unit != null) {
					this.setTarget(unit);
				} else {
					this.setTarget(tower);
				}
			}
		}
		for (TowerEntity tower : this.getWorld().getEntitiesByClass(TowerEntity.class, box,
				t -> TeamComponent.getTeam(t) == team)) {
			double dx = this.getX() - tower.getX();
			double dz = this.getZ() - tower.getZ();
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len < 1.6) {
				if (len < 0.05) {
					dx = 1;
					dz = 0;
					len = 1;
				}
				this.refreshPositionAndAngles(
						tower.getX() + dx / len * 2.2,
						this.getY(),
						tower.getZ() + dz / len * 2.2,
						this.getYaw(), this.getPitch());
				this.getNavigation().stop();
			}
		}
		LivingEntity t = this.getTarget();
		if (t != null && !isEnemy(t)) {
			this.setTarget(null);
		}
	}

	/** Prefer enemy creeps / heroes over towers. */
	void retargetPreferUnits() {
		LivingEntity current = this.getTarget();
		LivingEntity unit = findNearbyEnemyUnit(12.0);
		if (unit != null) {
			if (current == null || current instanceof TowerEntity || current instanceof AncientEntity
					|| current instanceof BarrackEntity
					|| this.squaredDistanceTo(unit) + 4.0 < this.squaredDistanceTo(current)) {
				this.setTarget(unit);
			}
			return;
		}
		if (current == null) {
			TowerEntity tower = findBlockingEnemyTower(10.0);
			if (tower != null) {
				this.setTarget(tower);
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

	TowerEntity findBlockingEnemyTower(double range) {
		Box box = this.getBoundingBox().expand(range);
		TowerEntity best = null;
		double bestDist = range * range;
		for (TowerEntity t : this.getWorld().getEntitiesByClass(TowerEntity.class, box, this::isEnemy)) {
			if (!t.isAlive()) {
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
			if (!b.isAlive()) {
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
	public boolean isFireImmune() {
		return true;
	}

	@Override
	protected boolean burnsInDaylight() {
		return false;
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
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	public void setBaby(boolean baby) {
		super.setBaby(false);
	}

	public boolean isSuperCreep() {
		return superCreep;
	}

	/** ×2 HP / damage / armor after enemy melee barrack on this lane is down. */
	public void applySuperStats() {
		superCreep = true;
		var hp = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		var dmg = this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		var arm = this.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		if (hp != null) {
			hp.setBaseValue(MELEE_HP * 2.0);
		}
		if (dmg != null) {
			dmg.setBaseValue(MELEE_DAMAGE * 2.0);
		}
		if (arm != null) {
			arm.setBaseValue(4.0);
		}
		this.setHealth(this.getMaxHealth());
		equipDiamondArmor();
		refreshNameplate();
	}

	private void equipDiamondArmor() {
		equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_HELMET));
		equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_CHESTPLATE));
		equipStack(net.minecraft.entity.EquipmentSlot.LEGS, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_LEGGINGS));
		equipStack(net.minecraft.entity.EquipmentSlot.FEET, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_BOOTS));
		for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{
				net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
				net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET,
				net.minecraft.entity.EquipmentSlot.MAINHAND, net.minecraft.entity.EquipmentSlot.OFFHAND}) {
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
		String label = superCreep ? " Super Creep " : " Creep ";
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
		} else if (nbt.contains("LaneGoal")) {
			path.add(BlockPos.fromLong(nbt.getLong("LaneGoal")));
		}
	}

	/** Follow dense waypoints along dirt paths. */
	public static class FollowLanePathGoal extends Goal {
		private final CreepEntity creep;
		private int recalcCooldown;

		public FollowLanePathGoal(CreepEntity creep) {
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
				LivingEntity unit = creep.findNearbyEnemyUnit(10.0);
				if (unit != null) {
					creep.setTarget(unit);
				} else {
					TowerEntity tower = creep.findBlockingEnemyTower(10.0);
					if (tower != null) {
						creep.setTarget(tower);
					} else {
						BarrackEntity barrack = creep.findBlockingEnemyBarrack(10.0);
						if (barrack != null) {
							creep.setTarget(barrack);
						}
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
			return creep.findBlockingEnemyTower(8.0) != null || creep.findBlockingEnemyBarrack(8.0) != null;
		}

		private boolean nearAlliedTower(BlockPos wp) {
			Box box = new Box(wp).expand(3.5);
			return !creep.getWorld().getEntitiesByClass(TowerEntity.class, box,
					t -> TeamComponent.getTeam(t) == creep.getDotaTeam()).isEmpty();
		}
	}
}
