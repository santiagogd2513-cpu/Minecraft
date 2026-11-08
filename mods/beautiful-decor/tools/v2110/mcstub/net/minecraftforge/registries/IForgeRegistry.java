package net.minecraftforge.registries;
import net.minecraft.resources.ResourceLocation;
public interface IForgeRegistry<V> { ResourceLocation getKey(V value); }
