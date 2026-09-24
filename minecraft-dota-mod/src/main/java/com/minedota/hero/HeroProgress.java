package com.minedota.hero;

/**
 * Per-player match progression: level, XP, gold, skill points, ability ranks.
 */
public final class HeroProgress {
	private int level = 1;
	private int xp;
	private int gold;
	private int skillPoints = 1;
	private final int[] ranks = new int[4]; // Q W E R
	private int attackCooldownTicks;
	private int respawnTicksLeft;
	private boolean awaitingRespawn;
	private int kills;
	private int deaths;
	private int assists;
	/** Tree Grab: remaining cleave attacks (0 = inactive). */
	private int treeGrabHitsLeft;

	public int getLevel() {
		return level;
	}

	public int getTreeGrabHitsLeft() {
		return treeGrabHitsLeft;
	}

	public boolean hasTreeGrab() {
		return treeGrabHitsLeft > 0;
	}

	public void startTreeGrab() {
		treeGrabHitsLeft = 5;
	}

	/** Hits by Tree Grab rank: 4/5/6/7. */
	public void startTreeGrab(int abilityRank) {
		treeGrabHitsLeft = treeGrabHitsForRank(abilityRank);
	}

	public static int treeGrabHitsForRank(int rank) {
		return Math.max(4, Math.min(7, 3 + Math.max(1, rank)));
	}

	/** Bonus attack while holding a tree: +2/+4/+6/+8 by Tree Grab rank. */
	public static float treeGrabAttackBonus(int rank) {
		return 2f * Math.max(1, Math.min(4, rank));
	}

	public float getTreeGrabAttackBonus() {
		if (!hasTreeGrab()) {
			return 0f;
		}
		return treeGrabAttackBonus(getRank(AbilitySlot.E));
	}

	/** @return true if tree grab just finished (caller should start CD) */
	public boolean consumeTreeGrabHit() {
		if (treeGrabHitsLeft <= 0) {
			return false;
		}
		treeGrabHitsLeft--;
		return treeGrabHitsLeft <= 0;
	}

	public void clearTreeGrab() {
		treeGrabHitsLeft = 0;
	}

	public int getKills() {
		return kills;
	}

	public int getDeaths() {
		return deaths;
	}

	public int getAssists() {
		return assists;
	}

	public void addKill() {
		kills++;
	}

	public void addDeath() {
		deaths++;
	}

	public void addAssist() {
		assists++;
	}

	public int getXp() {
		return xp;
	}

	public int getXpToNext() {
		return ProgressionConstants.xpToNext(level);
	}

	public int getGold() {
		return gold;
	}

	public int getSkillPoints() {
		return skillPoints;
	}

	public int getRank(AbilitySlot slot) {
		return ranks[slot.getIndex()];
	}

	public int getRank(int index) {
		return ranks[Math.max(0, Math.min(3, index))];
	}

	public int[] getRanksCopy() {
		return ranks.clone();
	}

	public int getAttackCooldownTicks() {
		return attackCooldownTicks;
	}

	public boolean canAttack() {
		return attackCooldownTicks <= 0 && !awaitingRespawn;
	}

	public void startAttackCooldown() {
		attackCooldownTicks = ProgressionConstants.ATTACK_COOLDOWN_TICKS;
	}

	public void startAttackCooldown(HeroDef def) {
		attackCooldownTicks = ProgressionConstants.attackIntervalTicks(getAttackSpeed(def));
	}

	public int getRespawnTicksLeft() {
		return respawnTicksLeft;
	}

	public boolean isAwaitingRespawn() {
		return awaitingRespawn;
	}

	public void beginRespawn() {
		awaitingRespawn = true;
		respawnTicksLeft = ProgressionConstants.respawnTicks(level);
	}

	public void clearRespawn() {
		awaitingRespawn = false;
		respawnTicksLeft = 0;
	}

	public void addGold(int amount) {
		if (amount > 0) {
			gold += amount;
		}
	}

	/** @return number of levels gained */
	public int addXp(int amount) {
		if (amount <= 0 || level >= ProgressionConstants.MAX_LEVEL) {
			return 0;
		}
		int before = level;
		xp += amount;
		while (level < ProgressionConstants.MAX_LEVEL) {
			int need = ProgressionConstants.xpToNext(level);
			if (xp < need) {
				break;
			}
			xp -= need;
			level++;
			skillPoints++;
		}
		if (level >= ProgressionConstants.MAX_LEVEL) {
			xp = 0;
		}
		return level - before;
	}

	/**
	 * Spend 1 SP on a slot. Returns null on success, error message otherwise.
	 * Basic ranks unlock at hero levels 1/3/5/7; ultimate at 6/11/15.
	 */
	public String tryUpgrade(AbilitySlot slot) {
		if (skillPoints <= 0) {
			return "Нет skill points.";
		}
		int idx = slot.getIndex();
		int max = slot == AbilitySlot.R
				? ProgressionConstants.MAX_RANK_ULT
				: ProgressionConstants.MAX_RANK_BASIC;
		if (ranks[idx] >= max) {
			return "Макс. ранг " + slot.getKey() + ".";
		}
		int nextRank = ranks[idx] + 1;
		if (slot == AbilitySlot.R) {
			int req = ProgressionConstants.ULT_LEVEL_REQ[nextRank];
			if (level < req) {
				return "Ульт ранг " + nextRank + " с " + req + " уровня.";
			}
		} else {
			int req = ProgressionConstants.BASIC_LEVEL_REQ[nextRank];
			if (level < req) {
				return slot.getKey() + " ранг " + nextRank + " с " + req + " уровня.";
			}
		}
		ranks[idx]++;
		skillPoints--;
		return null;
	}

