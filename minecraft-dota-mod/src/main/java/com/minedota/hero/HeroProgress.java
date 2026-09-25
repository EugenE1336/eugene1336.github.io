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
	private boolean rotOn;
	private int fleshHeapStacks;
	private float duelBonusAtk;
	private int helixHits;
	private int helixInternalCd;
	private int momentCourageCd;
	private int godsStrengthTicks;
	private float godsStrengthBonus;
	private int asBonusPoints;
	private int asBonusTicks;
	private int counterspellTicks;

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
		float hp = def.maxHealth() + hpGain(def.attribute()) * (level - 1);
		if ("pudge".equals(def.id()) && fleshHeapStacks > 0) {
			int r = Math.max(1, getRank(AbilitySlot.E));
			float per = 1.0f + 0.5f * r; // 1.5/2/2.5/3
			hp += fleshHeapStacks * per;
		}
		return hp;
	}

	public boolean toggleRot() {
		rotOn = !rotOn;
		return rotOn;
	}

	public boolean isRotOn() {
		return rotOn;
	}

	public void addFleshHeap(int rank) {
		fleshHeapStacks++;
	}

	public int getFleshHeapStacks() {
		return fleshHeapStacks;
	}

	public void addDuelBonus(float amount) {
		duelBonusAtk += amount;
	}

	public float getDuelBonusAtk() {
		return duelBonusAtk;
	}

	/** @return true when Helix should fire */
	public boolean tickHelixHit(int rank) {
		if (helixInternalCd > 0) {
			return false;
		}
		helixHits++;
		int need = Math.max(3, 7 - rank); // 6/5/4/3
		if (helixHits >= need) {
			helixHits = 0;
			helixInternalCd = 6; // 0.3s
			return true;
		}
		return false;
	}

	public boolean tryMomentOfCourage(int rank) {
		if (momentCourageCd > 0) {
			return false;
		}
		float chance = 0.15f + 0.10f * rank; // 25→55
		if (Math.random() >= chance) {
			return false;
		}
		momentCourageCd = (int) ((3.0f - 0.5f * rank) * 20); // 2.5→1
		return true;
	}

	public void startGodsStrength(int ticks, float bonusFraction) {
		godsStrengthTicks = ticks;
		godsStrengthBonus = bonusFraction;
	}

	public float getGodsStrengthBonus() {
		return godsStrengthTicks > 0 ? godsStrengthBonus : 0f;
	}

	public void addAttackSpeedBonus(int points, int ticks) {
		asBonusPoints = points;
		asBonusTicks = ticks;
	}

	public void armCounterspell(int ticks) {
		counterspellTicks = ticks;
	}

	public boolean consumeCounterspell() {
		if (counterspellTicks <= 0) {
			return false;
		}
		counterspellTicks = 0;
		return true;
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
		if (asBonusTicks > 0) {
			as += asBonusPoints;
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
		dmg += duelBonusAtk;
		dmg *= (1f + getGodsStrengthBonus());
		return dmg;
	}

	public float getArmor(HeroDef def) {
		float per = ProgressionConstants.ARMOR_PER_LEVEL;
		if (def.attribute() == HeroAttribute.AGILITY) {
			per *= ProgressionConstants.AGI_ARMOR_MULT;
		}
		float armor = ProgressionConstants.BASE_ARMOR + per * (level - 1);
		if ("pudge".equals(def.id())) {
			int r = getRank(AbilitySlot.E);
			if (r > 0) {
				armor += r; // +1/2/3/4
			}
		}
		return armor;
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
		float p;
		// Tiny Avalanche / Toss: +50% of base power per rank after 1 (10 → 15 → 20 → 25)
		if ("tiny".equals(hero.id())
				&& ("avalanche".equals(base.id()) || "toss".equals(base.id()))) {
			p = base.power() * (1f + 0.5f * (rank - 1));
		} else {
			float rankMult = 0.75f + 0.25f * rank;
			p = base.power() * rankMult;
		}
		p *= getSpellAmp(hero);
		// Tiny Grow: +10% to Q/W per ult rank
		if ("tiny".equals(hero.id()) && (slot == AbilitySlot.Q || slot == AbilitySlot.W)) {
			p *= 1f + 0.10f * getRank(AbilitySlot.R);
		}
		return p;
	}

	public float scaledAbilityHeal(AbilityDef base, AbilitySlot slot, HeroDef hero) {
		return scaledAbilityPower(base, slot, hero);
	}

	/**
	 * Cooldown in ticks at current rank.
	 * Default: basics −1s/rank, ults −5s/rank (min 1s). Specials per ability id.
	 */
	public int effectiveCooldownTicks(AbilityDef base, AbilitySlot slot, HeroDef hero) {
		int cd = Math.max(0, base.cooldownTicks());
		if (cd <= 0 || base.type() == AbilityDef.EffectType.PASSIVE) {
			return 0;
		}
		int rank = getRank(slot);
		if (rank <= 1) {
			return Math.max(20, cd);
		}
		int extra = rank - 1;
		cd -= switch (base.id()) {
			case "healing_ward" -> extra * 80; // −4s
			case "stifling_dagger" -> extra * 10; // −0.5s
			case "culling_blade", "dismember", "omnislash", "mana_void", "duel",
					"gods_strength", "assassinate" -> extra * 100; // −5s
			default -> extra * 20; // −1s (incl. Tiny)
		};
		return Math.max(20, cd);
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

	public void tickCombatFlags() {
		if (helixInternalCd > 0) {
			helixInternalCd--;
		}
		if (momentCourageCd > 0) {
			momentCourageCd--;
		}
		if (godsStrengthTicks > 0) {
			godsStrengthTicks--;
		}
		if (asBonusTicks > 0) {
			asBonusTicks--;
		}
		if (counterspellTicks > 0) {
			counterspellTicks--;
		}
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
		rotOn = false;
		fleshHeapStacks = 0;
		duelBonusAtk = 0;
		helixHits = 0;
		helixInternalCd = 0;
		momentCourageCd = 0;
		godsStrengthTicks = 0;
		godsStrengthBonus = 0;
		asBonusPoints = 0;
		asBonusTicks = 0;
		counterspellTicks = 0;
		clearTreeGrab();
		clearRespawn();
	}
}
