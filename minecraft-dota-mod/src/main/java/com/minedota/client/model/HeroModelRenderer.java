package com.minedota.client.model;

import com.minedota.MineDota;
import com.minedota.client.ClientHeroData;
import com.minedota.hero.HeroVisual;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class HeroModelRenderer {
	public static final EntityModelLayer LAYER = new EntityModelLayer(new Identifier(MineDota.MOD_ID, "hero"), "main");
	private static final Identifier TEXTURE = new Identifier(MineDota.MOD_ID, "textures/entity/hero_base.png");

	private static HeroModel model;

	private HeroModelRenderer() {
	}

	private static void ensureModel() {
		if (model != null) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.getEntityModelLoader() == null) {
			return;
		}
		try {
			model = new HeroModel(mc.getEntityModelLoader().getModelPart(LAYER));
		} catch (Exception ignored) {
			// layer not ready yet
		}
	}

	public static void render(PlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider consumers, int light) {
		ensureModel();
		if (model == null) {
			return;
		}
		String heroId = ClientHeroData.getHeroId(player.getUuid());
		if (heroId.isEmpty() && player.getUuid().equals(MinecraftClient.getInstance().player != null
				? MinecraftClient.getInstance().player.getUuid() : null)) {
			heroId = ClientHeroData.getLocalHeroId();
		}
		if (heroId.isEmpty()) {
			return;
		}
		HeroVisual visual = HeroVisual.forHero(heroId);
		model.setVisual(visual);

		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
		float headYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw);
		float headPitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
		float limbSwing = player.limbAnimator.getPos(tickDelta);
		float limbSwingAmount = player.limbAnimator.getSpeed(tickDelta);
		float anim = player.age + tickDelta;

		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));
		// Full Steve scale (+ slight hero presence boost). Old mesh pivoted wrong → looked ~0.5.
		matrices.scale(-1.15f, -1.15f, 1.15f);
		matrices.translate(0.0, -1.501, 0.0);

		model.setAngles(player, limbSwing, limbSwingAmount, anim, headYaw - bodyYaw, headPitch);
		VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		model.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
		matrices.pop();
	}
}
