package com.griffin.fortifiedstorage.client.datagen;

import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageKind;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.block.TieredStorage;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public class LangGen extends FabricLanguageProvider {
	public LangGen(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	public void generateTranslations(HolderLookup.Provider registries, TranslationBuilder lang) {
		lang.add("itemGroup.fortified_storage", "Fortified Storage");
		lang.add("message.fortified_storage.in_use", "Someone is using this container");
		lang.add("message.fortified_storage.needs_two_kits", "A double chest needs two upgrade kits");
		for (StorageKind kind : StorageKind.values()) {
			for (Block block : ModContent.blocks(kind)) {
				String tier = title(((TieredStorage) block).tier().getSerializedName());
				String name = switch (kind) {
					case CHEST -> tier + " Chest";
					case TRAPPED_CHEST -> tier + " Trapped Chest";
					case BARREL -> tier + " Barrel";
					case SHULKER_BOX -> {
						DyeColor color = ((ShulkerBoxBlock) block).getColor();
						yield (color == null ? tier : title(color.getSerializedName()) + " " + tier) + " Shulker Box";
					}
				};
				lang.add(block, name);
				if (kind == StorageKind.CHEST || kind == StorageKind.TRAPPED_CHEST) {
					lang.add(block.getDescriptionId() + ".double", "Large " + name);
				}
			}
		}
		for (StorageTier tier : StorageTier.values()) {
			lang.add(ModContent.UPGRADE_KITS.get(tier), title(tier.getSerializedName()) + " Upgrade Kit");
		}
	}

	private static String title(String id) {
		StringBuilder out = new StringBuilder();
		for (String word : id.split("_")) {
			out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}
}
