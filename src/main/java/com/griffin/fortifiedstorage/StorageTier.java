package com.griffin.fortifiedstorage;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/** Every tier number lives here. Nothing else hard-codes slot counts. */
public enum StorageTier implements StringRepresentable {
	//        name        rows cols hardness blast   fireImmune trimShade material tag
	IRON     ("iron",      6,   9,   5.0F,    6.0F,   false,     1.00F,    "ingots/iron",      SoundType.METAL, MapColor.METAL),
	GOLD     ("gold",      9,   9,   3.0F,    6.0F,   false,     1.00F,    "ingots/gold",      SoundType.METAL, MapColor.GOLD),
	DIAMOND  ("diamond",   10,  12,  5.0F,    6.0F,   false,     1.00F,    "gems/diamond",     SoundType.METAL, MapColor.DIAMOND),
	NETHERITE("netherite", 11,  15,  50.0F,   1200.0F, true,     0.85F,    "ingots/netherite", SoundType.NETHERITE_BLOCK, MapColor.COLOR_BLACK);

	private final String name;
	public final int rows;
	public final int columns;
	public final float hardness;
	public final float blastResistance;
	public final boolean fireImmune;
	/** Brightness multiplier applied to the metal trim when compositing textures. */
	public final float trimShade;
	public final TagKey<Item> material;
	public final SoundType sound;
	public final MapColor mapColor;

	StorageTier(String name, int rows, int columns, float hardness, float blastResistance, boolean fireImmune, float trimShade, String materialTag, SoundType sound, MapColor mapColor) {
		this.name = name;
		this.rows = rows;
		this.columns = columns;
		this.hardness = hardness;
		this.blastResistance = blastResistance;
		this.fireImmune = fireImmune;
		this.trimShade = trimShade;
		this.material = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", materialTag));
		this.sound = sound;
		this.mapColor = mapColor;
	}

	public int slots() {
		return rows * columns;
	}

	/** The vanilla block texture the trim is sampled from, e.g. {@code minecraft:block/iron_block}. */
	public Identifier metalTexture() {
		return Identifier.withDefaultNamespace("block/" + name + "_block");
	}

	/** The tier an upgrade kit to this tier starts from; {@code null} means vanilla. */
	public @Nullable StorageTier previous() {
		return ordinal() == 0 ? null : values()[ordinal() - 1];
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
