package net.corevk.corevk.screen;

import net.corevk.corevk.ScreenText;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

public final class ProfileWidget extends OptionWidget {

	private static final String NAME = "profile";

	private static final int LABEL_ROOM = 16;

	private static final Component LABEL = Component.translatable(ScreenText.PROFILE);

	private static final Component CUSTOM = Component
			.translatable(ScreenText.PROFILE_CUSTOM)
			.withStyle(ChatFormatting.YELLOW);

	private final List<String> profiles;

	private Component profileLabel = CUSTOM;

	public ProfileWidget(List<String> profiles) {
		this.profiles = List.copyOf(profiles);
	}

	@Override
	public void init(ScreenHost host) {
		super.init(host);
		setLabel(LABEL.copy());

		String current = host.values().matchedProfile();
		this.profileLabel = current.isEmpty()
				? CUSTOM
				: ScreenText.fromPack(host.lang().profile(current));
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered) {
		measure(bounds().width() - (Minecraft.getInstance().font.width(LABEL) + LABEL_ROOM));
		drawWithValue(graphics, hovered || isFocused());
	}

	@Override
	protected String name() {
		return NAME;
	}

	@Override
	protected Component valueLabel() {
		return this.profileLabel;
	}

	private boolean step(int by) {
		if (this.profiles.isEmpty()) {
			return false;
		}

		int index = this.profiles.indexOf(host().values().matchedProfile());
		int wanted = index < 0
				? (by < 0 ? this.profiles.size() - 1 : 0)
				: Math.floorMod(index + by, this.profiles.size());
		host().values().queueProfile(this.profiles.get(wanted));

		return true;
	}

	@Override
	protected boolean nextValue() {
		return step(1);
	}

	@Override
	protected boolean previousValue() {
		return step(-1);
	}

	@Override
	protected boolean originalValue() {
		return false;
	}

	@Override
	protected boolean modified() {
		return false;
	}

	@Override
	protected Optional<String> comment() {
		return host().lang().profileComment();
	}
}
