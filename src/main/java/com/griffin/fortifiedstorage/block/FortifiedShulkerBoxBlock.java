package com.griffin.fortifiedstorage.block;

import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageKind;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.block.entity.FortifiedShulkerBoxBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Extends vanilla ShulkerBoxBlock so every "no shulker boxes inside shulker boxes" check in the
 * game (slots, bundles, hoppers) already applies to these. Vanilla's methods are hard-wired to
 * ShulkerBoxBlockEntity, so the ones touching the block entity are re-pointed here.
 */
public class FortifiedShulkerBoxBlock extends ShulkerBoxBlock implements TieredStorage {
	private final StorageTier tier;

	public FortifiedShulkerBoxBlock(StorageTier tier, @Nullable DyeColor color, Properties properties) {
		super(color, properties);
		this.tier = tier;
	}

	@Override
	public StorageTier tier() {
		return tier;
	}

	@Override
	public StorageKind kind() {
		return StorageKind.SHULKER_BOX;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FortifiedShulkerBoxBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, ModContent.SHULKER_BOX_BLOCK_ENTITY, FortifiedShulkerBoxBlockEntity::tick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel serverLevel
			&& level.getBlockEntity(pos) instanceof FortifiedShulkerBoxBlockEntity box
			&& canOpen(state, level, pos, box)) {
			player.openMenu(box);
			player.awardStat(Stats.OPEN_SHULKER_BOX);
			PiglinAi.angerNearbyPiglins(serverLevel, player, true);
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean canOpen(BlockState state, Level level, BlockPos pos, FortifiedShulkerBoxBlockEntity box) {
		if (!box.isClosed()) {
			return true;
		}
		AABB lid = Shulker.getProgressDeltaAabb(1.0F, state.getValue(FACING), 0.0F, 0.5F, Vec3.atBottomCenterOf(pos)).deflate(1.0E-6);
		return level.noCollision(lid);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level.getBlockEntity(pos) instanceof FortifiedShulkerBoxBlockEntity box) {
			if (!level.isClientSide() && player.preventsBlockDrops() && !box.isEmpty()) {
				// Creative mode: still drop a filled box so contents aren't lost (vanilla behaviour).
				ItemStack stack = new ItemStack(state.getBlock());
				stack.applyComponents(box.collectComponents());
				ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
				entity.setDefaultPickUpDelay();
				level.addFreshEntity(entity);
			} else {
				box.unpackLootTable(player);
			}
		}
		// ShulkerBoxBlock.playerWillDestroy ignores non-vanilla block entities, so this is safe.
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
		if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof FortifiedShulkerBoxBlockEntity box) {
			params = params.withDynamicDrop(CONTENTS, output -> {
				for (int i = 0; i < box.getContainerSize(); i++) {
					output.accept(box.getItem(i));
				}
			});
		}
		return super.getDrops(state, params);
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof FortifiedShulkerBoxBlockEntity box && !box.isClosed()
			? SHAPES_OPEN_SUPPORT.get(state.getValue(FACING).getOpposite())
			: Shapes.block();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return level.getBlockEntity(pos) instanceof FortifiedShulkerBoxBlockEntity box
			? Shapes.create(box.getBoundingBox(state))
			: Shapes.block();
	}
}
