package com.minedota.client.model;

import com.minedota.hero.HeroVisual;
import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Detailed original silhouettes inspired by DotA archetypes (fan art geometry, not Valve meshes).
 */
public class HeroModel extends EntityModel<LivingEntity> {
	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart belly;
	private final ModelPart chest;
	private final ModelPart head;
	private final ModelPart helm;
	private final ModelPart crest;
	private final ModelPart hood;
	private final ModelPart horns;
	private final ModelPart mask;
	private final ModelPart leftArm;
	private final ModelPart rightArm;
	private final ModelPart leftPad;
	private final ModelPart rightPad;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;
	private final ModelPart cape;
	private final Map<String, ModelPart> props = new HashMap<>();
	private HeroVisual visual = HeroVisual.forHero("");

	public HeroModel(ModelPart root) {
		this.root = root;
		this.body = root.getChild("body");
		this.belly = root.getChild("belly");
		this.chest = root.getChild("chest");
		this.head = root.getChild("head");
		this.helm = root.getChild("helm");
		this.crest = root.getChild("crest");
		this.hood = root.getChild("hood");
		this.horns = root.getChild("horns");
		this.mask = root.getChild("mask");
		this.leftArm = root.getChild("left_arm");
		this.rightArm = root.getChild("right_arm");
		this.leftPad = root.getChild("left_pad");
		this.rightPad = root.getChild("right_pad");
		this.leftLeg = root.getChild("left_leg");
		this.rightLeg = root.getChild("right_leg");
		this.cape = root.getChild("cape");
		for (String name : PROP_NAMES) {
			props.put(name, root.getChild(name));
		}
	}

