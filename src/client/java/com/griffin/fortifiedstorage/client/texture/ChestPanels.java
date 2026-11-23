package com.griffin.fortifiedstorage.client.texture;

import com.griffin.fortifiedstorage.client.texture.TieredSpriteSource.Pixels;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Clads a chest texture in metal: every visible face of the lid and body is filled with the
 * inside of the pack's metal block, tiled as on a placed block. The block's own border row and
 * column are skipped: vanilla shades them darkly (diamond's bottom row is #11BEC1 against
 * #6CF1E6 inside), and narrow faces would otherwise be mostly border. Each face's outer texel is the
 * same metal, darkened, so lid and body stay distinct with no wood showing. The inside of the lid
 * and the floor of the chest are shadowed metal. Only the latch comes from the pack's chest texture. Geometry is in vanilla's 64x64 texel layout, scaled to the pack's
 * resolution, so 16x, 32x, 64x... chests all line up.
 */
public final class ChestPanels {
	public enum Part {
		SINGLE("", 14, 0), LEFT("_left", 15, -1), RIGHT("_right", 15, 1);

		public final String suffix;
		final int width;
		/** Which side the other half joins on: -1 = the box's min x, 1 = max x, 0 = none. */
		final int seam;

		Part(String suffix, int width, int seam) {
			this.suffix = suffix;
			this.width = width;
			this.seam = seam;
		}
	}

	/** A face rectangle in vanilla texels. Open sides continue into the other half, so they get no dark edge. */
	record Panel(int x, int y, int w, int h, float light, boolean openLeft, boolean openRight) {
	}

	private static final float INSIDE_OF_LID = 0.45F;
	/** The floor you see inside an open chest: deep in shadow. */
	private static final float INSIDE_FLOOR = 0.3F;
	/** Brightness of the one-texel metal edge around each face. */
	private static final float EDGE = 0.6F;

	private ChestPanels() {
	}

	static List<Panel> panels(Part part) {
		List<Panel> panels = new ArrayList<>();
		box(panels, 0, 0, part.width, 5, 14, part.seam, INSIDE_OF_LID, 1.0F);  // lid; its DOWN face is the underside
		box(panels, 0, 19, part.width, 10, 14, part.seam, 1.0F, INSIDE_FLOOR); // body; its UP face is the floor inside
		return panels;
	}

	/**
	 * Face rectangles of a ModelPart box at texture offset (u, v) with size (w, h, d), as laid out by
	 * ModelPart.Cube. In UV space max-x is the right edge of DOWN, UP and NORTH and the left edge of SOUTH.
	 */
	private static void box(List<Panel> out, int u, int v, int w, int h, int d, int seam, float downLight, float upLight) {
		boolean seamMax = seam == 1;
		boolean seamMin = seam == -1;
		out.add(new Panel(u + d, v, w, d, downLight, seamMin, seamMax));                // DOWN
		out.add(new Panel(u + d + w, v, w, d, upLight, seamMin, seamMax));         // UP
		out.add(new Panel(u + d, v + d, w, h, 1.0F, seamMin, seamMax));            // NORTH
		out.add(new Panel(u + 2 * d + w, v + d, w, h, 1.0F, seamMax, seamMin));    // SOUTH
		if (!seamMin) {
			out.add(new Panel(u, v + d, d, h, 1.0F, false, false));                // WEST
		}
		if (!seamMax) {
			out.add(new Panel(u + d + w, v + d, d, h, 1.0F, false, false));        // EAST
		}
	}

	public static NativeImage clad(Pixels base, Pixels metal, Part part, float shade) {
		int w = base.width();
		int h = base.height();
		int scale = Math.max(1, w / 64);
		int metalSize = Math.min(metal.width(), metal.height()); // first frame if animated
		int border = Math.max(1, metalSize / 16); // one vanilla texel at the pack's resolution
		NativeImage out = new NativeImage(w, h, false);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				out.setPixel(x, y, base.get(x, y));
			}
		}
		for (Panel panel : panels(part)) {
			int x0 = panel.x() * scale;
			int y0 = panel.y() * scale;
			int pw = panel.w() * scale;
			int ph = panel.h() * scale;
			for (int py = 0; py < ph; py++) {
				for (int px = 0; px < pw; px++) {
					int x = x0 + px;
					int y = y0 + py;
					if (x >= w || y >= h || (base.get(x, y) >>> 24) == 0) {
						continue;
					}
					boolean edge = py < scale || py >= ph - scale
						|| (px < scale && !panel.openLeft()) || (px >= pw - scale && !panel.openRight());
					int mx = interior(px, metalSize, border);
					int my = interior(py, metalSize, border);
					out.setPixel(x, y, scaleRgb(metal.get(mx, my), panel.light() * shade * (edge ? EDGE : 1.0F)));
				}
			}
		}
		return out;
	}

	/** Tiles the block's inside (everything but its outermost texel on each side) across a face. */
	static int interior(int p, int metalSize, int border) {
		return border + Math.floorMod(p, Math.max(1, metalSize - 2 * border));
	}

	private static int scaleRgb(int argb, float light) {
		int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * light));
		int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * light));
		int b = Math.min(255, Math.round((argb & 0xFF) * light));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
