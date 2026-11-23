package com.griffin.fortifiedstorage.test;

import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageKind;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.client.screen.FortifiedContainerScreen;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;

/**
 * Runs only when popular inventory mods are loaded with -PcompatMods (see README): sorts a
 * fortified chest and a double chest with whichever sorting mod is present, checks on the
 * server that nothing was lost or duplicated and that the items really got grouped, tries
 * Mouse Tweaks' wheel transfer, and screenshots the recipe-viewer overlay and block tooltip.
 */
public class CompatScenarioTest implements FabricClientGameTest {
	private static final int Y = -60;
	private static final BlockPos NETHERITE = new BlockPos(0, Y, 2);
	private static final BlockPos DOUBLE_LEFT = new BlockPos(3, Y, 2);
	private static final List<Item> MIX = List.of(Items.COBBLESTONE, Items.DIRT, Items.OAK_LOG, Items.IRON_INGOT, Items.REDSTONE);

	@Override
	public void runTest(ClientGameTestContext context) {
		FabricLoader loader = FabricLoader.getInstance();
		boolean clientSort = loader.isModLoaded("clientsort");
		boolean ipn = loader.isModLoaded("inventoryprofilesnext");
		if (!clientSort && !ipn && !loader.isModLoaded("mousetweaks")) {
			return; // plain test run: nothing to check
		}
		String group = clientSort ? "A" : "B";

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			world.getConnection().waitForChunksRender();
			server.runCommand("setblock " + NETHERITE.getX() + " " + Y + " " + NETHERITE.getZ() + " "
				+ BuiltInRegistries.BLOCK.getKey(ModContent.block(StorageKind.CHEST, StorageTier.NETHERITE, null)) + "[facing=north]");
			String diamond = BuiltInRegistries.BLOCK.getKey(ModContent.block(StorageKind.CHEST, StorageTier.DIAMOND, null)).toString();
			server.runCommand("setblock 3 " + Y + " 2 " + diamond + "[facing=north,type=left]");
			server.runCommand("setblock 4 " + Y + " 2 " + diamond + "[facing=north,type=right]");
			server.runCommand("time set noon");
			fillJumbled(server, NETHERITE, false);
			fillJumbled(server, DOUBLE_LEFT, true);
			Map<Item, Integer> netheriteBefore = totals(server, NETHERITE);
			Map<Item, Integer> doubleBefore = totals(server, DOUBLE_LEFT);

			if (ipn) {
				// Control: a vanilla chest sorted the same way, so a failure below is about this mod, not the test.
				BlockPos vanilla = new BlockPos(-3, Y, 2);
				server.runCommand("setblock -3 " + Y + " 2 minecraft:chest[facing=north]");
				fillJumbled(server, vanilla, false);
				Map<Item, Integer> vanillaBefore = totals(server, vanilla);
				open(context, server, vanilla, net.minecraft.client.gui.screens.inventory.ContainerScreen.class);
				sort(context, false);
				context.takeScreenshot("compat_B_0_vanilla_chest_after_sort");
				closeMenu(context);
				checkSorted(server, vanilla, vanillaBefore, "vanilla chest (control: if this fails, the IPN step itself is broken)");
			}

			// Single netherite chest: open, screenshot (recipe viewer overlay, sort buttons), sort, verify.
			open(context, server, NETHERITE);
			context.takeScreenshot("compat_" + group + "_1_netherite_before_sort");
			sort(context, clientSort);
			context.takeScreenshot("compat_" + group + "_2_netherite_after_sort");
			closeMenu(context);
			checkSorted(server, NETHERITE, netheriteBefore, "netherite chest");

			// Double diamond chest (two halves side by side).
			open(context, server, DOUBLE_LEFT);
			sort(context, clientSort);
			context.takeScreenshot("compat_" + group + "_3_double_after_sort");
			closeMenu(context);
			checkSorted(server, DOUBLE_LEFT, doubleBefore, "double diamond chest");

			// Scroll fallback: a window too small even at GUI scale 1, so most rows are scrolled out of view.
			// The sorter must still sort the whole chest, not just the visible rows.
			server.runOnServer(s -> container(s.overworld(), DOUBLE_LEFT).clearContent());
			fillJumbled(server, DOUBLE_LEFT, true);
			Map<Item, Integer> scrolledBefore = totals(server, DOUBLE_LEFT);
			context.getInput().resizeWindow(500, 300);
			context.waitTicks(5);
			open(context, server, DOUBLE_LEFT);
			if (!context.computeOnClient(mc -> mc.player.containerMenu.slots.stream().anyMatch(slot ->
				slot instanceof com.griffin.fortifiedstorage.menu.FortifiedContainerMenu.GridSlot grid && !grid.visible))) {
				throw new AssertionError("expected the double chest to scroll in a 500x300 window");
			}
			sort(context, clientSort);
			context.takeScreenshot("compat_" + group + "_3b_double_scrolled_after_sort");
			closeMenu(context);
			context.getInput().resizeWindow(854, 480);
			context.waitTicks(5);
			checkSorted(server, DOUBLE_LEFT, scrolledBefore, "scrolled double diamond chest");

			// Mouse Tweaks: the wheel over a stack in the player's inventory moves items into the chest.
			if (loader.isModLoaded("mousetweaks")) {
				server.runCommand("clear @a");
				server.runCommand("give @a minecraft:emerald 16");
				open(context, server, NETHERITE);
				hover(context, slots -> slots.stream().filter(s -> s.container == context.computeOnClient(mc -> mc.player.getInventory())
					&& s.getItem().is(Items.EMERALD)).findFirst().orElseThrow());
				for (int i = 0; i < 4; i++) {
					context.getInput().scroll(-1);
					context.waitTicks(2);
				}
				context.getInput().scroll(1);
				context.waitTicks(5);
				closeMenu(context);
				int emeralds = server.computeOnServer(s -> container(s.overworld(), NETHERITE).countItem(Items.EMERALD));
				int held = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().countItem(Items.EMERALD));
				if (emeralds == 0 || emeralds + held != 16) {
					throw new AssertionError("Mouse Tweaks wheel transfer: chest " + emeralds + ", player " + held + " (16 total expected, some moved)");
				}
			}

