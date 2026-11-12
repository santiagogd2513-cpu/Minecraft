import com.example.beautifuldecor.entity.FlightPhysics;
import com.example.beautifuldecor.entity.FlightPhysics.Kind;

public class FlightPhysicsTest {
    static void check(boolean c, String m) {
        if (!c) {
            throw new AssertionError(m);
        }
    }

    public static void main(String[] a) {
        float full = FlightPhysics.airlinerLoad(54 * 64);
        check(full > 1.9f && full < 2.0f, "bodega llena " + full);
        // avión cargado necesita más velocidad para despegar y se cae antes
        check(FlightPhysics.takeoffSpeed(3.6f, full) > 5.0f && FlightPhysics.takeoffSpeed(3.6f, 1.0f) == 3.6f, "velocidad de despegue");
        check(FlightPhysics.stallSink(2.0f, FlightPhysics.stallSpeed(2.8f, 1.0f)) > 0.2f, "pérdida");
        check(FlightPhysics.stallSink(3.0f, 2.8f) == 0.0f, "sin pérdida por encima");
        // separación gradual
        float vr = 3.6f;
        check(FlightPhysics.liftFraction(vr, vr) == 0.0f && FlightPhysics.liftFraction(vr * 1.2f, vr) > 0.4f && FlightPhysics.liftFraction(vr * 1.4f, vr) == 1.0f, "fracción de ascenso");
        check(FlightPhysics.climbScale(full) < 0.6f && FlightPhysics.climbScale(1.0f) == 1.0f, "ascenso con carga");
        // helicóptero: vacío sube normal; lleno apenas
        float hl = FlightPhysics.heliLoad(27 * 64);
        float m1 = FlightPhysics.heliLiftMargin(1.0f);
        float m2 = FlightPhysics.heliLiftMargin(hl);
        check(m1 == 1.0f && m2 > 0.05f && m2 < 0.35f, "empuje del helicóptero lleno " + m2);
        check(FlightPhysics.heliLiftMargin(2.6f) == 0.0f, "sobrecargado no sube");
        check(FlightPhysics.heliRpmFactor(0.8f, 0.6f, m2) < FlightPhysics.heliRpmFactor(0.8f, 0.6f, 1.0f), "con carga pide más régimen");
        check(FlightPhysics.heliRpmFactor(1.0f, 0.6f, m2) == 1.0f, "a tope siempre rinde");
        // aterrizaje
        check(FlightPhysics.landing(0.15f, 1.0f).kind() == Kind.SOFT, "suave vacío");
        check(FlightPhysics.landing(0.15f, full).kind() == Kind.HARD, "la misma caída con carga es dura");
        check(FlightPhysics.landing(0.5f, 1.0f).kind() == Kind.CRASH, "caída fuerte = estrellarse");
        check(FlightPhysics.landing(0.8f, full).damage() >= 20.0f, "muy fuerte con carga mata");
        check(FlightPhysics.landing(0.3f, 1.0f).damage() > 0.0f && FlightPhysics.landing(0.3f, 1.0f).damage() < 20.0f, "duro hiere pero no mata");
        System.out.println("FlightPhysicsTest OK");
    }
}
