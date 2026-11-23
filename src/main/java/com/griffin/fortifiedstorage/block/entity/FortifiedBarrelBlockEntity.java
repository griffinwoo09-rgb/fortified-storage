package com.griffin.fortifiedstorage.block.entity;

import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.block.TieredStorage;
import com.griffin.fortifiedstorage.menu.FortifiedContainerMenu;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Mirrors vanilla BarrelBlockEntity (which has no subclassable constructor) with a tier-sized inventory. */
public class FortifiedBarrelBlockEntity extends RandomizableContainerBlockEntity implements ExtendedMenuProvider<FortifiedContainerMenu.Data> {
	private NonNullList<ItemStack> items;
	private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level level, BlockPos pos, BlockState state) {
			playSound(state, SoundEvents.BARREL_OPEN);
			setOpen(state, true);
		}

		@Override
		protected void onClose(Level level, BlockPos pos, BlockState state) {
			playSound(state, SoundEvents.BARREL_CLOSE);
			setOpen(state, false);
		}

		@Override
		protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int current) {
		}

		@Override
		public boolean isOwnContainer(Player player) {
			return FortifiedContainerMenu.isViewing(player, FortifiedBarrelBlockEntity.this);
		}
	};

	public FortifiedBarrelBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.BARREL_BLOCK_ENTITY, pos, state);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
	}

	public StorageTier tier() {
		return ((TieredStorage) getBlockState().getBlock()).tier();
	}

	@Override
	public int getContainerSize() {
		return tier().slots();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, items);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		if (!tryLoadLootTable(input)) {
			ContainerHelper.loadAllItems(input, items);
		}
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
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

	public void recheckOpen() {
		if (!remove) {
			openers.recheckOpeners(getLevel(), getBlockPos(), getBlockState());
		}
	}

	private void setOpen(BlockState state, boolean open) {
		level.setBlockAndUpdate(getBlockPos(), state.setValue(BarrelBlock.OPEN, open));
	}

	private void playSound(BlockState state, SoundEvent sound) {
		Vec3i direction = state.getValue(BarrelBlock.FACING).getUnitVec3i();
		double x = worldPosition.getX() + 0.5 + direction.getX() / 2.0;
		double y = worldPosition.getY() + 0.5 + direction.getY() / 2.0;
		double z = worldPosition.getZ() + 0.5 + direction.getZ() / 2.0;
		level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
	}
}
