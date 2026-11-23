package com.griffin.fortifiedstorage.block;

import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageKind;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.block.entity.FortifiedChestBlockEntity;
import com.griffin.fortifiedstorage.menu.FortifiedContainerMenu;
import java.util.Optional;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A vanilla ChestBlock with a tier. Placement, LEFT/RIGHT/SINGLE merging, waterlogging, the
 * blocked-by-block/cat check, piglin anger and comparator output are all inherited.
 * Trapped variants add redstone output equal to the viewer count.
 */
public class FortifiedChestBlock extends ChestBlock implements TieredStorage {
	private final StorageTier tier;
	public final boolean trapped;

	public FortifiedChestBlock(StorageTier tier, boolean trapped, Properties properties) {
		super(() -> ModContent.CHEST_BLOCK_ENTITY, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
		this.tier = tier;
		this.trapped = trapped;
	}

	@Override
	public StorageTier tier() {
		return tier;
	}

	@Override
	public StorageKind kind() {
		return trapped ? StorageKind.TRAPPED_CHEST : StorageKind.CHEST;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FortifiedChestBlockEntity(pos, state);
	}

	@Override
	protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		return combine(state, level, pos, false).apply(MENU_PROVIDER).orElse(null);
	}

	@Override
	protected Stat<Identifier> getOpenChestStat() {
		return Stats.CUSTOM.get(trapped ? Stats.TRIGGER_TRAPPED_CHEST : Stats.OPEN_CHEST);
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return trapped;
	}

	@Override
	protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
		return trapped && level.getBlockEntity(pos) instanceof FortifiedChestBlockEntity chest ? Mth.clamp(chest.openerCount(), 0, 15) : 0;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return direction == Direction.UP ? state.getSignal(level, pos, direction) : 0;
	}

	/** Like vanilla's double-chest menu provider, but opens a {@link FortifiedContainerMenu} with both halves. */
	private static final DoubleBlockCombiner.Combiner<ChestBlockEntity, Optional<MenuProvider>> MENU_PROVIDER = new DoubleBlockCombiner.Combiner<>() {
		@Override
		public Optional<MenuProvider> acceptDouble(ChestBlockEntity first, ChestBlockEntity second) {
			FortifiedChestBlockEntity a = (FortifiedChestBlockEntity) first;
			CompoundContainer container = new CompoundContainer(first, second);
			return Optional.of(new ExtendedMenuProvider<FortifiedContainerMenu.Data>() {
				@Override
				public FortifiedContainerMenu.Data getScreenOpeningData(ServerPlayer player) {
					// The two chests side by side; the screen lowers the GUI scale if that doesn't fit.
					return new FortifiedContainerMenu.Data(a.tier().rows, a.tier().columns * 2, false, 2);
				}

				@Override
				public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
					if (first.canOpen(player) && second.canOpen(player)) {
						first.unpackLootTable(inventory.player);
						second.unpackLootTable(inventory.player);
						return new FortifiedContainerMenu(containerId, inventory, container, getScreenOpeningData(null));
					}
					if (!player.isSpectator()) {
						Direction direction = ChestBlock.getConnectedDirection(first.getBlockState());
						Vec3 middle = Vec3.atCenterOf(first.getBlockPos()).add(direction.getStepX() / 2.0, 0.0, direction.getStepZ() / 2.0);
						BaseContainerBlockEntity.sendChestLockedNotifications(middle, player, getDisplayName());
					}
					return null;
				}

				@Override
				public Component getDisplayName() {
					if (first.hasCustomName()) {
						return first.getDisplayName();
					}
					if (second.hasCustomName()) {
						return second.getDisplayName();
					}
					return Component.translatable(first.getBlockState().getBlock().getDescriptionId() + ".double");
				}
			});
		}

		@Override
		public Optional<MenuProvider> acceptSingle(ChestBlockEntity single) {
			return Optional.of(single);
		}

		@Override
		public Optional<MenuProvider> acceptNone() {
			return Optional.empty();
		}
	};
}
