package com.minedota.team;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public enum DotaTeam {
	RADIANT("radiant", "Radiant", Formatting.GREEN),
	DIRE("dire", "Dire", Formatting.RED),
	NONE("none", "Spectator", Formatting.GRAY);

	private final String id;
	private final String displayName;
	private final Formatting color;

	DotaTeam(String id, String displayName, Formatting color) {
		this.id = id;
		this.displayName = displayName;
		this.color = color;
	}

	public String getId() {
		return id;
	}

	public Text getDisplayName() {
		return Text.literal(displayName).formatted(color);
	}

	public Formatting getColor() {
		return color;
	}

	public DotaTeam opposite() {
		return switch (this) {
			case RADIANT -> DIRE;
			case DIRE -> RADIANT;
			default -> NONE;
		};
	}

	public static DotaTeam fromId(String id) {
		for (DotaTeam team : values()) {
			if (team.id.equalsIgnoreCase(id)) {
				return team;
			}
		}
		return NONE;
	}
}
