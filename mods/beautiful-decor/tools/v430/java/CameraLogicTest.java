import com.example.beautifuldecor.camera.CameraClock;
import com.example.beautifuldecor.camera.CameraInfo;
import com.example.beautifuldecor.camera.CameraNight;
import com.example.beautifuldecor.camera.CameraSweep;

public class CameraLogicTest {
    static void check(boolean c, String m) {
        if (!c) {
            throw new AssertionError(m);
        }
    }

    static boolean[] b(boolean s, boolean w, boolean n, boolean e) {
        return new boolean[]{s, w, n, e};
    }

    public static void main(String[] a) {
        // ---- techo: solo despejado da la vuelta completa
        check(CameraSweep.ceilingArc(b(false, false, false, false), 0).full(), "techo despejado: 360");
        CameraSweep.Arc corner = CameraSweep.ceilingArc(b(true, true, false, false), 180);       // sur y oeste bloqueados
        check(!corner.full(), "esquina: no da la vuelta");
        check(Math.abs(corner.span() - 160f) < 0.01f, "esquina: 2 sectores = 160 grados, fue " + corner.span());
        check(corner.start() > 135f - 1 && corner.end() < 315f + 1, "esquina: arco solo hacia norte y este (" + corner.start() + ".." + corner.end() + ")");
        CameraSweep.Arc wall = CameraSweep.ceilingArc(b(true, false, false, false), 180);        // pegada a una pared
        check(Math.abs(wall.span() - 250f) < 0.01f && !wall.full(), "una pared: 3 sectores = 250 grados");
        CameraSweep.Arc three = CameraSweep.ceilingArc(b(true, true, true, false), 270);
        check(Math.abs(three.span() - 70f) < 0.01f, "3 bloqueados: un sector = 70 grados");
        CameraSweep.Arc closed = CameraSweep.ceilingArc(b(true, true, true, true), 90);
        check(closed.fixed() && Math.abs(closed.start() - 90f) < 0.01f, "todo bloqueado: fija");
        CameraSweep.Arc corridor = CameraSweep.ceilingArc(b(true, false, true, false), 90);     // pasillo este-oeste, mira al oeste
        check(Math.abs(corridor.span() - 70f) < 0.01f && corridor.start() < 90f && corridor.end() > 90f, "pasillo: el sector hacia donde mira");
        CameraSweep.Arc corridor2 = CameraSweep.ceilingArc(b(true, false, true, false), 270);   // mira al este
        check(corridor2.start() < 270f && corridor2.end() > 270f, "pasillo: elige el sector de su lado");
        // pared: un poco a cada lado, menos con un bloque al lado
        CameraSweep.Arc w = CameraSweep.wallArc(180f, false, false);
        check(Math.abs(w.span() - 100f) < 0.01f && !w.full(), "pared libre: 100 grados");
        check(Math.abs(CameraSweep.wallArc(180f, true, false).span() - 65f) < 0.01f, "pared con un lado bloqueado");

        // ---- movimiento: siempre dentro del arco, continuo, con pausa en los extremos
        float prev = Float.NaN;
        float min = 1e9f, max = -1e9f;
        float maxStep = 0f;
        for (int t = 0; t < 20000; t++) {
            float y = CameraSweep.yaw(w, 180f, true, t, 0);
            min = Math.min(min, y);
            max = Math.max(max, y);
            if (!Float.isNaN(prev)) {
                maxStep = Math.max(maxStep, Math.abs(y - prev));
            }
            prev = y;
        }
        check(min >= 130f - 0.01f && max <= 230f + 0.01f, "barrido dentro del arco: " + min + ".." + max);
        check(min < 131f && max > 229f, "el barrido recorre todo el arco");
        check(maxStep <= 0.6f, "movimiento lento y continuo, paso máximo " + maxStep);
        check(CameraSweep.yaw(w, 180f, false, 1234, 0) == 180f, "sin rotación mira fijo");
        check(CameraSweep.yaw(w, 180f, true, 0, 0) == CameraSweep.yaw(w, 180f, true, 20, 0), "pausa al empezar");
        float f0 = CameraSweep.yaw(new CameraSweep.Arc(0, 360, true), 0f, true, 0, 0);
        float f1 = CameraSweep.yaw(new CameraSweep.Arc(0, 360, true), 0f, true, 450, 0);
        check(Math.abs(f0) < 0.01f && Math.abs(f1 - 0.4f * 450) < 0.01f, "vuelta completa a 0,4 grados por tick");
        check(CameraSweep.yaw(w, 180f, true, 500, 0) != CameraSweep.yaw(w, 180f, true, 500, 100), "el desfase cambia el movimiento");
        check(Math.abs(CameraSweep.norm(-90f) - 270f) < 0.001f && CameraSweep.norm(725f) == 5f, "normalizar ángulos");

        // ---- visión nocturna
        check(CameraNight.isNightTime(12000) && CameraNight.isNightTime(18000) && !CameraNight.isNightTime(6000) && !CameraNight.isNightTime(0) && !CameraNight.isNightTime(23800), "ventana de noche");
        check(CameraNight.isNightTime(12000 + 24000L * 5), "la noche se repite cada día");
        check(!CameraNight.next(false, 15, 6000) && CameraNight.next(false, 3, 6000), "de día: solo con muy poca luz");
        check(!CameraNight.next(false, 9, 6000), "de día con luz media: normal");
        check(CameraNight.next(false, 10, 13000) && !CameraNight.next(false, 15, 13000), "de noche: con luz de cielo oscuro se activa; con lámparas no");
        check(CameraNight.next(true, 12, 13000) && !CameraNight.next(true, 14, 13000), "histéresis al apagarse");
        check(CameraNight.next(false, 4, 12000), "justo a las 18:00 ya cuenta como noche");

        // ---- hora
        check(CameraClock.format(0).equals("06:00"), "tick 0 = 06:00, fue " + CameraClock.format(0));
        check(CameraClock.format(6000).equals("12:00") && CameraClock.format(12000).equals("18:00") && CameraClock.format(18000).equals("00:00"), "horas en punto");
        check(CameraClock.format(13500).equals("19:30") && CameraClock.format(24000L * 3 + 500).equals("06:30"), "minutos");
        check(CameraClock.format(23999).equals("05:59"), "fin del día");

        // ---- datos
        CameraInfo i = new CameraInfo(7, "  Entrada \n principal  ", "x".repeat(500), true);
        check(i.name().equals("Entrada  principal") || i.name().equals("Entrada principal") || i.name().startsWith("Entrada"), "nombre limpio: " + i.name());
        check(!i.name().contains("\n") && i.description().length() <= CameraInfo.MAX_DESC, "descripción acotada");
        check(new CameraInfo(3, "", "", false).name().equals("Cámara 3"), "nombre por defecto");
        i.set("Garaje", "", false);
        check(i.name().equals("Garaje") && !i.rotate(), "set");
        System.out.println("CameraLogicTest OK");
    }
}
