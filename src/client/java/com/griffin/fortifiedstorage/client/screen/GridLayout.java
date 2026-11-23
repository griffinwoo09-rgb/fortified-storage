package com.griffin.fortifiedstorage.client.screen;

import static com.griffin.fortifiedstorage.menu.FortifiedContainerMenu.GRID_TOP;
import static com.griffin.fortifiedstorage.menu.FortifiedContainerMenu.SLOT;

/**
 * Where the container grid and the player inventory go. The grid always keeps the container's own
 * shape (full rows: 9 x 6 for iron, 15 x 11 for netherite, two halves side by side for a double
 * chest). To make it fit, the screen lowers the GUI scale ({@link #fittingScale}); it only scrolls
 * when even GUI scale 1 is too small.
 */
public record GridLayout(int columns, int rows, int visibleRows, int width, int height) {
	static final int SCROLLBAR = 18;
	private static final int MARGIN = 4;
	private static final int INVENTORY_WIDTH = 9 * SLOT;

	public boolean scrolls() {
		return visibleRows < rows;
	}

	/** Left edge of the grid's first slot. */
	public int gridX() {
		return (baseWidth() - columns * SLOT) / 2 + 1;
	}

	public int inventoryX() {
		return (baseWidth() - INVENTORY_WIDTH) / 2 + 1;
	}

	/** Top of the player's main inventory (the hotbar is 58 below). */
	public int inventoryY() {
		return GRID_TOP + visibleRows * SLOT + 13;
	}

	/** Width without the scroll bar, for centring. */
	private int baseWidth() {
		return Math.max(176, columns * SLOT + 14);
	}

	public static GridLayout of(int columns, int rows, int visibleRows) {
		int width = Math.max(176, columns * SLOT + 14) + (visibleRows < rows ? SCROLLBAR : 0);
		return new GridLayout(columns, rows, visibleRows, width, GRID_TOP + visibleRows * SLOT + 96);
	}

	/** The full layout if it fits the GUI-scaled screen; otherwise as many rows as fit, scrolling (if allowed). */
	public static GridLayout choose(int columns, int rows, int screenWidth, int screenHeight, boolean allowScroll) {
		GridLayout full = of(columns, rows, rows);
		if (!allowScroll || fits(full, screenWidth, screenHeight)) {
			return full;
		}
		int visible = Math.clamp((screenHeight - 2 * MARGIN - 114) / SLOT, 1, rows);
		return of(columns, rows, visible);
	}

	/**
	 * The largest GUI scale, no bigger than {@code currentScale}, at which the full layout fits a
	 * window of {@code windowWidth} x {@code windowHeight} real pixels. 1 if nothing larger fits.
	 */
	public static int fittingScale(int columns, int rows, int windowWidth, int windowHeight, int currentScale) {
		GridLayout full = of(columns, rows, rows);
		for (int scale = currentScale; scale > 1; scale--) {
			if (fits(full, windowWidth / scale, windowHeight / scale)) {
				return scale;
			}
		}
		return 1;
	}

	private static boolean fits(GridLayout layout, int screenWidth, int screenHeight) {
		return layout.width() <= screenWidth - 2 * MARGIN && layout.height() <= screenHeight - 2 * MARGIN;
	}
}
