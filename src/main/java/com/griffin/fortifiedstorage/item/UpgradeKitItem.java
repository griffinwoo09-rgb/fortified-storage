package com.griffin.fortifiedstorage.item;

import com.griffin.fortifiedstorage.ModContent;
import com.griffin.fortifiedstorage.StorageTier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Sneak-use on a container to convert it to {@link #target} in place, keeping items, facing,
 * waterlogging, custom name, double-chest pairing and shulker colour.
 */
public class UpgradeKitItem extends Item {
	public final StorageTier target;

	public UpgradeKitItem(StorageTier target, Properties properties) {
		super(properties);
		this.target = target;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = context.getClickedPos();
		BlockState state = context.getLevel().getBlockState(pos);
		Block upgraded = ModContent.upgradeTarget(state.getBlock(), target);
		if (upgraded == null) {
			return InteractionResult.PASS;
		}
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}

		// A double chest is upgraded as a pair, costing one kit per half. The partner only counts if it
		// really pairs back: /setblock or structure files can leave a "left" half next to anything.
		List<BlockPos> positions = new ArrayList<>(List.of(pos));
		boolean halfWithoutPartner = false;
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos partner = ChestBlock.getConnectedBlockPos(pos, state);
			BlockState partnerState = context.getLevel().getBlockState(partner);
			if (partnerState.is(state.getBlock()) && partnerState.getValue(ChestBlock.TYPE) != ChestType.SINGLE
				&& ChestBlock.getConnectedBlockPos(partner, partnerState).equals(pos)) {
				positions.add(partner);
			} else {
				halfWithoutPartner = true;
			}
		}
		ItemStack kit = context.getItemInHand();
		if (!player.hasInfiniteMaterials() && kit.getCount() < positions.size()) {
			player.sendOverlayMessage(Component.translatable("message.fortified_storage.needs_two_kits"));
			return InteractionResult.FAIL;
		}
		for (BlockPos p : positions) {
			if (level.getBlockEntity(p) instanceof Container container && isOpen(level, container)) {
				player.sendOverlayMessage(Component.translatable("message.fortified_storage.in_use"));
				return InteractionResult.FAIL;
			}
		}

		for (BlockPos p : positions) {
			convert(level, p, upgraded, halfWithoutPartner);
		}
		for (BlockPos p : positions) {
			level.updateNeighborsAt(p, upgraded);
		}
		kit.consume(positions.size(), player);
		level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		return InteractionResult.SUCCESS;
	}

	private static void convert(ServerLevel level, BlockPos pos, Block upgraded, boolean makeSingle) {
		BlockState old = level.getBlockState(pos);
		BlockEntity oldEntity = level.getBlockEntity(pos);
		if (oldEntity == null) {
			return; // nothing to carry over; leave the block alone rather than guess
		}
		BlockState next = upgraded.withPropertiesOf(old);
		if (makeSingle) {
			next = next.setValue(ChestBlock.TYPE, ChestType.SINGLE);
		}
		// Items, custom name, lock and unopened loot table all travel as components.
		DataComponentMap components = oldEntity.collectComponents();
		if (oldEntity instanceof Container container) {
			container.clearContent(); // so replacing the block spills nothing
		}
		// Skip neighbour shape updates so the other half of a double chest doesn't unpair mid-upgrade.
		level.setBlock(pos, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		BlockEntity newEntity = level.getBlockEntity(pos);
		if (newEntity != null) {
			newEntity.applyComponents(components, DataComponentPatch.EMPTY);
			newEntity.setChanged();
		}
	}

	/** True if any player has a menu open on {@code container}, whatever menu class it uses. */
	private static boolean isOpen(ServerLevel level, Container container) {
		return level.players().stream().anyMatch(p -> p.containerMenu.slots.stream().anyMatch(slot ->
			slot.container == container || slot.container instanceof CompoundContainer compound && compound.contains(container)));
	}
}