			// Block tooltip (Jade / WTHIT) looking at the chest.
			server.runCommand("tp @a 0.5 -60 0.2 0 20");
			context.getInput().lookAt(NETHERITE);
			context.waitTicks(20);
			context.takeScreenshot("compat_" + group + "_4_block_tooltip");
		}
	}

	/** Five kinds of items in odd counts, scattered with gaps across the whole container. */
	private static void fillJumbled(TestServerContext server, BlockPos pos, boolean includeSecondHalf) {
		server.runOnServer(s -> {
			Container container = container(s.overworld(), pos);
			int n = container.getContainerSize();
			int step = n < 100 ? 2 : 7; // always enough stacks that every kind repeats, so "sorted" can't happen by accident
			for (int i = 0; i < n; i += step) {
				container.setItem(i, new ItemStack(MIX.get(i % MIX.size()), 1 + i % 40));
			}
		});
	}

	private static Container container(ServerLevel level, BlockPos pos) {
		return Objects.requireNonNull(ChestBlock.getContainer((ChestBlock) level.getBlockState(pos).getBlock(), level.getBlockState(pos), level, pos, true));
	}

	private static Map<Item, Integer> totals(TestServerContext server, BlockPos pos) {
		return server.computeOnServer(s -> {
			Map<Item, Integer> totals = new HashMap<>();
			Container container = container(s.overworld(), pos);
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (!stack.isEmpty()) {
					totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
			}
			return totals;
		});
	}

	/** Nothing lost or duplicated, and each kind of item now sits in one unbroken run of slots. */
	private static void checkSorted(TestServerContext server, BlockPos pos, Map<Item, Integer> before, String what) {
		Map<Item, Integer> after = totals(server, pos);
		if (!after.equals(before)) {
			throw new AssertionError(what + ": items changed while sorting. before " + before + ", after " + after);
		}
		List<Item> order = server.computeOnServer(s -> {
			List<Item> items = new ArrayList<>();
			Container container = container(s.overworld(), pos);
			for (int i = 0; i < container.getContainerSize(); i++) {
				if (!container.getItem(i).isEmpty()) {
					items.add(container.getItem(i).getItem());
				}
			}
			return items;
		});
		List<Item> seen = new ArrayList<>();
		for (Item item : order) {
			if (seen.isEmpty() || seen.getLast() != item) {
				if (seen.contains(item)) {
					throw new AssertionError(what + " was not sorted: " + item + " appears in more than one group: " + order);
				}
				seen.add(item);
			}
		}
	}

	private static void open(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
		open(context, server, pos, FortifiedContainerScreen.class);
	}

	private static void open(ClientGameTestContext context, TestServerContext server, BlockPos pos, Class<? extends net.minecraft.client.gui.screens.Screen> screen) {
		server.runCommand("tp @a " + (pos.getX() + 0.5) + " " + pos.getY() + " " + (pos.getZ() - 1.5) + " 0 30");
		context.waitTicks(3);
		server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().openMenu(s.overworld().getBlockState(pos).getMenuProvider(s.overworld(), pos)));
		context.waitForScreen(screen);
		context.waitTicks(10);
	}

	private static void closeMenu(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.player.closeContainer());
		context.waitTicks(5);
	}

	/** Puts the mouse over a container slot (sorting mods act on the inventory under the cursor), then sorts. */
	private static void sort(ClientGameTestContext context, boolean clientSort) {
		hover(context, slots -> slots.getFirst());
		if (clientSort) {
			KeyMapping sortKey = context.computeOnClient(mc -> java.util.Arrays.stream(mc.options.keyMappings)
				.filter(k -> k.getName().equals("key.clientsort.op.sort")).findFirst().orElseThrow());
			context.getInput().pressKey(sortKey);
		} else {
			// Inventory Profiles Next doesn't receive the test harness's simulated mouse clicks (not even on a vanilla
			// chest), so call exactly what its container "Sort" button runs: doSort(menu, fromButton = true, playerSide = false).
			try {
				context.runOnClient(mc -> {
					Class<?> actions = Class.forName("org.anti_ad.mc.ipnext.inventory.GeneralInventoryActions");
					actions.getMethod("doSort", net.minecraft.world.inventory.AbstractContainerMenu.class, boolean.class, boolean.class)
						.invoke(actions.getField("INSTANCE").get(null), mc.player.containerMenu, true, false);
				});
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("couldn't call Inventory Profiles Next's sort", e);
			}
		}
		context.waitTicks(40); // client-side sorters click slot by slot
	}

	private static void hover(ClientGameTestContext context, java.util.function.Function<List<Slot>, Slot> pick) {
		Slot slot = context.computeOnClient(mc -> pick.apply(((AbstractContainerScreen<?>) mc.gui.screen()).getMenu().slots));
		double[] at = screenPoint(context, (w, h) -> new int[] {slot.x + 8, slot.y + 8});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
	}

	/** Window pixel position of a point given relative to the open container screen (from its width and height). */
	private static double[] screenPoint(ClientGameTestContext context, java.util.function.BiFunction<Integer, Integer, int[]> relative) {
		return context.computeOnClient(mc -> {
			var screen = (AbstractContainerScreen<?>) mc.gui.screen();
			double scale = mc.getWindow().getGuiScale();
			try {
				int left = field(screen, "leftPos");
				int top = field(screen, "topPos");
				int[] p = relative.apply(field(screen, "imageWidth"), field(screen, "imageHeight"));
				return new double[] {(left + p[0]) * scale, (top + p[1]) * scale};
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
	}

	private static int field(AbstractContainerScreen<?> screen, String name) throws ReflectiveOperationException {
		var field = AbstractContainerScreen.class.getDeclaredField(name);
		field.setAccessible(true);
		return (int) field.get(screen);
	}
}
