package com.griffin.fortifiedstorage.block.entity;

import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.block.FortifiedChestBlock;
import com.griffin.fortifiedstorage.menu.FortifiedContainerMenu;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Extends the vanilla chest so lids, double-chest combining, hoppers, comparators and
 * piglins all work unchanged. Only the size, menu and viewer counter differ: vanilla's
 * counter only recognises {@code ChestMenu}, so this one tracks {@link FortifiedContainerMenu}.
 */
public class FortifiedChestBlockEntity extends ChestBlockEntity implements ExtendedMenuProvider<FortifiedContainerMenu.Data> {
	private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level level, BlockPos pos, BlockState state) {
			if (state.getBlock() instanceof ChestBlock chest) {
				playSound(level, pos, state, chest.getOpenChestSound());
			}
		}

		@Override
		protected void onClose(Level level, BlockPos pos, BlockState state) {
			if (state.getBlock() instanceof ChestBlock chest) {
				playSound(level, pos, state, chest.getCloseChestSound());
			}
		}

		@Override
		protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int current) {
			signalOpenCount(level, pos, state, previous, current);
		}

		@Override
		public boolean isOwnContainer(Player player) {
			return FortifiedContainerMenu.isViewing(player, FortifiedChestBlockEntity.this);
		}
	};

	public FortifiedChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.CHEST_BLOCK_ENTITY, pos, state);
		setItems(NonNullList.withSize(getContainerSize(), ItemStack.EMPTY));
	}

	public StorageTier tier() {
		return ((FortifiedChestBlock) getBlockState().getBlock()).tier();
	}

	public boolean isTrapped() {
		return ((FortifiedChestBlock) getBlockState().getBlock()).trapped;
	}

	public int openerCount() {
		return openers.getOpenerCount();
	}

	@Override
	public int getContainerSize() {
		return tier().slots();
	}

	@Override
	protected Component getDefaultName() {
		return getBlockState().getBlock().getName();
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new FortifiedContainerMenu(containerId, inventory, this, getScreenOpeningData(null));
	}

	@Override
	public FortifiedContainerMenu.Data getScreenOpeningData(ServerPlayer player) {
		return new FortifiedContainerMenu.Data(tier().rows, tier().columns, false, 1);
	}

	@Override
	public void startOpen(ContainerUser user) {
		if (!remove && !user.getLivingEntity().isSpectator()) {
			openers.incrementOpeners(user.getLivingEntity(), getLevel(), getBlockPos(), getBlockState(), user.getContainerInteractionRange());
		}
	}

	@Override
	public void stopOpen(ContainerUser user) {
		if (!remove && !user.getLivingEntity().isSpectator()) {
			openers.decrementOpeners(user.getLivingEntity(), getLevel(), getBlockPos(), getBlockState());
		}
	}

	@Override
	public List<ContainerUser> getEntitiesWithContainerOpen() {
		return openers.getEntitiesWithContainerOpen(getLevel(), getBlockPos());
	}

	@Override
	public void recheckOpen() {
		if (!remove) {
			openers.recheckOpeners(getLevel(), getBlockPos(), getBlockState());
		}
	}

	@Override
	protected void signalOpenCount(Level level, BlockPos pos, BlockState state, int previous, int current) {
		super.signalOpenCount(level, pos, state, previous, current);
		if (isTrapped() && previous != current) {
			// Same as vanilla TrappedChestBlockEntity: redstone output follows the viewer count.
			level.updateNeighborsAt(pos, state.getBlock());
			level.updateNeighborsAt(pos.below(), state.getBlock());
		}
	}

	// Copy of vanilla ChestBlockEntity.playSound (private there): double chests play once, from the middle.
	private static void playSound(Level level, BlockPos pos, BlockState state, SoundEvent sound) {
		ChestType type = state.getValue(ChestBlock.TYPE);
		if (type == ChestType.LEFT) {
			return;
		}
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		if (type == ChestType.RIGHT) {
			Direction direction = ChestBlock.getConnectedDirection(state);
			x += direction.getStepX() * 0.5;
			z += direction.getStepZ() * 0.5;
		}
		level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
	}
}
