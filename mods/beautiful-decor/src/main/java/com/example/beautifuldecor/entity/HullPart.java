package com.example.beautifuldecor.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;

/**
 * V6.13.0: un trozo de la caja de colisión de un vehículo largo (tren). Una entidad solo tiene UNA caja que no gira, así que el vehículo se
 * descompone en varios trozos chicos que se van moviendo con él (cliente y servidor lo calculan igual, sin paquetes). Es sólido para los demás,
 * pero no se puede apuntar ni golpear: los clics pasan al vehículo.
 */
public class HullPart extends PartEntity<Entity> {
    public HullPart(Entity parent) {
        super(parent);
    }

    /** Pone el trozo en esa caja (la posición de la parte es el centro de su base). */
    public void place(AABB box) {
        this.setBoundingBox(box);
        this.setPosRaw((box.minX + box.maxX) * 0.5, box.minY, (box.minZ + box.maxZ) * 0.5);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
}
