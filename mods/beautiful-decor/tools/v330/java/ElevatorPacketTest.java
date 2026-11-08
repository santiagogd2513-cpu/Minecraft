package com.example.beautifuldecor.network;

import com.example.beautifuldecor.client.ClientHooks;
import com.example.beautifuldecor.elevator.ElevatorShaft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Field;
import java.util.Random;

/**
 * Prueba de la V3.3.0 sin Minecraft: los PAQUETES REALES del ascensor (ElevatorSavePacket y ElevatorControllerPacket) con piezas de
 * mentira de FriendlyByteBuf (mismos VarInt y bytes con signo que Minecraft). Lo que se escribe es lo que se lee, con el desplazamiento
 * X/Y/Z incluso negativo, y lo que llega al servidor es lo que llama a ElevatorShaft.applySettings; lo que llega al cliente es lo que
 * abre el menú.
 */
public class ElevatorPacketTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void roundTripSave(Random rnd, BlockPos pos, Direction facing, int w, int d, int h, int ox, int oy, int oz, float speed, String name)
            throws ReflectiveOperationException {
        ElevatorSavePacket sent = new ElevatorSavePacket(pos, facing, w, d, h, ox, oy, oz, speed, name);
        FriendlyByteBuf buf = new FriendlyByteBuf();
        sent.encode(buf);
        buf.startReading();
        ElevatorSavePacket got = ElevatorSavePacket.decode(buf);
        check(buf.unread() == 0, "el paquete de guardar se leyó entero (sobraron " + buf.unread() + " bytes)");
        check(field(got, "pos").equals(pos) && field(got, "facing") == facing, "guardar: posición y lado");
        check((int) field(got, "width") == w && (int) field(got, "depth") == d && (int) field(got, "height") == h, "guardar: tamaño");
        check((int) field(got, "offX") == ox && (int) field(got, "offY") == oy && (int) field(got, "offZ") == oz,
                "guardar: desplazamiento " + ox + "," + oy + "," + oz + " llegó como " + field(got, "offX") + "," + field(got, "offY") + "," + field(got, "offZ"));
        check((float) field(got, "speed") == speed && field(got, "name").equals(name), "guardar: velocidad y nombre");
    }

    private static void roundTripController(BlockPos pos, Direction facing, int w, int d, int h, int ox, int oy, int oz, float speed, String name, boolean open) {
        ElevatorControllerPacket sent = new ElevatorControllerPacket(pos, facing, w, d, h, ox, oy, oz, speed, name, open);
        FriendlyByteBuf buf = new FriendlyByteBuf();
        sent.encode(buf);
        buf.startReading();
        ElevatorControllerPacket got = ElevatorControllerPacket.decode(buf);
        check(buf.unread() == 0, "el paquete del menú se leyó entero (sobraron " + buf.unread() + " bytes)");
        check(got.pos.equals(pos) && got.facing == facing && got.width == w && got.depth == d && got.height == h, "menú: posición, lado y tamaño");
        check(got.offX == ox && got.offY == oy && got.offZ == oz, "menú: desplazamiento " + ox + "," + oy + "," + oz + " llegó como " + got.offX + "," + got.offY + "," + got.offZ);
        check(got.speed == speed && got.name.equals(name) && got.openMenu == open, "menú: velocidad, nombre y si abre el menú");
    }

    public static void main(String[] args) throws Exception {
        Random rnd = new Random(330);
        int[] extremes = {-64, -63, -21, -5, -1, 0, 1, 5, 21, 63, 64};
        int cases = 0;
        for (Direction facing : Direction.values()) {
            for (int ox : extremes) {
                for (int oy : extremes) {
                    for (int oz : extremes) {
                        BlockPos pos = new BlockPos(rnd.nextInt(60_000_000) - 30_000_000, rnd.nextInt(384) - 64, rnd.nextInt(60_000_000) - 30_000_000);
                        int w = 1 + rnd.nextInt(50);
                        int d = 1 + rnd.nextInt(50);
                        int h = 1 + rnd.nextInt(24);
                        float speed = 1.0f + rnd.nextInt(39) / 2.0f;
                        String name = rnd.nextBoolean() ? "" : "Piso " + rnd.nextInt(100) + " ñandú";
                        roundTripSave(rnd, pos, facing, w, d, h, ox, oy, oz, speed, name);
                        roundTripController(pos, facing, w, d, h, ox, oy, oz, speed, name, rnd.nextBoolean());
                        cases++;
                    }
                }
            }
        }

        // lo que llega al servidor llama a applySettings con lo mismo
        ServerLevel level = new ServerLevel();
        ServerPlayer player = new ServerPlayer(level);
        BlockPos pos = new BlockPos(100, 70, -300);
        player.nearX = 100.5;
        player.nearY = 70.5;
        player.nearZ = -299.5;
        ElevatorSavePacket save = new ElevatorSavePacket(pos, Direction.EAST, 7, 4, 6, -12, 3, 40, 6.5f, "Lobby");
        FriendlyByteBuf buf = new FriendlyByteBuf();
        save.encode(buf);
        ElevatorSavePacket received = ElevatorSavePacket.decode(buf.startReading());
        NetworkEvent.Context context = new NetworkEvent.Context();
        context.sender = player;
        ElevatorShaft.last = null;
        ElevatorSavePacket.handle(received, () -> context);
        check(context.handled, "el paquete de guardar queda marcado como atendido");
        check(ElevatorShaft.last != null, "el servidor llamó a applySettings");
        Object[] a = ElevatorShaft.last;
        check(a[0].equals(pos) && a[1] == Direction.EAST && (int) a[2] == 7 && (int) a[3] == 4 && (int) a[4] == 6, "applySettings: posición, lado y tamaño");
        check((int) a[5] == -12 && (int) a[6] == 3 && (int) a[7] == 40, "applySettings: desplazamiento X, Y, Z = -12, 3, 40; llegó " + a[5] + "," + a[6] + "," + a[7]);
        check((float) a[8] == 6.5f && a[9].equals("Lobby"), "applySettings: velocidad y nombre");

        // un jugador lejos o una zona sin cargar NO cambia nada
        player.nearX = 500;
        ElevatorShaft.last = null;
        ElevatorSavePacket.handle(received, () -> context);
        check(ElevatorShaft.last == null, "un jugador a más de 10 bloques no cambia el ascensor");
        player.nearX = 100.5;
        level.loaded = false;
        ElevatorSavePacket.handle(received, () -> context);
        check(ElevatorShaft.last == null, "con la zona sin cargar no se cambia nada");

        // lo que llega al cliente abre el menú con lo mismo
        ElevatorControllerPacket menu = new ElevatorControllerPacket(pos, Direction.NORTH, 3, 3, 4, 9, -2, 17, 4.0f, "Piso 1", true);
        buf = new FriendlyByteBuf();
        menu.encode(buf);
        ElevatorControllerPacket clientGot = ElevatorControllerPacket.decode(buf.startReading());
        ClientHooks.lastController = null;
        ElevatorControllerPacket.handle(clientGot, () -> new NetworkEvent.Context());
        check(ClientHooks.lastController == clientGot && clientGot.offX == 9 && clientGot.offY == -2 && clientGot.offZ == 17, "el cliente abre el menú con el desplazamiento recibido");

        // el desplazamiento ocupa un byte por eje, valga lo que valga: el paquete no crece con números grandes ni negativos
        FriendlyByteBuf zero = new FriendlyByteBuf();
        new ElevatorSavePacket(new BlockPos(0, 0, 0), Direction.SOUTH, 3, 3, 4, 0, 0, 0, 4.0f, "").encode(zero);
        FriendlyByteBuf wide = new FriendlyByteBuf();
        new ElevatorSavePacket(new BlockPos(0, 0, 0), Direction.SOUTH, 3, 3, 4, 64, -64, 64, 4.0f, "").encode(wide);
        check(zero.written() == wide.written(), "el desplazamiento ocupa lo mismo con 0 que con ±64 (" + zero.written() + " y " + wide.written() + " bytes)");

        System.out.println("ELEVATOR PACKETS OK (" + cases + " combinaciones, " + checks + " comprobaciones)");
    }
}
