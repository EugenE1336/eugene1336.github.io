package com.minedota.client;

import com.minedota.hero.AbilitySlot;
import com.minedota.hero.HeroAttribute;
import com.minedota.hero.HeroCatalog;
import com.minedota.hero.HeroDef;
import com.minedota.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HeroSelectScreen extends Screen {
	private static final Map<UUID, String> TAKEN = new HashMap<>();
	private HeroAttribute tab = HeroAttribute.STRENGTH;

	public HeroSelectScreen() {
		super(Text.literal("Выбор героя"));
	}

	public static void updateTaken(Map<UUID, String> picks) {
		TAKEN.clear();
		TAKEN.putAll(picks);
	}

	public static Set<String> takenHeroIds() {
		return new HashSet<>(TAKEN.values());
	}

	@Override
	protected void init() {
		clearChildren();
		int tabY = 28;
		int tx = width / 2 - 160;
		for (HeroAttribute attr : HeroAttribute.values()) {
			HeroAttribute a = attr;
			addDrawableChild(ButtonWidget.builder(attr.getTitle(), b -> {
				tab = a;
				rebuild();
			}).dimensions(tx, tabY, 78, 18).build());
			tx += 82;
		}

		int y = 56;
		int col = 0;
		Set<String> taken = takenHeroIds();
		UUID self = client != null && client.player != null ? client.player.getUuid() : null;
		String myHero = self == null ? null : TAKEN.get(self);

		for (HeroDef hero : HeroCatalog.byAttribute(tab)) {
			boolean takenByOther = taken.contains(hero.id()) && !hero.id().equals(myHero);
			boolean mine = hero.id().equals(myHero);
			Text label = Text.literal(hero.name())
					.formatted(mine ? Formatting.GREEN : (takenByOther ? Formatting.DARK_GRAY : hero.attribute().getColor()));
			if (takenByOther) {
				label = Text.literal(hero.name() + " ✕").formatted(Formatting.DARK_GRAY);
			} else if (mine) {
				label = Text.literal("★ " + hero.name()).formatted(Formatting.GREEN);
			}
			int bx = width / 2 - 160 + col * 165;
			int by = y;
			String heroId = hero.id();
			ButtonWidget btn = ButtonWidget.builder(label, b -> {
				PacketByteBuf buf = PacketByteBufs.create();
				buf.writeString(heroId);
				ClientPlayNetworking.send(ModNetworking.SELECT_HERO, buf);
			}).dimensions(bx, by, 155, 20).build();
			btn.active = !takenByOther;
			addDrawableChild(btn);
			col++;
			if (col >= 2) {
				col = 0;
				y += 24;
			}
		}

		addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close())
				.dimensions(width / 2 - 40, height - 28, 80, 20).build());
	}

	private void rebuild() {
		clearChildren();
		init();
	}

	/** Refresh buttons when picks sync arrives. */
	public void clearAndInitPublic() {
		rebuild();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.literal("Занятого героя взять нельзя").formatted(Formatting.GRAY),
				width / 2, 42, 0xAAAAAA);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
