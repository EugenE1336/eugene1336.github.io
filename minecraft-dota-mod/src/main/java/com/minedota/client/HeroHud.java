package com.minedota.client;

import com.minedota.hero.AbilityDef;
import com.minedota.hero.AbilitySlot;
import com.minedota.hero.HeroCatalog;
import com.minedota.hero.HeroDef;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class HeroHud {
	private static String heroId = "";
	private static final int[] CDS = new int[4];
	private static final int[] RANKS = new int[4];
	private static int level = 1;
	private static int xp;
	private static int xpToNext = 200;
	private static int gold;
	private static int skillPoints;
	private static int attackCd;
	private static int respawnCd;
	private static float attackDamage;
	private static float armor;
	private static int kills;
	private static int deaths;
	private static int assists;
	private static int treeGrabHits;
	private static int attackSpeed = 100;

	private HeroHud() {
	}

	public static void setState(String id, int[] cds, int lvl, int curXp, int nextXp, int g, int sp,
			int[] ranks, int atkCd, int respCd, float atk, float arm) {
		setState(id, cds, lvl, curXp, nextXp, g, sp, ranks, atkCd, respCd, atk, arm, 0, 0, 0, 0, 100);
	}

	public static void setState(String id, int[] cds, int lvl, int curXp, int nextXp, int g, int sp,
			int[] ranks, int atkCd, int respCd, float atk, float arm, int k, int d, int a) {
		setState(id, cds, lvl, curXp, nextXp, g, sp, ranks, atkCd, respCd, atk, arm, k, d, a, 0, 100);
	}

	public static void setState(String id, int[] cds, int lvl, int curXp, int nextXp, int g, int sp,
			int[] ranks, int atkCd, int respCd, float atk, float arm, int k, int d, int a, int treeHits) {
		setState(id, cds, lvl, curXp, nextXp, g, sp, ranks, atkCd, respCd, atk, arm, k, d, a, treeHits, 100);
	}

	public static void setState(String id, int[] cds, int lvl, int curXp, int nextXp, int g, int sp,
			int[] ranks, int atkCd, int respCd, float atk, float arm, int k, int d, int a, int treeHits, int as) {
		heroId = id == null ? "" : id;
		if (cds != null) {
			System.arraycopy(cds, 0, CDS, 0, Math.min(4, cds.length));
		}
		level = lvl;
		xp = curXp;
		xpToNext = Math.max(0, nextXp);
		gold = g;
		skillPoints = sp;
		if (ranks != null) {
			System.arraycopy(ranks, 0, RANKS, 0, Math.min(4, ranks.length));
		}
		attackCd = atkCd;
		respawnCd = respCd;
		attackDamage = atk;
		armor = arm;
		kills = k;
		deaths = d;
		assists = a;
		treeGrabHits = Math.max(0, treeHits);
		attackSpeed = Math.max(20, Math.min(300, as));
	}

	public static int[] getRanks() {
		return RANKS.clone();
	}

	public static void tickClientCds() {
		for (int i = 0; i < 4; i++) {
			if (CDS[i] > 0) {
				CDS[i]--;
			}
		}
		if (attackCd > 0) {
			attackCd--;
		}
		if (respawnCd > 0) {
			respawnCd--;
		}
	}

	public static void register() {
		HudRenderCallback.EVENT.register(HeroHud::render);
	}

	private static String formatClock(int totalSec) {
		int m = totalSec / 60;
		int s = totalSec % 60;
		return String.format("%d:%02d", m, s);
	}

	private static void render(DrawContext context, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden) {
			return;
		}

		String phase = ClientHeroData.getPhase();
		int sec = ClientHeroData.getPhaseSeconds();
		int sw = mc.getWindow().getScaledWidth();
		if ("HERO_SELECT".equals(phase) || "SHOP".equals(phase)) {
			String label = "HERO_SELECT".equals(phase) ? "Пик героев" : "Закуп";
			String timer = label + ": " + sec + "с";
			int tw = mc.textRenderer.getWidth(timer);
			context.drawTextWithShadow(mc.textRenderer, timer, sw / 2 - tw / 2, 12, 0xFFFFFF00);
		}

		if ("IN_GAME".equals(phase)) {
			int matchSec = ClientHeroData.getMatchSeconds();
			int waveSec = ClientHeroData.getNextWaveSeconds();
			String top;
			if (ClientHeroData.isPrepCountdown()) {
				top = "До начала: " + matchSec + "с  |  1 волна через " + waveSec + "с";
			} else {
				top = "Матч " + formatClock(matchSec) + "  |  Волна через " + waveSec + "с";
			}
			int tw = mc.textRenderer.getWidth(top);
			context.drawTextWithShadow(mc.textRenderer, top, sw / 2 - tw / 2, 8, 0xFFFFFFAA);
		}

		if (heroId.isEmpty()) {
			return;
		}
		HeroDef hero = HeroCatalog.get(heroId);
		if (hero == null) {
			return;
		}

		int sh = mc.getWindow().getScaledHeight();

		String goldStr = "G: " + gold;
		String lvlStr = "Ур." + level + (skillPoints > 0 ? "  SP:" + skillPoints : "");
		String kdaStr = "K/D/A " + kills + "/" + deaths + "/" + assists;
		context.drawTextWithShadow(mc.textRenderer, goldStr, sw - 8 - mc.textRenderer.getWidth(goldStr), 8, 0xFFFFD700);
		context.drawTextWithShadow(mc.textRenderer, lvlStr, sw - 8 - mc.textRenderer.getWidth(lvlStr), 20, 0xFF88FF88);
		context.drawTextWithShadow(mc.textRenderer, kdaStr, sw - 8 - mc.textRenderer.getWidth(kdaStr), 32, 0xFFFFCCCC);

		if (xpToNext > 0 && level < 15) {
			int barW = 80;
			int barX = sw - 8 - barW;
			int barY = 44;
			context.fill(barX, barY, barX + barW, barY + 4, 0xAA000000);
			int fill = (int) (barW * Math.min(1f, (float) xp / xpToNext));
			context.fill(barX, barY, barX + fill, barY + 4, 0xFF44AAFF);
		}

		if ("IN_GAME".equals(phase)) {
			float range = hero.effectiveAttackRange(RANKS);
			String atkStr;
			if (treeGrabHits > 0) {
				float treeBonus = 2f * Math.max(1, Math.min(4, RANKS[AbilitySlot.E.getIndex()]));
				atkStr = String.format("ATK %.1f (+%.0f Tree)", attackDamage, treeBonus);
			} else {
				atkStr = String.format("ATK %.1f", attackDamage);
			}
			String armStr = String.format("ARM %.1f", armor);
			String rngStr = String.format("RNG %.0f", range);
			String attrStr = hero.attribute().getTitle().getString();
			context.drawTextWithShadow(mc.textRenderer, atkStr, 8, 8,
					treeGrabHits > 0 ? 0xFFFFFF55 : 0xFFFFAAAA);
			context.drawTextWithShadow(mc.textRenderer, armStr, 8, 20, 0xFFAAAAFF);
			context.drawTextWithShadow(mc.textRenderer, rngStr, 8, 32, 0xFFAAFFAA);
			context.drawTextWithShadow(mc.textRenderer, attrStr, 8, 44, 0xFFDDDDDD);
			float interval = 2.0f - 0.005f * attackSpeed;
			int growRank = "tiny".equals(heroId) ? RANKS[AbilitySlot.R.getIndex()] : 0;
			String asStr = growRank > 0
					? String.format("AS %d (%.2fs) Grow-%d", attackSpeed, interval, growRank * 30)
					: String.format("AS %d (%.2fs)", attackSpeed, interval);
			context.drawTextWithShadow(mc.textRenderer, asStr, 8, 56,
					growRank > 0 ? 0xFFFF8866 : 0xFFFFCC88);
		}

		if (respawnCd > 0) {
			String rs = "Респаун: " + ((respawnCd + 19) / 20) + "с";
			int tw = mc.textRenderer.getWidth(rs);
			context.drawTextWithShadow(mc.textRenderer, rs, sw / 2 - tw / 2, sh / 2 - 40, 0xFFFF4444);
		}

		// Left of hotbar — chat sits bottom-left upward; keep skills clear of message stream
		int totalW = 4 * 48 + 3 * 4;
		int hotbarLeft = sw / 2 - 91;
		int startX = Math.max(8, hotbarLeft - totalW - 10);
		int baseY = sh - 66;

		String nameLine = hero.name() + (attackCd > 0 ? "  ATK " + ((attackCd + 19) / 20) + "с" : "");
		context.drawTextWithShadow(mc.textRenderer,
				Text.literal(nameLine).formatted(hero.attribute().getColor()),
				startX, baseY - 12, 0xFFFFFF);

		for (AbilitySlot slot : AbilitySlot.values()) {
			int i = slot.getIndex();
			int x = startX + i * 52;
			int y = baseY;
			AbilityDef ab = hero.ability(slot);
			int bg = switch (hero.attribute()) {
				case STRENGTH -> 0xAA881111;
				case AGILITY -> 0xAA118811;
				case INTELLECT -> 0xAA114488;
				case UNIVERSAL -> 0xAA661188;
			};
			if (RANKS[i] <= 0) {
				bg = 0xAA333333;
			}
			context.fill(x, y, x + 48, y + 28, bg);
			context.fill(x, y, x + 48, y + 1, 0xFFFFFFFF);
			String keyLabel = ClientNetworking.abilityKeyLabel(i);
			context.drawTextWithShadow(mc.textRenderer, keyLabel, x + 3, y + 2, 0xFFFFFF);

			var matrices = context.getMatrices();
			matrices.push();
			matrices.translate(x + 3, y + 12, 0);
			matrices.scale(0.65f, 0.65f, 1f);
			context.drawTextWithShadow(mc.textRenderer, ab.name(), 0, 0, 0xFFDDDDDD);
			matrices.pop();

			int maxRank = slot == AbilitySlot.R ? 3 : 4;
			for (int r = 0; r < maxRank; r++) {
				int px = x + 3 + r * 6;
				int py = y + 23;
				int col = r < RANKS[i] ? 0xFFFFFF00 : 0xFF555555;
				context.fill(px, py, px + 4, py + 3, col);
			}

			if (CDS[i] > 0) {
				context.fill(x, y, x + 48, y + 28, 0x99000000);
				int secLeft = (CDS[i] + 19) / 20;
				String cdSec = String.valueOf(Math.max(1, secLeft));
				int tw = mc.textRenderer.getWidth(cdSec);
				context.drawTextWithShadow(mc.textRenderer, cdSec, x + 24 - tw / 2, y + 10, 0xFFFFFF);
			} else if (treeGrabHits > 0 && "tree_grab".equals(ab.id())) {
				String hits = String.valueOf(treeGrabHits);
				int tw = mc.textRenderer.getWidth(hits);
				context.drawTextWithShadow(mc.textRenderer, hits, x + 48 - tw - 3, y + 2, 0xFFFFFF55);
			}
		}

		if (skillPoints > 0 && "IN_GAME".equals(phase)) {
			String k0 = ClientNetworking.abilityKeyLabel(0);
			String k1 = ClientNetworking.abilityKeyLabel(1);
			String k2 = ClientNetworking.abilityKeyLabel(2);
			String k3 = ClientNetworking.abilityKeyLabel(3);
			String hint = "Ctrl+" + k0 + "/" + k1 + "/" + k2 + "/" + k3 + " — апгрейд";
			context.drawTextWithShadow(mc.textRenderer, hint, startX, baseY - 24, 0xFFFFFF55);
		}
	}
}
