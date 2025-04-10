package uk.co.newagedev.craftminebiomefix.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TheGame;
import net.minecraft.server.level.DimensionGenerator;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.mines.SpecialMine;
import net.minecraft.world.level.mines.WorldEffect;
import net.minecraft.world.level.mines.WorldGenBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Mixin(DimensionGenerator.class)
public abstract class DimensionGeneratorMixin {

    @Shadow
    private static <T> CompletableFuture<?> save(T object, Codec<T> codec, RegistryOps<JsonElement> registryOps, Path path) {
        return null;
    }

    @Inject(method = "generateDimension", at = @At("TAIL"))
    private static void injectBiomeTags(
        TheGame theGame,
        List<WorldEffect> list,
        Optional<SpecialMine> optional,
        CallbackInfoReturnable<DimensionGenerator.GeneratedDimension> cir,
        @Local(ordinal = 1) Path path2,
        @Local RegistryOps<JsonElement> registryOps,
        @Local ResourceLocation resourceLocation,
        @Local WorldGenBuilder worldGenBuilder
    ) {
        if (optional.isPresent()) return;
        Set<ResourceKey<Biome>> enabledBiomes = ((WorldGenBuilderAccessor) worldGenBuilder).getEnabledBiomes();
        Registry<Biome> biomeRegistry = theGame.registryAccess().lookupOrThrow(Registries.BIOME);

        Map<Path, List<TagEntry>> biomeTagsToWrite = new HashMap<>();

        for (ResourceKey<Biome> enabledBiome : enabledBiomes) {
            for (TagKey<Biome> biomeTag : ALL_BIOME_TAGS) {
                Optional<HolderSet.Named<Biome>> biomeTagOptional = biomeRegistry.get(biomeTag);
                if (biomeTagOptional.isPresent()) {
                    HolderSet.Named<Biome> biomeSet = biomeTagOptional.get();

                    if (biomeSet.stream().map(Holder::unwrapKey).anyMatch(optionalKey -> optionalKey.isPresent() && optionalKey.get() == enabledBiome)) {
                        Path tagPath = path2.resolve("tags").resolve("worldgen").resolve("biome").resolve(biomeTag.location().getPath() + ".json");

                        if (!biomeTagsToWrite.containsKey(tagPath)) {
                            biomeTagsToWrite.put(tagPath, new ArrayList<>());
                        }

                        biomeTagsToWrite.get(tagPath).add(TagEntry.element(enabledBiome.location().withPrefix(resourceLocation.getPath() + "/")));
                    }
                }
            }
        }

        for (Map.Entry<Path, List<TagEntry>> entry : biomeTagsToWrite.entrySet()) {
            TagFile tagFile = new TagFile(entry.getValue(), false);
            save(tagFile, TagFile.CODEC, registryOps, entry.getKey());
        }
    }

    @Unique
    private static final TagKey<Biome>[] ALL_BIOME_TAGS = new TagKey[]{
            BiomeTags.IS_DEEP_OCEAN,
            BiomeTags.IS_OCEAN,
            BiomeTags.IS_BEACH,
            BiomeTags.IS_RIVER,
            BiomeTags.IS_MOUNTAIN,
            BiomeTags.IS_BADLANDS,
            BiomeTags.IS_HILL,
            BiomeTags.IS_TAIGA,
            BiomeTags.IS_JUNGLE,
            BiomeTags.IS_FOREST,
            BiomeTags.IS_SAVANNA,
            BiomeTags.IS_OVERWORLD,
            BiomeTags.IS_NETHER,
            BiomeTags.IS_END,
            BiomeTags.STRONGHOLD_BIASED_TO,
            BiomeTags.HAS_BURIED_TREASURE,
            BiomeTags.HAS_DESERT_PYRAMID,
            BiomeTags.HAS_IGLOO,
            BiomeTags.HAS_JUNGLE_TEMPLE,
            BiomeTags.HAS_MINESHAFT,
            BiomeTags.HAS_MINESHAFT_MESA,
            BiomeTags.HAS_OCEAN_MONUMENT,
            BiomeTags.HAS_OCEAN_RUIN_COLD,
            BiomeTags.HAS_OCEAN_RUIN_WARM,
            BiomeTags.HAS_PILLAGER_OUTPOST,
            BiomeTags.HAS_RUINED_PORTAL_DESERT,
            BiomeTags.HAS_RUINED_PORTAL_JUNGLE,
            BiomeTags.HAS_RUINED_PORTAL_OCEAN,
            BiomeTags.HAS_RUINED_PORTAL_SWAMP,
            BiomeTags.HAS_RUINED_PORTAL_MOUNTAIN,
            BiomeTags.HAS_RUINED_PORTAL_STANDARD,
            BiomeTags.HAS_SHIPWRECK_BEACHED,
            BiomeTags.HAS_SHIPWRECK,
            BiomeTags.HAS_STRONGHOLD,
            BiomeTags.HAS_TRIAL_CHAMBERS,
            BiomeTags.HAS_SWAMP_HUT,
            BiomeTags.HAS_VILLAGE_DESERT,
            BiomeTags.HAS_VILLAGE_PLAINS,
            BiomeTags.HAS_VILLAGE_SAVANNA,
            BiomeTags.HAS_VILLAGE_SNOWY,
            BiomeTags.HAS_VILLAGE_TAIGA,
            BiomeTags.HAS_TRAIL_RUINS,
            BiomeTags.HAS_WOODLAND_MANSION,
            BiomeTags.HAS_NETHER_FORTRESS,
            BiomeTags.HAS_NETHER_FOSSIL,
            BiomeTags.HAS_BASTION_REMNANT,
            BiomeTags.HAS_ANCIENT_CITY,
            BiomeTags.HAS_RUINED_PORTAL_NETHER,
            BiomeTags.HAS_END_CITY,
            BiomeTags.REQUIRED_OCEAN_MONUMENT_SURROUNDING,
            BiomeTags.MINESHAFT_BLOCKING,
            BiomeTags.PLAYS_UNDERWATER_MUSIC,
            BiomeTags.HAS_CLOSER_WATER_FOG,
            BiomeTags.WATER_ON_MAP_OUTLINES,
            BiomeTags.PRODUCES_CORALS_FROM_BONEMEAL,
            BiomeTags.INCREASED_FIRE_BURNOUT,
            BiomeTags.SNOW_GOLEM_MELTS,
            BiomeTags.WITHOUT_ZOMBIE_SIEGES,
            BiomeTags.WITHOUT_PATROL_SPAWNS,
            BiomeTags.WITHOUT_WANDERING_TRADER_SPAWNS,
            BiomeTags.SPAWNS_COLD_VARIANT_FROGS,
            BiomeTags.SPAWNS_WARM_VARIANT_FROGS,
            BiomeTags.SPAWNS_COLD_VARIANT_FARM_ANIMALS,
            BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS,
            BiomeTags.SPAWNS_GOLD_RABBITS,
            BiomeTags.SPAWNS_WHITE_RABBITS,
            BiomeTags.REDUCED_WATER_AMBIENT_SPAWNS,
            BiomeTags.ALLOWS_TROPICAL_FISH_SPAWNS_AT_ANY_HEIGHT,
            BiomeTags.POLAR_BEARS_SPAWN_ON_ALTERNATE_BLOCKS,
            BiomeTags.MORE_FREQUENT_DROWNED_SPAWNS,
            BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS,
            BiomeTags.SPAWNS_SNOW_FOXES
    };

}
