package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.TrainCarEntity;
import net.minecraft.client.Minecraft;

/** V6.2.0: arranca los sonidos de cada coche (solo la locomotora y uno de cada tres coches, para no saturar). */
public final class TrainSounds {
    private TrainSounds() {
    }

    public static void tick(TrainCarEntity car) {
        if (!(car.variant().isLoco() || car.getId() % 3 == 0)) {
            return;
        }
        float speed = Math.abs(car.visualSpeed());
        int state = car.brakeState();
        if (car.soundRoll == null && speed > 0.03f) {
            car.soundRoll = start(car, TrainWheelSound.Mode.ROLL);
        }
        if (car.soundBrake == null && state == 1 && speed > 0.04f) {
            car.soundBrake = start(car, TrainWheelSound.Mode.BRAKE);
        }
        if (car.soundEmergency == null && state == 2 && speed > 0.03f) {
            car.soundEmergency = start(car, TrainWheelSound.Mode.EMERGENCY);
        }
    }

    private static Object start(TrainCarEntity car, TrainWheelSound.Mode mode) {
        TrainWheelSound s = new TrainWheelSound(car, mode);
        Minecraft.getInstance().getSoundManager().play(s);
        return s;
    }
}
