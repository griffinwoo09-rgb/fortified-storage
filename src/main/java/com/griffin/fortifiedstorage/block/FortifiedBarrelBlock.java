package com.griffin.fortifiedstorage.block;

import com.griffin.fortifiedstorage.StorageKind;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.block.entity.FortifiedBarrelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A vanilla BarrelBlock (6-way FACING, OPEN state, comparator output) with a tier. */
public class FortifiedBarrelBlock extends BarrelBlock implements TieredStorage {
	private final StorageTier tier;

	public FortifiedBarrelBlock(StorageTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
	}

	@Override
	public StorageTier tier() {
		return tier;
	}

	@Override
	public StorageKind kind() {
		return StorageKind.BARREL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FortifiedBarrelBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof FortifiedBarrelBlockEntity barrel) {
			player.openMenu(barrel);
			player.awardStat(Stats.OPEN_BARREL);
			PiglinAi.angerNearbyPiglins(serverLevel, player, true);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.getBlockEntity(pos) instanceof FortifiedBarrelBlockEntity barrel) {
			barrel.recheckOpen();
		}
	}
}
