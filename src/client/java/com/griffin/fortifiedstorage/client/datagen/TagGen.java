package com.griffin.fortifiedstorage.client.datagen;

import com.griffin.fortifiedstorage.FortifiedStorage;
import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageKind;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.block.TieredStorage;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class TagGen {
	static final TagKey<Block> C_CHESTS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "chests"));
	static final TagKey<Block> C_BARRELS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "barrels"));
	static final TagKey<Block> C_SHULKER_BOXES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "shulker_boxes"));

	/** All shulker boxes of one tier; the dyeing recipes take any of these. Kept out of minecraft:shulker_boxes so vanilla dye recipes can't downgrade them. */
	static TagKey<Item> shulkerBoxes(StorageTier tier) {
		return TagKey.create(Registries.ITEM, FortifiedStorage.id("shulker_boxes/" + tier.getSerializedName()));
	}

	public static class Blocks extends FabricTagsProvider.BlockTagsProvider {
		public Blocks(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
			super(output, registries);
		}

		@Override
		protected void addTags(HolderLookup.Provider registries) {
			for (StorageKind kind : StorageKind.values()) {
				TagKey<Block> common = switch (kind) {
					case CHEST, TRAPPED_CHEST -> C_CHESTS;
					case BARREL -> C_BARRELS;
					case SHULKER_BOX -> C_SHULKER_BOXES;
				};
				for (Block block : ModContent.blocks(kind)) {
					builder(common).add(block.builtInRegistryHolder().key());
					builder(BlockTags.MINEABLE_WITH_PICKAXE).add(block.builtInRegistryHolder().key());
					builder(BlockTags.GUARDED_BY_PIGLINS).add(block.builtInRegistryHolder().key());
				}
			}
			// What an iron upgrade kit converts. Copper chests are intentionally absent.
			TagKey<Block> vanilla = ModContent.UPGRADEABLE_VANILLA;
			builder(vanilla).add(net.minecraft.world.level.block.Blocks.CHEST.builtInRegistryHolder().key())
				.add(net.minecraft.world.level.block.Blocks.TRAPPED_CHEST.builtInRegistryHolder().key())
				.add(net.minecraft.world.level.block.Blocks.BARREL.builtInRegistryHolder().key())
				.add(net.minecraft.world.level.block.Blocks.SHULKER_BOX.builtInRegistryHolder().key());
			net.minecraft.world.level.block.Blocks.DYED_SHULKER_BOX.forEach(box -> builder(vanilla).add(box.builtInRegistryHolder().key()));
		}
	}

	public static class Items extends FabricTagsProvider.ItemTagsProvider {
		public Items(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries, Blocks blockTags) {
			super(output, registries, blockTags);
		}

		@Override
		protected void addTags(HolderLookup.Provider registries) {
			copy(C_CHESTS, TagKey.create(Registries.ITEM, C_CHESTS.location()));
			copy(C_BARRELS, TagKey.create(Registries.ITEM, C_BARRELS.location()));
			copy(C_SHULKER_BOXES, TagKey.create(Registries.ITEM, C_SHULKER_BOXES.location()));
			for (Block block : ModContent.blocks(StorageKind.SHULKER_BOX)) {
				builder(shulkerBoxes(((TieredStorage) block).tier())).add(block.asItem().builtInRegistryHolder().key());
			}
		}
	}

	private TagGen() {
	}
}
