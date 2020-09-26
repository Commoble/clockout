package commoble.clockout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import commoble.clockout.util.NBTListHelper;
import commoble.clockout.util.NBTMapHelper;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.state.BooleanProperty;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;

public class OwnedClockoutBlocksData extends WorldSavedData
{
	public static final String DATA_NAME = Clockout.MODID + ":data";
	public static final String PLAYERS = "players";
	public static final String PLAYER = "player";
	public static final String DIMENSIONS = "dimensions";
	public static final String DIMENSION = "dimension";
	public static final String POSITIONS = "positions";
	public static final String POS = "position";
	
	// this shouldn't be called on the client, return a fake instance if it is
	public static final OwnedClockoutBlocksData CLIENT_DUMMY = new OwnedClockoutBlocksData();
	
	// map of player UUID to map of dimension IDs to set of extant positions of clockout blocks owned by that player
	private Map<UUID, Map<ResourceLocation, Set<BlockPos>>> map = new HashMap<>();
	
	private static final NBTListHelper<BlockPos> BLOCKPOS_LISTER = new NBTListHelper<BlockPos>(
		POSITIONS,
		(nbt, pos) -> nbt.put(POS, NBTUtil.writeBlockPos(pos)),
		nbt -> NBTUtil.readBlockPos(nbt.getCompound(POS))
	);
	
	private static final NBTMapHelper<ResourceLocation, Set<BlockPos>> DIMENSION_TO_BLOCKS_MAPPER =
		new NBTMapHelper<ResourceLocation, Set<BlockPos>>(
			DIMENSIONS,
			(nbt, dimID) -> nbt.putString(DIMENSION, dimID.toString()),
			nbt -> new ResourceLocation(nbt.getString(DIMENSION)),
			(nbt, set) -> BLOCKPOS_LISTER.write(new ArrayList<BlockPos>(set), nbt),
			nbt -> new HashSet<BlockPos>(BLOCKPOS_LISTER.read(nbt))
	);
	
	private static final NBTMapHelper<UUID, Map<ResourceLocation, Set<BlockPos>>> PLAYER_TO_DIMENSION_MAPPER =
		new NBTMapHelper<UUID, Map<ResourceLocation, Set<BlockPos>>>(
			PLAYERS,
			(nbt, playerID) -> nbt.put(PLAYER, NBTUtil.writeUniqueId(playerID)),
			nbt -> NBTUtil.readUniqueId(nbt.getCompound(PLAYER)),
			(nbt, map) -> DIMENSION_TO_BLOCKS_MAPPER.write(map, nbt),
			nbt -> DIMENSION_TO_BLOCKS_MAPPER.read(nbt)
	);

	public OwnedClockoutBlocksData()
	{
		super(DATA_NAME);
	}
	
	// get the data from the world saved data manager, instantiating it first if it doesn't exist
	public static OwnedClockoutBlocksData get(IWorld world)
	{
		if (!(world instanceof ServerWorld))
		{
			return CLIENT_DUMMY;
		}
		
		ServerWorld overworld = ((ServerWorld)world).getServer().getWorld(DimensionType.OVERWORLD);
		DimensionSavedDataManager storage = overworld.getSavedData();
		return storage.getOrCreate(OwnedClockoutBlocksData::new, DATA_NAME);
	}
	
	// returns null if nobody owns block
	public @Nullable UUID getBlockOwner(@Nonnull World world, BlockPos pos)
	{
		return this.map.entrySet().stream()
			.filter(
				entry -> entry.getValue() != null &&
				entry.getValue().getOrDefault(world.getDimension().getType().getRegistryName(), new HashSet<BlockPos>()).contains(pos))
			.findAny()
			.map(entry -> entry.getKey()).orElse(null);
	}
	
	public void putBlock(@Nonnull UUID playerID, @Nonnull World world, @Nonnull BlockPos inputPos)
	{
		BlockPos pos = inputPos.toImmutable();	// just in case;
		if (!this.map.containsKey(playerID))
		{
			this.map.put(playerID, new HashMap<ResourceLocation, Set<BlockPos>>());
		}
		
		Map<ResourceLocation, Set<BlockPos>> subMap = this.map.get(playerID);
		ResourceLocation dimID = getDimId(world);
		
		if (!subMap.containsKey(dimID))
		{
			subMap.put(dimID, new HashSet<BlockPos>());
		}
		
		Set<BlockPos> blockSet = subMap.get(dimID);
		
		blockSet.add(pos);
		this.markDirty();
	}
	
	// Block::remove is player-agnostic, so just check every player here
	public void removeBlock(@Nonnull World world, @Nonnull BlockPos pos)
	{
		this.map.keySet().forEach(playerID -> this.removeBlock(playerID, world, pos));
	}
	
	private void removeBlock(@Nonnull UUID playerID, @Nonnull World world, @Nonnull BlockPos pos)
	{
		Map<ResourceLocation, Set<BlockPos>> subMap = this.map.get(playerID);
		if (subMap != null)
		{
			ResourceLocation dimID = getDimId(world);
			Set<BlockPos> blockSet = subMap.get(dimID);
			if (blockSet != null)
			{
				blockSet.remove(pos);
				this.markDirty();
			}
		}
	}
	
	private static ResourceLocation getDimId(@Nonnull IWorld world)
	{
		return world.getDimension().getType().getRegistryName();
	}
	
	public void onPlayerLogin(@Nonnull ServerWorld serverWorld, @Nonnull PlayerEntity player)
	{
		this.onPlayerLoginStateChange(serverWorld, player, true);
	}
	
	public void onPlayerLogout(@Nonnull ServerWorld serverWorld, @Nonnull PlayerEntity player)
	{
		this.onPlayerLoginStateChange(serverWorld, player, false);
	}
	
	private void onPlayerLoginStateChange(@Nonnull ServerWorld serverWorld, @Nonnull PlayerEntity player, boolean isLoggedInNow)
	{
		UUID playerID = player.getGameProfile().getId();
		Optional.ofNullable(this.map.get(playerID))
			.ifPresent(subMap ->
				subMap.entrySet().forEach(entry ->
				this.setAllBlockStates(
					playerID,
					serverWorld.getServer().getWorld(DimensionType.byName(entry.getKey())),
					entry.getValue(),
					isLoggedInNow
				)
			)
		);
	}
	
	private void setAllBlockStates(UUID playerID, ServerWorld world, Set<BlockPos> blockSet, boolean active)
	{
		blockSet.forEach(pos -> this.setBlockPowered(playerID, world, pos, active));
	}
	
	private void setBlockPowered(UUID playerID, ServerWorld world, BlockPos pos, boolean shouldBePoweredNow)
	{
		BlockState state = world.getBlockState(pos);
		ClockoutBlock clockoutBlock = ObjectHolders.CLOCKOUT_BLOCK;
		BooleanProperty powered = ClockoutBlock.POWERED;
		if (state.getBlock() == clockoutBlock)
		{
			if (state.has(powered) && (state.get(powered) != shouldBePoweredNow))
			{
				world.setBlockState(pos, state.with(powered, shouldBePoweredNow));
			}
		}
		else	// block was removed from world but not removed from map, so make sure it's removed from map as well
		{
			this.removeBlock(playerID, world, pos);
		}
	}

	@Override
	public void read(CompoundNBT nbt)
	{
		this.map = PLAYER_TO_DIMENSION_MAPPER.read(nbt);
	}

	@Override
	public CompoundNBT write(CompoundNBT nbt)
	{
		return PLAYER_TO_DIMENSION_MAPPER.write(this.map, nbt);
	}

}
