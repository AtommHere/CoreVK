package net.corevk.corevk.pack.menu;

import net.corevk.corevk.pack.option.OptionValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

public final class MenuValues {

	private static final String PROFILE_KEY = "profile";

	private final PackMenu menu;

	private Map<String, String> saved;
	private Map<String, String> forced;

	private final Map<String, String> pending = new LinkedHashMap<>();

	private MenuValues(PackMenu menu, Map<String, String> saved, Map<String, String> forced) {
		this.menu = menu;
		this.saved = copy(saved);
		this.forced = copy(forced);
	}

	public static MenuValues of(PackMenu menu, Map<String, String> saved,
			Map<String, String> forced) {
		return new MenuValues(menu, saved, forced);
	}

	public MenuValues reread(PackMenu menu, Map<String, String> saved, Map<String, String> forced) {
		MenuValues next = new MenuValues(menu, saved, forced);
		next.pending.putAll(this.pending);

		return next;
	}

	public void rebase(Map<String, String> saved, Map<String, String> forced) {
		this.saved = copy(saved);
		this.forced = copy(forced);
	}

	public String applied(String name) {
		String value = this.forced.get(name);
		if (value == null) {
			value = this.saved.get(name);
		}
		if (value == null) {
			value = this.menu.profile(forcedProfile()).get(name);
		}

		return value == null ? packDefault(name) : value;
	}

	public String pending(String name) {
		String value = this.forced.get(name);

		return value == null ? unforced(name) : value;
	}

	public boolean modified(String name) {
		return !pending(name).equals(applied(name));
	}

	public boolean forced(String name) {
		return this.forced.containsKey(name);
	}

	public int forcedShown() {
		int count = 0;
		for (String name : this.forced.keySet()) {
			boolean shown = PROFILE_KEY.equals(name)
					? !this.menu.profileNames().isEmpty()
					: this.menu.option(name).isPresent();
			if (shown) {
				count++;
			}
		}

		return count;
	}

	public void queue(String name, String value) {
		this.pending.put(name, value);
	}

	public void queueAll(Map<String, String> values) {
		values.forEach((name, value) -> this.pending.put(name, spelt(name, value)));
	}

	public void queueProfile(String name) {
		this.pending.putAll(this.menu.profile(name));
	}

	public String matchedProfile() {
		return match(this::pending);
	}

	public String matchedAppliedProfile() {
		return match(this::applied);
	}

	public String profileInfo() {
		String matched = matchedAppliedProfile();
		String name = matched.isEmpty() ? "Custom" : matched;
		int extra = appliedOptionsChanged() - profileOptionsChanged(matched);
		return "Profile: " + name + " (+" + extra + " option"
				+ (extra == 1 ? "" : "s") + " changed by user)";
	}

	private int appliedOptionsChanged() {
		int count = 0;
		for (String name : this.menu.optionNames()) {
			if (!applied(name).equals(packDefault(name))) {
				count++;
			}
		}

		return count;
	}

	private int profileOptionsChanged(String profileName) {
		if (profileName.isEmpty()) {
			return 0;
		}

		int count = 0;
		for (Map.Entry<String, String> setting : this.menu.profile(profileName).entrySet()) {
			if (this.menu.option(setting.getKey()).isEmpty()) {
				continue;
			}
			if (!setting.getValue().equals(packDefault(setting.getKey()))) {
				count++;
			}
		}

		return count;
	}

	private String match(UnaryOperator<String> layer) {
		String best = "";
		int constraints = -1;
		for (String name : this.menu.profileNames()) {
			Map<String, String> profile = this.menu.profile(name);
			if (profile.isEmpty() || profile.size() <= constraints || !matches(profile, layer)) {
				continue;
			}

			best = name;
			constraints = profile.size();
		}

		return best;
	}

	private boolean matches(Map<String, String> profile, UnaryOperator<String> layer) {
		for (Map.Entry<String, String> setting : profile.entrySet()) {
			if (!setting.getValue().equals(layer.apply(setting.getKey()))) {
				return false;
			}
		}

		return true;
	}

	public String profile() {
		String over = this.forced.get(PROFILE_KEY);

		return over == null ? matchedProfile() : over;
	}

	public void reset(String name) {
		this.pending.put(name, packDefault(name));
	}

	public void clearPending() {
		this.pending.clear();
	}

	public int pendingCount() {
		int count = 0;
		for (String name : touched()) {
			if (modified(name)) {
				count++;
			}
		}

		return count;
	}

	public Map<String, String> toSave() {
		Map<String, String> result = new LinkedHashMap<>();

		for (String name : touched()) {

			if (this.forced.containsKey(name)) {
				String kept = this.saved.get(name);
				if (kept != null) {
					result.put(name, written(name, kept));
				}

				continue;
			}

			String value = chosen(name);

			if (!value.equals(packDefault(name))) {
				result.put(name, written(name, value));
			}
		}

		return result;
	}

	private String unforced(String name) {
		String value = this.pending.get(name);
		if (value == null) {
			value = this.saved.get(name);
		}
		if (value == null) {
			value = this.menu.profile(forcedProfile()).get(name);
		}

		return value == null ? packDefault(name) : value;
	}

	private String chosen(String name) {
		String value = this.pending.get(name);
		if (value == null) {
			value = this.saved.get(name);
		}

		return value == null ? packDefault(name) : value;
	}

	private String forcedProfile() {
		return this.forced.getOrDefault(PROFILE_KEY, "");
	}

	private Set<String> touched() {
		Set<String> names = new LinkedHashSet<>(this.saved.keySet());
		names.addAll(this.pending.keySet());

		return names;
	}

	private String packDefault(String name) {
		return this.menu.option(name).map(MenuOption::defaultValue).orElse("");
	}

	private Map<String, String> copy(Map<String, String> values) {
		Map<String, String> copied = new LinkedHashMap<>();
		values.forEach((name, value) -> copied.put(name, spelt(name, value)));

		return Collections.unmodifiableMap(copied);
	}

	private String spelt(String name, String value) {
		return toggle(name) ? OptionValue.parse(value).asText() : value;
	}

	private String written(String name, String value) {
		return written(this.menu, name, value);
	}

	public static String written(PackMenu menu, String name, String value) {

		return menu.option(name).filter(option -> option.form() == MenuOption.Form.TOGGLE).isPresent()
				? Boolean.toString(OptionValue.parse(value).asBoolean())
				: value;
	}

	private boolean toggle(String name) {
		return this.menu.option(name)
				.filter(option -> option.form() == MenuOption.Form.TOGGLE)
				.isPresent();
	}
}
