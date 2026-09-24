package com.minedota.hero;

/**
 * Locked progression numbers (design TZ).
 */
public final class ProgressionConstants {
	public static final int MAX_LEVEL = 15;
	public static final int MAX_RANK_BASIC = 4;
	public static final int MAX_RANK_ULT = 3;

	/** Ultimate rank N requires hero level >= this. */
	public static final int[] ULT_LEVEL_REQ = {0, 6, 11, 15};

	/**
	 * Basic ability rank N requires hero level >= this (DotA: 1 / 3 / 5 / 7).
	 * Index = rank; unused 0.
	 */
	public static final int[] BASIC_LEVEL_REQ = {0, 1, 3, 5, 7};

	public static final int REWARD_RADIUS = 15;

	public static final int CREEP_XP = 40;
	public static final int CREEP_GOLD = 40;

	public static final int HERO_XP_BASE = 100;
	public static final int HERO_XP_PER_LEVEL = 20;

	public static final int HERO_GOLD_BASE = 250;
	public static final int HERO_GOLD_PER_LEVEL = 30;
	public static final int ASSIST_GOLD_PER_LEVEL = 25;

	public static final int TOWER_GOLD = 150;

	public static final int ATTACK_COOLDOWN_TICKS = 30; // legacy: 100 AS → 1.5s

	/**
	 * Attack speed: 100 = 1.5s/hit, 200 = 1.0s/hit, 300 = 0.5s/hit (cap).
	 * intervalSec = 2.0 − 0.005 × AS
	 */
	public static final int BASE_ATTACK_SPEED = 100;
	public static final int ATTACK_SPEED_PER_LEVEL = 5;
	public static final int MAX_ATTACK_SPEED = 300;
	public static final int MIN_ATTACK_SPEED = 20;
	/** Tiny Grow: AS reduction per ult rank (noticeable on attack interval). */
	public static final int TINY_GROW_AS_PENALTY = 30;

	/** Seconds between auto-attacks for given AS. */
	public static float attackIntervalSeconds(int attackSpeed) {
		int as = Math.max(MIN_ATTACK_SPEED, Math.min(MAX_ATTACK_SPEED, attackSpeed));
		return 2.0f - 0.005f * as;
	}

	public static int attackIntervalTicks(int attackSpeed) {
		return Math.max(1, Math.round(attackIntervalSeconds(attackSpeed) * 20f));
	}

	public static final float BASE_ARMOR = 1.0f;
	public static final float ARMOR_PER_LEVEL = 0.2f;
	public static final float AGI_ARMOR_MULT = 1.5f;

	public static final float STR_HP_PER_LEVEL = 4.0f;
	public static final float STR_ATK_PER_LEVEL = 0.5f;

	public static final float AGI_HP_PER_LEVEL = 2.0f;
	public static final float AGI_ATK_PER_LEVEL = 1.5f;

	public static final float INT_HP_PER_LEVEL = 2.0f;
	public static final float INT_ATK_PER_LEVEL = 0.5f;
	public static final float INT_SPELL_AMP_PER_LEVEL = 0.01f;

	public static final float UNI_FACTOR = 0.75f;

	private ProgressionConstants() {
	}

	/** XP needed to go from {@code level} to {@code level + 1}. */
	public static int xpToNext(int level) {
		if (level < 1 || level >= MAX_LEVEL) {
			return Integer.MAX_VALUE;
		}
		return (int) Math.round(200.0 * Math.pow(1.2, level - 1));
	}

	public static int respawnTicks(int level) {
		return (10 + 2 * Math.max(1, level)) * 20;
	}
}
