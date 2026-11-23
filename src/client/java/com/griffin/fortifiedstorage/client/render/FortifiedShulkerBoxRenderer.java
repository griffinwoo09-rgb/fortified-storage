package com.griffin.fortifiedstorage.client.render;

import com.griffin.fortifiedstorage.FortifiedStorage;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.block.entity.FortifiedShulkerBoxBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Delegates to vanilla's ShulkerBoxRenderer (same model, lid animation) with the tier's sprite. */
public class FortifiedShulkerBoxRenderer implements BlockEntityRenderer<FortifiedShulkerBoxBlockEntity, FortifiedShulkerBoxRenderer.State> {
	private final ShulkerBoxRenderer vanilla;

	public FortifiedShulkerBoxRenderer(BlockEntityRendererProvider.Context context) {
		this.vanilla = new ShulkerBoxRenderer(context);
	}

	/** {@code fortified_storage:entity/shulker/<tier>[_<color>]} in the shulker atlas. */
	public static String spriteName(StorageTier tier, @Nullable DyeColor color) {
		return tier.getSerializedName() + (color == null ? "" : "_" + color.getSerializedName());
	}

	public static class State extends ShulkerBoxRenderState {
		SpriteId sprite;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FortifiedShulkerBoxBlockEntity box, State state, float partialTicks, Vec3 cameraPosition,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(box, state, partialTicks, cameraPosition, breakProgress);
		state.direction = box.getBlockState().getValueOrElse(ShulkerBoxBlock.FACING, Direction.UP);
		state.color = box.getColor();
		state.progress = box.getProgress(partialTicks);
		state.sprite = Sheets.SHULKER_MAPPER.apply(FortifiedStorage.id(spriteName(box.tier(), box.getColor())));
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(ShulkerBoxRenderer.modelTransform(state.direction));
		vanilla.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.progress, state.breakProgress, state.sprite, 0);
		poseStack.popPose();
	}
}
