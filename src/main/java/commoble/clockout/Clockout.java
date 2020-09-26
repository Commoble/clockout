package commoble.clockout;

import commoble.clockout.util.ConfigHelper;
import commoble.clockout.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Clockout.MODID)
public class Clockout
{
	public static final String MODID = "clockout";
	public static final String CLOCKOUT_BLOCK_NAME = "clockout_block";
	public static final ResourceLocation CLOCKOUT_BLOCK_RL = getModRL(CLOCKOUT_BLOCK_NAME);
	
	public static Config config;
		
	public static ResourceLocation getModRL(String name)
	{
		return new ResourceLocation(Clockout.MODID, name);
	}
	
	public Clockout()
	{
		IEventBus mod_bus = FMLJavaModLoadingContext.get().getModEventBus();
		IEventBus forge_bus = MinecraftForge.EVENT_BUS;
		
		config = ConfigHelper.register(ModConfig.Type.SERVER, Config::new);
		
		mod_bus.addGenericListener(Block.class, Registrator.getRegistryHandler(Clockout::onRegisterBlocks));
		mod_bus.addGenericListener(Item.class, Registrator.getRegistryHandler(Clockout::onRegisterItems));
		
		forge_bus.addListener(Clockout::onPlayerLoggedIn);
		forge_bus.addListener(Clockout::onPlayerLoggedOut);
	}
	
	public static void onRegisterBlocks(Registrator<Block> reg)
	{
		reg.register(CLOCKOUT_BLOCK_RL, new ClockoutBlock(Block.Properties.create(Material.REDSTONE_LIGHT).hardnessAndResistance(3.0F).setLightLevel(ClockoutBlock::getLightValue)));
	}
	
	public static void onRegisterItems(Registrator<Item> reg)
	{
		reg.register(CLOCKOUT_BLOCK_RL, new BlockItem(ObjectHolders.CLOCKOUT_BLOCK, new Item.Properties().group(ItemGroup.REDSTONE)));
	}
	
	public static void onPlayerLoggedIn(PlayerLoggedInEvent event)
	{
		PlayerEntity player = event.getPlayer();
		Util.as(player.getEntityWorld(), ServerWorld.class)
			.ifPresent(world -> OwnedClockoutBlocksData.get(world).onPlayerLogin(world, player));
	}
	
	public static void onPlayerLoggedOut(PlayerLoggedOutEvent event)
	{
		PlayerEntity player = event.getPlayer();
		Util.as(player.getEntityWorld(), ServerWorld.class)
			.ifPresent(world -> OwnedClockoutBlocksData.get(world).onPlayerLogout(world, player));
	}
}
