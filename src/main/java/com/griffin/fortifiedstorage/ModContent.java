package com.griffin.fortifiedstorage;

import com.griffin.fortifiedstorage.block.FortifiedBarrelBlock;
import com.griffin.fortifiedstorage.block.FortifiedChestBlock;
import com.griffin.fortifiedstorage.block.FortifiedShulkerBoxBlock;
import com.griffin.fortifiedstorage.block.TieredStorage;
import com.griffin.fortifiedstorage.block.entity.FortifiedBarrelBlockEntity;
import com.griffin.fortifiedstorage.block.entity.FortifiedChestBlockEntity;
import com.griffin.fortifiedstorage.block.entity.FortifiedShulkerBoxBlockEntity;
import com.griffin.fortifiedstorage.item.UpgradeKitItem;
import com.griffin.fortifiedstorage.menu.FortifiedContainerMenu;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.ShulkerBoxDispenseBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jspecify.annotations.Nullable;

/** Registers every block, item, block entity type, menu type and the creative tab, looping over tiers. */
public final class ModContent {
	/** Vanilla blocks an iron upgrade kit may convert. The copper chest is deliberately not in it. */
	public static final TagKey<Block> UPGRADEABLE_VANILLA = TagKey.create(Registries.BLOCK, FortifiedStorage.id("upgradeable/vanilla"));

	private static final Map<StorageKind, Map<StorageTier, Block>> BLOCKS = new EnumMap<>(StorageKind.class);
	/** Shulker boxes by tier; index 0 is undyed, 1..16 are DyeColor ids + 1. */
	private static final Map<StorageTier, Block[]> SHULKERS = new EnumMap<>(StorageTier.class);
	public static final Map<StorageTier, Item> UPGRADE_KITS = new EnumMap<>(StorageTier.class);
	/** Creative-tab order: kind, then tier (then colour). */
	public static final List<Item> ALL_ITEMS = new ArrayList<>();

	public static final BlockEntityType<FortifiedChestBlockEntity> CHEST_BLOCK_ENTITY;
	public static final BlockEntityType<FortifiedBarrelBlockEntity> BARREL_BLOCK_ENTITY;
	public static final BlockEntityType<FortifiedShulkerBoxBlockEntity> SHULKER_BOX_BLOCK_ENTITY;
	public static final ExtendedMenuType<FortifiedContainerMenu, FortifiedContainerMenu.Data> MENU;

