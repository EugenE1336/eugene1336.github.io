package com.minedota.client.render;

import com.minedota.entity.AncientEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;

/** Renders vanilla Warden mesh on AncientEntity (model bound to WardenEntity otherwise). */
public class FreeWardenModel extends EntityModel<AncientEntity> {
	private final ModelPart root;

	public FreeWardenModel(ModelPart root) {
		this.root = root;
	}

	@Override
	public void setAngles(AncientEntity entity, float limbAngle, float limbDistance,
			float animationProgress, float headYaw, float headPitch) {
		// Static throne — no walk cycle
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
			float red, float green, float blue, float alpha) {
		root.render(matrices, vertices, light, overlay, red, green, blue, alpha);
	}
}
