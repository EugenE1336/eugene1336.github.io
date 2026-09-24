package com.minedota.client.render;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Multiplies vertex RGB — soft team tint for structure models. */
public final class TintedVertexConsumer implements VertexConsumer {
	private final VertexConsumer parent;
	private final float rMul;
	private final float gMul;
	private final float bMul;
	private final float aMul;

	public TintedVertexConsumer(VertexConsumer parent, float r, float g, float b, float a) {
		this.parent = parent;
		this.rMul = r;
		this.gMul = g;
		this.bMul = b;
		this.aMul = a;
	}

	@Override
	public VertexConsumer vertex(double x, double y, double z) {
		parent.vertex(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
		parent.vertex(matrix, x, y, z);
		return this;
	}

	@Override
	public VertexConsumer color(int r, int g, int b, int a) {
		parent.color(
				Math.min(255, Math.round(r * rMul)),
				Math.min(255, Math.round(g * gMul)),
				Math.min(255, Math.round(b * bMul)),
				Math.min(255, Math.round(a * aMul)));
		return this;
	}

	@Override
	public VertexConsumer texture(float u, float v) {
		parent.texture(u, v);
		return this;
	}

	@Override
	public VertexConsumer overlay(int u, int v) {
		parent.overlay(u, v);
		return this;
	}

	@Override
	public VertexConsumer light(int u, int v) {
		parent.light(u, v);
		return this;
	}

	@Override
	public VertexConsumer normal(float x, float y, float z) {
		parent.normal(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer normal(Matrix3f matrix, float x, float y, float z) {
		parent.normal(matrix, x, y, z);
		return this;
	}

	@Override
	public void next() {
		parent.next();
	}

	@Override
	public void fixedColor(int r, int g, int b, int a) {
		parent.fixedColor(
				Math.min(255, Math.round(r * rMul)),
				Math.min(255, Math.round(g * gMul)),
				Math.min(255, Math.round(b * bMul)),
				Math.min(255, Math.round(a * aMul)));
	}

	@Override
	public void unfixColor() {
		parent.unfixColor();
	}
}
