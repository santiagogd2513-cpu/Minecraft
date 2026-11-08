package com.mojang.math;
import org.joml.Quaternionf;
public interface Axis {
    Axis YP = new Axis() { @Override public Quaternionf rotationDegrees(float degrees) { return new Quaternionf(); } };
    Quaternionf rotationDegrees(float degrees);
}
