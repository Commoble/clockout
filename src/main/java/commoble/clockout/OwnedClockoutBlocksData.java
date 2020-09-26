package commoble.clockout;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;

import commoble.clockout.util.CodecHelper;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.state.BooleanProperty;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.UUIDCodec;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;

public class OwnedClockoutBlocksData extends WorldSavedData
{
	public static final String DATA_NAME = Clockout.MODID + ":data";
	public static final String PLAYERS = "players";
	
	// this shouldn't be called on the client, return a fake instance if it is
	public static final OwnedClockoutBlocksData CLIENT_DUMMY = new OwnedClockoutBlocksData();
	
	public static final Codec<Set<BlockPos>> POS_SET_CODEC = CodecHelper.makeSetCodec(BlockPos.CODEC);
	public static final Codec<Map<RegistryKey<World>, Set<BlockPos>>> WORLD_MAP_CODEC = Codec.unboundedMap(World.CODEC, POS_SET_CODEC);
	public static final Codec<Map<UUID, Map<RegistryKey<World>, Set<BlockPos>>>> PLAYER_MAP_CODEC =
		CodecHelper.makeEntryListCodec(UUIDCodec.CODEC, WORLD_MAP_CODEC);
	
	
	// map of player UUID to map of dimension IDs to set of extant positions of clockout blocks owned by that player
	// we need player->world->positions rather than world->player->positions, so we can't just store data in different worlds
	private Map<UUID, Map<RegistryKey<World>, Set<BlockPos>>> map = new HashMap<>();
	
//	private static final NBTListHelper<BlockPos> BLOCKPOS_LISTER = new NBTListHelper<BlockPos>(
//		POSITIONS,
//		(nbt, pos) -> nbt.put(POS, NBTUtil.writeBlockPos(pos)),
//		nbt -> NBTUtil.readBlockPos(nbt.getCompound(POS))
//	);
//	
//	private static final NBTMapHelper<ResourceLocation, Set<BlockPos>> DIMENSION_TO_BLOCKS_MAPPER =
//		new NBTMapHelper<ResourceLocation, Set<BlockPos>>(
//			DIMENSIONS,
//			(nbt, dimID) -> nbt.putString(DIMENSION, dimID.toString()),
//			nbt -> new ResourceLocation(nbt.getString(DIMENSION)),
//			(nbt, set) -> BLOCKPOS_LISTER.write(new ArrayList<BlockPos>(set), nbt),
//			nbt -> new HashSet<BlockPos>(BLOCKPOS_LISTER.read(nbt))
//	);
//	
//	private static final NBTMapHelper<UUID, Map<ResourceLocation, Set<BlockPos>>> PLAYER_TO_DIMENSION_MAPPER =
//		new NBTMapHelper<UUID, Map<ResourceLocation, Set<BlockPos>>>(
//			PLAYERS,
//			(nbt, playerID) -> nbt.put(PLAYER, NBTUtil.writeUniqueId(playerID)),
//			nbt -> NBTUtil.readUniqueId(nbt.getCompound(PLAYER)),
//			(nbt, map) -> DIMENSION_TO_BLOCKS_MAPPER.write(map, nbt),
//			nbt -> DIMENSION_TO_BLOCKS_MAPPER.read(nbt)
//	);

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
		
		ServerWorld overworld = ((ServerWorld)world).getServer().getWorld(World.OVERWORLD);
		DimensionSavedDataManager storage = overworld.getSavedData();
		return storage.getOrCreate(OwnedClockoutBlocksData::new, DATA_NAME);
	}
	
	// returns null if nobody owns block
	public @Nullable UUID getBlockOwner(@Nonnull World world, BlockPos pos)
	{
		return this.map.entrySet().stream()
			.filter(
				entry -> entry.getValue() != null &&
				entry.getValue().getOrDefault(world.getDimensionKey(), new HashSet<BlockPos>()).contains(pos))
			.findAny()
			.map(entry -> entry.getKey()).orElse(null);
	}
	
	public void putBlock(@Nonnull UUID playerID, @Nonnull World world, @Nonnull BlockPos inputPos)
	{
		BlockPos pos = inputPos.toImmutable();	// just in case;
		if (!this.map.containsKey(playerID))
		{
			this.map.put(playerID, new HashMap<RegistryKey<World>, Set<BlockPos>>());
		}
		
		Map<RegistryKey<World>, Set<BlockPos>> subMap = this.map.get(playerID);
		RegistryKey<World> dimID = world.getDimensionKey();
		
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
		Map<RegistryKey<World>, Set<BlockPos>> subMap = this.map.get(playerID);
		if (subMap != null)
		{
			RegistryKey<World> dimID = world.getDimensionKey();
			Set<BlockPos> blockSet = subMap.get(dimID);
			if (blockSet != null)
			{
				blockSet.remove(pos);
				this.markDirty();
			}
		}
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
					serverWorld.getServer().getWorld(entry.getKey()),
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
			if (state.hasProperty(powered) && (state.get(powered) != shouldBePoweredNow))
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
		this.map = PLAYER_MAP_CODEC.decode(NBTDynamicOps.INSTANCE, nbt.get(PLAYERS))
			.result()
			.map(Pair::getFirst)
			.orElse(new HashMap<>());
	}

	@Override
	public CompoundNBT write(CompoundNBT nbt)
	{
		PLAYER_MAP_CODEC.encodeStart(NBTDynamicOps.INSTANCE, this.map)
			.result()
			.ifPresent(inbt -> nbt.put(PLAYERS, inbt));
		return nbt;
	}

}
