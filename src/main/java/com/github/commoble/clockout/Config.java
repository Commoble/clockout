package com.github.commoble.clockout;

import com.github.commoble.clockout.util.ConfigHelper;
import com.github.commoble.clockout.util.ConfigHelper.ConfigValueListener;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config
{
	public ConfigValueListener<Integer> permission_level_for_seeing_clockout_owners;
	
	public Config(ForgeConfigSpec.Builder builder, ConfigHelper.Subscriber subscriber)
	{
		builder.push("Permissions");
		this.permission_level_for_seeing_clockout_owners = subscriber.subscribe(builder
			.comment("Minimum permission level for seeing who owns somebody else's clockout block")
			.translation("clockout.permission_level_for_seeing_clockout_owners")
			.define("permission_level_for_seeing_clockout_owners", 1));
		builder.pop();
	}
}
