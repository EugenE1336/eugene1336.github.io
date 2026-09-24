package com.minedota.hero;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public enum HeroAttribute {
	STRENGTH("Сила", Formatting.RED),
	AGILITY("Ловкость", Formatting.GREEN),
	INTELLECT("Интеллект", Formatting.BLUE),
	UNIVERSAL("Универсал", Formatting.LIGHT_PURPLE);

	private final String title;
	private final Formatting color;

	HeroAttribute(String title, Formatting color) {
		this.title = title;
		this.color = color;
	}

	public Text getTitle() {
		return Text.literal(title).formatted(color);
	}

	public Formatting getColor() {
		return color;
	}
}
