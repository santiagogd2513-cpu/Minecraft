package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.ElevatorCabinEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dibuja los bloques de la cabina mientras viaja. La entidad está en el centro de la base de la cabina.
 * <p>
 * V3.3.3: (1) cuando la cabina ya volvió a ser bloques ({@link ElevatorCabinEntity#isLanded()}) no se dibuja, y un bloque de la cabina que
 * coincide con el bloque de verdad que ya está en ese sitio tampoco (al salir y al llegar): el paso entre cabina y bloques no se nota.
 * (2) Cada bloque toma la luz del sitio por el que pasa (antes toda la cabina usaba la luz de su esquina de abajo): se ve igual que los
 * bloques quietos, sin cambio de brillo al salir ni al llegar.
 */
public class ElevatorCabinRenderer extends EntityRenderer<ElevatorCabinEntity> {
    public ElevatorCabinRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(ElevatorCabinEntity cabin, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (!cabin.isReady() || cabin.isLanded()) {
            return;
        }
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        Level level = cabin.level();
        int sx = cabin.getSizeX();
        int sz = cabin.getSizeZ();
        int[] cells = cabin.getCells();
        // la esquina de la cabina en el mundo, en la posición suave de este fotograma (la misma que usa el dibujo de la entidad)
        double cornerX = Mth.lerp(partialTick, cabin.xo, cabin.getX()) - sx / 2.0;
        double cornerY = Mth.lerp(partialTick, cabin.yo, cabin.getY());
        double cornerZ = Mth.lerp(partialTick, cabin.zo, cabin.getZ()) - sz / 2.0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int index : cabin.getDrawList()) {
            int x = index % sx;
            int rest = index / sx;
            int z = rest % sz;
            int y = rest / sz;
            BlockState state = cabin.paletteState(cells[index]);
            at.set(Mth.floor(cornerX + x + 0.5), Mth.floor(cornerY + y + 0.5), Mth.floor(cornerZ + z + 0.5));
            int light = packedLight;
            if (level != null) {
                if (level.getBlockState(at) == state) {
                    // el bloque de verdad ya está justo ahí (al salir, el tick antes de que se quite; al llegar, apenas aparece): se ve ese
                    continue;
                }
                light = LevelRenderer.getLightColor(level, at);
            }
            poseStack.pushPose();
            poseStack.translate(x - sx / 2.0, y, z - sz / 2.0);
            dispatcher.renderSingleBlock(state, poseStack, bufferSource, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        super.render(cabin, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ElevatorCabinEntity cabin) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
