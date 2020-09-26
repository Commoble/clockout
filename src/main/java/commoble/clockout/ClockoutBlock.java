package commoble.clockout;

import java.util.UUID;

import commoble.clockout.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer.Builder;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

public class ClockoutBlock extends Block
{
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	public final int POWER_WHILE_ON = 15;
	public final int POWER_WHILE_OFF = 0;
	
	public static final String OWN_BLOCK_MESSAGE = "block.clockout.clockout_block.owned";
	public static final String ANONYMOUS_OWNER_MESSAGE = "block.clockout.clockout_block.anonymous_owner";
	public static final String KNOWN_OWNER_MESSAGE = "block.clockout.clockout_block.known_owner";

	public ClockoutBlock(Properties properties)
	{
		super(properties);
		this.setDefaultState(this.getDefaultState().with(POWERED, false));
	}

	public static int getLightValue(BlockState state)
	{
		return state.get(POWERED) ? 7 : 0;
	}

	@Override
	public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit)
	{
		if (!(world instanceof ServerWorld))
		{
			return ActionResultType.SUCCESS;
		}
		else
		{
			ServerWorld serverWorld = (ServerWorld)world;
			UUID ownerID = OwnedClockoutBlocksData.get(world).getBlockOwner(world, pos);
			UUID playerID = player.getGameProfile().getId();
			ITextComponent message = ownerID.equals(playerID)
				? new TranslationTextComponent(OWN_BLOCK_MESSAGE)
				: player.hasPermissionLevel(Clockout.config.permission_level_for_seeing_clockout_owners.get())
					? new TranslationTextComponent(KNOWN_OWNER_MESSAGE, serverWorld.getServer().getPlayerProfileCache().getProfileByUUID(ownerID).getName())
					: new TranslationTextComponent(ANONYMOUS_OWNER_MESSAGE);
			player.sendStatusMessage(message, true);
			return ActionResultType.SUCCESS;
		}
	}

	/**
	 * Called by ItemBlocks after a block is set in the world, to allow post-place
	 * logic
	 */
	@Override
	public void onBlockPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
	{
		if (!world.isRemote() && placer != null)
		{
			Util.as(placer, PlayerEntity.class)
				.filter(player -> player.getGameProfile().getId() != null)
				.ifPresent(player -> 
					OwnedClockoutBlocksData.get(world).putBlock(player.getGameProfile().getId(), world, pos)
				);
		}
		super.onBlockPlacedBy(world, pos, state, placer, stack);
	}

	@Override
	public void onReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving)
	{
		if (!world.isRemote() && newState.getBlock() != state.getBlock())
		{
			OwnedClockoutBlocksData.get(world).removeBlock(world, pos);
		}
		super.onReplaced(state, world, pos, newState, isMoving);
	}

	@Override
	public BlockState getStateForPlacement(BlockItemUseContext context)
	{
		boolean shouldStartPowered = context.getPlayer() != null && context.getPlayer().getGameProfile().getId() != null;
		return this.getDefaultState().with(POWERED, shouldStartPowered);
	}

	@Override
	public int getWeakPower(BlockState blockState, IBlockReader blockAccess, BlockPos pos, Direction side)
	{
		return blockState.get(POWERED) ? this.POWER_WHILE_ON : this.POWER_WHILE_OFF;
	}

	@Override
	public boolean canProvidePower(BlockState state)
	{
		return state.get(POWERED);
	}

	@Override
	public int getStrongPower(BlockState blockState, IBlockReader blockAccess, BlockPos pos, Direction side)
	{
		return this.getWeakPower(blockState, blockAccess, pos, side);
	}

	@Override
	protected void fillStateContainer(Builder<Block, BlockState> builder)
	{
		builder.add(POWERED);
	}
}
