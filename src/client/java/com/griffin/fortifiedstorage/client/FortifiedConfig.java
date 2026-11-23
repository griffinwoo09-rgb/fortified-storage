package com.griffin.fortifiedstorage.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.griffin.fortifiedstorage.FortifiedStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** {@code config/fortified_storage.json}. Written with defaults on first launch. */
public final class FortifiedConfig {
	/** When true, grids taller than the window scroll; when false, every row is always shown. */
	public boolean scrollGui = true;

	public static FortifiedConfig INSTANCE = new FortifiedConfig();

	static void load() {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Path file = FabricLoader.getInstance().getConfigDir().resolve(FortifiedStorage.MOD_ID + ".json");
		try {
			if (Files.exists(file)) {
				FortifiedConfig loaded = gson.fromJson(Files.readString(file), FortifiedConfig.class);
				if (loaded != null) {
					INSTANCE = loaded;
				}
			}
			Files.writeString(file, gson.toJson(INSTANCE));
		} catch (IOException | RuntimeException e) {
			FortifiedStorage.LOG.warn("Could not read {}, using defaults", file, e);
		}
	}
}
