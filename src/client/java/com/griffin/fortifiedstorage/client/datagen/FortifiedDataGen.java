package com.griffin.fortifiedstorage.client.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public final class FortifiedDataGen implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator generator) {
		FabricDataGenerator.Pack pack = generator.createPack();
		pack.addProvider(ModelGen::new);
		pack.addProvider(RecipeGen::new);
		pack.addProvider(LootGen::new);
		TagGen.Blocks blockTags = pack.addProvider(TagGen.Blocks::new);
		pack.addProvider((output, registries) -> new TagGen.Items(output, registries, blockTags));
		pack.addProvider(LangGen::new);
	}
}
