package com.minedota.hero;

public record HeroDef(
		String id,
		String name,
		HeroAttribute attribute,
		float maxHealth,
		float attackDamage,
		boolean ranged,
		float attackRange,
		AbilityDef q,
		AbilityDef w,
		AbilityDef e,
		AbilityDef r
) {
	public AbilityDef ability(AbilitySlot slot) {
		return switch (slot) {
			case Q -> q;
			case W -> w;
			case E -> e;
			case R -> r;
		};
	}

	public AbilityDef[] abilities() {
		return new AbilityDef[]{q, w, e, r};
	}

	public float meleeReach() {
		return ranged ? attackRange : Math.max(3.0f, attackRange);
	}

	/**
	 * Effective auto-attack range.
	 * Sniper Take Aim (E): +2 per rank (12 → 14/16/18/20 at ranks 1–4).
	 */
	public float effectiveAttackRange(HeroProgress prog) {
		int takeAim = prog == null ? 0 : prog.getRank(AbilitySlot.E);
		return effectiveAttackRangeFromTakeAim(takeAim);
	}

	/** Client helper when only ability ranks are available. */
	public float effectiveAttackRange(int[] ranks) {
		int takeAim = 0;
		if (ranks != null && ranks.length > AbilitySlot.E.getIndex()) {
			takeAim = ranks[AbilitySlot.E.getIndex()];
		}
		return effectiveAttackRangeFromTakeAim(takeAim);
	}

	private float effectiveAttackRangeFromTakeAim(int takeAimRank) {
		if ("sniper".equals(id)) {
			return attackRange + 2.0f * Math.max(0, takeAimRank);
		}
		return attackRange;
	}
}
