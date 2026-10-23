package net.corevk.corevk.pack.menu;

import net.corevk.corevk.pack.option.OptionIndex;
import net.corevk.corevk.pack.option.OptionValue;
import net.corevk.corevk.pack.option.PackOption;
import net.corevk.corevk.pack.source.PackLang;
import net.corevk.corevk.pack.source.ShaderProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PackMenu {

	private static final int WIDE_PAGE = 18;

	private static final MenuSlot BLANK = new MenuSlot.Blank();
	private static final MenuSlot PROFILES = new MenuSlot.Profiles();

	private final String packName;
	private final Map<String, MenuPage> pages;
	private final Map<String, MenuOption> options;
	private final Map<String, Map<String, String>> profiles;
	private final List<String> profileOrder;
	private final PackLang lang;
	private final List<String> warnings;

	private PackMenu(String packName, Map<String, MenuPage> pages, Map<String, MenuOption> options,
			Map<String, Map<String, String>> profiles, PackLang lang, List<String> warnings) {

		this.packName = packName;
		this.pages = Collections.unmodifiableMap(new LinkedHashMap<>(pages));
		this.options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
		this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
		this.profileOrder = order(this.profiles);
		this.lang = lang;
		this.warnings = List.copyOf(warnings);
	}

	private static List<String> order(Map<String, Map<String, String>> profiles) {
		List<String> names = new ArrayList<>(profiles.keySet());
		names.sort(Comparator.comparingInt((String name) -> profiles.get(name).size()).reversed());

		return List.copyOf(names);
	}

	public static PackMenu build(String packName, OptionIndex index, ShaderProperties properties,
			PackLang lang) {
		Set<String> sliders = new HashSet<>(properties.sliders());
		Map<String, List<ShaderProperties.ScreenToken>> layout = properties.screenLayout();
		boolean hasProfiles = !properties.profiles().isEmpty();

		List<String> warnings = new ArrayList<>();
		Map<String, MenuOption> options = new LinkedHashMap<>();
		Map<String, List<MenuSlot>> slotsByPage = new LinkedHashMap<>();

		record Rest(List<MenuSlot> slots, int at) {
		}
		List<Rest> rests = new ArrayList<>();
		Set<String> subPlaced = new HashSet<>();

		for (Map.Entry<String, List<ShaderProperties.ScreenToken>> page : layout.entrySet()) {
			String where = page.getKey().isEmpty() ? "screen" : "screen." + page.getKey();
			List<MenuSlot> slots = new ArrayList<>();

			for (ShaderProperties.ScreenToken token : page.getValue()) {
				switch (token) {
					case ShaderProperties.ScreenToken.Blank _ -> slots.add(BLANK);
					case ShaderProperties.ScreenToken.Name(String name) -> {
						PackOption declared = index.get(name).orElse(null);
						if (declared == null) {
							warnings.add(where + " names " + name
									+ ", which the pack does not declare");
							slots.add(BLANK);
						} else if (!index.offers(declared)) {
							warnings.add(where + " names " + name
									+ ", declared but not a setting");
							slots.add(BLANK);
						} else {
							slots.add(new MenuSlot.Option(options.computeIfAbsent(name,
									_ -> MenuOption.of(declared, sliders.contains(name)))));
							if (!page.getKey().isEmpty()) {
								subPlaced.add(name);
							}
						}
					}
					case ShaderProperties.ScreenToken.Link(String target) -> {
						boolean resolved = layout.containsKey(target);
						if (!resolved) {
							warnings.add(where + " links to " + target
									+ ", which the pack does not lay out");
						}

						slots.add(new MenuSlot.Link(target, resolved));
					}

					case ShaderProperties.ScreenToken.Profiles _ -> {
						if (hasProfiles) {
							slots.add(PROFILES);
						}
					}
					case ShaderProperties.ScreenToken.Rest _ ->
							rests.add(new Rest(slots, slots.size()));
				}
			}

			slotsByPage.put(page.getKey(), slots);
		}

		if (!slotsByPage.containsKey("")) {
			List<MenuSlot> main = new ArrayList<>();
			rests.add(0, new Rest(main, 0));
			slotsByPage.put("", main);
		}

		if (!rests.isEmpty()) {
			List<MenuSlot> spill = new ArrayList<>();
			for (PackOption declared : index.all()) {
				if (subPlaced.contains(declared.name()) || !index.offers(declared)) {
					continue;
				}

				spill.add(new MenuSlot.Option(options.computeIfAbsent(declared.name(),
						_ -> MenuOption.of(declared, sliders.contains(declared.name())))));
			}

			rests.get(0).slots().addAll(rests.get(0).at(), spill);
		}

		Map<String, MenuPage> pages = new LinkedHashMap<>();
		slotsByPage.forEach((name, slots) -> pages.put(name, new MenuPage(name, slots,
				properties.columns(name).orElse(slots.size() > WIDE_PAGE ? 3 : 2))));

		Map<String, Map<String, String>> profiles = new LinkedHashMap<>();
		for (String name : properties.profiles().keySet()) {
			profiles.put(name, expand(properties, index, warnings, name));
		}

		return new PackMenu(packName, pages, options, profiles, lang, warnings);
	}

	private static Map<String, String> expand(ShaderProperties properties, OptionIndex index,
			List<String> warnings, String name) {
		Map<String, String> expanded = new LinkedHashMap<>();
		properties.expandProfile(name, token -> {
			boolean toggle = index.get(token)
					.filter(option -> option.kind() == PackOption.Kind.TOGGLE)
					.isPresent();
			if (!toggle) {
				warnings.add("profile." + name + " names " + token
						+ ", which this pack declares nowhere as a toggle, so it is not a constraint");
			}

			return toggle;
		}).forEach((option, value) -> expanded.put(option, text(value)));

		return Collections.unmodifiableMap(expanded);
	}

	private static String text(OptionValue value) {
		if (value.isBoolean()) {
			return value.asBoolean() ? "on" : "off";
		}

		return value.text();
	}

	public String packName() {
		return this.packName;
	}

	public MenuPage main() {
		return this.pages.get("");
	}

	public Optional<MenuPage> page(String name) {
		return Optional.ofNullable(this.pages.get(name));
	}

	public Collection<MenuPage> pages() {
		return this.pages.values();
	}

	public Optional<MenuOption> option(String name) {
		return Optional.ofNullable(this.options.get(name));
	}

	public int optionCount() {
		return this.options.size();
	}

	public Set<String> optionNames() {
		return this.options.keySet();
	}

	public List<String> profileNames() {
		return this.profileOrder;
	}

	public Map<String, String> profile(String name) {
		return this.profiles.getOrDefault(name, Map.of());
	}

	public PackLang lang() {
		return this.lang;
	}

	public List<String> warnings() {
		return this.warnings;
	}
}
