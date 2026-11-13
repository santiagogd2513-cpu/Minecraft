package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.PaintableVehicle;
import com.example.beautifuldecor.entity.VehiclePaint;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.VehiclePaintPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Menú de colores de un vehículo (V3.2.0), en modo oscuro con azul como los demás menús del mod. Se abre con la tecla R
 * mirando el vehículo (o yendo montado en él): hasta tres fichas (pintura, manijas de las puertas y rines; cada vehículo
 * muestra las que tiene), cada una con tres barras RGB, un cuadro hexadecimal y una paleta de colores listos.
 * <p>
 * V3.6.0: un botón "Otros" abre la lista de TODOS los colores del vehículo (los tres de siempre y además vidrios, asientos, luces,
 * cromados...; menos los neumáticos y las hélices o el rotor, que no se pueden cambiar). Se elige uno de la lista y se edita con las
 * mismas barras, el mismo cuadro hexadecimal y la misma paleta.
 * <p>
 * El panel va a un lado y no oscurece la pantalla: el vehículo se repinta EN VIVO mientras eliges (solo en tu pantalla),
 * y solo cambia de verdad para todos al pulsar "Aplicar". Cancelar o cerrar con Esc lo deja como estaba.
 */
public class VehicleColorScreen extends Screen {
    /** Hasta dónde llega la mirada para escoger un vehículo, en bloques. */
    private static final double PICK_RANGE = 8.0;
    private static final int W = 272;
    private static final int H = 242;
    private static final String[] TAB_KEYS = {
            "gui.beautifuldecor.vehicle.body", "gui.beautifuldecor.vehicle.handle", "gui.beautifuldecor.vehicle.rim"};
    private static final String[] CHANNEL_KEYS = {
            "gui.beautifuldecor.garage.red", "gui.beautifuldecor.garage.green", "gui.beautifuldecor.garage.blue"};
    private static final int[] CHANNEL_COLORS = {0xFFE05555, 0xFF55C46A, 0xFF5588E0};
    private static final int[] PRESETS = {
            0xFFFFFF, 0xC8C8D0, 0x6B707A, 0x2E3139, 0x111111, 0xCE1C24, 0xE8651A, 0xF2C230,
            0x3FA34D, 0x1FA8A0, 0x2F6FDE, 0x1B2F6B, 0x7A3FD1, 0xE86AB5, 0x8B5A2B, 0xD8C8A0};
    private static final int TRACK_X = 56;
    private static final int TRACK_W = 136;
    /** V3.6.0: la lista de "Otros": dos columnas de filas de este alto, debajo de las fichas. */
    private static final int LIST_ROW = 17;
    private static final int LIST_TOP = 64;

    private final Entity entity;
    private final PaintableVehicle vehicle;
    private final int[] colors = new int[VehiclePaint.CHANNELS];
    private final int[] channels;
    /** Canal que se está editando (0..CHANNELS-1): uno de los tres de siempre o uno de "Otros". */
    private int channel;
    private boolean listOpen;
    private int dragging = -1;
    private boolean updatingBox;
    private EditBox hexBox;
    private int left;
    private int top;

    public VehicleColorScreen(Entity entity, PaintableVehicle vehicle) {
        super(Component.translatable("gui.beautifuldecor.vehicle.title"));
        this.entity = entity;
        this.vehicle = vehicle;
        int[] now = VehiclePaint.colors(vehicle);
        int count = 0;
        for (int ch = 0; ch < VehiclePaint.CHANNELS; ch++) {
            this.colors[ch] = now[ch];
            if (ch < VehiclePaint.MAIN && vehicle.paintKind().has(ch)) {
                count++;
            }
        }
        this.channels = new int[count];
        int i = 0;
        for (int ch = 0; ch < VehiclePaint.MAIN; ch++) {
            if (vehicle.paintKind().has(ch)) {
                this.channels[i++] = ch;
            }
        }
        this.channel = this.channels.length == 0 ? VehiclePaint.BODY : this.channels[0];
    }

    // ------------------------------------------------------------------ cómo se abre

