package com.griffin.fortifiedstorage.client.texture;

import com.google.common.base.Suppliers;
import com.griffin.fortifiedstorage.FortifiedStorage;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.client.render.FortifiedShulkerBoxRenderer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * Atlas sprite source {@code fortified_storage:tiered}. On every resource reload it builds each tier
 * texture from whatever the active packs provide:
 * <ol>
 *   <li>the pack's vanilla texture as the base (e.g. {@code minecraft:entity/chest/normal}),</li>
 *   <li>the pack's metal block texture (e.g. {@code minecraft:block/gold_block}),</li>
 *   <li>a grayscale mask shipped by this mod (alpha = where trim goes, gray = trim brightness),</li>
 * </ol>
 * scaled to the base texture's resolution, so 16x through 256x packs all line up.
 * If any pack ships the output texture itself, that file wins and nothing is generated.
 */
public record TieredSpriteSource(String set) implements SpriteSource {
	public static final MapCodec<TieredSpriteSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.STRING.fieldOf("set").forGetter(TieredSpriteSource::set)
	).apply(i, TieredSpriteSource::new));

	/**
	 * One generated sprite: output id, vanilla base texture, tier, and either a trim mask
	 * (barrels, shulkers, kits) or a chest part (chests are fully clad in metal, see {@link ChestPanels}).
	 */
	record Target(Identifier output, Identifier base, @Nullable Identifier mask, StorageTier tier, ChestPanels.@Nullable Part chest) {
		Target(Identifier output, Identifier base, Identifier mask, StorageTier tier) {
			this(output, base, mask, tier, null);
		}

		static Target chest(Identifier output, Identifier base, StorageTier tier, ChestPanels.Part part) {
			return new Target(output, base, null, tier, part);
		}
	}

	@Override
	public MapCodec<TieredSpriteSource> codec() {
		return CODEC;
	}

	@Override
	public void run(ResourceManager resources, Output output) {
		// Shared inputs are read once per reload and only as plain int arrays, so concurrent loaders can share them.
		Map<Identifier, Supplier<@Nullable Pixels>> cache = new HashMap<>();
		for (Target target : targets(set)) {
			if (resources.getResource(TEXTURE_ID_CONVERTER.idToFile(target.output())).isPresent()) {
				// A resource pack ships this texture explicitly (this mod ships none at these paths): use it as-is.
				// The atlas's vanilla "directory" source already picks the file up.
				continue;
			}
			Supplier<@Nullable Pixels> base = cache.computeIfAbsent(target.base(), id -> Suppliers.memoize(() -> Pixels.read(resources, id)));
			Supplier<@Nullable Pixels> mask = target.mask() == null ? () -> null
				: cache.computeIfAbsent(target.mask(), id -> Suppliers.memoize(() -> Pixels.read(resources, id)));
			Identifier metalId = target.tier().metalTexture();
			Supplier<@Nullable Pixels> metal = cache.computeIfAbsent(metalId, id -> Suppliers.memoize(() -> Pixels.read(resources, id)));
			output.add(target.output(), (DiscardableLoader) loader -> {
				Pixels b = base.get();
				Pixels m = mask.get();
				Pixels t = metal.get();
				if (b == null || t == null || (m == null && target.chest() == null)) {
					FortifiedStorage.LOG.warn("Can't generate {}: missing base, mask or metal texture", target.output());
					return null;
				}
				NativeImage image = target.chest() != null
					? ChestPanels.clad(b, t, target.chest(), target.tier().trimShade)
					: composite(b, m, t, target.tier().trimShade);
				return new SpriteContents(target.output(), new FrameSize(image.getWidth(), image.getHeight()), image);
			});
		}
	}

	/**
	 * Blends metal over the base wherever the mask has alpha. The mask is scaled to the base's
	 * resolution (nearest neighbour); the metal is tiled at its own resolution so trim stays as
	 * crisp as the pack's metal block. Gray 200 in the mask = metal at its normal brightness.
	 */
	static NativeImage composite(Pixels base, Pixels mask, Pixels metal, float shade) {
		int w = base.width();
		int h = base.height();
		int metalSize = Math.min(metal.width(), metal.height()); // first frame if animated
		NativeImage out = new NativeImage(w, h, false);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int b = base.get(x, y);
				int m = mask.get(x * mask.width() / w, y * mask.height() / h);
				float coverage = (m >>> 24) / 255.0F;
				if (coverage == 0.0F || (b >>> 24) == 0) {
					out.setPixel(x, y, b);
					continue;
				}
				int t = metal.get(x % metalSize, y % metalSize);
				float light = ((m >> 16) & 0xFF) / 200.0F * shade;
				out.setPixel(x, y, (b & 0xFF000000)
					| blend(b >> 16, t >> 16, light, coverage) << 16
					| blend(b >> 8, t >> 8, light, coverage) << 8
					| blend(b, t, light, coverage));
			}
		}
		return out;
	}

	private static int blend(int base, int metal, float light, float coverage) {
		float trim = Math.min(255.0F, (metal & 0xFF) * light);
		return Math.round((base & 0xFF) * (1.0F - coverage) + trim * coverage);
	}

	/** Every sprite in a set. Sets map to atlases via assets/minecraft/atlases/*.json. */
	static List<Target> targets(String set) {
		List<Target> targets = new ArrayList<>();
		for (StorageTier tier : StorageTier.values()) {
			String t = tier.getSerializedName();
			switch (set) {
				case "chests" -> {
					for (ChestPanels.Part part : ChestPanels.Part.values()) {
						targets.add(Target.chest(id("entity/chest/" + t + part.suffix), mc("entity/chest/normal" + part.suffix), tier, part));
						targets.add(Target.chest(id("entity/chest/" + t + "_trapped" + part.suffix), mc("entity/chest/trapped" + part.suffix), tier, part));
					}
				}
				case "shulkers" -> {
					targets.add(new Target(id("entity/shulker/" + FortifiedShulkerBoxRenderer.spriteName(tier, null)), mc("entity/shulker/shulker"), id("mask/shulker"), tier));
					for (DyeColor color : DyeColor.values()) {
						targets.add(new Target(id("entity/shulker/" + FortifiedShulkerBoxRenderer.spriteName(tier, color)),
							mc("entity/shulker/shulker_" + color.getSerializedName()), id("mask/shulker"), tier));
					}
				}
				case "blocks" -> {
					for (String part : new String[] {"side", "top", "top_open", "bottom"}) {
						targets.add(new Target(id("block/" + t + "_barrel_" + part), mc("block/barrel_" + part), id("mask/barrel_" + part), tier));
					}
					// Breaking particles for the block-entity-rendered blocks.
					targets.add(Target.chest(id("block/" + t + "_chest"), mc("entity/chest/normal"), tier, ChestPanels.Part.SINGLE));
					targets.add(Target.chest(id("block/" + t + "_trapped_chest"), mc("entity/chest/trapped"), tier, ChestPanels.Part.SINGLE));
					targets.add(new Target(id("block/" + t + "_shulker_box"), mc("block/shulker_box"), id("mask/shulker_block"), tier));
					for (DyeColor color : DyeColor.values()) {
						String c = color.getSerializedName();
						targets.add(new Target(id("block/" + t + "_" + c + "_shulker_box"), mc("block/" + c + "_shulker_box"), id("mask/shulker_block"), tier));
					}
				}
				case "items" -> targets.add(new Target(id("item/" + t + "_upgrade_kit"), mc("item/netherite_upgrade_smithing_template"), id("mask/upgrade_kit"), tier));
				default -> FortifiedStorage.LOG.warn("Unknown {} sprite set '{}'", FortifiedStorage.MOD_ID, set);
			}
		}
		return targets;
	}

	private static Identifier id(String path) {
		return FortifiedStorage.id(path);
	}

	private static Identifier mc(String path) {
		return Identifier.withDefaultNamespace(path);
	}

	/** An image copied to a plain ARGB array (no native memory to free, safe to share between threads). */
	public record Pixels(int width, int height, int[] argb) {
		int get(int x, int y) {
			return argb[x + y * width];
		}

		public static @Nullable Pixels read(ResourceManager resources, Identifier texture) {
			Optional<Resource> resource = resources.getResource(TEXTURE_ID_CONVERTER.idToFile(texture));
			if (resource.isEmpty()) {
				return null;
			}
			try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
				int[] argb = new int[image.getWidth() * image.getHeight()];
				for (int y = 0; y < image.getHeight(); y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						argb[x + y * image.getWidth()] = image.getPixel(x, y);
					}
				}
				return new Pixels(image.getWidth(), image.getHeight(), argb);
			} catch (IOException e) {
				FortifiedStorage.LOG.warn("Couldn't read texture {}", texture, e);
				return null;
			}
		}
	}
}
