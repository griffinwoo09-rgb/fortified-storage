package com.griffin.fortifiedstorage.menu;

import com.griffin.fortifiedstorage.ModContent;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One menu for every tier and kind: a rows x columns grid plus the player inventory.
 * Slots start at their natural grid positions (vanilla's layout, before any screen offset or
 * scrolling) because inventory mods such as Inventory Profiles Next read the grid shape from them.
 */
public class FortifiedContainerMenu extends AbstractContainerMenu {
	/** Sent to the client when the menu opens so it can build the same layout. */
	/** {@code halves} is 2 for a double chest: the grid is two chests side by side, each half its own block of slots. */
	public record Data(int rows, int columns, boolean shulker, int halves) {
		public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Data::rows,
			ByteBufCodecs.VAR_INT, Data::columns,
			ByteBufCodecs.BOOL, Data::shulker,
			ByteBufCodecs.VAR_INT, Data::halves,
			Data::new
		);

		/** Also runs on data decoded from the network: a broken server can't make the client divide by zero. */
		public Data {
			if (rows < 1 || columns < 1 || halves < 1 || halves > 2 || columns % halves != 0) {
				throw new IllegalArgumentException("bad container layout: " + rows + "x" + columns + ", halves " + halves);
			}
		}

		public int slots() {
			return rows * columns;
		}
	}

	public static final int SLOT = 18;
	public static final int GRID_TOP = 18;

	private final Container container;
	private final Data data;

	/** Client-side constructor (from the open-screen packet). */
	public FortifiedContainerMenu(int containerId, Inventory inventory, Data data) {
		this(containerId, inventory, new SimpleContainer(data.slots()), data);
	}

	public FortifiedContainerMenu(int containerId, Inventory inventory, Container container, Data data) {
		super(ModContent.MENU, containerId);
		checkContainerSize(container, data.slots());
		this.container = container;
		this.data = data;
		container.startOpen(inventory.player);

		// A double chest's slots run through the first chest, then the second: each fills its own half.
		int halfColumns = data.columns() / data.halves();
		int perHalf = data.slots() / data.halves();
		for (int i = 0; i < data.slots(); i++) {
			int local = i % perHalf;
			int column = i / perHalf * halfColumns + local % halfColumns;
			addSlot(new GridSlot(container, i, column, local / halfColumns, data.shulker()));
		}
		addStandardInventorySlots(inventory, 8, GRID_TOP + data.rows() * SLOT + 13);
	}

	/** True when {@code player} is looking at {@code container} (or a double chest containing it) through this menu. */
	public static boolean isViewing(Player player, Container container) {
		return player.containerMenu instanceof FortifiedContainerMenu menu
			&& (menu.container == container || menu.container instanceof CompoundContainer compound && compound.contains(container));
	}

	public Data data() {
		return data;
	}

	public Container getContainer() {
		return container;
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int gridSlots = data.slots();
		boolean moved = slotIndex < gridSlots
			? moveItemStackTo(stack, gridSlots, slots.size(), true)
			: moveItemStackTo(stack, 0, gridSlots, false);
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		container.stopOpen(player);
	}

	/**
	 * A grid slot that knows its cell and refuses shulker boxes inside shulker boxes. {@code visible} is
	 * false while scrolled out of view; the slot stays active so sorting mods still include it.
	 */
	public static class GridSlot extends Slot {
		public final int column;
		public final int row;
		private final boolean shulker;
		public boolean visible = true;

		public GridSlot(Container container, int slot, int column, int row, boolean shulker) {
			super(container, slot, 8 + column * SLOT, GRID_TOP + row * SLOT);
			this.column = column;
			this.row = row;
			this.shulker = shulker;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			// Same rule as vanilla's ShulkerBoxSlot.
			return !shulker || stack.getItem().canFitInsideContainerItems();
		}
	}
}
