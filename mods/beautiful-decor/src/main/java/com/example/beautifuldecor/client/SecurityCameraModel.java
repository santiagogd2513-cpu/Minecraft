package com.example.beautifuldecor.client;

import com.example.beautifuldecor.BeautifulDecor;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * V4.3.0: la cabeza de la cámara de seguridad (la parte que gira): cuerpo alargado con visera, anillo del lente y tapa trasera. Atlas de
 * textura de 32x32 (una por color). Coordenadas del modelo de entidad de Minecraft (y hacia abajo); el frente es -z.
 */
public final class SecurityCameraModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(BeautifulDecor.MOD_ID, "security_camera"), "main");

    private SecurityCameraModel() {
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2f, -2f, -5f, 4f, 4f, 10f)             // cuerpo
                .texOffs(0, 14).addBox(-2.5f, -3f, -5.5f, 5f, 1f, 11f)        // visera
                .texOffs(0, 26).addBox(-1.5f, -1.5f, -5.6f, 3f, 3f, 1f)       // anillo del lente
                .texOffs(12, 26).addBox(-1.5f, -1.5f, 5f, 3f, 3f, 2f),        // tapa trasera y cable
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** La pieza que se inclina hacia abajo (la cabeza completa). */
    public static ModelPart head(ModelPart root) {
        return root.getChild("head");
    }
}
