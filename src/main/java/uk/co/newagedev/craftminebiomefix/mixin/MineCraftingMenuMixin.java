package uk.co.newagedev.craftminebiomefix.mixin;

import net.minecraft.server.TheGame;
import net.minecraft.server.level.DimensionGenerator;
import net.minecraft.world.inventory.MineCraftingMenu;
import net.minecraft.world.level.mines.SpecialMine;
import net.minecraft.world.level.mines.WorldEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.co.newagedev.craftminebiomefix.FixedDimensionGenerator;

import java.util.List;
import java.util.Optional;

@Mixin(MineCraftingMenu.class)
public class MineCraftingMenuMixin {
    @Redirect(method = "onOpenMine", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DimensionGenerator;generateDimension(Lnet/minecraft/server/TheGame;Ljava/util/List;Ljava/util/Optional;)Lnet/minecraft/server/level/DimensionGenerator$GeneratedDimension;"))
    private DimensionGenerator.GeneratedDimension generateDimension(TheGame theGame, List<WorldEffect> list, Optional<SpecialMine> optional) {
        return FixedDimensionGenerator.generateDimension(theGame, list, optional);
    }
}