package com.minedota.hero;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.minedota.hero.AbilityDef.EffectType;

/**
 * 20 popular heroes: 5 per attribute.
 */
public final class HeroCatalog {
	private static final Map<String, HeroDef> BY_ID = new LinkedHashMap<>();

	static {
		// —— Strength (melee) ——
		add(melee("axe", "Axe", HeroAttribute.STRENGTH, 36, 7,
				abil("berserkers_call", "Berserker's Call", 14 * 20, EffectType.AOE_STUN, 0, 4, 30),
				abil("battle_hunger", "Battle Hunger", 12 * 20, EffectType.TARGET_NUKE, 2, 8, 120),
				abil("counter_helix", "Counter Helix", 0, EffectType.PASSIVE, 8, 3, 0),
				abil("culling_blade", "Culling Blade", 30 * 20, EffectType.TARGET_NUKE, 12, 3, 0)));

		add(melee("pudge", "Pudge", HeroAttribute.STRENGTH, 40, 6,
				abil("meat_hook", "Meat Hook", 14 * 20, EffectType.LINE_DAMAGE, 10, 10, 0),
				abil("rot", "Rot", 0, EffectType.AOE_DOT, 3, 3.5f, 0),
				abil("flesh_heap", "Flesh Heap", 0, EffectType.PASSIVE, 0, 0, 0),
				abil("dismember", "Dismember", 30 * 20, EffectType.TARGET_NUKE, 6, 2, 50)));

		add(melee("sven", "Sven", HeroAttribute.STRENGTH, 38, 8,
				abil("storm_hammer", "Storm Hammer", 13 * 20, EffectType.LINE_DAMAGE, 8, 10, 24),
				abil("great_cleave", "Great Cleave", 0, EffectType.PASSIVE, 0, 2.5f, 0),
				abil("warcry", "Warcry", 18 * 20, EffectType.BUFF_SELF, 0, 6, 120),
				abil("gods_strength", "God's Strength", 40 * 20, EffectType.BUFF_SELF, 0, 0, 240)));

		add(melee("tiny", "Tiny", HeroAttribute.STRENGTH, 42, 9,
				abil("avalanche", "Avalanche", 14 * 20, EffectType.AOE_STUN, 10, 5, 30),
				abil("toss", "Toss", 10 * 20, EffectType.TARGET_NUKE, 11, 1, 0),
				abil("tree_grab", "Tree Grab", 15 * 20, EffectType.BUFF_SELF, 0, 2, 0),
				abil("grow", "Grow", 0, EffectType.PASSIVE, 0, 0, 0)));

		add(melee("legion", "Legion Commander", HeroAttribute.STRENGTH, 37, 7,
				abil("overwhelming_odds", "Overwhelming Odds", 12 * 20, EffectType.AOE_DAMAGE, 8, 5, 60),
				abil("press_the_attack", "Press The Attack", 14 * 20, EffectType.HEAL, 3, 0, 100),
				abil("moment_of_courage", "Moment of Courage", 0, EffectType.PASSIVE, 0, 0, 0),
				abil("duel", "Duel", 35 * 20, EffectType.TARGET_NUKE, 0, 2, 60)));

		// —— Agility ——
		add(melee("jugg", "Juggernaut", HeroAttribute.AGILITY, 32, 8,
				abil("blade_fury", "Blade Fury", 16 * 20, EffectType.AOE_DOT, 4, 3.5f, 80),
				abil("healing_ward", "Healing Ward", 30 * 20, EffectType.HEAL, 2, 5, 160),
				abil("blade_dance", "Blade Dance", 0, EffectType.PASSIVE, 0, 0, 0),
				abil("omnislash", "Omnislash", 35 * 20, EffectType.TARGET_NUKE, 0, 5, 60)));

		add(melee("pa", "Phantom Assassin", HeroAttribute.AGILITY, 30, 9,
				abil("stifling_dagger", "Stifling Dagger", 6 * 20, EffectType.LINE_DAMAGE, 0, 15, 40),
				abil("phantom_strike", "Phantom Strike", 10 * 20, EffectType.BLINK, 0, 10, 50),
				abil("blur", "Blur", 0, EffectType.PASSIVE, 0, 10, 0),
				abil("coup_de_grace", "Coup de Grace", 0, EffectType.PASSIVE, 0, 0, 0)));

		add(melee("am", "Anti-Mage", HeroAttribute.AGILITY, 31, 8,
				abil("mana_break", "Mana Break", 0, EffectType.PASSIVE, 0, 0, 0),
				abil("blink", "Blink", 8 * 20, EffectType.BLINK, 0, 12, 0),
				abil("counterspell", "Counterspell", 14 * 20, EffectType.BUFF_SELF, 0, 0, 24),
				abil("mana_void", "Mana Void", 40 * 20, EffectType.AOE_DAMAGE, 8, 5, 0)));

		add(ranged("drow", "Drow Ranger", HeroAttribute.AGILITY, 30, 8,
				abil("frost_arrows", "Frost Arrows", 0, EffectType.PASSIVE, 2, 0, 30),
				abil("gust", "Gust", 13 * 20, EffectType.AOE_STUN, 0, 6, 40),
				abil("multishot", "Multishot", 18 * 20, EffectType.LINE_DAMAGE, 0, 16, 36),
				abil("marksmanship", "Marksmanship", 0, EffectType.PASSIVE, 5, 3, 0)));

		add(ranged("sniper", "Sniper", HeroAttribute.AGILITY, 28, 9,
				abil("shrapnel", "Shrapnel", 12 * 20, EffectType.AOE_DOT, 3, 3.5f, 120),
				abil("headshot", "Headshot", 0, EffectType.PASSIVE, 3, 0, 0),
				abil("take_aim", "Take Aim", 0, EffectType.PASSIVE, 0, 0, 0),
				abil("assassinate", "Assassinate", 25 * 20, EffectType.LINE_DAMAGE, 18, 28, 34)));

		// —— Intellect ——
		add(ranged("cm", "Crystal Maiden", HeroAttribute.INTELLECT, 26, 5,
				abil("crystal_nova", "Crystal Nova", 10 * 20, EffectType.AOE_STUN, 8, 5, 40),
				abil("frostbite", "Frostbite", 8 * 20, EffectType.TARGET_NUKE, 10, 8, 50),
				abil("arcane_aura", "Arcane Aura", 20 * 20, EffectType.BUFF_SELF, 0, 0, 200),
				abil("freezing_field", "Freezing Field", 55 * 20, EffectType.AOE_DOT, 6, 7, 100)));

		add(ranged("zeus", "Zeus", HeroAttribute.INTELLECT, 27, 6,
				abil("arc_lightning", "Arc Lightning", 5 * 20, EffectType.TARGET_NUKE, 8, 14, 0),
				abil("lightning_bolt", "Lightning Bolt", 7 * 20, EffectType.TARGET_NUKE, 12, 18, 20),
				abil("heavenly_jump", "Heavenly Jump", 12 * 20, EffectType.AOE_STUN, 6, 4, 20),
				abil("thundergods_wrath", "Thundergod's Wrath", 50 * 20, EffectType.AOE_DAMAGE, 14, 32, 0)));

		add(ranged("lina", "Lina", HeroAttribute.INTELLECT, 27, 7,
				abil("dragon_slave", "Dragon Slave", 8 * 20, EffectType.LINE_DAMAGE, 11, 14, 0),
				abil("light_strike_array", "Light Strike Array", 10 * 20, EffectType.AOE_STUN, 10, 4, 35),
				abil("fiery_soul", "Fiery Soul", 0, EffectType.BUFF_SELF, 0, 0, 300),
				abil("laguna_blade", "Laguna Blade", 40 * 20, EffectType.TARGET_NUKE, 24, 16, 0)));

		add(ranged("lion", "Lion", HeroAttribute.INTELLECT, 27, 6,
				abil("earth_spike", "Earth Spike", 12 * 20, EffectType.LINE_DAMAGE, 10, 12, 30),
				abil("hex", "Hex", 14 * 20, EffectType.TARGET_NUKE, 4, 10, 50),
				abil("mana_drain", "Mana Drain", 8 * 20, EffectType.TARGET_NUKE, 8, 12, 60),
				abil("finger_of_death", "Finger of Death", 50 * 20, EffectType.TARGET_NUKE, 26, 14, 0)));

		add(ranged("wd", "Witch Doctor", HeroAttribute.INTELLECT, 28, 5,
				abil("paralyzing_cask", "Paralyzing Cask", 12 * 20, EffectType.LINE_DAMAGE, 8, 12, 40),
				abil("voodoo_restoration", "Voodoo Restoration", 0, EffectType.HEAL, 4, 5, 100),
				abil("maledict", "Maledict", 14 * 20, EffectType.AOE_DOT, 5, 5, 80),
				abil("death_ward", "Death Ward", 45 * 20, EffectType.AOE_DAMAGE, 12, 8, 120)));

		// —— Universal ——
		add(melee("spectre", "Spectre", HeroAttribute.UNIVERSAL, 34, 7,
				abil("spectral_dagger", "Spectral Dagger", 10 * 20, EffectType.LINE_DAMAGE, 10, 14, 40),
				abil("desolate", "Desolate", 8 * 20, EffectType.TARGET_NUKE, 9, 6, 0),
				abil("dispersion", "Dispersion", 15 * 20, EffectType.BUFF_SELF, 0, 0, 160),
				abil("haunt", "Haunt", 60 * 20, EffectType.AOE_DAMAGE, 12, 20, 0)));

		add(ranged("venomancer", "Venomancer", HeroAttribute.UNIVERSAL, 30, 6,
				abil("venomous_gale", "Venomous Gale", 12 * 20, EffectType.LINE_DAMAGE, 8, 14, 60),
				abil("poison_sting", "Poison Sting", 5 * 20, EffectType.TARGET_NUKE, 6, 10, 60),
				abil("plague_ward", "Plague Ward", 8 * 20, EffectType.AOE_DOT, 3, 3, 120),
				abil("poison_nova", "Poison Nova", 50 * 20, EffectType.AOE_DOT, 5, 8, 140)));

		add(melee("abaddon", "Abaddon", HeroAttribute.UNIVERSAL, 36, 6,
				abil("mist_coil", "Mist Coil", 6 * 20, EffectType.HEAL, 10, 0, 0),
				abil("aphotic_shield", "Aphotic Shield", 10 * 20, EffectType.BUFF_SELF, 0, 0, 120),
				abil("curse_of_avernus", "Curse of Avernus", 8 * 20, EffectType.TARGET_NUKE, 7, 8, 40),
				abil("borrowed_time", "Borrowed Time", 40 * 20, EffectType.HEAL, 20, 0, 100)));

		add(melee("void_spirit", "Void Spirit", HeroAttribute.UNIVERSAL, 32, 8,
				abil("aether_remnant", "Aether Remnant", 12 * 20, EffectType.LINE_DAMAGE, 10, 12, 40),
				abil("dissimilate", "Dissimilate", 14 * 20, EffectType.BLINK, 0, 8, 20),
				abil("resonant_pulse", "Resonant Pulse", 10 * 20, EffectType.AOE_DAMAGE, 9, 4, 0),
				abil("astral_step", "Astral Step", 16 * 20, EffectType.DASH, 8, 10, 0)));

		add(ranged("snapfire", "Snapfire", HeroAttribute.UNIVERSAL, 33, 7,
				abil("scatterblast", "Scatterblast", 10 * 20, EffectType.LINE_DAMAGE, 11, 12, 20),
				abil("firesnap_cookie", "Firesnap Cookie", 12 * 20, EffectType.DASH, 0, 8, 0),
				abil("lil_shredder", "Lil' Shredder", 8 * 20, EffectType.BUFF_SELF, 0, 0, 80),
				abil("mortimer_kisses", "Mortimer Kisses", 55 * 20, EffectType.AOE_DOT, 7, 10, 120)));
	}

