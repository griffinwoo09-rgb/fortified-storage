package com.griffin.fortifiedstorage.client.render;

import com.griffin.fortifiedstorage.FortifiedStorage;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.block.entity.FortifiedChestBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.MultiblockChestResources;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla's ChestRenderer (lid animation, double-chest halves, vanilla model layers) with the
 * tier's sprites instead of the wooden ones.
 */
public class FortifiedChestRenderer extends ChestRenderer<FortifiedChestBlockEntity> {
	private static final Map<StorageTier, MultiblockChestResources<SpriteId>> NORMAL = textures("");
	private static final Map<StorageTier, MultiblockChestResources<SpriteId>> TRAPPED = textures("_trapped");

	private final SpriteGetter sprites;
	private final MultiblockChestResources<ChestModel> models;

	public FortifiedChestRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
		this.sprites = context.sprites();
		this.models = LAYERS.map(layer -> new ChestModel(context.bakeLayer(layer)));
	}

	/** {@code fortified_storage:entity/chest/<tier><suffix>[_left|_right]} in the chest atlas. */
	private static Map<StorageTier, MultiblockChestResources<SpriteId>> textures(String suffix) {
		Map<StorageTier, MultiblockChestResources<SpriteId>> map = new EnumMap<>(StorageTier.class);
		for (StorageTier tier : StorageTier.values()) {
			String name = tier.getSerializedName() + suffix;
			map.put(tier, new MultiblockChestResources<>(
				FortifiedStorage.id(name), FortifiedStorage.id(name + "_left"), FortifiedStorage.id(name + "_right")
			).map(Sheets.CHEST_MAPPER::apply));
		}
		return map;
	}

	public static class State extends ChestRenderState {
		MultiblockChestResources<SpriteId> textures;
	}

	@Override
	public ChestRenderState createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FortifiedChestBlockEntity chest, ChestRenderState state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		super.extractRenderState(chest, state, partialTicks, cameraPosition, breakProgress);
		((State) state).textures = (chest.isTrapped() ? TRAPPED : NORMAL).get(chest.tier());
	}

	@Override
	public void submit(ChestRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		// Same as ChestRenderer.submit, except for the sprite.
		poseStack.pushPose();
		poseStack.mulPose(modelTransformation(state.facing));
		float open = 1.0F - state.open;
		open = 1.0F - open * open * open;
		SpriteId sprite = ((State) state).textures.select(state.type);
		ChestModel model = models.select(state.type);
		collector.submitModel(model, open, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, sprite, sprites, 0);
		if (state.breakProgress != null) {
			collector.order(1).submitCrumblingOverlay(model, open, poseStack, sprite.renderType(model.renderType()), state.lightCoords,
				OverlayTexture.NO_OVERLAY, -1, state.breakProgress);
		}
		poseStack.popPose();
	}
}
