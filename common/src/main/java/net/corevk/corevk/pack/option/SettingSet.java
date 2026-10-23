package net.corevk.corevk.pack.option;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SettingSet {

	private static volatile int shadowMapScale = 100;

	private final Map<String, OptionValue> chosen;
	private final Map<String, String> engine;
	private final String variantName;
	private final int scale;

	private SettingSet(Map<String, OptionValue> chosen, Map<String, String> engine, String variantName,
			int scale) {
		this.chosen = Map.copyOf(chosen);
		this.engine = Map.copyOf(engine);
		this.variantName = variantName;
		this.scale = scale;
	}

	public static void shadowMapScale(int percent) {
		shadowMapScale = percent;
	}

	public static int askedShadowMapScale() {
		return shadowMapScale;
	}

	public static SettingSet resolve(Map<String, OptionValue> profile, Map<String, OptionValue> user,
			String variantName) {
		Map<String, OptionValue> chosen = new LinkedHashMap<>(profile);
		chosen.putAll(user);

		return new SettingSet(chosen, EngineDefines.table(EngineDefines.machine()), variantName,
			shadowMapScale);
	}

	public int scale() {
		return this.scale;
	}

	public static SettingSet defaults() {
		return resolve(Map.of(), Map.of(), "default");
	}

	public Map<String, OptionValue> chosen() {
		return this.chosen;
	}

	public String variantName() {
		return this.variantName;
	}

	public Map<String, String> globalDefines(OptionIndex index) {
		Map<String, String> defines = new LinkedHashMap<>(this.engine);

		for (PackOption option : index.all()) {
			if (option.kind() == PackOption.Kind.CONST) {
				if (!index.offers(option)) {
					continue;
				}

				if ("bool".equals(option.constType())) {
					OptionValue held = this.chosen.get(option.name());
					boolean on = held == null ? "true".equals(option.defaultText())
							: held.isBoolean() && held.asBoolean();
					if (on) {
						defines.put(option.name(), "true");
					}

					continue;
				}
			}

			OptionValue value = this.chosen.get(option.name());
			if (value == null) {

				if (!option.defaultOff()) {
					defines.put(option.name(), option.defaultText());
				}

				continue;
			}

			if (!value.isBoolean()) {
				defines.put(option.name(), value.text());
			} else if (value.asBoolean()) {
				defines.put(option.name(), option.defaultText());
			}
		}

		return defines;
	}

	public Map<String, String> unitDefines() {
		return this.engine;
	}
}