	public void tickCooldowns() {
		if (attackCooldownTicks > 0) {
			attackCooldownTicks--;
		}
		if (awaitingRespawn && respawnTicksLeft > 0) {
			respawnTicksLeft--;
		}
	}

	public float getMaxHealth(HeroDef def) {
		return def.maxHealth() + hpGain(def.attribute()) * (level - 1);
	}

	/**
	 * Attack speed points. Base 100; +5/level; Tiny Grow −30/rank. Clamped [20, 300].
	 * intervalSec = 2.0 − 0.005 × AS
	 */
	public int getAttackSpeed(HeroDef def) {
		int as = ProgressionConstants.BASE_ATTACK_SPEED
				+ (level - 1) * ProgressionConstants.ATTACK_SPEED_PER_LEVEL;
		if (def != null && "tiny".equals(def.id())) {
			as -= getRank(AbilitySlot.R) * ProgressionConstants.TINY_GROW_AS_PENALTY;
		}
		return Math.max(ProgressionConstants.MIN_ATTACK_SPEED,
				Math.min(ProgressionConstants.MAX_ATTACK_SPEED, as));
	}

	public float getAttackDamage(HeroDef def) {
		float dmg = def.attackDamage() + atkGain(def.attribute()) * (level - 1);
		if ("tiny".equals(def.id())) {
			dmg += getRank(AbilitySlot.R) * 5f;
			dmg += getTreeGrabAttackBonus();
		}
		return dmg;
	}

	public float getArmor(HeroDef def) {
		float per = ProgressionConstants.ARMOR_PER_LEVEL;
		if (def.attribute() == HeroAttribute.AGILITY) {
			per *= ProgressionConstants.AGI_ARMOR_MULT;
		}
		return ProgressionConstants.BASE_ARMOR + per * (level - 1);
	}

	/** Multiplier for ability power/heal. Later: primary from Intellect DotA stats. */
	public float getSpellAmp(HeroDef def) {
		return switch (def.attribute()) {
			case INTELLECT -> 1f + ProgressionConstants.INT_SPELL_AMP_PER_LEVEL * level;
			case UNIVERSAL -> 1f + ProgressionConstants.INT_SPELL_AMP_PER_LEVEL
					* ProgressionConstants.UNI_FACTOR * level;
			default -> 1f;
		};
	}

	/** Ability power at current rank (rank 0 = unlearned). */
	public float scaledAbilityPower(AbilityDef base, AbilitySlot slot, HeroDef hero) {
		int rank = getRank(slot);
		if (rank <= 0) {
			return 0f;
		}
		float rankMult = 0.75f + 0.25f * rank;
		float p = base.power() * rankMult * getSpellAmp(hero);
		// Tiny Grow: +10% to Q/W per ult rank
		if ("tiny".equals(hero.id()) && (slot == AbilitySlot.Q || slot == AbilitySlot.W)) {
			p *= 1f + 0.10f * getRank(AbilitySlot.R);
		}
		return p;
	}

	public float scaledAbilityHeal(AbilityDef base, AbilitySlot slot, HeroDef hero) {
		return scaledAbilityPower(base, slot, hero);
	}

	private static float hpGain(HeroAttribute attr) {
		return switch (attr) {
			case STRENGTH -> ProgressionConstants.STR_HP_PER_LEVEL;
			case AGILITY -> ProgressionConstants.AGI_HP_PER_LEVEL;
			case INTELLECT -> ProgressionConstants.INT_HP_PER_LEVEL;
			case UNIVERSAL -> ProgressionConstants.STR_HP_PER_LEVEL * ProgressionConstants.UNI_FACTOR;
		};
	}

	private static float atkGain(HeroAttribute attr) {
		return switch (attr) {
			case STRENGTH -> ProgressionConstants.STR_ATK_PER_LEVEL;
			case AGILITY -> ProgressionConstants.AGI_ATK_PER_LEVEL;
			case INTELLECT -> ProgressionConstants.INT_ATK_PER_LEVEL;
			case UNIVERSAL -> ProgressionConstants.AGI_ATK_PER_LEVEL * ProgressionConstants.UNI_FACTOR;
		};
	}

	public void resetForMatch() {
		level = 1;
		xp = 0;
		gold = 0;
		skillPoints = 1;
		ranks[0] = ranks[1] = ranks[2] = ranks[3] = 0;
		attackCooldownTicks = 0;
		kills = 0;
		deaths = 0;
		assists = 0;
		clearTreeGrab();
		clearRespawn();
	}
}
