package com.griffin.fortifiedstorage.client;

import com.griffin.fortifiedstorage.FortifiedStorage;
import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.client.render.FortifiedChestRenderer;
import com.griffin.fortifiedstorage.client.render.FortifiedShulkerBoxRenderer;
import com.griffin.fortifiedstorage.client.screen.FortifiedContainerScreen;
import com.griffin.fortifiedstorage.client.texture.TieredSpriteSource;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.SpriteSourceRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class FortifiedStorageClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FortifiedConfig.load();
		SpriteSourceRegistry.register(FortifiedStorage.id("tiered"), TieredSpriteSource.CODEC);
		BlockEntityRenderers.register(ModContent.CHEST_BLOCK_ENTITY, FortifiedChestRenderer::new);
		BlockEntityRenderers.register(ModContent.SHULKER_BOX_BLOCK_ENTITY, FortifiedShulkerBoxRenderer::new);
		MenuScreens.register(ModContent.MENU, FortifiedContainerScreen::new);
	}
}
