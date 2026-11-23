package com.griffin.fortifiedstorage.client.datagen;

import com.griffin.fortifiedstorage.FortifiedStorage;
import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageKind;
import com.griffin.fortifiedstorage.StorageTier;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder;
import net.minecraft.data.recipes.TransmuteRecipeBuilder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

/**
 * Iron/gold/diamond: eight of the tier material (common tags) around the previous tier.
 * Netherite: smithing table, like netherite gear. Kits follow the same pattern around a stick.
 */
public class RecipeGen extends FabricRecipeProvider {
	public RecipeGen(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	public String getName() {
		return "Fortified Storage recipes";
	}

	@Override
	protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
		return new RecipeProvider(recipes, advancements) {
			@Override
			public void buildRecipes() {
				for (StorageTier tier : StorageTier.values()) {
					StorageTier previous = tier.previous();
					for (StorageKind kind : StorageKind.values()) {
						Item result = ModContent.block(kind, tier, null).asItem();
						ItemLike base = previous != null ? ModContent.block(kind, previous, null) : switch (kind) {
							case CHEST -> Blocks.CHEST;
							case TRAPPED_CHEST -> Blocks.TRAPPED_CHEST;
							case BARREL -> Blocks.BARREL;
							case SHULKER_BOX -> Blocks.SHULKER_BOX;
						};
						upgrade(tier, result, Ingredient.of(base), kind.id);
					}
					// Fortified chest + tripwire hook = fortified trapped chest, like vanilla.
					Item trapped = ModContent.block(StorageKind.TRAPPED_CHEST, tier, null).asItem();
					shapeless(RecipeCategory.REDSTONE, trapped)
						.requires(ModContent.block(StorageKind.CHEST, tier, null))
						.requires(Items.TRIPWIRE_HOOK)
						.unlockedBy("has_tripwire_hook", has(Items.TRIPWIRE_HOOK))
						.save(output, key(tier.getSerializedName() + "_trapped_chest_from_chest"));
					// Upgrade kits: the material ring around a stick (netherite: smithing, like the blocks).
					Item kit = ModContent.UPGRADE_KITS.get(tier);
					Ingredient kitBase = tier == StorageTier.NETHERITE ? Ingredient.of(ModContent.UPGRADE_KITS.get(previous)) : Ingredient.of(Items.STICK);
					upgrade(tier, kit, kitBase, "upgrade_kit");
					// Dyeing: any box of this tier + dye -> that colour, keeping contents (crafting_transmute, like vanilla).
					for (DyeColor color : DyeColor.values()) {
						Item dyed = ModContent.block(StorageKind.SHULKER_BOX, tier, color).asItem();
						TransmuteRecipeBuilder.transmute(RecipeCategory.DECORATIONS, tag(TagGen.shulkerBoxes(tier)), Ingredient.of(Items.DYE.pick(color)), dyed)
							.group(tier.getSerializedName() + "_shulker_box_dye")
							.unlockedBy("has_shulker_box", has(TagGen.shulkerBoxes(tier)))
							.save(output, key(tier.getSerializedName() + "_" + color.getSerializedName() + "_shulker_box"));
					}
				}
			}

			private void upgrade(StorageTier tier, Item result, Ingredient base, String name) {
				if (tier == StorageTier.NETHERITE) {
					SmithingTransformRecipeBuilder.smithing(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), base, tag(tier.material), RecipeCategory.DECORATIONS, result)
						.unlocks("has_netherite_ingot", has(tier.material))
						.save(output, key("netherite_" + name + "_smithing"));
				} else {
					shaped(RecipeCategory.DECORATIONS, result)
						.define('M', tag(tier.material))
						.define('B', base)
						.pattern("MMM")
						.pattern("MBM")
						.pattern("MMM")
						.unlockedBy("has_material", has(tier.material))
						.save(output);
				}
			}
		};
	}

	private static ResourceKey<Recipe<?>> key(String path) {
		return ResourceKey.create(Registries.RECIPE, FortifiedStorage.id(path));
	}
}
