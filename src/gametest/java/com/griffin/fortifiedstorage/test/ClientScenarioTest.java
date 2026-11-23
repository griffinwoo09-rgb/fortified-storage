package com.griffin.fortifiedstorage.test;

import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageKind;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.block.entity.FortifiedChestBlockEntity;
import com.griffin.fortifiedstorage.client.screen.FortifiedContainerScreen;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Builds one scene of every kind and tier, then screenshots it with no pack, with a generated
 * 32x "test pack" (tinted, double resolution vanilla textures) and with an explicit override pack.
 * Also checks the GUI, the trapped chest's redstone output, and a dedicated server round trip.
 */
public class ClientScenarioTest implements FabricClientGameTest {
	private static final int Y = -60; // superflat surface
	private static final String VIEW = "tp @a 0.5 -57.4 -2.2 0 27";

	@Override
	public void runTest(ClientGameTestContext context) {
		checkLayouts();
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			buildScene(world.getServer());
			context.getInput().setCursorPos(0, 0);
			world.getServer().runCommand(VIEW);
			world.getServer().runCommand("time set noon");
			context.waitTicks(40);
			world.getServer().runCommand(VIEW); // again, once the client has settled after joining
			context.waitTicks(5);
			world.getConnection().waitForChunksRender();
			for (String item : new String[] {"iron_chest", "gold_trapped_chest", "diamond_barrel", "netherite_shulker_box",
				"gold_lime_shulker_box", "iron_upgrade_kit", "gold_upgrade_kit", "diamond_upgrade_kit", "netherite_upgrade_kit"}) {
				world.getServer().runCommand("give @a fortified_storage:" + item);
			}
			context.waitTicks(5);
			context.takeScreenshot("1_scene_default");
			world.getServer().runCommand("tp @a 0.5 -57.3 0.3 0 40"); // close-up of the barrel and shulker rows
			context.waitTicks(10);
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("1b_closeup_barrels_shulkers");
			// The real diamond and gold blocks beside the diamond and gold chests, to compare colours by eye.
			world.getServer().runCommand("setblock 0 -60 3 minecraft:diamond_block");
			world.getServer().runCommand("setblock 2 -60 3 minecraft:gold_block");
			world.getServer().runCommand("tp @a 0.9 -58.9 0.6 0 30");
			context.waitTicks(10);
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("1c_chests_beside_real_blocks");
			setLids(world.getServer(), 1); // everyone's lids open, as if someone were looking in
			context.waitTicks(15);
			context.takeScreenshot("1d_chests_open");
			setLids(world.getServer(), 0);
			world.getServer().runCommand("setblock 0 -60 3 minecraft:air");
			world.getServer().runCommand("setblock 2 -60 3 minecraft:air");

			setGuiScale(context, 1); // 854x480 GUI pixels: the 330-slot double chest, two halves side by side
			openMenu(context, world.getServer(), new BlockPos(5, Y, 9));
			context.waitForScreen(FortifiedContainerScreen.class);
			context.takeScreenshot("2_gui_double_netherite_scale1");
			context.runOnClient(mc -> {
				// The second chest's first slot (index 165) starts the right half, level with slot 0.
				var slots = mc.player.containerMenu.slots;
				int perHalf = StorageTier.NETHERITE.slots();
				if (slots.get(perHalf).y != slots.get(0).y || slots.get(perHalf).x != slots.get(0).x + StorageTier.NETHERITE.columns * 18) {
					throw new AssertionError("double chest halves not side by side: slot " + perHalf + " at "
						+ slots.get(perHalf).x + "," + slots.get(perHalf).y + ", slot 0 at " + slots.get(0).x + "," + slots.get(0).y);
				}
			});
			closeMenu(context);
			setGuiScale(context, 2); // too small for 15 x 11 at scale 2, so the screen steps down to scale 1 while open
			openMenu(context, world.getServer(), new BlockPos(3, Y, 3));
			context.waitForScreen(FortifiedContainerScreen.class);
			context.takeScreenshot("3_gui_netherite_from_scale2");
			closeMenu(context);

			// Trapped chest: redstone output while someone is viewing it.
			BlockPos trapped = new BlockPos(-3, Y, 9);
			openMenu(context, world.getServer(), trapped);
			context.waitForScreen(FortifiedContainerScreen.class);
			context.waitTicks(12); // past the server's 5-tick viewer re-check, which vanilla's counter would fail
			int signal = world.getServer().computeOnServer(server ->
				server.overworld().getBlockState(trapped).getSignal(server.overworld(), trapped, Direction.NORTH));
			if (signal != 1) {
				throw new AssertionError("trapped chest signal with one viewer: expected 1, got " + signal);
			}
			closeMenu(context);
			context.waitTicks(10);
			int after = world.getServer().computeOnServer(server ->
				server.overworld().getBlockState(trapped).getSignal(server.overworld(), trapped, Direction.NORTH));
			if (after != 0) {
				throw new AssertionError("trapped chest signal after closing: expected 0, got " + after);
			}

			checkBarrelOpenState(context, world.getServer(), new BlockPos(-3, Y, 5));
			checkKitRefusedWhileOpen(context, world.getServer(), new BlockPos(-3, Y, 3));
			checkRefitOnResize(context, world.getServer(), new BlockPos(5, Y, 9));
			world.getServer().runCommand(VIEW);
			try {
				context.runOnClient(ClientScenarioTest::writeTestPacks);
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
			world.getServer().runCommand(VIEW);
			selectPacks(context, "file/fs_test_64x");
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("4_scene_test_pack_64x");
			openMenu(context, world.getServer(), new BlockPos(1, Y, 3));
			context.waitForScreen(FortifiedContainerScreen.class);
			context.takeScreenshot("5_gui_diamond_64x");
			closeMenu(context);

			world.getServer().runCommand(VIEW);
			selectPacks(context, "file/fs_test_64x", "file/fs_test_override");
			world.getConnection().waitForChunksRender();
			context.takeScreenshot("6_scene_override_gold");

			// Optional: a real third-party pack (e.g. Faithful 32x) that knows nothing about this mod.
			// Not bundled; point FS_THIRD_PARTY_PACK at an unzipped pack folder to include it.
			String thirdParty = System.getenv("FS_THIRD_PARTY_PACK");
			if (thirdParty != null) {
				try {
					copyPack(Path.of(thirdParty), context.computeOnClient(Minecraft::getResourcePackDirectory).resolve("third_party"));
				} catch (IOException e) {
					throw new java.io.UncheckedIOException(e);
				}
				world.getServer().runCommand(VIEW);
				selectPacks(context, "file/third_party");
				world.getConnection().waitForChunksRender();
				context.takeScreenshot("9_scene_third_party_pack");
				world.getServer().runCommand("tp @a 0.5 -57.3 0.3 0 40");
				context.waitTicks(10);
				world.getConnection().waitForChunksRender();
				context.takeScreenshot("9b_closeup_third_party_pack");
				openMenu(context, world.getServer(), new BlockPos(4, Y, 9));
				context.waitForScreen(FortifiedContainerScreen.class);
				context.takeScreenshot("9c_gui_third_party_pack");
				closeMenu(context);
			}
			selectPacks(context);
		}

		// Multiplayer: the same blocks on a dedicated server, client connected.
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
			TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			buildScene(server);
			server.runCommand(VIEW);
			server.runCommand("time set noon");
			context.waitTicks(120); // let join toasts disappear
			server.runCommand(VIEW);
			context.waitTicks(5);
			connection.waitForChunksRender();
			context.takeScreenshot("7_dedicated_server_scene");
			openMenu(context, server, new BlockPos(3, Y, 3));
			context.waitForScreen(FortifiedContainerScreen.class);
			context.takeScreenshot("8_dedicated_server_gui");
			closeMenu(context);
		}
	}

	/** Rows facing the camera: chests, barrels, shulker boxes, then trapped/double chests. */
	private static void buildScene(TestServerContext server) {
		StorageTier[] tiers = StorageTier.values();
		for (int i = 0; i < tiers.length; i++) {
			int x = -3 + i * 2;
			setblock(server, x, 3, ModContent.block(StorageKind.CHEST, tiers[i], null), "[facing=north]");
			setblock(server, x, 5, ModContent.block(StorageKind.BARREL, tiers[i], null), "[facing=north]");
			setblock(server, x, 7, ModContent.block(StorageKind.SHULKER_BOX, tiers[i], i == 0 ? null : DyeColor.values()[i * 4]), "[facing=up]");
		}
		setblock(server, -3, 9, ModContent.block(StorageKind.TRAPPED_CHEST, StorageTier.GOLD, null), "[facing=north]");
		setblock(server, -1, 9, ModContent.block(StorageKind.TRAPPED_CHEST, StorageTier.DIAMOND, null), "[facing=north,type=left]");
		setblock(server, 0, 9, ModContent.block(StorageKind.TRAPPED_CHEST, StorageTier.DIAMOND, null), "[facing=north,type=right]");
		setblock(server, 4, 9, ModContent.block(StorageKind.CHEST, StorageTier.NETHERITE, null), "[facing=north,type=left]"); // facing north: west half is LEFT
		setblock(server, 5, 9, ModContent.block(StorageKind.CHEST, StorageTier.NETHERITE, null), "[facing=north,type=right]");
		// Something to look at in the GUI, including the last slot of the double chest.
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			if (level.getBlockEntity(new BlockPos(4, Y, 9)) instanceof FortifiedChestBlockEntity left
				&& level.getBlockEntity(new BlockPos(5, Y, 9)) instanceof FortifiedChestBlockEntity right) {
				// Vanilla order: the RIGHT half holds the first slots of a double chest.
				right.setItem(0, new ItemStack(Items.DIAMOND, 64));
				right.setItem(14, new ItemStack(Items.NETHERITE_INGOT, 3));
				left.setItem(left.getContainerSize() - 1, new ItemStack(Items.EMERALD, 12));
			}
		});
	}

	private static void setblock(TestServerContext server, int x, int z, Block block, String state) {
		server.runCommand("setblock " + x + " " + Y + " " + z + " " + BuiltInRegistries.BLOCK.getKey(block) + state);
	}

	/** Opens the block's menu for the (only) player, exactly as right-clicking would. */
	private static void openMenu(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
		// Stand next to it: the server closes menus that are out of reach, just like in normal play.
		server.runCommand("tp @a " + (pos.getX() + 0.5) + " " + pos.getY() + " " + (pos.getZ() - 1.5) + " 0 30");
		context.waitTicks(3);
		server.runOnServer((MinecraftServer s) -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			ServerLevel level = s.overworld();
			MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
			if (provider == null) {
				throw new AssertionError("no menu at " + pos + ": " + level.getBlockState(pos));
			}
			player.openMenu(provider);
		});
	}

	/**
	 * The metal on a chest should read as the same colour as the placed block. Checks the lid's narrow
	 * front band, where sampling the block's dark border used to turn the diamond chest deep teal.
	 */
	private static void checkChestMatchesBlockColour(Minecraft mc) {
		var resources = mc.getResourceManager();
		for (StorageTier tier : StorageTier.values()) {
			var block = com.griffin.fortifiedstorage.client.texture.TieredSpriteSource.Pixels.read(resources, tier.metalTexture());
			var chest = com.griffin.fortifiedstorage.client.texture.TieredSpriteSource.Pixels.read(resources, Identifier.withDefaultNamespace("entity/chest/normal"));
			try (NativeImage clad = com.griffin.fortifiedstorage.client.texture.ChestPanels.clad(chest, block,
				com.griffin.fortifiedstorage.client.texture.ChestPanels.Part.SINGLE, tier.trimShade)) {
				int scale = clad.getWidth() / 64;
				int[] band = average(clad::getPixel, 15 * scale, 15 * scale, 12 * scale, 3 * scale); // lid front, inside the outline
				int b = Math.max(1, block.width() / 16);
				int[] inside = average((x, y) -> block.argb()[x + y * block.width()], b, b, block.width() - 2 * b, block.width() - 2 * b);
				for (int c = 0; c < 3; c++) {
					if (Math.abs(band[c] - Math.round(inside[c] * tier.trimShade)) > 16) {
						throw new AssertionError(tier + " chest lid band " + java.util.Arrays.toString(band)
							+ " doesn't match the block's colour " + java.util.Arrays.toString(inside));
					}
				}
				// The floor you see when the lid is open is shadowed metal, not the pack's brown wood.
				int[] floor = average(clad::getPixel, 28 * scale, 19 * scale, 14 * scale, 14 * scale);
				for (int c = 0; c < 3; c++) {
					if (Math.abs(floor[c] - Math.round(inside[c] * tier.trimShade * 0.3F)) > 16) {
						throw new AssertionError(tier + " chest floor " + java.util.Arrays.toString(floor)
							+ " isn't shadowed metal (block " + java.util.Arrays.toString(inside) + ")");
					}
				}
				// The face's outer edge is darkened metal too, not the pack's wooden chest outline.
				int[] edge = average(clad::getPixel, 15 * scale, 14 * scale, 12 * scale, scale); // top edge of the lid front
				for (int c = 0; c < 3; c++) {
					if (Math.abs(edge[c] - Math.round(inside[c] * tier.trimShade * 0.6F)) > 16) {
						throw new AssertionError(tier + " chest edge " + java.util.Arrays.toString(edge)
							+ " isn't darkened metal (block " + java.util.Arrays.toString(inside) + ")");
					}
				}
			}
		}
	}

	private static int[] average(java.util.function.IntBinaryOperator pixel, int x0, int y0, int w, int h) {
		long[] sum = new long[3];
		for (int y = y0; y < y0 + h; y++) {
			for (int x = x0; x < x0 + w; x++) {
				int p = pixel.applyAsInt(x, y);
				sum[0] += (p >> 16) & 0xFF;
				sum[1] += (p >> 8) & 0xFF;
				sum[2] += p & 0xFF;
			}
		}
		int n = w * h;
		return new int[] {(int) (sum[0] / n), (int) (sum[1] / n), (int) (sum[2] / n)};
	}

	/** The barrel's OPEN state follows its viewers, including after the 5-tick re-check. */
	private static void checkBarrelOpenState(ClientGameTestContext context, TestServerContext server, BlockPos barrel) {
		openMenu(context, server, barrel);
		context.waitForScreen(FortifiedContainerScreen.class);
		context.waitTicks(12);
		if (!server.computeOnServer(s -> s.overworld().getBlockState(barrel).getValue(net.minecraft.world.level.block.BarrelBlock.OPEN))) {
			throw new AssertionError("barrel not OPEN while viewed");
		}
		closeMenu(context);
		context.waitTicks(10);
		if (server.computeOnServer(s -> s.overworld().getBlockState(barrel).getValue(net.minecraft.world.level.block.BarrelBlock.OPEN))) {
			throw new AssertionError("barrel still OPEN after closing");
		}
	}

	/** A kit must not convert a container someone is looking into (their menu would point at the emptied old one). */
	private static void checkKitRefusedWhileOpen(ClientGameTestContext context, TestServerContext server, BlockPos chest) {
		openMenu(context, server, chest);
		context.waitForScreen(FortifiedContainerScreen.class);
		boolean refused = server.computeOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			ItemStack held = player.getMainHandItem();
			ItemStack kit = new ItemStack(ModContent.UPGRADE_KITS.get(StorageTier.GOLD));
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, kit);
			player.setShiftKeyDown(true);
			var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(chest), Direction.UP, chest, false);
			kit.useOn(new net.minecraft.world.item.context.UseOnContext(s.overworld(), player, net.minecraft.world.InteractionHand.MAIN_HAND, kit, hit));
			player.setShiftKeyDown(false);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
			return s.overworld().getBlockState(chest).is(ModContent.block(StorageKind.CHEST, StorageTier.IRON, null)) && kit.getCount() == 1;
		});
		closeMenu(context);
		if (!refused) {
			throw new AssertionError("upgrade kit converted a chest that was open");
		}
	}

	/** Changing the GUI scale while the screen is open re-fits it instead of leaving it off screen. */
	private static void checkRefitOnResize(ClientGameTestContext context, TestServerContext server, BlockPos chest) {
		setGuiScale(context, 1);
		openMenu(context, server, chest);
		context.waitForScreen(FortifiedContainerScreen.class);
		setGuiScale(context, 3);
		boolean fits = context.computeOnClient(mc -> {
			if (mc.getWindow().getGuiScale() != 1) {
				return false; // the double netherite chest only fits 854x480 at scale 1, so it must have stepped down
			}
			try {
				var field = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("imageWidth");
				field.setAccessible(true);
				return (int) field.get(mc.gui.screen()) <= mc.getWindow().getGuiScaledWidth();
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
		closeMenu(context);
		int restored = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		int expected = context.computeOnClient(mc -> mc.getWindow().calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode()));
		setGuiScale(context, 2);
		if (!fits) {
			throw new AssertionError("screen did not re-fit after the GUI scale changed");
		}
		if (restored != expected) {
			throw new AssertionError("GUI scale not restored after closing: " + restored);
		}
	}

	private static void setGuiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTick();
	}

	/** The grid keeps its own shape; the GUI scale drops just enough to fit it. */
	private static void checkLayouts() {
		record Case(int columns, int rows, int windowWidth, int windowHeight, int guiScale, int expectedScale) {
		}
		for (Case c : List.of(
			new Case(9, 6, 1920, 1080, 4, 4),     // iron chest at 1080p, GUI scale 4: fits as is
			new Case(15, 11, 1920, 1080, 4, 3),   // netherite chest: one step down
			new Case(30, 11, 1920, 1080, 4, 3),   // double netherite chest (330 slots): one step down
			new Case(24, 10, 2560, 1440, 4, 4),   // double diamond at 1440p
			new Case(30, 11, 854, 480, 2, 1))) {  // double netherite in the small default window
			int scale = com.griffin.fortifiedstorage.client.screen.GridLayout.fittingScale(c.columns(), c.rows(), c.windowWidth(), c.windowHeight(), c.guiScale());
			var layout = com.griffin.fortifiedstorage.client.screen.GridLayout.choose(c.columns(), c.rows(), c.windowWidth() / scale, c.windowHeight() / scale, true);
			if (scale != c.expectedScale() || layout.scrolls() || layout.columns() != c.columns() || layout.rows() != c.rows()) {
				throw new AssertionError("layout for " + c + ": scale " + scale + ", " + layout);
			}
		}
	}

	/** Opens (1) or closes (0) the lids of the front row of chests with the same block event a viewer triggers. */
	private static void setLids(TestServerContext server, int open) {
		server.runOnServer(s -> {
			for (int i = 0; i < StorageTier.values().length; i++) {
				BlockPos pos = new BlockPos(-3 + i * 2, Y, 3);
				s.overworld().blockEvent(pos, s.overworld().getBlockState(pos).getBlock(), 1, open);
			}
		});
	}

	/** Like pressing Escape: closes the screen and tells the server. */
	private static void closeMenu(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.player.closeContainer());
		context.waitTicks(2);
	}

	private static void selectPacks(ClientGameTestContext context, String... extra) {
		java.util.concurrent.CompletableFuture<Void> reload = context.computeOnClient(mc -> {
			var repository = mc.getResourcePackRepository();
			repository.reload();
			List<String> selected = new ArrayList<>(repository.getSelectedIds().stream().filter(id -> !id.startsWith("file/")).toList());
			selected.addAll(List.of(extra));
			repository.setSelected(selected);
			mc.options.updateResourcePacks(repository); // saves options; we trigger the reload ourselves to wait on it
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 20 * 60);
		context.waitTicks(60); // let the loading overlay fade out
	}

	/**
	 * fs_test_64x: vanilla chest/barrel/shulker/metal textures at 4x resolution, red and blue swapped,
	 * standing in for a 64x pack. fs_test_override: an explicit gold chest and gold barrel texture.
	 */
	private static void writeTestPacks(Minecraft mc) throws IOException {
		Path packs = mc.getResourcePackDirectory();
		Path upscaled = packs.resolve("fs_test_64x");
		writeMeta(upscaled, "Fortified Storage test: 64x tinted vanilla");
		List<String> textures = new ArrayList<>(List.of(
			"entity/chest/normal", "entity/chest/normal_left", "entity/chest/normal_right",
			"entity/chest/trapped", "entity/chest/trapped_left", "entity/chest/trapped_right",
			"block/barrel_side", "block/barrel_top", "block/barrel_top_open", "block/barrel_bottom",
			"entity/shulker/shulker", "block/shulker_box",
			"block/iron_block", "block/gold_block", "block/diamond_block", "block/netherite_block"));
		for (DyeColor color : DyeColor.values()) {
			textures.add("entity/shulker/shulker_" + color.getSerializedName());
		}
		for (String texture : textures) {
			Identifier file = Identifier.withDefaultNamespace("textures/" + texture + ".png");
			try (InputStream in = mc.getResourceManager().getResourceOrThrow(file).open(); NativeImage src = NativeImage.read(in)) {
				NativeImage out = new NativeImage(src.getWidth() * 4, src.getHeight() * 4, false);
				for (int y = 0; y < out.getHeight(); y++) {
					for (int x = 0; x < out.getWidth(); x++) {
						int p = src.getPixel(x / 4, y / 4);
						if ((x + y) % 2 == 0) {
							p = (p & 0xFF000000) | (Math.max(0, ((p >> 16) & 0xFF) - 24) << 16) | (Math.max(0, ((p >> 8) & 0xFF) - 24) << 8) | Math.max(0, (p & 0xFF) - 24);
						}
						// Swap red and blue so it's obvious the generated textures follow the pack.
						out.setPixel(x, y, (p & 0xFF00FF00) | ((p >> 16) & 0xFF) | ((p & 0xFF) << 16));
					}
				}
				write(out, upscaled.resolve("assets/minecraft/textures/" + texture + ".png"));
			}
		}

		Path override = packs.resolve("fs_test_override");
		writeMeta(override, "Fortified Storage test: explicit gold textures");
		for (String texture : List.of("entity/chest/gold", "block/gold_barrel_side")) {
			int size = texture.startsWith("entity") ? 64 : 16;
			NativeImage solid = new NativeImage(size, size, false);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					solid.setPixel(x, y, ((x / 4 + y / 4) % 2 == 0) ? 0xFFFF00FF : 0xFF00FFFF); // magenta/cyan checker
				}
			}
			write(solid, override.resolve("assets/fortified_storage/textures/" + texture + ".png"));
		}
	}

	private static void copyPack(Path from, Path to) throws IOException {
		try (var files = Files.walk(from)) {
			for (Path file : (Iterable<Path>) files::iterator) {
				Path target = to.resolve(from.relativize(file).toString());
				if (Files.isDirectory(file)) {
					Files.createDirectories(target);
				} else {
					Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private static void writeMeta(Path pack, String description) throws IOException {
		Files.createDirectories(pack);
		Files.writeString(pack.resolve("pack.mcmeta"),
			"{\"pack\":{\"description\":\"" + description + "\",\"min_format\":97,\"max_format\":97}}");
	}

	private static void write(NativeImage image, Path file) throws IOException {
		Files.createDirectories(file.getParent());
		try (image) {
			image.writeToFile(file);
		}
	}
}
