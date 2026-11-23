package com.griffin.fortifiedstorage.block;

import com.griffin.fortifiedstorage.StorageKind;
import com.griffin.fortifiedstorage.StorageTier;

/** Implemented by every Fortified Storage block. */
public interface TieredStorage {
	StorageTier tier();

	StorageKind kind();
}
