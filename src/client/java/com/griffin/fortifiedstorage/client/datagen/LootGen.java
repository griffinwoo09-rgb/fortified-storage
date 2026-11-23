package com.griffin.fortifiedstorage.client.datagen;

import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageKind;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;

/** Chests and barrels drop themselves (keeping a custom name); shulker boxes drop themselves with contents. */
public class LootGen extends FabricBlockLootSubProvider {
	public LootGen(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	public void generate() {
		for (StorageKind kind : StorageKind.values()) {
			for (Block block : ModContent.blocks(kind)) {
				add(block, kind == StorageKind.SHULKER_BOX ? createShulkerBoxDrop(block) : createNameableBlockEntityTable(block));
			}
		}
	}
}
