package net.corevk.corevk.uniform;

public final class Smoothed {

	private static final double LN_OF_2 = Math.log(2.0);

	private float accumulator;
	private boolean started;

	
	public float updateAndGet(float value, float halfLifeUp, float halfLifeDown, float dt) {
		if (!this.started) {
			this.started = true;
			this.accumulator = value;

			return this.accumulator;
		}

		if (dt <= 0.0F) {
			return this.accumulator;
		}

		float factor = blend(value > this.accumulator ? halfLifeUp : halfLifeDown, dt);
		this.accumulator = this.accumulator + (value - this.accumulator) * factor;

		return this.accumulator;
	}

	public void reset() {
		this.started = false;
		this.accumulator = 0.0F;
	}

	
	public static float blend(float halfLife, float dt) {
		return 1.0F - (float) Math.exp(-decayConstant(halfLife) * dt);
	}

	private static float decayConstant(float halfLife) {
		return (float) (1.0 / (halfLife * 0.1 / LN_OF_2));
	}
}
