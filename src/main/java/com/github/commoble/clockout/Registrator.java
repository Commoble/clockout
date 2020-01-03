package com.github.commoble.clockout;

import java.util.function.Consumer;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent.Register;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

public class Registrator<T extends IForgeRegistryEntry<T>>
{
	public final IForgeRegistry<T> registry;
	
	public Registrator(IForgeRegistry<T> registry)
	{
		this.registry = registry;
	}
	
	public T register(String registryKey, T entry)
	{
		return this.register(new ResourceLocation(Clockout.MODID, registryKey), entry);
	}
	
	public T register(ResourceLocation loc, T entry)
	{
		entry.setRegistryName(loc);
		this.registry.register(entry);
		return entry;
	}
	
	public static <T extends IForgeRegistryEntry<T>> Consumer<Register<T>> getRegistryHandler(Consumer<Registrator<T>> consumer)
	{
		return event -> consumer.accept(new Registrator<T>(event.getRegistry()));
	}
}