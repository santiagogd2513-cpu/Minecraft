package com.mojang.blaze3d.vertex;
import org.joml.Matrix4f;
public interface VertexConsumer {
    VertexConsumer vertex(double x, double y, double z);
    VertexConsumer color(int r, int g, int b, int a);
    VertexConsumer uv(float u, float v);
    VertexConsumer uv2(int u, int v);
    void endVertex();
    default VertexConsumer vertex(Matrix4f m, float x, float y, float z) { return vertex(x, y, z); }
    default VertexConsumer uv2(int packed) { return uv2(packed & 0xFFFF, packed >> 16 & 0xFFFF); }
}