	private static final String[] PROP_NAMES = {
			"prop_axe", "prop_hook", "prop_hammer", "prop_rock", "prop_sword",
			"prop_blades", "prop_dagger", "prop_bow", "prop_rifle",
			"prop_staff", "prop_scythe", "prop_ward", "prop_shield",
			"prop_orb", "prop_gun", "prop_fangs"
	};

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		root.addChild("body", ModelPartBuilder.create()
						.uv(16, 16).cuboid(-4, 0, -2, 8, 12, 4),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("belly", ModelPartBuilder.create()
						.uv(16, 32).cuboid(-5, 2, -4, 10, 8, 5),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("chest", ModelPartBuilder.create()
						.uv(0, 48).cuboid(-4.5f, 0, -3, 9, 7, 2),
				ModelTransform.pivot(0, 0, 0));

		root.addChild("head", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-4, -8, -4, 8, 8, 8),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("helm", ModelPartBuilder.create()
						.uv(32, 0).cuboid(-4.5f, -9, -4.5f, 9, 4, 9),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("crest", ModelPartBuilder.create()
						.uv(48, 16).cuboid(-1, -14, -1, 2, 6, 6),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("hood", ModelPartBuilder.create()
						.uv(32, 32).cuboid(-5, -9, -5, 10, 10, 8),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("horns", ModelPartBuilder.create()
						.uv(0, 32).cuboid(-7, -12, -1, 3, 6, 2)
						.cuboid(4, -12, -1, 3, 6, 2),
				ModelTransform.pivot(0, 0, 0));
		root.addChild("mask", ModelPartBuilder.create()
						.uv(48, 48).cuboid(-3.5f, -7, -5, 7, 6, 1),
				ModelTransform.pivot(0, 0, 0));

		root.addChild("left_arm", ModelPartBuilder.create()
						.uv(40, 16).cuboid(-1, -2, -2, 4, 12, 4),
				ModelTransform.pivot(5, 2, 0));
		root.addChild("right_arm", ModelPartBuilder.create()
						.uv(32, 48).cuboid(-3, -2, -2, 4, 12, 4),
				ModelTransform.pivot(-5, 2, 0));
		root.addChild("left_pad", ModelPartBuilder.create()
						.uv(48, 32).cuboid(0, -4, -3, 5, 4, 6),
				ModelTransform.pivot(5, 2, 0));
		root.addChild("right_pad", ModelPartBuilder.create()
						.uv(48, 32).cuboid(-5, -4, -3, 5, 4, 6),
				ModelTransform.pivot(-5, 2, 0));

		root.addChild("left_leg", ModelPartBuilder.create()
						.uv(0, 16).cuboid(-2, 0, -2, 4, 12, 4),
				ModelTransform.pivot(2, 12, 0));
		root.addChild("right_leg", ModelPartBuilder.create()
						.uv(16, 48).cuboid(-2, 0, -2, 4, 12, 4),
				ModelTransform.pivot(-2, 12, 0));

		root.addChild("cape", ModelPartBuilder.create()
						.uv(0, 40).cuboid(-5, 0, 2, 10, 16, 1),
				ModelTransform.pivot(0, 0, 0));

		// Unique weapons / props — attached near right hand (Steve-scale)
		root.addChild("prop_axe", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-1, -1, -1, 2, 2, 16)
						.cuboid(-1, -6, 12, 2, 12, 5)
						.cuboid(-2, -8, 14, 4, 4, 4),
				ModelTransform.pivot(-6, 2, 0));
		root.addChild("prop_hook", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-0.5f, -0.5f, -0.5f, 1, 1, 18)
						.cuboid(-2, -2, 16, 4, 4, 3)
						.cuboid(-3, -4, 18, 2, 6, 2),
				ModelTransform.pivot(-6, 4, 0));
		root.addChild("prop_hammer", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-1, -1, -1, 2, 2, 14)
						.cuboid(-4, -4, 11, 8, 8, 6),
				ModelTransform.pivot(-6, 2, 0));
		root.addChild("prop_rock", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-5, -3, -4, 10, 10, 10)
						.cuboid(-3, -6, -2, 6, 4, 6),
				ModelTransform.pivot(-7, 0, 0));
		root.addChild("prop_sword", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-0.5f, -0.5f, -1, 1, 1, 18)
						.cuboid(-1.5f, -1.5f, 15, 3, 3, 2)
						.cuboid(-2, -2, -3, 4, 4, 3),
				ModelTransform.pivot(-6, 2, 0));
		root.addChild("prop_blades", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-1, -1, -1, 2, 2, 14)
						.cuboid(2, -1, -1, 2, 2, 14)
						.cuboid(-1, -2, 12, 5, 4, 3),
				ModelTransform.pivot(-6, 2, 0));
		root.addChild("prop_dagger", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-0.5f, -0.5f, -1, 1, 1, 10)
						.cuboid(-1.5f, -1, 7, 3, 2, 4)
						.cuboid(-1, -1, -3, 2, 2, 3),
				ModelTransform.pivot(-5.5f, 4, 0));
		root.addChild("prop_bow", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-0.5f, -8, -0.5f, 1, 16, 1)
						.cuboid(-0.5f, -9, -0.5f, 1, 1, 6)
						.cuboid(-0.5f, 8, -0.5f, 1, 1, 6)
						.cuboid(0, -8, 4, 0.3f, 16, 0.3f),
				ModelTransform.pivot(-6, 4, 2));
		root.addChild("prop_rifle", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-1, -1, -2, 2, 2, 18)
						.cuboid(-1.5f, -2, -4, 3, 3, 4)
						.cuboid(-0.5f, -0.5f, 14, 1, 1, 6),
				ModelTransform.pivot(-6, 4, 0));
		root.addChild("prop_staff", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-0.7f, -0.7f, -1, 1.4f, 1.4f, 20)
						.cuboid(-2.5f, -2.5f, 16, 5, 5, 5)
						.cuboid(-1.5f, -4, 18, 3, 3, 3),
				ModelTransform.pivot(-6, 0, 0));
		root.addChild("prop_scythe", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-0.6f, -0.6f, -1, 1.2f, 1.2f, 16)
						.cuboid(-1, -7, 12, 2, 8, 4)
						.cuboid(-1, -9, 10, 2, 3, 6),
				ModelTransform.pivot(-6, 1, 0));
		root.addChild("prop_ward", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-1, -1, -1, 2, 2, 10)
						.cuboid(-3, -3, 8, 6, 6, 4)
						.cuboid(-2, -5, 9, 4, 2, 4)
						.cuboid(-1, -1, 12, 2, 2, 4),
				ModelTransform.pivot(-6, 2, 0));
		root.addChild("prop_shield", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-1, -6, -5, 2, 12, 10)
						.cuboid(-2, -4, -3, 1, 8, 6),
				ModelTransform.pivot(6, 4, 0));
		root.addChild("prop_orb", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-2.5f, -2.5f, -2.5f, 5, 5, 5)
						.cuboid(-1.5f, -4, -1.5f, 3, 2, 3),
				ModelTransform.pivot(-6, 0, 0));
		root.addChild("prop_gun", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-1.5f, -1.5f, -2, 3, 3, 12)
						.cuboid(-2, -2, -5, 4, 4, 4)
						.cuboid(1, -3, 2, 4, 2, 3),
				ModelTransform.pivot(-6, 4, 0));
		root.addChild("prop_fangs", ModelPartBuilder.create()
						.uv(0, 0).cuboid(-3, -2, -6, 2, 2, 5)
						.cuboid(1, -2, -6, 2, 2, 5)
						.cuboid(-2, 0, -4, 4, 3, 3),
				ModelTransform.pivot(0, 0, 0));

		return TexturedModelData.of(data, 64, 64);
	}

	public void setVisual(HeroVisual visual) {
		this.visual = visual == null ? HeroVisual.forHero("") : visual;
		hideExtras();
		applyStyle(this.visual.style());
	}

	private void hideExtras() {
		belly.visible = false;
		chest.visible = false;
		helm.visible = false;
		crest.visible = false;
		hood.visible = false;
		horns.visible = false;
		mask.visible = false;
		leftPad.visible = false;
		rightPad.visible = false;
		cape.visible = false;
		for (ModelPart p : props.values()) {
			p.visible = false;
		}
	}

	private void show(String prop) {
		ModelPart p = props.get(prop);
		if (p != null) {
			p.visible = true;
		}
	}

	private void applyStyle(HeroVisual.Style style) {
		switch (style) {
			case AXE -> {
				helm.visible = true;
				crest.visible = true;
				leftPad.visible = true;
				rightPad.visible = true;
				chest.visible = true;
				show("prop_axe");
			}
			case PUDGE -> {
				belly.visible = true;
				hood.visible = false;
				mask.visible = true;
				show("prop_hook");
			}
			case SVEN -> {
				helm.visible = true;
				crest.visible = true;
				cape.visible = true;
				chest.visible = true;
				leftPad.visible = true;
				rightPad.visible = true;
				show("prop_hammer");
			}
			case TINY -> {
				belly.visible = true;
				chest.visible = true;
				show("prop_rock");
			}
			case LEGION -> {
				helm.visible = true;
				crest.visible = true;
				cape.visible = true;
				chest.visible = true;
				show("prop_sword");
			}
			case JUGG -> {
				helm.visible = true;
				mask.visible = true;
				show("prop_blades");
			}
			case PA -> {
				hood.visible = true;
				mask.visible = true;
				cape.visible = true;
				show("prop_dagger");
			}
			case AM -> {
				helm.visible = true;
				horns.visible = true;
				show("prop_blades");
			}
			case DROW -> {
				hood.visible = true;
				cape.visible = true;
				show("prop_bow");
			}
			case SNIPER -> {
				helm.visible = true;
				chest.visible = true;
				show("prop_rifle");
			}
			case CM -> {
				hood.visible = true;
				cape.visible = true;
				show("prop_staff");
			}
			case ZEUS -> {
				helm.visible = true;
				crest.visible = true;
				show("prop_staff");
				show("prop_orb");
			}
			case LINA -> {
				hood.visible = true;
				cape.visible = true;
				horns.visible = true;
				show("prop_staff");
			}
			case LION -> {
				mask.visible = true;
				horns.visible = true;
				cape.visible = true;
				show("prop_scythe");
			}
			case WD -> {
				mask.visible = true;
				helm.visible = true;
				show("prop_ward");
			}
			case SPECTRE -> {
				hood.visible = true;
				cape.visible = true;
				mask.visible = true;
				show("prop_blades");
			}
			case VENO -> {
				horns.visible = true;
				belly.visible = true;
				show("prop_fangs");
				show("prop_staff");
			}
			case ABADDON -> {
				helm.visible = true;
				crest.visible = true;
				cape.visible = true;
				chest.visible = true;
				leftPad.visible = true;
				rightPad.visible = true;
				show("prop_shield");
				show("prop_sword");
			}
			case VOID -> {
				mask.visible = true;
				hood.visible = true;
				show("prop_orb");
				show("prop_blades");
			}
			case SNAP -> {
				belly.visible = true;
				helm.visible = true;
				show("prop_gun");
			}
			default -> {
			}
		}
	}

	@Override
	public void setAngles(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
			float headYaw, float headPitch) {
		float hy = headYaw * ((float) Math.PI / 180f);
		float hp = headPitch * ((float) Math.PI / 180f);
		head.yaw = hy;
		head.pitch = hp;
		helm.yaw = hy;
		helm.pitch = hp;
		crest.yaw = hy;
		crest.pitch = hp;
		hood.yaw = hy;
		hood.pitch = hp;
		horns.yaw = hy;
		horns.pitch = hp;
		mask.yaw = hy;
		mask.pitch = hp;

		float swing = MathHelper.cos(limbAngle * 0.6662f) * 1.15f * limbDistance;
		rightArm.pitch = -swing;
		leftArm.pitch = swing;
		rightPad.pitch = rightArm.pitch;
		leftPad.pitch = leftArm.pitch;
		rightLeg.pitch = swing;
		leftLeg.pitch = -swing;
		cape.pitch = 0.15f + MathHelper.sin(animationProgress * 0.1f) * 0.05f + limbDistance * 0.3f;

		float idle = MathHelper.sin(animationProgress * 0.08f) * 0.05f;
		for (ModelPart p : props.values()) {
			if (!p.visible) {
				continue;
			}
			p.pitch = -0.55f + idle;
			p.yaw = 0f;
			p.roll = 0f;
		}
		poseProps();
	}

	private void poseProps() {
		switch (visual.style()) {
			case AXE -> pose("prop_axe", -0.95f, 0.15f, 0.35f);
			case PUDGE -> pose("prop_hook", -0.25f, 0.55f, 0.1f);
			case SVEN -> pose("prop_hammer", -0.85f, 0.1f, 0.2f);
			case TINY -> pose("prop_rock", -0.4f, 0.2f, 0.15f);
			case LEGION -> pose("prop_sword", -0.75f, 0.05f, 0.1f);
			case JUGG, AM, SPECTRE, VOID -> {
				pose("prop_blades", -0.7f, 0.1f, 0.15f);
				pose("prop_orb", -0.2f, 0.3f, 0f);
			}
			case PA -> pose("prop_dagger", -0.6f, 0.25f, 0.2f);
			case DROW -> pose("prop_bow", -0.1f, 0.4f, 0f);
			case SNIPER -> pose("prop_rifle", -1.15f, -0.2f, 0f);
			case CM, LINA -> pose("prop_staff", -0.2f, 0.15f, 0f);
			case ZEUS -> {
				pose("prop_staff", -0.25f, 0.1f, 0f);
				pose("prop_orb", 0.1f, 0.4f, 0f);
			}
			case LION -> pose("prop_scythe", -0.8f, 0.2f, 0.25f);
			case WD -> pose("prop_ward", -0.35f, 0.2f, 0f);
			case VENO -> {
				pose("prop_fangs", 0.2f, 0f, 0f);
				pose("prop_staff", -0.3f, 0.25f, 0f);
			}
			case ABADDON -> {
				pose("prop_shield", 0.05f, 1.15f, 0f);
				pose("prop_sword", -0.7f, 0.1f, 0.1f);
			}
			case SNAP -> pose("prop_gun", -1.05f, -0.15f, 0.1f);
			default -> {
			}
		}
	}

	private void pose(String name, float pitch, float yaw, float roll) {
		ModelPart p = props.get(name);
		if (p != null && p.visible) {
			p.pitch = pitch;
			p.yaw = yaw;
			p.roll = roll;
		}
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
			float red, float green, float blue, float alpha) {
		matrices.push();
		matrices.scale(visual.bodyWide(), visual.bodyTall(), visual.bodyWide());
		matrices.translate(0, (1f - visual.bodyTall()) * 1.5f, 0);

		float br = red * visual.red();
		float bg = green * visual.green();
		float bb = blue * visual.blue();
		float ar = red * visual.accentR();
		float ag = green * visual.accentG();
		float ab = blue * visual.accentB();

		body.render(matrices, vertices, light, overlay, br, bg, bb, alpha);
		if (belly.visible) {
			belly.render(matrices, vertices, light, overlay, br * 0.9f, bg * 0.9f, bb * 0.85f, alpha);
		}
		if (chest.visible) {
			chest.render(matrices, vertices, light, overlay, ar, ag, ab, alpha);
		}
		if (cape.visible) {
			cape.render(matrices, vertices, light, overlay, br * 0.7f, bg * 0.7f, bb * 0.85f, alpha);
		}

		matrices.push();
		matrices.scale(visual.headScale(), visual.headScale(), visual.headScale());
		head.render(matrices, vertices, light, overlay, br, bg, bb, alpha);
		if (helm.visible) {
			helm.render(matrices, vertices, light, overlay, ar, ag, ab, alpha);
		}
		if (crest.visible) {
			crest.render(matrices, vertices, light, overlay, ar, ag, ab, alpha);
		}
		if (hood.visible) {
			hood.render(matrices, vertices, light, overlay, br * 0.8f, bg * 0.8f, bb * 0.95f, alpha);
		}
		if (horns.visible) {
			horns.render(matrices, vertices, light, overlay, ar, ag, ab, alpha);
		}
		if (mask.visible) {
			mask.render(matrices, vertices, light, overlay, ar, ag, ab, alpha);
		}
		matrices.pop();

		matrices.push();
		matrices.scale(visual.armScale(), visual.armScale(), visual.armScale());
		leftArm.render(matrices, vertices, light, overlay, br, bg, bb, alpha);
		rightArm.render(matrices, vertices, light, overlay, br, bg, bb, alpha);
		if (leftPad.visible) {
			leftPad.render(matrices, vertices, light, overlay, ar, ag, ab, alpha);
		}
		if (rightPad.visible) {
			rightPad.render(matrices, vertices, light, overlay, ar, ag, ab, alpha);
		}
		matrices.pop();

		matrices.push();
		matrices.scale(visual.legScale(), 1f, visual.legScale());
		leftLeg.render(matrices, vertices, light, overlay, br, bg, bb, alpha);
		rightLeg.render(matrices, vertices, light, overlay, br, bg, bb, alpha);
		matrices.pop();

		for (ModelPart p : props.values()) {
			if (p.visible) {
				p.render(matrices, vertices, light, overlay, ar, ag, ab, alpha);
			}
		}
		matrices.pop();
	}
}