	private HeroCatalog() {
	}

	private static AbilityDef abil(String id, String name, int cd, EffectType type, float power, float radius, int duration) {
		int cdTicks = type == EffectType.PASSIVE ? Math.max(0, cd) : (cd <= 0 ? 0 : Math.max(cd, 20));
		return new AbilityDef(id, name, cdTicks, type, power, radius, duration);
	}

	private static HeroDef hero(String id, String name, HeroAttribute attr, float hp, float dmg, boolean ranged,
			AbilityDef q, AbilityDef w, AbilityDef e, AbilityDef r) {
		float range = ranged ? 12.0f : 3.0f;
		return new HeroDef(id, name, attr, hp, dmg, ranged, range, q, w, e, r);
	}

	private static HeroDef melee(String id, String name, HeroAttribute attr, float hp, float dmg,
			AbilityDef q, AbilityDef w, AbilityDef e, AbilityDef r) {
		return hero(id, name, attr, hp, dmg, false, q, w, e, r);
	}

	private static HeroDef ranged(String id, String name, HeroAttribute attr, float hp, float dmg,
			AbilityDef q, AbilityDef w, AbilityDef e, AbilityDef r) {
		return hero(id, name, attr, hp, dmg, true, q, w, e, r);
	}

	private static void add(HeroDef def) {
		BY_ID.put(def.id(), def);
	}

	public static HeroDef get(String id) {
		return BY_ID.get(id);
	}

	public static List<HeroDef> all() {
		return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
	}

	public static List<HeroDef> byAttribute(HeroAttribute attribute) {
		List<HeroDef> list = new ArrayList<>();
		for (HeroDef h : BY_ID.values()) {
			if (h.attribute() == attribute) {
				list.add(h);
			}
		}
		return list;
	}
}
