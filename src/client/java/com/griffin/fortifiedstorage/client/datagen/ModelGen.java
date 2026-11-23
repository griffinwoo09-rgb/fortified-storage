package com.griffin.fortifiedstorage.client.datagen;

import com.griffin.fortifiedstorage.FortifiedStorage;
import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageKind;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.block.FortifiedShulkerBoxBlock;
import com.griffin.fortifiedstorage.client.render.FortifiedShulkerBoxRenderer;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.special.ChestSpecialRenderer;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Same patterns vanilla's BlockModelGenerators uses for chest, barrel and shulker box, pointed at this mod's sprites. */
public class ModelGen extends FabricModelProvider {
	public ModelGen(FabricPackOutput output) {
		super(output);
	}

	@Override
	public void generateBlockStateModels(BlockModelGenerators gen) {
		for (StorageTier tier : StorageTier.values()) {
			chest(gen, ModContent.block(StorageKind.CHEST, tier, null), tier.getSerializedName());
			chest(gen, ModContent.block(StorageKind.TRAPPED_CHEST, tier, null), tier.getSerializedName() + "_trapped");
			barrel(gen, ModContent.block(StorageKind.BARREL, tier, null));
		}
		for (Block block : ModContent.blocks(StorageKind.SHULKER_BOX)) {
			shulker(gen, (FortifiedShulkerBoxBlock) block);
		}
	}

	@Override
	public void generateItemModels(ItemModelGenerators gen) {
		ModContent.UPGRADE_KITS.values().forEach(kit -> gen.generateFlatItem(kit, ModelTemplates.FLAT_ITEM));
	}

	/** Particle-only block (the BE renderer draws it); item uses vanilla's chest special renderer with our sprite. */
	private static void chest(BlockModelGenerators gen, Block block, String texture) {
		gen.createParticleOnlyBlock(block);
		Item item = block.asItem();
		Identifier base = ModelTemplates.CHEST_INVENTORY.create(item, TextureMapping.particle(block), gen.modelOutput);
		gen.itemModelOutput.accept(item, ItemModelUtils.specialModel(base, new ChestSpecialRenderer.Unbaked(FortifiedStorage.id(texture))));
	}

	private static void shulker(BlockModelGenerators gen, FortifiedShulkerBoxBlock block) {
		gen.createParticleOnlyBlock(block);
		Item item = block.asItem();
		Identifier base = ModelTemplates.SHULKER_BOX_INVENTORY.create(item, TextureMapping.particle(block), gen.modelOutput);
		Identifier texture = FortifiedStorage.id(FortifiedShulkerBoxRenderer.spriteName(block.tier(), block.getColor()));
		gen.itemModelOutput.accept(item, ItemModelUtils.specialModel(base, ShulkerBoxRenderer.modelTransform(Direction.UP),
			new ShulkerBoxSpecialRenderer.Unbaked(texture, 0.0F)));
	}

	/** Copy of vanilla's createBarrel for any block: cube_bottom_top, open/closed top, six-way facing. */
	private static void barrel(BlockModelGenerators gen, Block block) {
		MultiVariant closed = BlockModelGenerators.plainVariant(TexturedModel.CUBE_BOTTOM_TOP.create(block, gen.modelOutput));
		MultiVariant open = BlockModelGenerators.plainVariant(TexturedModel.CUBE_BOTTOM_TOP.get(block)
			.updateTextures(t -> t.put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top_open")))
			.createWithSuffix(block, "_open", gen.modelOutput));
		gen.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
			.with(PropertyDispatch.<Boolean>initial(BlockStateProperties.OPEN).select(false, closed).select(true, open))
			.with(BlockModelGenerators.ROTATIONS_COLUMN_WITH_FACING));
	}
}
