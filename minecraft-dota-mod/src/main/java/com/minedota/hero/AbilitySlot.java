package com.minedota.hero;

public enum AbilitySlot {
	Q(0, "Q"),
	W(1, "W"),
	E(2, "E"),
	R(3, "R");

	private final int index;
	private final String key;

	AbilitySlot(int index, String key) {
		this.index = index;
		this.key = key;
	}

	public int getIndex() {
		return index;
	}

	public String getKey() {
		return key;
	}

	public static AbilitySlot fromIndex(int i) {
		return values()[Math.max(0, Math.min(3, i))];
	}
}
