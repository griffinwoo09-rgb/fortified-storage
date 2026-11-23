package com.griffin.fortifiedstorage.block.entity;

import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageTier;
import com.griffin.fortifiedstorage.block.FortifiedShulkerBoxBlock;
import com.griffin.fortifiedstorage.menu.FortifiedContainerMenu;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.AnimationStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mirrors vanilla ShulkerBoxBlockEntity (fixed to its own block entity type, so not subclassable)
 * with a tier-sized inventory. Contents travel with the item via the vanilla CONTAINER component.
 */
public class FortifiedShulkerBoxBlockEntity extends RandomizableContainerBlockEntity
	implements WorldlyContainer, ExtendedMenuProvider<FortifiedContainerMenu.Data> {
	private NonNullList<ItemStack> items;
	private final int[] slots;
	private int openCount;
	private AnimationStatus animationStatus = AnimationStatus.CLOSED;
	private float progress;
	private float progressOld;

	public FortifiedShulkerBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.SHULKER_BOX_BLOCK_ENTITY, pos, state);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		slots = IntStream.range(0, getContainerSize()).toArray();
	}

	public StorageTier tier() {
		return block().tier();
	}

	public @Nullable DyeColor getColor() {
		return block().getColor();
	}

	private FortifiedShulkerBoxBlock block() {
		return (FortifiedShulkerBoxBlock) getBlockState().getBlock();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, FortifiedShulkerBoxBlockEntity entity) {
		entity.updateAnimation(level, pos, state);
	}

	private void updateAnimation(Level level, BlockPos pos, BlockState state) {
		progressOld = progress;
		switch (animationStatus) {
			case CLOSED -> progress = 0.0F;
			case OPENING -> {
				progress += 0.1F;
				if (progressOld == 0.0F) {
					doNeighborUpdates(level, pos, state);
				}
				if (progress >= 1.0F) {
					animationStatus = AnimationStatus.OPENED;
					progress = 1.0F;
					doNeighborUpdates(level, pos, state);
				}
				moveCollidedEntities(level, pos, state);
			}
			case OPENED -> progress = 1.0F;
			case CLOSING -> {
				progress -= 0.1F;
				if (progressOld == 1.0F) {
					doNeighborUpdates(level, pos, state);
				}
				if (progress <= 0.0F) {
					animationStatus = AnimationStatus.CLOSED;
					progress = 0.0F;
					doNeighborUpdates(level, pos, state);
				}
			}
		}
	}

	public AnimationStatus getAnimationStatus() {
		return animationStatus;
	}

	public boolean isClosed() {
		return animationStatus == AnimationStatus.CLOSED;
	}

	public float getProgress(float partialTick) {
		return Mth.lerp(partialTick, progressOld, progress);
	}

	public AABB getBoundingBox(BlockState state) {
		return Shulker.getProgressAabb(1.0F, state.getValue(ShulkerBoxBlock.FACING), 0.5F * getProgress(1.0F), new Vec3(0.5, 0.0, 0.5));
	}

	private void moveCollidedEntities(Level level, BlockPos pos, BlockState state) {
		Direction direction = state.getValue(ShulkerBoxBlock.FACING);
		AABB box = Shulker.getProgressDeltaAabb(1.0F, direction, progressOld, progress, Vec3.atBottomCenterOf(pos));
		for (Entity entity : level.getEntities(null, box)) {
			if (entity.getPistonPushReaction() != PushReaction.IGNORE_ENTITY) {
				entity.move(MoverType.SHULKER_BOX, new Vec3(
					(box.getXsize() + 0.01) * direction.getStepX(),
					(box.getYsize() + 0.01) * direction.getStepY(),
					(box.getZsize() + 0.01) * direction.getStepZ()
				));
			}
		}
	}

	private static void doNeighborUpdates(Level level, BlockPos pos, BlockState state) {
		state.updateNeighbourShapes(level, pos, 3);
		level.updateNeighborsAt(pos, state.getBlock());
	}

	@Override
	public boolean triggerEvent(int id, int value) {
		if (id != 1) {
			return super.triggerEvent(id, value);
		}
		openCount = value;
		if (value == 0) {
			animationStatus = AnimationStatus.CLOSING;
		}
		if (value == 1) {
			animationStatus = AnimationStatus.OPENING;
		}
		return true;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// Contents leave with the item, never spill.
	}

	@Override
	public void startOpen(ContainerUser user) {
		if (remove || user.getLivingEntity().isSpectator()) {
			return;
		}
		openCount = Math.max(openCount, 0) + 1;
		level.blockEvent(worldPosition, getBlockState().getBlock(), 1, openCount);
		if (openCount == 1) {
			level.gameEvent(user.getLivingEntity(), GameEvent.CONTAINER_OPEN, worldPosition);
			level.playSound(null, worldPosition, SoundEvents.SHULKER_BOX_OPEN, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
		}
	}

	@Override
	public void stopOpen(ContainerUser user) {
		if (remove || user.getLivingEntity().isSpectator()) {
			return;
		}
		openCount--;
		level.blockEvent(worldPosition, getBlockState().getBlock(), 1, openCount);
		if (openCount <= 0) {
			level.gameEvent(user.getLivingEntity(), GameEvent.CONTAINER_CLOSE, worldPosition);
			level.playSound(null, worldPosition, SoundEvents.SHULKER_BOX_CLOSE, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
		}
	}

	public boolean isOpenedByAnyone() {
		return openCount > 0;
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
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		if (!tryLoadLootTable(input)) {
			ContainerHelper.loadAllItems(input, items);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, items, false);
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
	public int[] getSlotsForFace(Direction direction) {
		return slots;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
		// Hoppers, pipes and the Fabric Transfer API all go through this: no shulkers inside shulkers.
		return stack.getItem().canFitInsideContainerItems();
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return true;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.getItem().canFitInsideContainerItems();
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new FortifiedContainerMenu(containerId, inventory, this, getScreenOpeningData(null));
	}

	@Override
	public FortifiedContainerMenu.Data getScreenOpeningData(ServerPlayer player) {
		return new FortifiedContainerMenu.Data(tier().rows, tier().columns, true, 1);
	}
}
