package net.corevk.corevk.uniform;

import org.joml.Matrix3fc;
import org.joml.Matrix4fc;

import java.util.Locale;

public final class TextSink implements UniformSink {

	private static final String UNSUPPLIED = "   <- nothing supplies this";

	private final StringBuilder text = new StringBuilder();

	private String name = "";
	private boolean supplied = true;
	private int elements = 1;
	private int element;

	@Override
	public UniformSink member(String name, int elements, boolean supplied) {
		this.name = name;
		this.elements = elements;
		this.supplied = supplied;
		this.element = 0;

		return this;
	}

	
	public String text() {
		return this.text.toString();
	}

	@Override
	public UniformSink align(int alignment) {
		return this;
	}

	@Override
	public UniformSink putFloat(float v) {
		return line(num(v));
	}

	@Override
	public UniformSink putInt(int v) {
		return line(Integer.toString(v));
	}

	@Override
	public UniformSink putVec2(float x, float y) {
		return line("(" + num(x) + ", " + num(y) + ")");
	}

	@Override
	public UniformSink putVec3(float x, float y, float z) {
		return line("(" + num(x) + ", " + num(y) + ", " + num(z) + ")");
	}

	@Override
	public UniformSink putVec4(float x, float y, float z, float w) {
		return line("(" + num(x) + ", " + num(y) + ", " + num(z) + ", " + num(w) + ")");
	}

	@Override
	public UniformSink putIVec2(int x, int y) {
		return line("(" + x + ", " + y + ")");
	}

	@Override
	public UniformSink putIVec3(int x, int y, int z) {
		return line("(" + x + ", " + y + ", " + z + ")");
	}

	@Override
	public UniformSink putIVec4(int x, int y, int z, int w) {
		return line("(" + x + ", " + y + ", " + z + ", " + w + ")");
	}

	@Override
	public UniformSink putMat3(Matrix3fc m) {
		return rows(new float[][] {
				{ m.m00(), m.m01(), m.m02() },
				{ m.m10(), m.m11(), m.m12() },
				{ m.m20(), m.m21(), m.m22() } });
	}

	@Override
	public UniformSink putMat4(Matrix4fc m) {
		return rows(new float[][] {
				{ m.m00(), m.m01(), m.m02(), m.m03() },
				{ m.m10(), m.m11(), m.m12(), m.m13() },
				{ m.m20(), m.m21(), m.m22(), m.m23() },
				{ m.m30(), m.m31(), m.m32(), m.m33() } });
	}

	
	private UniformSink rows(float[][] columns) {
		StringBuilder block = new StringBuilder();
		for (float[] column : columns) {
			block.append("\n       (");
			for (int i = 0; i < column.length; i++) {
				block.append(i == 0 ? "" : ", ").append(num(column[i]));
			}

			block.append(')');
		}

		return line(block.toString());
	}

	private UniformSink line(String value) {
		this.text.append(this.elements > 1 ? this.name + "[" + this.element + "]" : this.name)
				.append(" = ")
				.append(value)
				.append(this.supplied ? "" : UNSUPPLIED)
				.append('\n');
		this.element++;

		return this;
	}

	
	private static String num(float v) {
		return String.format(Locale.ROOT, "%.6g", v);
	}
}
