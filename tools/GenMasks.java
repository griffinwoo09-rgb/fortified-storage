// Generates the trim masks in assets/fortified_storage/textures/mask from vanilla model UV geometry.
// Run: java tools/GenMasks.java src/main/resources/assets/fortified_storage/textures/mask
// Mask pixels: alpha = trim coverage, gray = trim brightness (200 = metal at normal brightness).
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenMasks {
	static final int EDGE = 200, CORNER = 245, SHADOW = 150, LATCH = 225;

	public static void main(String[] args) throws Exception {
		File dir = new File(args[0]);
		// Chests have no mask: they are clad in metal by ChestPanels at runtime.
		shulker(dir);
		barrel(dir);
		BufferedImage kit = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) kit.setRGB(x, y, 0xA0D2D2D2); // 63% metal tint over the template
		ImageIO.write(kit, "png", new File(dir, "upgrade_kit.png"));
	}

	/** ModelPart box UV layout: the six face rects for a box at (u,v) with size (w,h,d). */
	static void box(BufferedImage img, int u, int v, int w, int h, int d) {
		frame(img, u + d, v, w, d);             // DOWN
		frame(img, u + d + w, v, w, d);         // UP
		frame(img, u, v + d, d, h);             // WEST
		frame(img, u + d, v + d, w, h);         // NORTH
		frame(img, u + d + w, v + d, d, h);     // EAST
		frame(img, u + d + w + d, v + d, w, h); // SOUTH
	}

	/** 1-texel trim around a face; corners brighter. */
	static void frame(BufferedImage img, int x0, int y0, int w, int h) {
		for (int y = y0; y < y0 + h; y++) {
			for (int x = x0; x < x0 + w; x++) {
				boolean left = x == x0, right = x == x0 + w - 1;
				boolean top = y == y0, bottom = y == y0 + h - 1;
				boolean edgeX = left || right, edgeY = top || bottom;
				if (edgeX && edgeY) put(img, x, y, CORNER);
				else if (edgeX || edgeY) put(img, x, y, bottom ? SHADOW + 30 : EDGE);
			}
		}
	}

	static void fill(BufferedImage img, int x0, int y0, int w, int h, int gray) {
		for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) put(img, x, y, gray);
	}

	static void put(BufferedImage img, int x, int y, int gray) {
		if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, 0xFF000000 | gray << 16 | gray << 8 | gray);
	}


	static void shulker(File dir) throws Exception {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		box(img, 0, 0, 16, 12, 16);  // lid
		box(img, 0, 28, 16, 8, 16);  // base
		ImageIO.write(img, "png", new File(dir, "shulker.png"));
		BufferedImage block = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		frame(block, 0, 0, 16, 16);
		ImageIO.write(block, "png", new File(dir, "shulker_block.png"));
	}

	static void barrel(File dir) throws Exception {
		BufferedImage side = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		// Hoops sit on vanilla's dark bands (rows 3-4 and 11-12), plus the rims.
		for (int x = 0; x < 16; x++) {
			put(side, x, 0, EDGE); put(side, x, 15, SHADOW);
			put(side, x, 3, CORNER); put(side, x, 4, SHADOW);
			put(side, x, 11, CORNER); put(side, x, 12, SHADOW);
		}
		ImageIO.write(side, "png", new File(dir, "barrel_side.png"));
		for (String part : new String[] {"barrel_top", "barrel_top_open", "barrel_bottom"}) {
			BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			frame(img, 0, 0, 16, 16);
			ImageIO.write(img, "png", new File(dir, part + ".png"));
		}
	}
}