    /**
     * Tecla R: abre el menú del vehículo al que apunta el jugador (o en el que va montado). Devuelve false si no hay ninguno.
     *
     * @param blockDistance distancia al bloque apuntado si es uno que también usa R (puerta de garaje, computador HS);
     *                      solo se abre el menú del vehículo si está más cerca que ese bloque. Infinito si no hay.
     */
    public static boolean tryOpen(Minecraft minecraft, double blockDistance) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.getCameraEntity() != player) {
            return false;       // (mientras se pilota un carro RC o un dron, R es salir de él)
        }
        Entity ridden = player.getVehicle();
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        double range = Math.min(PICK_RANGE, blockDistance);
        Vec3 end = eye.add(look.x * range, look.y * range, look.z * range);
        Entity best = null;
        double bestSqr = range * range;
        for (Entity e : level.getEntities(player, new AABB(eye, end).inflate(3.0),
                x -> x instanceof PaintableVehicle && !x.isRemoved() && x != ridden)) {
            // La caja de choque de un vehículo es más chica que su modelo: se agranda un poco para que apuntar a la punta cuente.
            Optional<Vec3> hit = e.getBoundingBox().inflate(1.0, 0.3, 1.0).clip(eye, end);
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < bestSqr) {
                    best = e;
                    bestSqr = d;
                }
            }
        }
        if (best != null) {
            BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            if (wall.getType() == HitResult.Type.BLOCK && eye.distanceToSqr(wall.getLocation()) < bestSqr - 0.01) {
                best = null;    // hay una pared en medio
            }
        }
        if (best == null && blockDistance == Double.POSITIVE_INFINITY && ridden instanceof PaintableVehicle) {
            best = ridden;
        }
        if (best instanceof PaintableVehicle paintable) {
            minecraft.setScreen(new VehicleColorScreen(best, paintable));
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ pantalla

    @Override
    protected void init() {
        super.init();
        this.left = this.width >= W + 150 ? this.width - W - 10 : (this.width - W) / 2;
        this.top = Math.max(2, (this.height - H) / 2);
        this.hexBox = new EditBox(this.font, this.left + 44, this.top + 134, 64, 16, Component.empty());
        this.hexBox.setMaxLength(6);
        this.hexBox.setFilter(s -> s.matches("[0-9a-fA-F]{0,6}"));
        this.hexBox.setResponder(this::onHexChanged);
        this.hexBox.visible = !this.listOpen;
        this.addRenderableWidget(this.hexBox);
        if (this.entity instanceof com.example.beautifuldecor.entity.SkinnableVehicle skinnable && skinnable.skinCount() > 1) {
            // V6.7.0: ficha "Skins" arriba a la derecha del menú
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.beautifuldecor.skins.button"),
                    b -> this.minecraft.setScreen(new VehicleSkinScreen(this.entity, skinnable, this)))
                    .bounds(this.left + W - 96, this.top + 6, 80, 20).build());
        }
        syncHex();
        preview();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.entity.isRemoved() || (this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.distanceToSqr(this.entity) > 30.0 * 30.0)) {
            this.onClose();
        }
    }

    /** Pase lo que pase al cerrar (Aplicar, Cancelar, Esc, otra pantalla), la vista previa se quita. */
    @Override
    public void removed() {
        this.vehicle.setPaintPreview(null);
        super.removed();
    }

    // ------------------------------------------------------------------ valores

    private int current() {
        return this.channel;
    }

    /** V3.6.0: ¿tiene este vehículo partes de "Otros"? (todos los de ahora sí) */
    private boolean hasOthers() {
        return this.vehicle.paintKind().extraCount() > 0;
    }

    /** Cuántas fichas hay: las de siempre y, si hay, "Otros". */
    private int tabCount() {
        return this.channels.length + (hasOthers() ? 1 : 0);
    }

    private boolean isOthersTab(int i) {
        return i >= this.channels.length;
    }

    /** Nombre de un canal: el de su ficha o el de su parte de "Otros". */
    private String channelName(int ch) {
        if (ch < VehiclePaint.MAIN) {
            return DarkUi.tr(TAB_KEYS[ch]);
        }
        return DarkUi.tr("gui.beautifuldecor.paint." + this.vehicle.paintKind().extraKey(ch - VehiclePaint.EXTRA_FIRST));
    }

    /** Todos los canales de la lista de "Otros", en orden: los de siempre y después las partes. */
    private int[] listChannels() {
        VehiclePaint.Kind kind = this.vehicle.paintKind();
        int[] out = new int[VehiclePaint.CHANNELS];
        int n = 0;
        for (int ch = 0; ch < VehiclePaint.CHANNELS; ch++) {
            if (kind.has(ch)) {
                out[n++] = ch;
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private int listColumnWidth() {
        return (W - 32 - 6) / 2;
    }

    private int listX(int i) {
        return this.left + 16 + (i % 2) * (listColumnWidth() + 6);
    }

    private int listY(int i) {
        return this.top + LIST_TOP + 4 + (i / 2) * LIST_ROW;
    }

    private void setListOpen(boolean open) {
        this.listOpen = open;
        this.dragging = -1;
        if (this.hexBox != null) {
            this.hexBox.visible = !open;
            if (open) {
                this.hexBox.setFocused(false);
            }
        }
    }

    private int channel(int c) {
        return (this.colors[current()] >> (16 - 8 * c)) & 255;
    }

    private void setColor(int rgb) {
        this.colors[current()] = rgb & 0xFFFFFF;
        syncHex();
        preview();
    }

    private void setChannel(int c, int value) {
        int shift = 16 - 8 * c;
        setColor((this.colors[current()] & ~(255 << shift)) | (Mth.clamp(value, 0, 255) << shift));
    }

    private void preview() {
        this.vehicle.setPaintPreview(this.colors);
    }

    private void syncHex() {
        if (this.hexBox == null) {
            return;
        }
        this.updatingBox = true;
        this.hexBox.setValue(String.format("%06X", this.colors[current()] & 0xFFFFFF));
        this.updatingBox = false;
    }

    private void onHexChanged(String text) {
        if (!this.updatingBox && text.length() == 6) {
            this.colors[current()] = Integer.parseInt(text, 16);
            preview();
        }
    }

    private int sliderY(int c) {
        return this.top + 74 + c * 20;
    }

    private void dragTo(double mouseX) {
        if (this.dragging >= 0) {
            setChannel(this.dragging, (int) Math.round((mouseX - (this.left + TRACK_X)) / TRACK_W * 255.0));
        }
    }

    private int tabWidth() {
        return (W - 32 - 6 * (tabCount() - 1)) / Math.max(1, tabCount());
    }

    private int tabX(int i) {
        return this.left + 16 + i * (tabWidth() + 6);
    }

    private int presetX(int i) {
        return this.left + 16 + (i % 8) * 26;
    }

    private int presetY(int i) {
        return this.top + 160 + (i / 8) * 22;
    }

    private void applyAndClose() {
        int[] send = new int[VehiclePaint.CHANNELS];
        for (int ch = 0; ch < VehiclePaint.CHANNELS; ch++) {
            send[ch] = VehiclePaint.clean(this.vehicle.paintKind(), ch, this.colors[ch]);
            this.vehicle.setPaintRaw(ch, send[ch]);     // se ve de una vez; el servidor lo confirma enseguida
        }
        ModMessages.sendToServer(new VehiclePaintPacket(this.entity.getId(), send));
        this.onClose();
    }

    /** Pone TODOS los colores (también los de "Otros") como vinieron de fábrica. */
    private void restoreOriginals() {
        for (int ch = 0; ch < VehiclePaint.CHANNELS; ch++) {
            if (this.vehicle.paintKind().has(ch)) {
                this.colors[ch] = this.vehicle.paintKind().reference(ch);
            }
        }
        syncHex();
        preview();
    }

    // ------------------------------------------------------------------ teclado y ratón

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.listOpen && keyCode == 256) {
            setListOpen(false);         // Esc con la lista abierta solo la cierra
            return true;
        }
        if (!this.hexBox.isFocused() && GarageKeys.COLORS.matches(keyCode, scanCode)) {
            this.onClose();         // R otra vez cierra el menú
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < tabCount(); i++) {
                if (DarkUi.inside(mx, my, tabX(i), this.top + 36, tabWidth(), 22)) {
                    this.hexBox.setFocused(false);
                    if (isOthersTab(i)) {
                        if (this.listOpen) {
                            setListOpen(false);
                        } else {
                            setListOpen(true);
                        }
                    } else {
                        this.channel = this.channels[i];
                        setListOpen(false);
                        syncHex();
                    }
                    return true;
                }
            }
            if (this.listOpen) {
                int[] all = listChannels();
                for (int i = 0; i < all.length; i++) {
                    if (DarkUi.inside(mx, my, listX(i), listY(i), listColumnWidth(), LIST_ROW - 1)) {
                        this.channel = all[i];
                        setListOpen(false);
                        syncHex();
                        return true;
                    }
                }
                if (!DarkUi.inside(mx, my, this.left + 16, this.top + 210, W - 32, 20)) {
                    return true;        // con la lista abierta, lo de abajo (barras, paleta) no se toca
                }
            }
            for (int c = 0; c < 3; c++) {
                if (DarkUi.inside(mx, my, this.left + TRACK_X - 3, sliderY(c), TRACK_W + 6, 14)) {
                    this.dragging = c;
                    dragTo(mx);
                    return true;
                }
            }
            for (int i = 0; i < PRESETS.length; i++) {
                if (DarkUi.inside(mx, my, presetX(i), presetY(i), 18, 18)) {
                    setColor(PRESETS[i]);
                    return true;
                }
            }
            int bw = (W - 32 - 12) / 3;
            if (DarkUi.inside(mx, my, this.left + 16, this.top + 210, bw, 20)) {
                applyAndClose();
                return true;
            }
            if (DarkUi.inside(mx, my, this.left + 16 + bw + 6, this.top + 210, bw, 20)) {
                restoreOriginals();
                return true;
            }
            if (DarkUi.inside(mx, my, this.left + 16 + 2 * (bw + 6), this.top + 210, bw, 20)) {
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.dragging >= 0 && button == 0) {
            dragTo(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.dragging = -1;
        return super.mouseReleased(mx, my, button);
    }

    // ------------------------------------------------------------------ dibujo

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // Sin fondo oscuro a propósito: el vehículo tiene que verse con los colores que vas eligiendo.
        DarkUi.panel(gui, this.left, this.top, W, H, 30);
        gui.drawString(this.font, this.title, this.left + 16, this.top + 11, DarkUi.ACCENT);

        for (int i = 0; i < tabCount(); i++) {
            int x = tabX(i);
            int y = this.top + 36;
            boolean hover = DarkUi.inside(mouseX, mouseY, x, y, tabWidth(), 22);
            if (isOthersTab(i)) {
                DarkUi.button(gui, this.font, x + 8, y, tabWidth() - 8, 22, DarkUi.tr("gui.beautifuldecor.vehicle.others"),
                        hover, this.listOpen || this.channel >= VehiclePaint.EXTRA_FIRST);
                drawOthersSwatch(gui, x, y);
                continue;
            }
            int ch = this.channels[i];
            DarkUi.button(gui, this.font, x + 8, y, tabWidth() - 8, 22, DarkUi.tr(TAB_KEYS[ch]), hover,
                    !this.listOpen && ch == this.channel);
            gui.fill(x, y + 3, x + 14, y + 19, 0xFF000000);
            gui.fill(x + 1, y + 4, x + 13, y + 18, opaque(this.colors[ch]));
        }

        if (this.listOpen) {
            renderList(gui, mouseX, mouseY);
        } else {
            gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.vehicle.editing", channelName(this.channel)),
                    this.left + 16, this.top + 63, DarkUi.MUTED);
            renderEditor(gui, mouseX, mouseY);
        }

        int bw = (W - 32 - 12) / 3;
        DarkUi.button(gui, this.font, this.left + 16, this.top + 210, bw, 20, DarkUi.tr("gui.beautifuldecor.vehicle.apply"),
                DarkUi.inside(mouseX, mouseY, this.left + 16, this.top + 210, bw, 20), true);
        DarkUi.button(gui, this.font, this.left + 16 + bw + 6, this.top + 210, bw, 20,
                DarkUi.tr("gui.beautifuldecor.vehicle.original"),
                DarkUi.inside(mouseX, mouseY, this.left + 16 + bw + 6, this.top + 210, bw, 20), false);
        DarkUi.button(gui, this.font, this.left + 16 + 2 * (bw + 6), this.top + 210, bw, 20,
                DarkUi.tr("gui.beautifuldecor.vehicle.cancel"),
                DarkUi.inside(mouseX, mouseY, this.left + 16 + 2 * (bw + 6), this.top + 210, bw, 20), false);
        super.render(gui, mouseX, mouseY, partialTick);
    }

    /** Barras RGB, cuadro hexadecimal y paleta del canal que se está editando. */
    private void renderEditor(GuiGraphics gui, int mouseX, int mouseY) {
        for (int c = 0; c < 3; c++) {
            int y = sliderY(c);
            int trackX = this.left + TRACK_X;
            gui.drawString(this.font, DarkUi.tr(CHANNEL_KEYS[c]), this.left + 16, y + 3, DarkUi.TEXT);
            gui.fill(trackX - 1, y + 4, trackX + TRACK_W + 1, y + 10, DarkUi.BORDER);
            gui.fill(trackX, y + 5, trackX + TRACK_W, y + 9, DarkUi.BUTTON);
            int filled = (int) Math.round(channel(c) / 255.0 * TRACK_W);
            gui.fill(trackX, y + 5, trackX + filled, y + 9, CHANNEL_COLORS[c]);
            gui.fill(trackX + filled - 2, y + 1, trackX + filled + 2, y + 13, DarkUi.TEXT);
            gui.drawString(this.font, String.valueOf(channel(c)), trackX + TRACK_W + 8, y + 3, DarkUi.MUTED);
        }

        gui.drawString(this.font, "HEX #", this.left + 16, this.top + 138, DarkUi.MUTED);
        gui.fill(this.left + 114, this.top + 133, this.left + 132, this.top + 151, DarkUi.BORDER);
        gui.fill(this.left + 115, this.top + 134, this.left + 131, this.top + 150, opaque(this.colors[current()]));

        for (int i = 0; i < PRESETS.length; i++) {
            int x = presetX(i);
            int y = presetY(i);
            boolean selected = PRESETS[i] == (this.colors[current()] & 0xFFFFFF);
            gui.fill(x - 1, y - 1, x + 19, y + 19, selected ? DarkUi.ACCENT
                    : DarkUi.inside(mouseX, mouseY, x, y, 18, 18) ? DarkUi.TEXT : DarkUi.BORDER);
            gui.fill(x, y, x + 18, y + 18, opaque(PRESETS[i]));
        }
    }

    /** V3.6.0: la lista de TODOS los colores del vehículo, cada uno con su muestra y su nombre. */
    private void renderList(GuiGraphics gui, int mouseX, int mouseY) {
        int[] all = listChannels();
        int rows = (all.length + 1) / 2;
        gui.fill(this.left + 12, this.top + LIST_TOP, this.left + W - 12, this.top + LIST_TOP + rows * LIST_ROW + 8, DarkUi.BORDER);
        gui.fill(this.left + 13, this.top + LIST_TOP + 1, this.left + W - 13, this.top + LIST_TOP + rows * LIST_ROW + 7, DarkUi.HEADER);
        for (int i = 0; i < all.length; i++) {
            int ch = all[i];
            int x = listX(i);
            int y = listY(i);
            int w = listColumnWidth();
            boolean hover = DarkUi.inside(mouseX, mouseY, x, y, w, LIST_ROW - 1);
            gui.fill(x, y, x + w, y + LIST_ROW - 1, ch == this.channel ? DarkUi.ACCENT : DarkUi.BORDER);
            gui.fill(x + 1, y + 1, x + w - 1, y + LIST_ROW - 2,
                    ch == this.channel ? DarkUi.BUTTON_ACTIVE : hover ? DarkUi.BUTTON_HOVER : DarkUi.BUTTON);
            gui.fill(x + 3, y + 3, x + 14, y + LIST_ROW - 4, 0xFF000000);
            gui.fill(x + 4, y + 4, x + 13, y + LIST_ROW - 5, opaque(this.colors[ch]));
            String name = this.font.plainSubstrByWidth(channelName(ch), w - 22);
            gui.drawString(this.font, name, x + 18, y + 4, hover || ch == this.channel ? DarkUi.TEXT : 0xFFC9CFDA);
        }
    }

    /** La muestra del botón "Otros": el color de la parte elegida, o cuatro cuadritos si todavía no hay una. */
    private void drawOthersSwatch(GuiGraphics gui, int x, int y) {
        gui.fill(x, y + 3, x + 14, y + 19, 0xFF000000);
        if (this.channel >= VehiclePaint.EXTRA_FIRST) {
            gui.fill(x + 1, y + 4, x + 13, y + 18, opaque(this.colors[this.channel]));
            return;
        }
        int first = VehiclePaint.EXTRA_FIRST;
        int count = this.vehicle.paintKind().extraCount();
        for (int i = 0; i < 4; i++) {
            int ch = first + Math.min(i, count - 1);
            int qx = x + 1 + (i % 2) * 6;
            int qy = y + 4 + (i / 2) * 7;
            gui.fill(qx, qy, qx + 6, qy + 7, opaque(this.colors[ch]));
        }
    }
}
