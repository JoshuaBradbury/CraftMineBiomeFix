/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.google.gson.JsonElement
 *  com.google.gson.stream.JsonWriter
 *  com.mojang.datafixers.kinds.App
 *  com.mojang.datafixers.kinds.Applicative
 *  com.mojang.logging.LogUtils
 *  com.mojang.serialization.Codec
 *  com.mojang.serialization.DataResult
 *  com.mojang.serialization.DynamicOps
 *  com.mojang.serialization.JsonOps
 *  com.mojang.serialization.codecs.RecordCodecBuilder
 *  javax.annotation.CheckReturnValue
 *  org.slf4j.Logger
 */
package uk.co.newagedev.craftminebiomefix;

import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.DataProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TheGame;
import net.minecraft.server.level.DimensionGenerator;
import net.minecraft.server.packs.GeneratedMarkerMetadataSection;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.mines.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.slf4j.Logger;
import uk.co.newagedev.craftminebiomefix.mixin.WorldGenBuilderAccessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class FixedDimensionGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String LEVEL_PREFIX = "level";

    public static DimensionGenerator.GeneratedDimension generateDimension(TheGame theGame, List<WorldEffect> list, Optional<SpecialMine> optional) {
        ResourceKey<DimensionType> resourceKey;
        ServerLevelData serverLevelData = theGame.getWorldData().overworldData();
        int n = serverLevelData.incrementAndGetLevelCount();
        ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace(LEVEL_PREFIX + n);
        Path path = theGame.server().getWorldPath(LevelResource.DATAPACK_DIR).resolve(resourceLocation.getPath());
        MutableComponent mutableComponent = Component.translatable("minecraftlike.pack_name", n);
        RegistryOps<JsonElement> registryOps = theGame.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        ArrayList<CompletableFuture<?>> arrayList = new ArrayList<>();
        arrayList.add(save(new DimensionPackMetadata(new PackMetadataSection(mutableComponent, 71, Optional.empty()), new GeneratedMarkerMetadataSection()), DimensionPackMetadata.CODEC, registryOps, path.resolve("pack.mcmeta")));
        Path path2 = path.resolve("data").resolve(resourceLocation.getNamespace());
        Holder.Reference<DimensionType> reference = theGame.registryAccess().getOrThrow(BuiltinDimensionTypes.GENERATED);
        WorldGenBuilder worldGenBuilder = new WorldGenBuilder(theGame.registryAccess());
        WorldEffects.componentsOfType(list, WorldGenEffect.class).forEach(worldGenEffect -> worldGenEffect.modifyWorld(worldGenBuilder));
        Optional<DimensionType> optionalDimensionType = worldGenBuilder.createDimensionType(reference.value());
        if (optionalDimensionType.isEmpty()) {
            resourceKey = reference.unwrapKey().get();
        } else {
            DimensionType dimensionType = optionalDimensionType.get();
            Path dimensionTypePath = path2.resolve("dimension_type").resolve(resourceLocation.getPath() + ".json");
            arrayList.add(save(dimensionType, DimensionType.DIRECT_CODEC, registryOps, dimensionTypePath));
            resourceKey = ResourceKey.create(Registries.DIMENSION_TYPE, resourceLocation);
        }

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

        List<WorldGenBuilder.ModifiedBiome> modifiedBiomes = worldGenBuilder.createModifiedBiomes(theGame.registryAccess().lookupOrThrow(Registries.BIOME), resourceLocation.getPath());
        for (WorldGenBuilder.ModifiedBiome modifiedBiome : modifiedBiomes) {
            arrayList.add(save(modifiedBiome.biome(), Biome.DIRECT_CODEC, registryOps, path2.resolve("worldgen").resolve("biome").resolve(modifiedBiome.modified().location().getPath() + ".json")));
        }
        Path dimensionPath = path2.resolve("dimension").resolve(resourceLocation.getPath() + ".json");
        arrayList.add(save(new FakeLevelStem(resourceKey, Optional.empty(), list, optional, worldGenBuilder.spawnStrategy()), FakeLevelStem.CODEC, registryOps, dimensionPath));
        return new DimensionGenerator.GeneratedDimension(ResourceKey.create(Registries.LEVEL_STEM, resourceLocation), () -> CompletableFuture.allOf(arrayList.toArray(new CompletableFuture[0])).join());
    }

    private static <T> CompletableFuture<?> save(T t, Codec<T> codec, RegistryOps<JsonElement> registryOps, Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                DataResult dataResult = codec.encodeStart((DynamicOps) registryOps, t);
                if (dataResult.isError()) {
                    LOGGER.error("Failed to encode entry {}: {}", path, dataResult.error());
                    return;
                }
                try (JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8));) {
                    jsonWriter.setSerializeNulls(false);
                    jsonWriter.setIndent("  ");
                    GsonHelper.writeValue(jsonWriter, (JsonElement) dataResult.getOrThrow(), DataProvider.KEY_COMPARATOR);
                }
                FileUtil.createDirectoriesSafe(path.getParent());
                Files.write(path, byteArrayOutputStream.toByteArray());
            } catch (IOException iOException) {
                LOGGER.error("Failed to save file to {}", path, iOException);
            }
        }, Util.backgroundExecutor().forName("saveDimension"));
    }

    record DimensionPackMetadata(PackMetadataSection pack, GeneratedMarkerMetadataSection generated) {
        public static final Codec<DimensionPackMetadata> CODEC = RecordCodecBuilder.create(instance -> instance.group(PackMetadataSection.CODEC.fieldOf("pack").forGetter(DimensionPackMetadata::pack), GeneratedMarkerMetadataSection.CODEC.fieldOf("generated").forGetter(DimensionPackMetadata::generated)).apply(instance, DimensionPackMetadata::new));
    }

    record FakeLevelStem(ResourceKey<DimensionType> type, Optional<ChunkGenerator> generator, List<WorldEffect> effects,
                         Optional<SpecialMine> mine, MineSpawnStrategy spawn) {
        public static final Codec<FakeLevelStem> CODEC = RecordCodecBuilder.create(instance -> instance.group(ResourceKey.codec(Registries.DIMENSION_TYPE).fieldOf("type").forGetter(FakeLevelStem::type), ChunkGenerator.CODEC.optionalFieldOf("generator").forGetter(FakeLevelStem::generator), WorldEffect.CODEC.listOf().fieldOf("effects").forGetter(FakeLevelStem::effects), SpecialMine.CODEC.optionalFieldOf("mine").forGetter(FakeLevelStem::mine), MineSpawnStrategy.CODEC.fieldOf("spawn").forGetter(FakeLevelStem::spawn)).apply(instance, FakeLevelStem::new));
    }

    private static TagKey<Biome>[] ALL_BIOME_TAGS = new TagKey[]{
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

