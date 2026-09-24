package com.minedota.hero;

/**
 * Fan-made silhouette profiles (not Valve assets).
 * Scales are relative to full Steve-size mesh (1.0 ≈ Steve).
 */
public record HeroVisual(
		float bodyWide,
		float bodyTall,
		float headScale,
		float armScale,
		float legScale,
		Style style,
		float red,
		float green,
		float blue,
		float accentR,
		float accentG,
		float accentB
) {
	public enum Style {
		AXE, PUDGE, SVEN, TINY, LEGION,
		JUGG, PA, AM, DROW, SNIPER,
		CM, ZEUS, LINA, LION, WD,
		SPECTRE, VENO, ABADDON, VOID, SNAP,
		DEFAULT
	}

	public static HeroVisual forHero(String heroId) {
		return switch (heroId == null ? "" : heroId) {
			// Strength — bulky / tall
			case "axe" -> v(1.35f, 1.15f, 1.2f, 1.35f, 1.15f, Style.AXE,
					0.78f, 0.08f, 0.06f, 0.65f, 0.65f, 0.7f);
			case "pudge" -> v(1.7f, 0.95f, 1.1f, 1.5f, 1.0f, Style.PUDGE,
					0.38f, 0.55f, 0.18f, 0.3f, 0.3f, 0.32f);
			case "sven" -> v(1.3f, 1.25f, 1.1f, 1.25f, 1.15f, Style.SVEN,
					0.15f, 0.28f, 0.85f, 0.85f, 0.85f, 0.9f);
			case "tiny" -> v(1.85f, 1.45f, 1.35f, 1.55f, 1.4f, Style.TINY,
					0.55f, 0.42f, 0.28f, 0.4f, 0.35f, 0.3f);
			case "legion" -> v(1.2f, 1.18f, 1.05f, 1.2f, 1.1f, Style.LEGION,
					0.85f, 0.12f, 0.1f, 0.95f, 0.8f, 0.35f);
			// Agility — lean
			case "jugg" -> v(1.05f, 1.12f, 1.05f, 1.1f, 1.1f, Style.JUGG,
					0.9f, 0.78f, 0.45f, 0.75f, 0.75f, 0.8f);
			case "pa" -> v(0.9f, 1.15f, 0.95f, 0.95f, 1.12f, Style.PA,
					0.55f, 0.08f, 0.45f, 0.7f, 0.7f, 0.8f);
			case "am" -> v(1.0f, 1.18f, 1.05f, 1.1f, 1.12f, Style.AM,
					0.12f, 0.28f, 0.95f, 0.4f, 0.8f, 1f);
			case "drow" -> v(0.9f, 1.22f, 0.95f, 0.92f, 1.15f, Style.DROW,
					0.25f, 0.45f, 0.95f, 0.55f, 0.4f, 0.3f);
			case "sniper" -> v(0.95f, 1.05f, 1.15f, 1.0f, 1.0f, Style.SNIPER,
					0.6f, 0.4f, 0.2f, 0.25f, 0.25f, 0.25f);
			// Intelligence
			case "cm" -> v(0.85f, 1.1f, 1.0f, 0.9f, 1.05f, Style.CM,
					0.5f, 0.82f, 1f, 0.8f, 0.95f, 1f);
			case "zeus" -> v(1.1f, 1.15f, 1.3f, 1.1f, 1.1f, Style.ZEUS,
					0.25f, 0.35f, 0.95f, 1f, 0.95f, 0.2f);
			case "lina" -> v(0.9f, 1.2f, 1.05f, 0.95f, 1.12f, Style.LINA,
					1f, 0.28f, 0.08f, 1f, 0.55f, 0.05f);
			case "lion" -> v(1.0f, 1.12f, 1.2f, 1.05f, 1.05f, Style.LION,
					0.6f, 0.18f, 0.75f, 1f, 0.8f, 0.2f);
			case "wd" -> v(1.0f, 1.0f, 1.25f, 1.0f, 1.0f, Style.WD,
					0.28f, 0.6f, 0.22f, 0.7f, 0.4f, 0.8f);
			// Universal / others
			case "spectre" -> v(0.95f, 1.2f, 1.05f, 1.0f, 1.15f, Style.SPECTRE,
					0.4f, 0.15f, 0.65f, 0.8f, 0.5f, 1f);
			case "venomancer" -> v(1.15f, 1.0f, 1.3f, 1.2f, 1.0f, Style.VENO,
					0.28f, 0.8f, 0.12f, 0.5f, 1f, 0.15f);
			case "abaddon" -> v(1.25f, 1.2f, 1.12f, 1.2f, 1.15f, Style.ABADDON,
					0.18f, 0.3f, 0.38f, 0.8f, 0.82f, 0.9f);
			case "void_spirit" -> v(1.05f, 1.18f, 1.08f, 1.1f, 1.12f, Style.VOID,
					0.3f, 0.08f, 0.55f, 0.9f, 0.35f, 1f);
			case "snapfire" -> v(1.25f, 0.92f, 1.15f, 1.2f, 0.95f, Style.SNAP,
					0.85f, 0.4f, 0.18f, 1f, 0.7f, 0.3f);
			default -> v(1.1f, 1.1f, 1.05f, 1.05f, 1.05f, Style.DEFAULT, 0.7f, 0.7f, 0.7f, 0.9f, 0.9f, 0.9f);
		};
	}

	private static HeroVisual v(float wide, float tall, float head, float arm, float leg, Style style,
			float r, float g, float b, float ar, float ag, float ab) {
		return new HeroVisual(wide, tall, head, arm, leg, style, r, g, b, ar, ag, ab);
	}
}
