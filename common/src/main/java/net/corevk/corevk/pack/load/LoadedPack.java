package net.corevk.corevk.pack.load;

import net.corevk.corevk.pack.option.OptionIndex;
import net.corevk.corevk.pack.program.ProgramResolver;
import net.corevk.corevk.pack.program.ProgramSet;
import net.corevk.corevk.pack.source.DimensionSet;
import net.corevk.corevk.pack.source.ExpansionStats;
import net.corevk.corevk.pack.source.ShaderProperties;

import java.util.List;
import java.util.Set;

public record LoadedPack(String packName, boolean fromZip, DimensionSet dimensions,
		ShaderProperties properties, OptionIndex options, ProgramSet programs,
		ProgramResolver resolved, PackStats stats, ExpansionStats expansion, int expandedUnits,
		List<String> looseDirectives, Set<String> disabledPrograms, int caseInsensitiveHits,
		long loadMillis) {
}
