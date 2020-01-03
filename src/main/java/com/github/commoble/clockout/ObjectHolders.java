package com.github.commoble.clockout;

import net.minecraft.item.BlockItem;
import net.minecraftforge.registries.ObjectHolder;

@ObjectHolder(Clockout.MODID)
public class ObjectHolders
{
	@ObjectHolder(Clockout.CLOCKOUT_BLOCK_NAME)
	public static final ClockoutBlock CLOCKOUT_BLOCK = null;
	
	@ObjectHolder(Clockout.CLOCKOUT_BLOCK_NAME)
	public static final BlockItem CLOCKOUT_BLOCK_ITEM = null;
}
