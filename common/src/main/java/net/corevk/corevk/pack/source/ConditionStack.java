package net.corevk.corevk.pack.source;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

public final class ConditionStack {

	private final Deque<Level> levels = new ArrayDeque<>();

	public void ifDirective(boolean condition) {
		push(condition, false);
	}

	public void ifDirective(BooleanSupplier condition) {
		push(!active() || condition.getAsBoolean(), false);
	}

	public void rewrittenIfDirective(boolean condition) {
		push(condition, true);
	}

	private void push(boolean condition, boolean rewritten) {
		boolean parent = active();
		this.levels.push(new Level(parent && condition, condition, parent, rewritten));
	}

	public void rewrittenElifDirective(boolean condition) {
		elifDirective(() -> condition);
	}

	public void elifDirective(BooleanSupplier condition) {
		Level level = this.levels.peek();
		if (level == null) {
			return;
		}

		if (level.taken) {
			level.active = false;
			return;
		}

		boolean value = condition.getAsBoolean();
		level.active = level.parentActive && value;
		level.taken = value;
	}

	public void elseDirective() {
		Level level = this.levels.peek();
		if (level == null) {
			return;
		}

		level.active = level.parentActive && !level.taken;
		level.taken = true;
	}

	public void endifDirective() {
		this.levels.poll();
	}

	public boolean active() {
		Level level = this.levels.peek();

		return level == null || level.active;
	}

	public int depth() {
		return this.levels.size();
	}

	public int unclosedRewritten() {
		return (int) this.levels.stream().filter(level -> level.rewritten).count();
	}

	private static final class Level {

		private boolean active;
		private boolean taken;
		private final boolean parentActive;
		private final boolean rewritten;

		private Level(boolean active, boolean taken, boolean parentActive, boolean rewritten) {
			this.active = active;
			this.taken = taken;
			this.parentActive = parentActive;
			this.rewritten = rewritten;
		}
	}
}
