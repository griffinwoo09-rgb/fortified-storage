package com.griffin.fortifiedstorage;

public enum StorageKind {
	CHEST("chest"),
	TRAPPED_CHEST("trapped_chest"),
	BARREL("barrel"),
	SHULKER_BOX("shulker_box");

	public final String id;

	StorageKind(String id) {
		this.id = id;
	}
}
