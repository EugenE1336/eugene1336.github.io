package com.minedota.client.render;

import com.minedota.entity.TowerEntity;
import com.minedota.team.DotaTeam;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.BlazeEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Tower visuals = Blaze, tinted green/red by team. Mechanics stay on TowerEntity. */
public class TowerBlazeRenderer extends MobEntityRenderer<TowerEntity, BlazeEntityModel<TowerEntity>> {
	private static final Identifier TEXTURE = new Identifier("textures/entity/blaze.png");

	public TowerBlazeRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new BlazeEntityModel<>(ctx.getPart(EntityModelLayers.BLAZE)), 0.5f);
	}

	@Override
	public Identifier getTexture(TowerEntity entity) {
		return TEXTURE;
	}

	@Override
	public void render(TowerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		float[] c = tint(entity.getDotaTeam());
		VertexConsumerProvider tinted = layer -> {
			VertexConsumer base = vertexConsumers.getBuffer(layer);
			return new TintedVertexConsumer(base, c[0], c[1], c[2], 1f);
		};
		matrices.push();
		matrices.scale(1.35f, 1.35f, 1.35f);
		super.render(entity, yaw, tickDelta, matrices, tinted, light);
		matrices.pop();
	}

	@Override
	protected RenderLayer getRenderLayer(TowerEntity entity, boolean showBody, boolean translucent, boolean outline) {
		return RenderLayer.getEntityCutoutNoCull(TEXTURE);
	}

	private static float[] tint(DotaTeam team) {
		if (team == DotaTeam.RADIANT) {
			return new float[]{0.45f, 1.0f, 0.45f};
		}
		if (team == DotaTeam.DIRE) {
			return new float[]{1.0f, 0.4f, 0.35f};
		}
		return new float[]{1f, 1f, 1f};
	}
}
