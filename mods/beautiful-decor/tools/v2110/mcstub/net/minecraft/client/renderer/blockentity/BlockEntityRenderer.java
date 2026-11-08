package net.minecraft.client.renderer.blockentity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
public interface BlockEntityRenderer<T extends BlockEntity> {
    void render(T entity, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay);
}
