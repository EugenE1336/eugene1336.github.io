package com.minedota.hero;

/**
 * Declarative ability effect — cast by {@link AbilityCaster}.
 */
public record AbilityDef(
		String id,
		String name,
		int cooldownTicks,
		EffectType type,
		float power,
		float radius,
		int durationTicks
) {
	public enum EffectType {
		/** Damage + particles around caster. */
		AOE_DAMAGE,
		/** Damage + brief slow/stun around caster. */
		AOE_STUN,
		/** Damage in look direction cone/line. */
		LINE_DAMAGE,
		/** Nearest enemy in range takes heavy damage. */
		TARGET_NUKE,
		/** Dash forward. */
		DASH,
		/** Short blink forward. */
		BLINK,
		/** Speed / strength buff. */
		BUFF_SELF,
		/** Heal self. */
		HEAL,
		/** Apply wither/poison to nearby enemies. */
		AOE_DOT,
		/** Pull enemies toward caster. */
		PULL_IN,
		/** No active cast — bonuses applied passively. */
		PASSIVE
	}
}
