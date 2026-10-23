package net.corevk.corevk.render.pbr;

import net.minecraft.util.ARGB;

import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.function.IntUnaryOperator;

public enum PbrMap {

	NORMALS("normals", "_n", ARGB.color(0xFF, 0x7F, 0x7F, 0xFF)),

	SPECULAR("specular", "_s", ARGB.color(0, 0, 0, 0));

	private static final IntUnaryOperator METAL = value -> value < 230 ? 0 : value - 229;
	private static final IntUnaryOperator POROSITY = value -> value < 65 ? 0 : 1;
	private static final IntUnaryOperator EMISSION = value -> value < 255 ? 0 : 1;

	private final String sampler;
	private final String suffix;
	private final int missing;

	PbrMap(String sampler, String suffix, int missing) {
		this.sampler = sampler;
		this.suffix = suffix;
		this.missing = missing;
	}

	public String sampler() {
		return this.sampler;
	}

	public static PbrMap named(String sampler) {
		for (PbrMap map : values()) {
			if (map.sampler.equals(sampler)) {
				return map;
			}
		}

		return null;
	}

	String suffix() {
		return this.suffix;
	}

	public Vector4fc missing() {
		return new Vector4f(ARGB.red(this.missing) / 255.0F, ARGB.green(this.missing) / 255.0F,
				ARGB.blue(this.missing) / 255.0F, ARGB.alpha(this.missing) / 255.0F);
	}

	public boolean interpolates(boolean labPbr) {
		return this != SPECULAR || !labPbr;
	}

	int blend(int first, int second, int third, int fourth, boolean labPbr) {
		if (this == SPECULAR && labPbr) {
			return ARGB.color(
					byClass(EMISSION, ARGB.alpha(first), ARGB.alpha(second), ARGB.alpha(third),
							ARGB.alpha(fourth)),
					mean(ARGB.red(first), ARGB.red(second), ARGB.red(third), ARGB.red(fourth)),
					byClass(METAL, ARGB.green(first), ARGB.green(second), ARGB.green(third),
							ARGB.green(fourth)),
					byClass(POROSITY, ARGB.blue(first), ARGB.blue(second), ARGB.blue(third),
							ARGB.blue(fourth)));
		}

		return ARGB.color(
				mean(ARGB.alpha(first), ARGB.alpha(second), ARGB.alpha(third), ARGB.alpha(fourth)),
				mean(ARGB.red(first), ARGB.red(second), ARGB.red(third), ARGB.red(fourth)),
				mean(ARGB.green(first), ARGB.green(second), ARGB.green(third), ARGB.green(fourth)),
				mean(ARGB.blue(first), ARGB.blue(second), ARGB.blue(third), ARGB.blue(fourth)));
	}

	private static int mean(int first, int second, int third, int fourth) {
		return (first + second + third + fourth) / 4;
	}

	private static int byClass(IntUnaryOperator classOf, int first, int second, int third,
			int fourth) {
		int[] values = {first, second, third, fourth};
		int[] classes = {classOf.applyAsInt(first), classOf.applyAsInt(second),
				classOf.applyAsInt(third), classOf.applyAsInt(fourth)};

		int winner = classes[0];
		int best = 0;
		for (int candidate = 0; candidate < classes.length; candidate++) {
			int count = 0;
			for (int against : classes) {
				if (against == classes[candidate]) {
					count++;
				}
			}

			if (count > best) {
				best = count;
				winner = classes[candidate];
			}
		}

		int sum = 0;
		int taken = 0;
		for (int index = 0; index < values.length; index++) {
			if (classes[index] == winner) {
				sum += values[index];
				taken++;
			}
		}

		return sum / taken;
	}
}
