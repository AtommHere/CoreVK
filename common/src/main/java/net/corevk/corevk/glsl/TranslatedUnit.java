package net.corevk.corevk.glsl;

import net.corevk.corevk.pack.model.ProgramStage;

import java.util.List;

public record TranslatedUnit(String entry, ProgramStage stage, String text, Notes notes,
		List<Integer> drawBuffers, List<Uniform> blockMembers, List<Uniform> samplers) {

	
	public record Uniform(String name, String type, String declaration) {

		public static Uniform of(String name, String declaration) {
			int space = declaration.indexOf(' ');

			return new Uniform(name, space < 0 ? declaration : declaration.substring(0, space),
					declaration);
		}
	}

	
	public record StorageBlock(String name, int binding) {
	}

	
	public record Notes(int fragmentOutputs, int dynamicFragData, int uniformConflicts,
			int shadowCalls, int unwrappedShadow, int strippedExtensions,
			int depthEpilogue, int alphaEpilogue, int coverage,
			int depthLookups, int parameterLookups, int pinnedLookups,
			int unpinnedParameterLookups,
			int fragCoordZ, int fragCoordXyz, int fragCoordUnhandled,
			int fragDepthWrites, int fragDepthUnhandled,
			List<String> conflictNames, List<String> comparedSamplers,
			List<String> hardwareCompared, List<String> retypedSamplers,
			List<StorageBlock> storageBlocks,
			int volumeLookups, int volumesLeftAlone, int trigCalls, int gameTextureMatrix,
			int gameModelView) {
	}
}
