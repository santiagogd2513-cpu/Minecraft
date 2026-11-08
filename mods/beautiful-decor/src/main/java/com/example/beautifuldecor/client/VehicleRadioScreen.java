package com.example.beautifuldecor.client;

import com.example.beautifuldecor.entity.AirlinerEntity;
import com.example.beautifuldecor.entity.HelicopterEntity;
import com.example.beautifuldecor.entity.VehicleEntity;
import com.example.beautifuldecor.network.ModMessages;
import com.example.beautifuldecor.network.VehicleRadioPacket;
import com.example.beautifuldecor.radio.RadioStations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * V3.5.0: menú de la radio de los vehículos terrestres (moto, bicicleta, camioneta y camión), en el modo oscuro de los demás menús del
 * mod. Se abre con la tecla X yendo montado. Arriba, lo que suena (emisora y canción, si la emisora la manda); debajo, encender/apagar y
 * el volumen; la lista de emisoras (las del mod y las tuyas, que se borran con la ✕); y abajo, para agregar una emisora tuya con su
 * nombre y su dirección (MP3 en vivo). Los cambios valen para todos los que van en el vehículo o están cerca.
 */
public class VehicleRadioScreen extends Screen {
    private static final int W = 264;
    private static final int ROW = 16;
    private static final int HEAD_ROW = 14;
    /** V4.0.1: ancho de la barra de desplazamiento de la derecha (en pantallas táctiles no hay rueda del ratón). */
    private static final int BAR_W = 6;

    private record Row(boolean header, String label, RadioStations.Station station, boolean custom) {
    }

    private final VehicleEntity vehicle;
    private final List<Row> rows = new ArrayList<>();
    private EditBox nameBox;
    private EditBox urlBox;
    private String nameText = "";
    private String urlText = "";
    private String feedback = "";
    private int feedbackColor = DarkUi.MUTED;
    private int left;
    private int top;
    private int height2;
    private int scroll;
    /** V4.0.1: se está arrastrando la barra de desplazamiento, y a qué altura de la barra se agarró. */
    private boolean draggingBar;
    private double dragOffset;
    /** Volumen pedido que todavía no llegó de vuelta del servidor (-1 = ninguno): así varios toques seguidos suman bien. */
    private int pendingVolume = -1;

    public VehicleRadioScreen(VehicleEntity vehicle) {
        super(Component.translatable("gui.beautifuldecor.radio.title"));
        this.vehicle = vehicle;
    }

