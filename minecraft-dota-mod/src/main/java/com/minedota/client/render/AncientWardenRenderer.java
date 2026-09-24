package com.minedota.client.render;

import com.minedota.entity.AncientEntity;
import com.minedota.team.DotaTeam;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Ancient visuals = Warden mesh, tinted green/red by team. */
public class AncientWardenRenderer extends MobEntityRenderer<AncientEntity, FreeWardenModel> {
	private static final Identifier TEXTURE = new Identifier("textures/entity/warden/warden.png");

	public AncientWardenRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new FreeWardenModel(ctx.getPart(EntityModelLayers.WARDEN)), 0.9f);
	}

	@Override
	public Identifier getTexture(AncientEntity entity) {
		return TEXTURE;
	}

	@Override
	public void render(AncientEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		float[] c = tint(entity.getDotaTeam());
		VertexConsumerProvider tinted = layer -> {
			VertexConsumer base = vertexConsumers.getBuffer(layer);
			return new TintedVertexConsumer(base, c[0], c[1], c[2], 1f);
		};
		matrices.push();
		matrices.scale(1.15f, 1.15f, 1.15f);
		super.render(entity, yaw, tickDelta, matrices, tinted, light);
		matrices.pop();
	}

	@Override
	protected RenderLayer getRenderLayer(AncientEntity entity, boolean showBody, boolean translucent, boolean outline) {
		return RenderLayer.getEntityCutoutNoCull(TEXTURE);
	}

	private static float[] tint(DotaTeam team) {
		if (team == DotaTeam.RADIANT) {
			return new float[]{0.4f, 1.0f, 0.5f};
		}
		if (team == DotaTeam.DIRE) {
			return new float[]{1.0f, 0.35f, 0.3f};
		}
		return new float[]{1f, 1f, 1f};
	}
}
