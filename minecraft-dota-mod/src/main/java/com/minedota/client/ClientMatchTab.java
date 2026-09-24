package com.minedota.client;

import com.minedota.match.MatchTabRow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom TAB scoreboard: Radiant / Dire with respawn | name | hero | lvl | KDA | items.
 */
public final class ClientMatchTab {
	private static final List<MatchTabRow> ROWS = new ArrayList<>();
	private static boolean packetInGame;

	private ClientMatchTab() {
	}

	public static void setRows(List<MatchTabRow> rows, boolean inGame) {
		ROWS.clear();
		if (rows != null) {
			ROWS.addAll(rows);
		}
		packetInGame = inGame;
	}

	public static void clear() {
		ROWS.clear();
		packetInGame = false;
	}

	public static boolean shouldShow() {
		return packetInGame || "IN_GAME".equals(ClientHeroData.getPhase());
	}

	/** True when vanilla player list should be hidden. */
	public static boolean shouldReplaceVanilla() {
		return shouldShow();
	}

	/** Draw while TAB held. Returns true if drew (caller may cancel vanilla). */
	public static boolean tryRenderWhileTabHeld(DrawContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.options == null || client.getWindow() == null) {
			return false;
		}
		KeyBinding tab = client.options.playerListKey;
		if (tab == null || !tab.isPressed()) {
			return false;
		}
		return render(ctx, client.getWindow().getScaledWidth());
	}

	/** @return true if custom board was drawn */
	public static boolean render(DrawContext ctx, int scaledWidth) {
		if (!shouldShow()) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer tr = client.textRenderer;

		List<MatchTabRow> radiant = new ArrayList<>();
		List<MatchTabRow> dire = new ArrayList<>();
		for (MatchTabRow r : ROWS) {
			if ("dire".equalsIgnoreCase(r.teamId)) {
				dire.add(r);
			} else if ("radiant".equalsIgnoreCase(r.teamId)) {
				radiant.add(r);
			} else {
				radiant.add(r);
			}
		}

		int pad = 6;
		int colResp = 28;
		int colName = 80;
		int colHero = 96;
		int colLvl = 28;
		int colKda = 56;
		int colItems = 72;
		int rowW = colResp + colName + colHero + colLvl + colKda + colItems + pad * 2;
		int headerH = 12;
		int rowH = 11;
		int teamGap = 10;
		int titleH = 14;
		int radiantRows = Math.max(1, radiant.size());
		int direRows = Math.max(1, dire.size());
		int blockH = titleH + pad
				+ headerH + radiantRows * rowH
				+ teamGap
				+ headerH + direRows * rowH
				+ pad;
		int x0 = Math.max(4, (scaledWidth - rowW) / 2);
		int y0 = 20;

		ctx.fill(x0 - 4, y0 - 4, x0 + rowW + 4, y0 + blockH + 4, 0xE0101018);
		ctx.fill(x0 - 4, y0 - 4, x0 + rowW + 4, y0 - 3, 0xFF55AAFF);

		int y = y0;
		ctx.drawText(tr, "MineDota — статистика матча", x0 + pad, y, 0xFFFFFFFF, true);
		y += titleH;

		y = drawTeam(ctx, tr, x0, y, pad, colResp, colName, colHero, colLvl, colKda, colItems,
				"RADIANT", 0xFF55FF55, radiant, rowH, headerH);
		y += teamGap;
		drawTeam(ctx, tr, x0, y, pad, colResp, colName, colHero, colLvl, colKda, colItems,
				"DIRE", 0xFFFF5555, dire, rowH, headerH);
		return true;
	}

	private static int drawTeam(DrawContext ctx, TextRenderer tr, int x0, int y, int pad,
			int colResp, int colName, int colHero, int colLvl, int colKda, int colItems,
			String title, int titleColor, List<MatchTabRow> rows, int rowH, int headerH) {
		ctx.drawText(tr, title, x0 + pad, y, titleColor, true);
		y += headerH;
		int hx = x0 + pad;
		drawCell(ctx, tr, "CD", hx, y, 0xFFCCCCCC);
		hx += colResp;
		drawCell(ctx, tr, "Ник", hx, y, 0xFFCCCCCC);
		hx += colName;
		drawCell(ctx, tr, "Герой", hx, y, 0xFFCCCCCC);
		hx += colHero;
		drawCell(ctx, tr, "Ур.", hx, y, 0xFFCCCCCC);
		hx += colLvl;
		drawCell(ctx, tr, "K/D/A", hx, y, 0xFFCCCCCC);
		hx += colKda;
		drawCell(ctx, tr, "Предметы", hx, y, 0xFFCCCCCC);
		y += rowH;

		if (rows.isEmpty()) {
			ctx.drawText(tr, "— нет игроков —", x0 + pad, y, 0xFF888888, false);
			return y + rowH;
		}
		for (MatchTabRow r : rows) {
			int x = x0 + pad;
			String cd = r.respawnSec > 0 ? String.valueOf(r.respawnSec) : "";
			drawCell(ctx, tr, cd, x, y, 0xFFFFAA00);
			x += colResp;
			drawCell(ctx, tr, truncate(r.name, 12), x, y, 0xFFFFFFFF);
			x += colName;
			drawCell(ctx, tr, truncate(r.heroName, 14), x, y, 0xFFDDDDFF);
			x += colHero;
			drawCell(ctx, tr, String.valueOf(r.level), x, y, 0xFFAAFFAA);
			x += colLvl;
			drawCell(ctx, tr, r.kills + "/" + r.deaths + "/" + r.assists, x, y, 0xFFFFFFAA);
			x += colKda;
			drawCell(ctx, tr, truncate(r.items, 10), x, y, 0xFFAAAAAA);
			y += rowH;
		}
		return y;
	}

	private static void drawCell(DrawContext ctx, TextRenderer tr, String text, int x, int y, int color) {
		ctx.drawText(tr, text == null ? "" : text, x, y, color, false);
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		if (s.length() <= max) {
			return s;
		}
		return s.substring(0, Math.max(1, max - 1)) + "…";
	}
}