    /** Tecla X: abre la radio si vas montado en un vehículo terrestre. En el helicóptero avisa que ahí no hay radio. */
    public static void tryOpen(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null || minecraft.getCameraEntity() != player) {
            return;
        }
        if (player.getVehicle() instanceof VehicleEntity vehicle) {
            minecraft.setScreen(new VehicleRadioScreen(vehicle));
        } else if (player.getVehicle() instanceof HelicopterEntity || player.getVehicle() instanceof AirlinerEntity) {
            player.displayClientMessage(Component.translatable("message.beautifuldecor.radio.land_only"), true);
        }
    }

    // ------------------------------------------------------------------ armado

    private void rebuildRows() {
        this.rows.clear();
        this.rows.add(new Row(true, DarkUi.tr("gui.beautifuldecor.radio.stations"), null, false));
        for (RadioStations.Station s : RadioStations.BUILT_IN) {
            this.rows.add(new Row(false, s.name(), s, false));
        }
        this.rows.add(new Row(true, DarkUi.tr("gui.beautifuldecor.radio.mine"), null, false));
        List<RadioStations.Station> mine = RadioFavorites.list();
        if (mine.isEmpty()) {
            this.rows.add(new Row(true, DarkUi.tr("gui.beautifuldecor.radio.mine_empty"), null, false));
        }
        for (RadioStations.Station s : mine) {
            this.rows.add(new Row(false, s.name(), s, true));
        }
        clampScroll();
    }

    @Override
    protected void init() {
        super.init();
        if (this.nameBox != null) {
            this.nameText = this.nameBox.getValue();
            this.urlText = this.urlBox.getValue();
        }
        this.height2 = Math.max(200, Math.min(252, this.height - 8));
        this.left = (this.width - W) / 2;
        this.top = Math.max(2, (this.height - this.height2) / 2);
        int y = this.top + this.height2 - 48;
        this.nameBox = new EditBox(this.font, this.left + 10, y, 72, 16, Component.translatable("gui.beautifuldecor.radio.name_hint"));
        this.nameBox.setMaxLength(RadioStations.MAX_NAME);
        this.nameBox.setValue(this.nameText);
        this.urlBox = new EditBox(this.font, this.left + 86, y, 122, 16, Component.translatable("gui.beautifuldecor.radio.url_hint"));
        this.urlBox.setMaxLength(RadioStations.MAX_URL);
        this.urlBox.setValue(this.urlText);
        this.addRenderableWidget(this.nameBox);
        this.addRenderableWidget(this.urlBox);
        rebuildRows();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        LocalPlayer player = this.minecraft == null ? null : this.minecraft.player;
        if (this.vehicle.isRemoved() || player == null || player.getVehicle() != this.vehicle) {
            this.onClose();     // te bajaste: la radio sigue (si alguien va a bordo), el menú se cierra
            return;
        }
        if (this.pendingVolume == this.vehicle.getRadioVolume()) {
            this.pendingVolume = -1;
        }
        this.nameBox.tick();
        this.urlBox.tick();
    }

    // ------------------------------------------------------------------ medidas

    private int listTop() {
        return this.top + 96;
    }

    private int listHeight() {
        return this.height2 - 96 - 56;
    }

    private int rowHeight(Row row) {
        return row.header() ? HEAD_ROW : ROW;
    }

    private int contentHeight() {
        int h = 0;
        for (Row r : this.rows) {
            h += rowHeight(r);
        }
        return h;
    }

    private void clampScroll() {
        int max = Math.max(0, contentHeight() - listHeight());
        this.scroll = Math.max(0, Math.min(this.scroll, max));
    }

    // ---- V4.0.1: barra de desplazamiento (se arrastra con el dedo o el ratón; un toque en la línea salta a ese punto)

    private boolean scrollable() {
        return contentHeight() > listHeight();
    }

    /** Borde derecho de las filas: con barra, se deja sitio para ella. */
    private int rowsRight() {
        return this.left + W - (scrollable() ? 16 : 6);
    }

    private int barX() {
        return this.left + W - 12;
    }

    private int barThumbH() {
        int lh = listHeight();
        return Math.max(18, lh * lh / Math.max(1, contentHeight()));
    }

    private int barThumbY() {
        int lh = listHeight();
        int max = Math.max(1, contentHeight() - lh);
        return listTop() + (lh - barThumbH()) * this.scroll / max;
    }

    /** Zona donde se agarra la barra: más ancha que la línea dibujada, para que sea fácil con el dedo. */
    private boolean overBar(double mx, double my) {
        return scrollable() && DarkUi.inside(mx, my, barX() - 4, listTop(), BAR_W + 8, listHeight());
    }

    /** Mueve la lista para que la barra quede donde está el puntero (menos el punto por donde se agarró). */
    private void scrollToPointer(double my) {
        int lh = listHeight();
        int span = Math.max(1, lh - barThumbH());
        double t = (my - this.dragOffset - listTop()) / span;
        this.scroll = (int) Math.round(t * (contentHeight() - lh));
        clampScroll();
    }

    private int powerX() {
        return this.left + 10;
    }

    private int controlsY() {
        return this.top + 72;
    }

    private int minusX() {
        return this.left + W - 96;
    }

    private int plusX() {
        return this.left + W - 30;
    }

    private int addX() {
        return this.left + 212;
    }

    // ------------------------------------------------------------------ acciones

    private void send(VehicleRadioPacket packet) {
        ModMessages.sendToServer(packet);
    }

    private void tell(String key, int color, Object... args) {
        this.feedback = DarkUi.tr(key, args);
        this.feedbackColor = color;
    }

    private int shownVolume() {
        return this.pendingVolume >= 0 ? this.pendingVolume : this.vehicle.getRadioVolume();
    }

    private void changeVolume(int delta) {
        this.pendingVolume = RadioStations.clampVolume(shownVolume() + delta);
        send(VehicleRadioPacket.volume(this.vehicle.getId(), this.pendingVolume));
    }

    private void addStation() {
        String url = RadioStations.cleanUrl(this.urlBox.getValue());
        if (url == null) {
            tell("gui.beautifuldecor.radio.bad_url", DarkUi.WARN);
            return;
        }
        if (RadioStations.containsUrl(RadioFavorites.list(), url) || RadioStations.containsUrl(RadioStations.BUILT_IN, url)) {
            tell("gui.beautifuldecor.radio.already", DarkUi.WARN);
            return;
        }
        if (!RadioFavorites.add(this.nameBox.getValue(), url)) {
            tell("gui.beautifuldecor.radio.full", DarkUi.WARN, RadioStations.MAX_CUSTOM);
            return;
        }
        String name = RadioStations.cleanName(this.nameBox.getValue());
        send(VehicleRadioPacket.select(this.vehicle.getId(), url, name.isEmpty() ? RadioStations.nameFromUrl(url) : name));
        this.nameBox.setValue("");
        this.urlBox.setValue("");
        tell("gui.beautifuldecor.radio.added", DarkUi.OK);
        rebuildRows();
        this.scroll = Integer.MAX_VALUE;     // que se vea la nueva, al final
        clampScroll();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean typing = this.nameBox.isFocused() || this.urlBox.isFocused();
        if (!typing && VehicleKeys.RADIO.matches(keyCode, scanCode)) {
            this.onClose();         // X otra vez cierra el menú
            return true;
        }
        if (typing && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            addStation();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (DarkUi.inside(mx, my, this.left, listTop(), W, listHeight())) {
            this.scroll -= (int) (delta * ROW);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && overBar(mx, my)) {
            int thumbY = barThumbY();
            this.dragOffset = my >= thumbY && my < thumbY + barThumbH() ? my - thumbY : barThumbH() / 2.0;
            this.draggingBar = true;
            scrollToPointer(my);
            return true;
        }
        if (button == 0) {
            int cy = controlsY();
            if (DarkUi.inside(mx, my, powerX(), cy, 84, 18)) {
                if (this.vehicle.getRadioUrl().isEmpty()) {
                    tell("gui.beautifuldecor.radio.no_station", DarkUi.WARN);
                } else {
                    send(VehicleRadioPacket.power(this.vehicle.getId(), !this.vehicle.isRadioOn()));
                }
                return true;
            }
            if (DarkUi.inside(mx, my, minusX(), cy, 20, 18)) {
                changeVolume(-RadioStations.VOLUME_STEP);
                return true;
            }
            if (DarkUi.inside(mx, my, plusX(), cy, 20, 18)) {
                changeVolume(RadioStations.VOLUME_STEP);
                return true;
            }
            if (DarkUi.inside(mx, my, addX(), this.top + this.height2 - 48, 44, 16)) {
                addStation();
                return true;
            }
            if (DarkUi.inside(mx, my, this.left + 6, listTop(), rowsRight() - this.left - 6, listHeight())) {
                int y = listTop() - this.scroll;
                for (Row row : this.rows) {
                    int h = rowHeight(row);
                    if (my >= y && my < y + h && !row.header()) {
                        if (row.custom() && mx >= rowsRight() - 18) {
                            RadioFavorites.remove(row.station().url());
                            tell("gui.beautifuldecor.radio.removed", DarkUi.MUTED);
                            rebuildRows();
                        } else {
                            send(VehicleRadioPacket.select(this.vehicle.getId(), row.station().url(), row.station().name()));
                            this.feedback = "";
                        }
                        return true;
                    }
                    y += h;
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.draggingBar && button == 0) {
            scrollToPointer(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (this.draggingBar && button == 0) {
            this.draggingBar = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    // ------------------------------------------------------------------ dibujo

    /** Las dos líneas de arriba: qué emisora y qué suena (o en qué estado está). */
    private String[] status() {
        boolean on = this.vehicle.isRadioOn();
        String url = this.vehicle.getRadioUrl();
        if (url.isEmpty()) {
            return new String[]{DarkUi.tr("gui.beautifuldecor.radio.no_station"), ""};
        }
        String name = this.vehicle.getRadioName().isEmpty() ? RadioStations.nameFromUrl(url) : this.vehicle.getRadioName();
        if (!on) {
            return new String[]{name, DarkUi.tr("gui.beautifuldecor.radio.off")};
        }
        HsAudioPlayer p = VehicleRadioClient.playerFor(this.vehicle);
        if (p == null || p.state() == HsAudioPlayer.LOADING || p.state() == HsAudioPlayer.IDLE) {
            return new String[]{name, DarkUi.tr("gui.beautifuldecor.radio.connecting")};
        }
        if (p.state() == HsAudioPlayer.ERROR) {
            return new String[]{name, p.message()};
        }
        String song = p.nowPlaying();
        return new String[]{name, song.isEmpty() ? DarkUi.tr("gui.beautifuldecor.radio.playing") : "♪ " + song};
    }

    private String fit(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, width - this.font.width("…")) + "…";
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, this.width, this.height, DarkUi.DIM);
        DarkUi.panel(gui, this.left, this.top, W, this.height2, 30);
        gui.drawString(this.font, this.title, this.left + 14, this.top + 8, DarkUi.ACCENT);
        gui.drawString(this.font, fit(this.vehicle.getName().getString(), W - 28), this.left + 14, this.top + 19, DarkUi.MUTED);

        // lo que suena
        String[] st = status();
        int sy = this.top + 36;
        gui.fill(this.left + 10, sy, this.left + W - 10, sy + 30, DarkUi.BUTTON);
        gui.fill(this.left + 10, sy, this.left + 13, sy + 30, this.vehicle.radioShouldPlay() ? DarkUi.OK : DarkUi.BORDER);
        gui.drawString(this.font, fit(st[0], W - 34), this.left + 18, sy + 5, DarkUi.TEXT);
        boolean error = VehicleRadioClient.playerFor(this.vehicle) != null
                && VehicleRadioClient.playerFor(this.vehicle).state() == HsAudioPlayer.ERROR && this.vehicle.isRadioOn();
        gui.drawString(this.font, fit(st[1], W - 34), this.left + 18, sy + 17, error ? DarkUi.WARN : DarkUi.ACCENT);

        // encender / apagar y volumen
        int cy = controlsY();
        boolean on = this.vehicle.isRadioOn();
        DarkUi.button(gui, this.font, powerX(), cy, 84, 18, DarkUi.tr(on ? "gui.beautifuldecor.radio.turn_off" : "gui.beautifuldecor.radio.turn_on"),
                DarkUi.inside(mouseX, mouseY, powerX(), cy, 84, 18), on);
        gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.radio.volume"), this.left + 104, cy + 5, DarkUi.MUTED);
        DarkUi.button(gui, this.font, minusX(), cy, 20, 18, "-", DarkUi.inside(mouseX, mouseY, minusX(), cy, 20, 18), false);
        DarkUi.button(gui, this.font, plusX(), cy, 20, 18, "+", DarkUi.inside(mouseX, mouseY, plusX(), cy, 20, 18), false);
        String vol = shownVolume() + "%";
        int mid = (minusX() + 20 + plusX()) / 2;
        gui.drawString(this.font, vol, mid - this.font.width(vol) / 2, cy + 5, DarkUi.TEXT);

        // emisoras
        int lt = listTop();
        int lh = listHeight();
        gui.enableScissor(this.left + 1, lt, this.left + W - 1, lt + lh);
        int y = lt - this.scroll;
        String current = this.vehicle.getRadioUrl();
        for (Row row : this.rows) {
            int h = rowHeight(row);
            if (y + h >= lt && y <= lt + lh) {
                if (row.header()) {
                    gui.drawString(this.font, row.label(), this.left + 12, y + 4, DarkUi.MUTED);
                } else {
                    boolean hover = DarkUi.inside(mouseX, mouseY, this.left + 6, y, rowsRight() - this.left - 6, h - 1) && DarkUi.inside(mouseX, mouseY, this.left, lt, W, lh);
                    boolean selected = row.station().url().equalsIgnoreCase(current);
                    gui.fill(this.left + 6, y, rowsRight(), y + h - 1, selected ? DarkUi.BUTTON_ACTIVE : hover ? DarkUi.BUTTON_HOVER : DarkUi.BUTTON);
                    gui.fill(this.left + 6, y, this.left + 9, y + h - 1, selected ? (on ? DarkUi.OK : DarkUi.ACCENT) : DarkUi.BORDER);
                    String right = row.custom() ? "✕" : row.station().genre();
                    int rightW = this.font.width(right);
                    gui.drawString(this.font, fit(row.label(), rowsRight() - this.left - 34 - rightW), this.left + 14, y + 4, DarkUi.TEXT);
                    boolean overX = row.custom() && hover && mouseX >= rowsRight() - 18;
                    gui.drawString(this.font, right, rowsRight() - 6 - rightW, y + 4, row.custom() ? (overX ? DarkUi.WARN : DarkUi.MUTED) : DarkUi.MUTED);
                }
            }
            y += h;
        }
        gui.disableScissor();
        if (scrollable()) {
            // la línea (pista) a la derecha de la lista y, encima, el tirador que se arrastra
            gui.fill(barX() + 2, lt, barX() + 4, lt + lh, DarkUi.BORDER);
            boolean grab = this.draggingBar || (overBar(mouseX, mouseY) && mouseY >= barThumbY() && mouseY < barThumbY() + barThumbH());
            gui.fill(barX(), barThumbY(), barX() + BAR_W, barThumbY() + barThumbH(), grab ? DarkUi.ACCENT : DarkUi.MUTED);
        }

        // agregar una emisora propia
        int ay = this.top + this.height2 - 48;
        gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.radio.add_title"), this.left + 10, ay - 11, DarkUi.MUTED);
        DarkUi.button(gui, this.font, addX(), ay, 44, 16, DarkUi.tr("gui.beautifuldecor.radio.add"), DarkUi.inside(mouseX, mouseY, addX(), ay, 44, 16), false);
        super.render(gui, mouseX, mouseY, partialTick);
        if (this.nameBox.getValue().isEmpty() && !this.nameBox.isFocused()) {
            gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.radio.name_hint"), this.left + 14, ay + 4, 0xFF6B7280);
        }
        if (this.urlBox.getValue().isEmpty() && !this.urlBox.isFocused()) {
            gui.drawString(this.font, DarkUi.tr("gui.beautifuldecor.radio.url_hint"), this.left + 90, ay + 4, 0xFF6B7280);
        }
        if (!this.feedback.isEmpty()) {
            gui.drawString(this.font, fit(this.feedback, W - 20), this.left + 10, ay + 22, this.feedbackColor);
        } else {
            gui.drawString(this.font, fit(DarkUi.tr("gui.beautifuldecor.radio.hint"), W - 20), this.left + 10, ay + 22, 0xFF6B7280);
        }
    }
}
