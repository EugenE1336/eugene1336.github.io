package com.minedota.entity;

import com.minedota.hero.HeroCatalog;
import com.minedota.hero.HeroDef;
import com.minedota.map.DotaMap;
import com.minedota.team.DotaTeam;
import com.minedota.team.TeamComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Simple mid-lane bot: walks mid until enemy T2, attacks creeps / towers / heroes.
 */
public class BotHeroEntity extends PathAwareEntity implements TeamComponent.TeamHolder {
	private DotaTeam team = DotaTeam.NONE;
	private String heroId = "axe";
	private final List<BlockPos> path = new ArrayList<>();
	private int pathIndex;

	public BotHeroEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 0;
	}

	public static DefaultAttributeContainer.Builder createBotAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 200.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0)
				.add(EntityAttributes.GENERIC_ARMOR, 2.0);
	}

	public void setup(DotaTeam team, String heroId, List<BlockPos> midPath) {
		this.team = team == null ? DotaTeam.NONE : team;
		this.heroId = heroId == null ? "axe" : heroId;
		this.path.clear();
		if (midPath != null) {
			this.path.addAll(midPath);
		}
		this.pathIndex = 0;
		HeroDef def = HeroCatalog.get(this.heroId);
		if (def != null) {
			this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(def.maxHealth());
			this.setHealth(def.maxHealth());
			this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(def.attackDamage());
		}
		refreshName();
	}

	public String getHeroId() {
		return heroId;
	}

	private void refreshName() {
		HeroDef def = HeroCatalog.get(heroId);
		String name = def != null ? def.name() : heroId;
		this.setCustomName(team.getDisplayName().copy()
				.append(net.minecraft.text.Text.literal(" Bot " + name)));
		this.setCustomNameVisible(true);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(2, new MeleeAttackGoal(this, 1.15, false));
		this.goalSelector.add(5, new FollowMidUntilT2Goal(this));
		this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		this.goalSelector.add(8, new LookAroundGoal(this));

		this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::isEnemy));
		this.targetSelector.add(2, new ActiveTargetGoal<>(this, CreepEntity.class, 10, true, false, this::isEnemy));
		this.targetSelector.add(3, new ActiveTargetGoal<>(this, RangedCreepEntity.class, 10, true, false, this::isEnemy));
		this.targetSelector.add(4, new ActiveTargetGoal<>(this, TowerEntity.class, 10, true, false, this::isEnemyTower));
		this.targetSelector.add(5, new ActiveTargetGoal<>(this, BotHeroEntity.class, 10, true, false, this::isEnemy));
	}

	private boolean isEnemy(LivingEntity e) {
		if (team == DotaTeam.NONE || !e.isAlive() || e == this) {
			return false;
		}
		return TeamComponent.getTeam(e) == team.opposite();
	}

	/** Only hit towers that are mid T1/T2 (don't push past T2). */
	private boolean isEnemyTower(LivingEntity e) {
		if (!(e instanceof TowerEntity tower) || !isEnemy(e)) {
			return false;
		}
		return tower.getLane() == DotaMap.Lane.MID && tower.getTier() <= 2;
	}

	@Override
	public boolean canTarget(LivingEntity target) {
		if (target instanceof TowerEntity) {
			return isEnemyTower(target);
		}
		return isEnemy(target);
	}

	@Override
	public boolean tryAttack(net.minecraft.entity.Entity target) {
		if (target instanceof LivingEntity living) {
			if (living instanceof TowerEntity && !isEnemyTower(living)) {
				return false;
			}
			if (!(living instanceof TowerEntity) && !isEnemy(living)) {
				return false;
			}
		}
		return super.tryAttack(target);
	}

	@Override
	public DotaTeam getDotaTeam() {
		return team;
	}

	@Override
	public void setDotaTeam(DotaTeam team) {
		this.team = team == null ? DotaTeam.NONE : team;
		refreshName();
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString(TeamComponent.NBT_KEY, team.getId());
		nbt.putString("HeroId", heroId);
		nbt.putInt("PathIndex", pathIndex);
		NbtList list = new NbtList();
		for (BlockPos p : path) {
			list.add(NbtLong.of(p.asLong()));
		}
		nbt.put("Path", list);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		team = DotaTeam.fromId(nbt.getString(TeamComponent.NBT_KEY));
		heroId = nbt.contains("HeroId") ? nbt.getString("HeroId") : "axe";
		pathIndex = nbt.getInt("PathIndex");
		path.clear();
		if (nbt.contains("Path", NbtElement.LIST_TYPE)) {
			NbtList list = nbt.getList("Path", NbtElement.LONG_TYPE);
			for (int i = 0; i < list.size(); i++) {
				path.add(BlockPos.fromLong(((NbtLong) list.get(i)).longValue()));
			}
		}
		refreshName();
	}

	/** Mid lane path ending at enemy T2. */
	public static List<BlockPos> midPathToEnemyT2(DotaTeam team) {
		List<BlockPos> full = new ArrayList<>();
		for (DotaMap.CreepSpawn s : DotaMap.creepSpawns()) {
			if (s.team() == team && s.lane() == DotaMap.Lane.MID) {
				full.addAll(s.path());
				break;
			}
		}
		DotaMap.TowerSpot stop = DotaMap.findTowerSpot(team.opposite(), DotaMap.Lane.MID, 2);
		if (stop == null || full.isEmpty()) {
			return List.copyOf(full);
		}
		BlockPos t2 = stop.pos();
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : full) {
			out.add(p);
			double dx = p.getX() - t2.getX();
			double dz = p.getZ() - t2.getZ();
			if (dx * dx + dz * dz <= 36) { // within ~6 blocks of T2 — stop
				break;
			}
		}
		if (out.isEmpty() || out.get(out.size() - 1).getSquaredDistance(t2) > 4) {
			out.add(t2);
		}
		return List.copyOf(out);
	}

	public static void discardAll(ServerWorld world) {
		Box box = new Box(-DotaMap.HALF, 0, -DotaMap.HALF, DotaMap.HALF, 128, DotaMap.HALF);
		world.getEntitiesByClass(BotHeroEntity.class, box, e -> true).forEach(BotHeroEntity::discard);
	}

	static class FollowMidUntilT2Goal extends Goal {
		private final BotHeroEntity bot;
		private int recalc;

		FollowMidUntilT2Goal(BotHeroEntity bot) {
			this.bot = bot;
			this.setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return !bot.path.isEmpty() && bot.getTarget() == null;
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void tick() {
			if (bot.path.isEmpty()) {
				return;
			}
			int idx = Math.min(bot.pathIndex, bot.path.size() - 1);
			BlockPos wp = bot.path.get(idx);
			double dist = bot.squaredDistanceTo(wp.getX() + 0.5, bot.getY(), wp.getZ() + 0.5);
			if (dist < 4.0 && bot.pathIndex < bot.path.size() - 1) {
				bot.pathIndex++;
				wp = bot.path.get(bot.pathIndex);
			}
			if (recalc-- > 0) {
				return;
			}
			recalc = 10;
			bot.getNavigation().startMovingTo(wp.getX() + 0.5, wp.getY(), wp.getZ() + 0.5, 1.05);
		}
	}
}
