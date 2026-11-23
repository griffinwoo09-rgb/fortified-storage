package com.griffin.fortifiedstorage.test;

import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageKind;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.block.entity.FortifiedBarrelBlockEntity;
import com.griffin.fortifiedstorage.block.entity.FortifiedChestBlockEntity;
import com.griffin.fortifiedstorage.block.entity.FortifiedShulkerBoxBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class StorageGameTests {
	private static Block block(StorageKind kind, StorageTier tier) {
		return ModContent.block(kind, tier, null);
	}

	@GameTest
	public void slotCountsComeFromTier(GameTestHelper helper) {
		BlockPos chestPos = new BlockPos(1, 1, 1);
		BlockPos barrelPos = new BlockPos(3, 1, 1);
		BlockPos boxPos = new BlockPos(5, 1, 1);
		for (StorageTier tier : StorageTier.values()) {
			// A barrel and a shulker box hold exactly what a single chest of the same tier holds, in the same grid.
			helper.setBlock(chestPos, block(StorageKind.CHEST, tier));
			helper.setBlock(barrelPos, block(StorageKind.BARREL, tier));
			helper.setBlock(boxPos, ModContent.block(StorageKind.SHULKER_BOX, tier, DyeColor.RED));
			var chest = helper.getBlockEntity(chestPos, FortifiedChestBlockEntity.class);
			var barrel = helper.getBlockEntity(barrelPos, FortifiedBarrelBlockEntity.class);
			var box = helper.getBlockEntity(boxPos, FortifiedShulkerBoxBlockEntity.class);
			helper.assertValueEqual(chest.getContainerSize(), tier.rows * tier.columns, tier + " chest slots");
			helper.assertValueEqual(barrel.getContainerSize(), chest.getContainerSize(), tier + " barrel vs chest slots");
			helper.assertValueEqual(box.getContainerSize(), chest.getContainerSize(), tier + " shulker vs chest slots");
			var grid = chest.getScreenOpeningData(null);
			helper.assertValueEqual(barrel.getScreenOpeningData(null).columns(), grid.columns(), tier + " barrel columns");
			helper.assertValueEqual(barrel.getScreenOpeningData(null).rows(), grid.rows(), tier + " barrel rows");
			helper.assertValueEqual(box.getScreenOpeningData(null).columns(), grid.columns(), tier + " shulker columns");
			helper.assertValueEqual(box.getScreenOpeningData(null).rows(), grid.rows(), tier + " shulker rows");
		}
		helper.assertValueEqual(StorageTier.GOLD.slots(), 81, "gold slots");
		helper.assertValueEqual(StorageTier.DIAMOND.slots(), 120, "diamond slots");
		helper.assertValueEqual(StorageTier.NETHERITE.slots(), 165, "netherite slots");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void hopperFeedsBarrelAndComparatorReads(GameTestHelper helper) {
		BlockPos barrel = new BlockPos(1, 1, 1);
		BlockPos hopper = barrel.above();
		helper.setBlock(barrel, block(StorageKind.BARREL, StorageTier.GOLD));
		helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
		((Container) helper.getBlockEntity(hopper, net.minecraft.world.level.block.entity.HopperBlockEntity.class)).setItem(0, new ItemStack(Items.DIAMOND, 5));
		helper.succeedWhen(() -> {
			FortifiedBarrelBlockEntity be = helper.getBlockEntity(barrel, FortifiedBarrelBlockEntity.class);
			helper.assertValueEqual(be.countItem(Items.DIAMOND), 5, "diamonds in barrel");
			BlockState state = helper.getBlockState(barrel);
			int signal = state.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(barrel), Direction.NORTH);
			helper.assertTrue(signal > 0, "comparator signal should be > 0, was " + signal);
		});
	}

	@GameTest
	public void doubleChestMergesAndDoublesSlots(GameTestHelper helper) {
		Block iron = block(StorageKind.CHEST, StorageTier.IRON);
		BlockPos a = new BlockPos(1, 1, 1);
		BlockPos b = new BlockPos(2, 1, 1);
		helper.setBlock(a, iron.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(b, iron.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		Container both = ChestBlock.getContainer((ChestBlock) iron, helper.getBlockState(a), helper.getLevel(), helper.absolutePos(a), true);
		helper.assertValueEqual(both.getContainerSize(), 108, "double iron chest slots");
		// A different tier next to it must not join.
		helper.setBlock(new BlockPos(3, 1, 1), block(StorageKind.CHEST, StorageTier.GOLD).defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
		helper.assertBlockState(new BlockPos(3, 1, 1), s -> s.getValue(ChestBlock.TYPE) == ChestType.SINGLE, s -> Component.literal("gold chest joined an iron chest"));
		helper.succeed();
	}

	@GameTest
	public void transferApiSeesDoubleChestAndRejectsNestedShulkers(GameTestHelper helper) {
		Block gold = block(StorageKind.CHEST, StorageTier.GOLD);
		BlockPos a = new BlockPos(1, 1, 1);
		helper.setBlock(a, gold.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(a.east(), gold.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		Storage<ItemVariant> chest = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(a), Direction.UP);
		helper.assertTrue(chest != null, "no item storage on chest");
		try (Transaction tx = Transaction.openOuter()) {
			long inserted = chest.insert(ItemVariant.of(Items.COBBLESTONE), 64L * 162, tx);
			helper.assertValueEqual(inserted, 64L * 162, "cobblestone accepted by double gold chest");
		}

		BlockPos box = new BlockPos(1, 1, 3);
		helper.setBlock(box, ModContent.block(StorageKind.SHULKER_BOX, StorageTier.IRON, DyeColor.RED));
		Storage<ItemVariant> shulker = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(box), Direction.UP);
		try (Transaction tx = Transaction.openOuter()) {
			helper.assertValueEqual(shulker.insert(ItemVariant.of(Items.SHULKER_BOX), 1, tx), 0L, "vanilla shulker inserted into fortified shulker");
			helper.assertValueEqual(shulker.insert(ItemVariant.of(ModContent.block(StorageKind.SHULKER_BOX, StorageTier.GOLD, null).asItem()), 1, tx), 0L,
				"fortified shulker inserted into fortified shulker");
			helper.assertValueEqual(shulker.insert(ItemVariant.of(Items.DIRT), 1, tx), 1L, "dirt into shulker");
		}
		helper.succeed();
	}

	@GameTest
	public void upgradeKitConvertsVanillaDoubleChestKeepingItems(GameTestHelper helper) {
		BlockPos a = new BlockPos(1, 1, 1);
		BlockPos b = new BlockPos(2, 1, 1);
		helper.setBlock(a, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(b, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		ChestBlockEntity left = helper.getBlockEntity(a, ChestBlockEntity.class);
		ItemStack named = new ItemStack(Items.STICK);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Loot"));
		left.applyComponentsFromItemStack(named);
		left.setItem(26, new ItemStack(Items.EMERALD, 7));
		helper.getBlockEntity(b, ChestBlockEntity.class).setItem(0, new ItemStack(Items.APPLE, 3));

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);
		ItemStack kits = new ItemStack(ModContent.UPGRADE_KITS.get(StorageTier.IRON), 3);
		player.setItemInHand(InteractionHand.MAIN_HAND, kits);
		useKit(helper, player, a);

		Block iron = block(StorageKind.CHEST, StorageTier.IRON);
		helper.assertBlockState(a, s -> s.is(iron) && s.getValue(ChestBlock.TYPE) == ChestType.LEFT, s -> Component.literal("left half not upgraded: " + s));
		helper.assertBlockState(b, s -> s.is(iron) && s.getValue(ChestBlock.TYPE) == ChestType.RIGHT, s -> Component.literal("right half not upgraded: " + s));
		FortifiedChestBlockEntity newLeft = helper.getBlockEntity(a, FortifiedChestBlockEntity.class);
		helper.assertValueEqual(newLeft.getItem(26).getCount(), 7, "emeralds kept in same slot");
		helper.assertValueEqual(newLeft.getCustomName() == null ? "" : newLeft.getCustomName().getString(), "Loot", "custom name");
		helper.assertValueEqual(helper.getBlockEntity(b, FortifiedChestBlockEntity.class).getItem(0).getCount(), 3, "apples kept");
		helper.assertValueEqual(kits.getCount(), 1, "kits left (one per half)");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(a)).inflate(3)).isEmpty(), "items spilled");

		// The vanilla copper chest is explicitly not upgradeable.
		BlockPos copper = new BlockPos(1, 1, 3);
		helper.setBlock(copper, Blocks.COPPER_CHEST.weathering().unaffected());
		useKit(helper, player, copper);
		helper.assertBlockState(copper, s -> s.is(Blocks.COPPER_CHEST.weathering().unaffected()), s -> Component.literal("copper chest was converted"));

		// Wrong tier kit does nothing.
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.UPGRADE_KITS.get(StorageTier.DIAMOND)));
		useKit(helper, player, a);
		helper.assertBlockState(a, s -> s.is(iron), s -> Component.literal("diamond kit applied to iron chest"));
		helper.succeed();
	}

	@GameTest
	public void upgradeKitIgnoresFakeDoubleChestPartner(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);
		ItemStack kits = new ItemStack(ModContent.UPGRADE_KITS.get(StorageTier.IRON), 4);
		player.setItemInHand(InteractionHand.MAIN_HAND, kits);
		Block iron = block(StorageKind.CHEST, StorageTier.IRON);

		// A "left" half whose partner spot holds a furnace (possible via /setblock or structure files).
		BlockPos furnace = new BlockPos(2, 1, 1);
		BlockPos chest = new BlockPos(1, 1, 1);
		helper.setBlock(furnace, Blocks.FURNACE);
		helper.getBlockEntity(furnace, net.minecraft.world.level.block.entity.FurnaceBlockEntity.class).setItem(0, new ItemStack(Items.IRON_ORE, 5));
		helper.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.getBlockEntity(chest, ChestBlockEntity.class).setItem(3, new ItemStack(Items.COAL, 2));
		useKit(helper, player, chest);
		helper.assertBlockState(furnace, s -> s.is(Blocks.FURNACE), s -> Component.literal("furnace was converted: " + s));
		helper.assertValueEqual(helper.getBlockEntity(furnace, net.minecraft.world.level.block.entity.FurnaceBlockEntity.class).getItem(0).getCount(), 5, "furnace ore");
		helper.assertBlockState(chest, s -> s.is(iron) && s.getValue(ChestBlock.TYPE) == ChestType.SINGLE, s -> Component.literal("lone half: " + s));
		helper.assertValueEqual(helper.getBlockEntity(chest, FortifiedChestBlockEntity.class).getItem(3).getCount(), 2, "coal kept");

		// Same with nothing at all next to it (used to crash on a null block entity).
		BlockPos lone = new BlockPos(1, 1, 3);
		helper.setBlock(lone, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		useKit(helper, player, lone);
		helper.assertBlockState(lone, s -> s.is(iron) && s.getValue(ChestBlock.TYPE) == ChestType.SINGLE, s -> Component.literal("lone right half: " + s));
		helper.assertValueEqual(kits.getCount(), 2, "one kit per converted block");
		helper.succeed();
	}

	@GameTest
	public void upgradeKitKeepsShulkerColourAndBarrelFacing(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.UPGRADE_KITS.get(StorageTier.GOLD), 2));

		BlockPos box = new BlockPos(1, 1, 1);
		helper.setBlock(box, ModContent.block(StorageKind.SHULKER_BOX, StorageTier.IRON, DyeColor.LIME));
		helper.getBlockEntity(box, FortifiedShulkerBoxBlockEntity.class).setItem(53, new ItemStack(Items.TNT, 2));
		useKit(helper, player, box);
		helper.assertBlockState(box, s -> s.is(ModContent.block(StorageKind.SHULKER_BOX, StorageTier.GOLD, DyeColor.LIME)), s -> Component.literal("shulker: " + s));
		helper.assertValueEqual(helper.getBlockEntity(box, FortifiedShulkerBoxBlockEntity.class).getItem(53).getCount(), 2, "tnt kept");

		BlockPos barrel = new BlockPos(3, 1, 1);
		helper.setBlock(barrel, block(StorageKind.BARREL, StorageTier.IRON).defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.EAST));
		useKit(helper, player, barrel);
		helper.assertBlockState(barrel, s -> s.is(block(StorageKind.BARREL, StorageTier.GOLD)) && s.getValue(net.minecraft.world.level.block.BarrelBlock.FACING) == Direction.EAST,
			s -> Component.literal("barrel: " + s));
		helper.succeed();
	}

	@GameTest
	public void brokenShulkerKeepsContents(GameTestHelper helper) {
		BlockPos box = new BlockPos(1, 1, 1);
		Block diamondBox = ModContent.block(StorageKind.SHULKER_BOX, StorageTier.DIAMOND, DyeColor.BLUE);
		helper.setBlock(box, diamondBox);
		helper.getBlockEntity(box, FortifiedShulkerBoxBlockEntity.class).setItem(107, new ItemStack(Items.GOLD_INGOT, 9));
		helper.getLevel().destroyBlock(helper.absolutePos(box), true);
		helper.succeedWhen(() -> {
			ItemEntity drop = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(box)).inflate(2))
				.stream().filter(e -> e.getItem().is(diamondBox.asItem())).findFirst().orElse(null);
			helper.assertTrue(drop != null, "no shulker item dropped");
			var contents = drop.getItem().get(DataComponents.CONTAINER);
			helper.assertTrue(contents != null && contents.nonEmptyItemCopyStream().anyMatch(s -> s.is(Items.GOLD_INGOT) && s.getCount() == 9), "contents lost");
		});
	}

	@GameTest(maxTicks = 40)
	public void netheriteSurvivesExplosionIronDoesNot(GameTestHelper helper) {
		BlockPos netherite = new BlockPos(1, 1, 1);
		BlockPos iron = new BlockPos(3, 1, 1);
		helper.setBlock(netherite, block(StorageKind.CHEST, StorageTier.NETHERITE));
		helper.setBlock(iron, block(StorageKind.CHEST, StorageTier.IRON));
		Vec3 center = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 1)));
		helper.getLevel().explode(null, center.x, center.y, center.z, 4.0F, Level.ExplosionInteraction.TNT);
		helper.assertBlockState(netherite, s -> s.is(block(StorageKind.CHEST, StorageTier.NETHERITE)), s -> Component.literal("netherite chest destroyed"));
		helper.assertBlockState(iron, BlockState::isAir, s -> Component.literal("iron chest survived TNT (expected to break)"));
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void netheriteItemSurvivesLava(GameTestHelper helper) {
		BlockPos pool = new BlockPos(1, 1, 1);
		helper.setBlock(pool, Blocks.LAVA);
		helper.setBlock(pool.east(), Blocks.LAVA);
		ItemEntity netherite = helper.spawnItem(block(StorageKind.BARREL, StorageTier.NETHERITE).asItem(), pool);
		ItemEntity gold = helper.spawnItem(block(StorageKind.BARREL, StorageTier.GOLD).asItem(), pool.east());
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(netherite.isAlive(), "netherite barrel item burned in lava");
			helper.assertFalse(gold.isAlive(), "gold barrel item survived lava (expected to burn)");
			helper.succeed();
		});
	}

	@GameTest
	public void shulkerDyeingAndWashingKeepTierAndContents(GameTestHelper helper) {
		Block ironUndyed = ModContent.block(StorageKind.SHULKER_BOX, StorageTier.IRON, null);
		ItemStack box = new ItemStack(ironUndyed);
		box.set(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(new ItemStack(Items.BREAD, 4))));
		var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, java.util.List.of(box, new ItemStack(Items.DYE.pick(DyeColor.CYAN))));
		var recipe = helper.getLevel().recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, helper.getLevel());
		helper.assertTrue(recipe.isPresent(), "no dye recipe for iron shulker + cyan dye");
		ItemStack dyed = recipe.get().value().assemble(input);
		helper.assertTrue(dyed.is(ModContent.block(StorageKind.SHULKER_BOX, StorageTier.IRON, DyeColor.CYAN).asItem()), "dyed result was " + dyed);
		helper.assertTrue(dyed.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().anyMatch(s -> s.is(Items.BREAD)), "contents lost when dyeing");

		BlockPos cauldron = new BlockPos(1, 1, 1);
		helper.setBlock(cauldron, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, dyed);
		net.minecraft.core.cauldron.CauldronInteractions.WATER.get(dyed).interact(helper.getBlockState(cauldron), helper.getLevel(),
			helper.absolutePos(cauldron), player, InteractionHand.MAIN_HAND, dyed);
		ItemStack washed = player.getMainHandItem();
		helper.assertTrue(washed.is(ironUndyed.asItem()), "washed result was " + washed);
		helper.assertTrue(washed.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().anyMatch(s -> s.is(Items.BREAD)), "contents lost when washing");
		helper.succeed();
	}

	private static void useKit(GameTestHelper helper, Player player, BlockPos relative) {
		BlockPos pos = helper.absolutePos(relative);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		player.getMainHandItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit));
	}
}
