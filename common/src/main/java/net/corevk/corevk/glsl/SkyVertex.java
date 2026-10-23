package net.corevk.corevk.glsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SkyVertex {

	
	public static final List<String> ATTRIBUTES = List.of("Position", "Color", "UV0");

	private SkyVertex() {
	}

	
	public static List<String> prologue(List<String> bound, Set<String> used,
			Map<String, String> synthesized) {
		List<String> lines = new ArrayList<>();

		for (String attribute : bound) {
			lines.add("in " + VertexPrologue.elementType(attribute) + " " + attribute + ";");
		}

		lines.add("#define of_Vertex vec4(Position, 1.0)");
		// The modulator, times the element where the format has one, and this is the one line of the
		// prologue that decides a picture rather than guarding one.
		//
		// The game does not put the sky's colour in the mesh; it puts it in the colour modulator of
		// its dynamic transforms, one value for the whole draw, and under OptiFine that value is
		// what a pack reads as gl_Color. White is not a neutral stand in for it: packs recognise
		// vanilla's stars by exactly that shape, a colour whose three channels are equal and above
		// nought, and Body Camera and Sildur's both then take their star branch and paint the whole
		// sky disc flat.
		//
		// The two MULTIPLY where both exist, because that is what the game's own shaders do with
		// them: core/position_color.fsh ends on fragColor = color * ColorModulator, and so does
		// core/position_tex. The one element of the sky whose format carries a colour is the sunrise
		// band, and there the mesh is white at the centre fading to a transparent white at the rim,
		// ARGB.white(1.0F) and ARGB.white(0.0F) in SkyRenderer.buildSunriseFan: every bit of the
		// band's actual colour is in the modulator, so keeping the element alone draws a white band
		// at sunset. Dropping the element instead would take the fade with it and paint a disc.
		lines.add(bound.contains("Color")
				? "#define of_Color (Color * of_PassColour)"
				: "#define of_Color of_PassColour");

		// The middle of the sprite where the format has no texture coordinate, which is the four
		// elements drawn from bare positions. The corner instead would send a pack that samples
		// gtexture there to one texel of the atlas rather than to the one the name stands for.
		String texture = bound.contains("UV0") ? "vec4(UV0, 0.0, 1.0)" : "vec4(0.5, 0.5, 0.0, 1.0)";
		lines.add("#define of_MultiTexCoord0 " + texture);

		// The light map, which no sky format carries. Both channels at the top of the range, in the
		// raw coordinates a pack expects to divide down itself, which is the pair the references hand
		// every format without a light map. Block light at nought instead reads as a place no lamp
		// reaches, and a pack that writes the coordinate straight into a colour target, as Body Camera
		// does over the whole sky, puts the bottom of the range there rather than the top.
		for (int unit = 1; unit <= 2; unit++) {
			lines.add("#define of_MultiTexCoord" + unit + " vec4(240.0, 240.0, 0.0, 1.0)");
		}

		lines.addAll(VertexPrologue.blankTexCoords());

		lines.add("#define of_Normal vec3(0.0, 0.0, 1.0)");
		lines.addAll(VertexPrologue.tail(used, synthesized));

		return List.copyOf(lines);
	}
}
