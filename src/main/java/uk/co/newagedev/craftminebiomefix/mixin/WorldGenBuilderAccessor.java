package uk.co.newagedev.craftminebiomefix.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.mines.WorldGenBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(WorldGenBuilder.class)
public interface WorldGenBuilderAccessor {
    @Accessor
    Set<ResourceKey<Biome>> getEnabledBiomes();
}