	static {
		for (StorageKind kind : StorageKind.values()) {
			for (StorageTier tier : StorageTier.values()) {
				if (kind == StorageKind.SHULKER_BOX) {
					Block[] colors = new Block[17];
					colors[0] = registerShulker(tier, null);
					for (DyeColor color : DyeColor.values()) {
						colors[color.getId() + 1] = registerShulker(tier, color);
					}
					SHULKERS.put(tier, colors);
				} else {
					Block block = registerBlock(tier.getSerializedName() + "_" + kind.id, properties(tier), p -> switch (kind) {
						case CHEST -> new FortifiedChestBlock(tier, false, p);
						case TRAPPED_CHEST -> new FortifiedChestBlock(tier, true, p);
						default -> new FortifiedBarrelBlock(tier, p);
					}, tier, false);
					BLOCKS.computeIfAbsent(kind, k -> new EnumMap<>(StorageTier.class)).put(tier, block);
				}
			}
		}
		for (StorageTier tier : StorageTier.values()) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, FortifiedStorage.id(tier.getSerializedName() + "_upgrade_kit"));
			Item.Properties properties = new Item.Properties().setId(key);
			if (tier.fireImmune) {
				properties.fireResistant();
			}
			Item kit = Registry.register(BuiltInRegistries.ITEM, key, new UpgradeKitItem(tier, properties));
			UPGRADE_KITS.put(tier, kit);
			ALL_ITEMS.add(kit);
		}

		CHEST_BLOCK_ENTITY = registerBlockEntity("chest", FortifiedChestBlockEntity::new, StorageKind.CHEST, StorageKind.TRAPPED_CHEST);
		BARREL_BLOCK_ENTITY = registerBlockEntity("barrel", FortifiedBarrelBlockEntity::new, StorageKind.BARREL);
		SHULKER_BOX_BLOCK_ENTITY = registerBlockEntity("shulker_box", FortifiedShulkerBoxBlockEntity::new, StorageKind.SHULKER_BOX);

		MENU = Registry.register(BuiltInRegistries.MENU, FortifiedStorage.id("container"),
			new ExtendedMenuType<>(FortifiedContainerMenu::new, FortifiedContainerMenu.Data.STREAM_CODEC));

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, FortifiedStorage.id("main"), FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.fortified_storage"))
			.icon(() -> new ItemStack(block(StorageKind.CHEST, StorageTier.DIAMOND, null)))
			.displayItems((parameters, output) -> ALL_ITEMS.forEach(output::accept))
			.build());
	}

	private ModContent() {
	}

	/** Forces the static registration above to run during mod init. */
	static void init() {
		// Mod tabs sit on a second page of creative tabs that's easy to miss, so also list the items where
		// players look first: containers next to the vanilla barrel, kits in Tools & Utilities. Dyed shulker
		// boxes stay in our own tab (and search) so they don't flood the vanilla tab.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.insertAfter(Items.BARREL,
			ALL_ITEMS.stream().filter(item -> !(item instanceof UpgradeKitItem)
				&& !(Block.byItem(item) instanceof ShulkerBoxBlock box && box.getColor() != null)).map(ItemStack::new).toList()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output ->
			UPGRADE_KITS.values().forEach(output::accept));
		for (Block[] colors : SHULKERS.values()) {
			for (Block box : colors) {
				DispenserBlock.registerBehavior(box, new ShulkerBoxDispenseBehavior());
				if (((ShulkerBoxBlock) box).getColor() != null) {
					CauldronInteractions.WATER.put(box.asItem(), ModContent::washShulkerBox);
				}
			}
		}
	}

	public static Block block(StorageKind kind, StorageTier tier, @Nullable DyeColor color) {
		return kind == StorageKind.SHULKER_BOX ? SHULKERS.get(tier)[color == null ? 0 : color.getId() + 1] : BLOCKS.get(kind).get(tier);
	}

	public static List<Block> blocks(StorageKind kind) {
		return kind == StorageKind.SHULKER_BOX
			? SHULKERS.values().stream().flatMap(java.util.Arrays::stream).toList()
			: List.copyOf(BLOCKS.get(kind).values());
	}

	/**
	 * The block {@code source} becomes when upgraded to {@code target}, or null if this kit doesn't apply.
	 * An iron kit converts vanilla blocks in {@link #UPGRADEABLE_VANILLA}; the others convert the previous tier.
	 */
	public static @Nullable Block upgradeTarget(Block source, StorageTier target) {
		StorageKind kind;
		if (source instanceof TieredStorage tiered) {
			if (tiered.tier() != target.previous()) {
				return null;
			}
			kind = tiered.kind();
		} else if (target.previous() == null && source.defaultBlockState().is(UPGRADEABLE_VANILLA)) {
			if (source == Blocks.CHEST) {
				kind = StorageKind.CHEST;
			} else if (source == Blocks.TRAPPED_CHEST) {
				kind = StorageKind.TRAPPED_CHEST;
			} else if (source == Blocks.BARREL) {
				kind = StorageKind.BARREL;
			} else if (source instanceof ShulkerBoxBlock) {
				kind = StorageKind.SHULKER_BOX;
			} else {
				return null;
			}
		} else {
			return null;
		}
		DyeColor color = source instanceof ShulkerBoxBlock shulker ? shulker.getColor() : null;
		return block(kind, target, color);
	}

	private static BlockBehaviour.Properties properties(StorageTier tier) {
		return BlockBehaviour.Properties.of()
			.mapColor(tier.mapColor)
			.strength(tier.hardness, tier.blastResistance)
			.sound(tier.sound);
	}

	private static Block registerShulker(StorageTier tier, @Nullable DyeColor color) {
		String name = tier.getSerializedName() + (color == null ? "" : "_" + color.getSerializedName()) + "_shulker_box";
		// Same as vanilla's shulkerBoxProperties, with the tier's strength.
		BlockBehaviour.Properties properties = properties(tier)
			.mapColor(color == null ? MapColor.COLOR_PURPLE : color == DyeColor.PURPLE ? MapColor.TERRACOTTA_PURPLE : color.getMapColor())
			.forceSolidOn()
			.dynamicShape()
			.noOcclusion()
			.isSuffocating((state, level, pos) -> !(level.getBlockEntity(pos) instanceof FortifiedShulkerBoxBlockEntity box) || box.isClosed())
			.pushReaction(PushReaction.POPPED);
		return registerBlock(name, properties, p -> new FortifiedShulkerBoxBlock(tier, color, p), tier, true);
	}

	private static Block registerBlock(String name, BlockBehaviour.Properties properties, Function<BlockBehaviour.Properties, Block> factory, StorageTier tier, boolean singleStack) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, FortifiedStorage.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, FortifiedStorage.id(name));
		Item.Properties itemProperties = new Item.Properties()
			.setId(itemKey)
			.useBlockDescriptionPrefix()
			.component(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		if (singleStack) {
			itemProperties.stacksTo(1);
		}
		if (tier.fireImmune) {
			itemProperties.fireResistant(); // also makes the item float in lava
		}
		BlockItem item = new BlockItem(block, itemProperties);
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		ALL_ITEMS.add(item);
		return block;
	}

	private static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityType<T> registerBlockEntity(
		String name, BlockEntityType.BlockEntitySupplier<T> factory, StorageKind... kinds) {
		Set<Block> valid = new HashSet<>();
		for (StorageKind kind : kinds) {
			valid.addAll(blocks(kind));
		}
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, FortifiedStorage.id(name), new BlockEntityType<>(factory, valid));
	}

	/** Cauldron washing, like vanilla: a dyed box becomes the undyed box of the same tier, contents kept. */
	private static InteractionResult washShulkerBox(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
		net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, ItemStack stack) {
		if (!(Block.byItem(stack.getItem()) instanceof FortifiedShulkerBoxBlock box)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			ItemStack washed = stack.transmuteCopy(block(StorageKind.SHULKER_BOX, box.tier(), null), 1);
			player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, washed, false));
			player.awardStat(Stats.CLEAN_SHULKER_BOX);
			LayeredCauldronBlock.lowerFillLevel(state, level, pos);
		}
		return InteractionResult.SUCCESS;
	}
}
