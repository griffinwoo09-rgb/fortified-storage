package com.griffin.fortifiedstorage.client.screen;

import com.griffin.fortifiedstorage.client.FortifiedConfig;
import com.griffin.fortifiedstorage.menu.FortifiedContainerMenu;
import com.griffin.fortifiedstorage.menu.FortifiedContainerMenu.GridSlot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Draws the background from vanilla pieces (the generic container panel's border, nine-sliced,
 * plus the {@code container/slot} sprite per slot), so a resource pack's GUI style carries over.
 * The grid always has the container's own shape. If that doesn't fit at the player's GUI scale,
 * the GUI scale is lowered while this screen is open and restored when it closes; it only scrolls
 * when even GUI scale 1 is too small.
 */
public class FortifiedContainerScreen extends AbstractContainerScreen<FortifiedContainerMenu> {
	private static final Identifier PANEL = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");
	private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
	private static final int S = FortifiedContainerMenu.SLOT;
	private static final int THUMB_HEIGHT = 15;
	private static final int OFFSCREEN_Y = 100_000;

	private GridLayout layout;
	private final int gridSlots;
	private int scroll;
	private boolean draggingBar;
	private boolean loweredGuiScale;

	public FortifiedContainerScreen(FortifiedContainerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title); // real size is set in init()
		this.gridSlots = menu.data().slots();
	}

	@Override
	protected void init() {
		// Runs again on window resize / GUI scale change, so the layout always fits the current window.
		var window = minecraft.getWindow();
		int columns = menu.data().columns();
		int rows = menu.data().rows();
		int scale = GridLayout.fittingScale(columns, rows, window.getWidth(), window.getHeight(), window.getGuiScale());
		if (scale != window.getGuiScale()) {
			window.setGuiScale(scale);
			width = window.getGuiScaledWidth();
			height = window.getGuiScaledHeight();
			minecraft.mouseHandler.resyncMousePosition();
			loweredGuiScale = true;
		}
		layout = GridLayout.choose(columns, rows, width, height, FortifiedConfig.INSTANCE.scrollGui);
		imageWidth = layout.width();
		imageHeight = layout.height();
		inventoryLabelX = layout.inventoryX();
		inventoryLabelY = layout.inventoryY() - 11;
		scroll = Math.min(scroll, layout.rows() - layout.visibleRows());
		super.init();
		layoutSlots();
	}

	/** Places every slot for the chosen layout and current scroll offset. */
	private void layoutSlots() {
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.slots.get(i);
			if (slot instanceof GridSlot grid) {
				int row = grid.row - scroll;
				grid.visible = row >= 0 && row < layout.visibleRows();
				slot.x = layout.gridX() + grid.column * S;
				// Scrolled-away rows stay active (sorting mods skip inactive or negative slots) but are parked
				// far below the screen, still in grid formation, where they are never drawn or hovered.
				slot.y = grid.visible ? FortifiedContainerMenu.GRID_TOP + row * S : OFFSCREEN_Y + grid.row * S;
			} else {
				int inv = i - gridSlots; // 27 main inventory slots, then 9 hotbar slots
				slot.x = layout.inventoryX() + (inv % 9) * S;
				slot.y = layout.inventoryY() + (inv < 27 ? (inv / 9) * S : 58);
			}
		}
	}

	@Override
	public void removed() {
		super.removed();
		if (loweredGuiScale) {
			// Back to the player's own GUI scale for whatever comes next (same as Minecraft.resizeGui, minus resizing this screen).
			var window = minecraft.getWindow();
			window.setGuiScale(window.calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode()));
			minecraft.mouseHandler.resyncMousePosition();
			loweredGuiScale = false;
		}
	}

	private void setScroll(int value) {
		int clamped = Mth.clamp(value, 0, layout.rows() - layout.visibleRows());
		if (clamped != scroll) {
			scroll = clamped;
			layoutSlots();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
		for (Slot slot : menu.slots) {
			if (!(slot instanceof GridSlot grid) || grid.visible) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, leftPos + slot.x - 1, topPos + slot.y - 1, S, S);
			}
		}
		if (layout.scrolls()) {
			int x = barX();
			int y = topPos + FortifiedContainerMenu.GRID_TOP - 1;
			int height = layout.visibleRows() * S;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 14, height); // sunken track
			int thumbY = y + 1 + (int) ((height - THUMB_HEIGHT - 2) * (scroll / (float) (layout.rows() - layout.visibleRows())));
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER, x + 1, thumbY, 12, THUMB_HEIGHT);
		}
	}

	/** Nine-slices the vanilla container panel: 4px corners/edges, centre stretched from a plain pixel. */
	private static void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		int c = 4;
		int r = 176 - c; // right edge of the source panel
		int b = 222 - c; // bottom edge of the source panel
		blit(g, x, y, 0, 0, c, c, c, c);
		blit(g, x + w - c, y, r, 0, c, c, c, c);
		blit(g, x, y + h - c, 0, b, c, c, c, c);
		blit(g, x + w - c, y + h - c, r, b, c, c, c, c);
		blit(g, x + c, y, c, 0, w - 2 * c, c, 1, c);
		blit(g, x + c, y + h - c, c, b, w - 2 * c, c, 1, c);
		blit(g, x, y + c, 0, c, c, h - 2 * c, c, 1);
		blit(g, x + w - c, y + c, r, c, c, h - 2 * c, c, 1);
		blit(g, x + c, y + c, 6, 6, w - 2 * c, h - 2 * c, 1, 1);
	}

	private static void blit(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h, int srcW, int srcH) {
		g.blit(RenderPipelines.GUI_TEXTURED, PANEL, x, y, u, v, w, h, srcW, srcH, 256, 256);
	}

	private int barX() {
		return leftPos + layout.gridX() + layout.columns() * S + 3;
	}

	private boolean overBar(double mouseX, double mouseY) {
		int top = topPos + FortifiedContainerMenu.GRID_TOP;
		return layout.scrolls() && mouseX >= barX() && mouseX < barX() + 14 && mouseY >= top && mouseY < top + layout.visibleRows() * S;
	}

	private void scrollToMouse(double mouseY) {
		float top = topPos + FortifiedContainerMenu.GRID_TOP + THUMB_HEIGHT / 2.0F;
		float range = layout.visibleRows() * S - THUMB_HEIGHT;
		setScroll(Math.round((float) (mouseY - top) / range * (layout.rows() - layout.visibleRows())));
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (super.mouseScrolled(x, y, scrollX, scrollY)) {
			return true; // e.g. bundle item scrolling
		}
		if (layout.scrolls() && scrollY != 0) {
			setScroll(scroll - (int) Math.signum(scrollY));
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && overBar(event.x(), event.y())) {
			draggingBar = true;
			scrollToMouse(event.y());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingBar) {
			scrollToMouse(event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggingBar && event.button() == 0) {
			draggingBar = false;
			return true;
		}
		return super.mouseReleased(event);
	}
}
