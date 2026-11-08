package com.example.beautifuldecor.client;

import com.example.beautifuldecor.block.HsComputers;
import com.example.beautifuldecor.browser.BrowserSession;
import com.example.beautifuldecor.browser.Css;
import com.example.beautifuldecor.browser.Downloads;
import com.example.beautifuldecor.browser.HtmlParser;
import com.example.beautifuldecor.browser.InternalPages;
import com.example.beautifuldecor.browser.Layout;
import com.example.beautifuldecor.browser.Node;
import com.example.beautifuldecor.browser.Page;
import com.example.beautifuldecor.browser.PageRuntime;
import com.example.beautifuldecor.browser.ScrollAnchor;
import com.example.beautifuldecor.browser.Url;
import com.example.beautifuldecor.browser.js.DomHost;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Navegador del computador HS (V2.4.0), siempre en modo oscuro. Se abre con la tecla R apuntando a un computador HS
 * encendido. Carga páginas HTML con CSS básico (sin JavaScript), baja archivos a la carpeta "HS Downloads" y reproduce audio
 * directo (MP3, OGG, WAV). Todo con código propio del mod: no usa Chromium. Los colores de las páginas se ignoran
 * a propósito para que ninguna pueda deslumbrar de noche.
 */
public class HsBrowserScreen extends Screen {
    private static final int PAD = 6;
    private static final int HEADER_H = 13;
    private static final int TOOL_H = 20;
    private static final int STATUS_H = 11;
    private static final int AUDIO_H = 17;
    private static final int BAR_W = 7;
    private static final int[] ZOOMS = {50, 60, 70, 80, 90, 100, 110, 125, 150, 175, 200};

    private static final int BG = 0xFF101216;
    private static final int C_TEXT = 0xFFD7DAE0;
    private static final int C_HEADING = 0xFFF0F2F6;
    private static final int C_LINK = 0xFF6CB6FF;
    private static final int C_LINK_HOVER = 0xFFA8D2FF;
    private static final int C_MUTED = 0xFF8D95A3;
    private static final int C_CODE = 0xFF9FD8A8;
    private static final int C_MARK = 0xFFE5C07B;
    private static final int C_ERROR = 0xFFF87171;
    private static final int C_CODE_BG = 0xFF171A20;
    private static final int C_QUOTE = 0xFF3A4150;
    private static final int C_FIELD = 0xFF0B0D10;
    private static final int C_EDGE = 0xFF3A4150;

    private static final int B_BACK = 0;
    private static final int B_FORWARD = 1;
    private static final int B_RELOAD = 2;
    private static final int B_HOME = 3;
    private static final int B_DOWNLOADS = 4;
    private static final int B_ZOOM_OUT = 5;
    private static final int B_ZOOM_IN = 6;
    private static final int B_CLOSE = 7;

    // lo que sobrevive entre una vez y otra que se abre el navegador
    private static BrowserSession session;
    private static int zoom = 100;
    private static volatile BlockPos anchorPos;
    private static volatile ResourceKey<Level> anchorDim;

    private static final BrowserSession.Audio HOST = new BrowserSession.Audio() {
        @Override
        public void playUrl(String url, String title) {
            stopVideo();
            HsAudioPlayer.get().play(url, null, title, anchorPos, anchorDim);
        }

        @Override
        public void playFile(Path file) {
            stopVideo();
            HsAudioPlayer.get().play(null, file, file.getFileName().toString(), anchorPos, anchorDim);
        }

        @Override
        public void playVideoUrl(String url, String title) {
            HsVideoPlayer.get().play(url, null, title, anchorPos, anchorDim);
        }

        @Override
        public void playVideoFile(Path file) {
            HsVideoPlayer.get().play(null, file, file.getFileName().toString(), anchorPos, anchorDim);
        }
    };

    private static void stopVideo() {
        if (HsVideoPlayer.get().active()) {
            HsVideoPlayer.get().stop();
        }
    }

    private record Btn(int id, int x, int y, int w, int h, String label, String tip) {
    }

    private final BlockPos computer;
    private final PageImages images = new PageImages();
    private final List<Btn> buttons = new ArrayList<>();
    private EditBox address;
    private int addrX;
    private int addrY;
    private int addrW;
    private int addrH;
    private int zoomLabelX = -1;

    private Page page;
    private int layoutVersion = -1;
    private int layoutWidth = -1;
    private int layoutZoom = -1;
    private int layoutImages = -1;
    private long lastLayoutMs;
    private long lastLayoutCost;
    private long stickUntil;
    private int lastNav = -1;
    private float scroll;
    private String pendingAnchor;
    private String hoverHref;
    private String lastShownUrl = "";

    private boolean pressed;
    private boolean dragged;
    private double pressX;
    private double pressY;
    private float pressScroll;
    private int barGrab = -1;
    private boolean volumeDrag;

    // V2.8.0: páginas con scripts
    private PageRuntime.SelectInfo popup;
    private int popupX;
    private int popupY;
    private int popupW;
    private int popupFirst;
    private Node focusNode;
    private boolean focusText;
    private int lastMoveX = Integer.MIN_VALUE;
    private int lastMoveY = Integer.MIN_VALUE;
    private int lastVpW = -1;
    private int lastVpH = -1;
    private int lastVpY = -1;

    public HsBrowserScreen(BlockPos computer) {
        super(Component.translatable("gui.beautifuldecor.browser.title"));
        this.computer = computer;
    }

    /** Abre el navegador para ese computador (el cliente ya comprobó que está encendido). */
    public static void open(Minecraft minecraft, BlockPos computer) {
        Downloads.init(minecraft.gameDirectory.toPath());
        InternalPages.spanish = "es".equals(Component.translatable("gui.beautifuldecor.browser.lang").getString());
        anchorPos = computer;
        anchorDim = minecraft.level.dimension();
        if (session == null) {
            session = new BrowserSession(HOST);
        }
        minecraft.setScreen(new HsBrowserScreen(computer));
    }

    /** Al salir del mundo: se olvida el historial y se calla el audio. */
    public static void reset() {
        if (session != null) {
            session.stop();
            session = null;
        }
        HsVideoPlayer.get().stop();
        HsVideoPlayer.get().update();
        HsAudioPlayer.get().stop();
        anchorPos = null;
        anchorDim = null;
    }

    // ------------------------------------------------------------------ medidas

    private final Layout.Metrics metrics = new Layout.Metrics() {
        @Override
        public int width(String text, int flags, int scale) {
            int w = flags == 0 ? HsBrowserScreen.this.font.width(text) : HsBrowserScreen.this.font.width(styled(text, flags));
            return Math.max(0, Math.round(w * scale / 100f));
        }

        @Override
        public int lineHeight(int scale) {
            return Math.round(9 * scale / 100f) + 2;
        }
    };

    private static Component styled(String text, int flags) {
        Style s = Style.EMPTY;
        if ((flags & Page.BOLD) != 0) {
            s = s.withBold(true);
        }
        if ((flags & Page.ITALIC) != 0) {
            s = s.withItalic(true);
        }
        if ((flags & Page.UNDERLINE) != 0) {
            s = s.withUnderlined(true);
        }
        if ((flags & Page.STRIKE) != 0) {
            s = s.withStrikethrough(true);
        }
        return Component.literal(text).setStyle(s);
    }

    private int left() {
        return PAD;
    }

    private int top() {
        return PAD;
    }

    private int panelW() {
        return this.width - 2 * PAD;
    }

    private int panelH() {
        return this.height - 2 * PAD;
    }

    private int toolY() {
        return top() + HEADER_H + 1;
    }

    private int viewTop() {
        return toolY() + TOOL_H + 1;
    }

    private int audioHeight() {
        return HsAudioPlayer.get().state() != HsAudioPlayer.IDLE ? AUDIO_H : 0;
    }

    private int statusY() {
        return top() + panelH() - STATUS_H;
    }

    private int audioY() {
        return statusY() - audioHeight();
    }

    private int viewHeight() {
        return Math.max(20, audioY() - viewTop());
    }

    private int viewWidth() {
        return panelW() - BAR_W;
    }

    private int pageHeight() {
        return this.page == null ? 0 : this.page.height;
    }

    private float maxScroll() {
        return Math.max(0, pageHeight() - viewHeight());
    }

    private void clampScroll() {
        this.scroll = Math.max(0f, Math.min(maxScroll(), this.scroll));
    }

    // ------------------------------------------------------------------ construcción

    @Override
    protected void init() {
        super.init();
        session.setPaused(false);
        this.buttons.clear();
        int x = left() + 4;
        int y = toolY() + 2;
        int bh = TOOL_H - 4;
        addButton(B_BACK, x, y, 16, bh, "←", "back");
        x += 18;
        addButton(B_FORWARD, x, y, 16, bh, "→", "forward");
        x += 18;
        addButton(B_RELOAD, x, y, 16, bh, "↻", "reload");
        x += 18;
        addButton(B_HOME, x, y, 16, bh, "⌂", "home");
        x += 18;
        int rx = left() + panelW() - 4;
        rx -= 16;
        addButton(B_DOWNLOADS, rx, y, 16, bh, "↓", "downloads");
        this.zoomLabelX = -1;
        if (panelW() >= 330) {
            rx -= 18;
            addButton(B_ZOOM_IN, rx, y, 16, bh, "+", "zoom_in");
            rx -= 30;
            this.zoomLabelX = rx;
            rx -= 18;
            addButton(B_ZOOM_OUT, rx, y, 16, bh, "-", "zoom_out");
        }
        addButton(B_CLOSE, left() + panelW() - 14, top() + 1, 12, HEADER_H - 2, "x", "close");
        this.addrX = x + 2;
        this.addrY = y;
        this.addrW = Math.max(60, rx - 4 - this.addrX);
        this.addrH = bh;
        String keep = this.address == null ? null : this.address.getValue();
        this.address = new EditBox(this.font, this.addrX + 4, this.addrY + 4, this.addrW - 8, 10, Component.empty());
        this.address.setMaxLength(2048);
        this.address.setBordered(false);
        this.address.setTextColor(DarkUi.TEXT);
        this.address.setValue(keep != null ? keep : shownUrl());
        this.addRenderableWidget(this.address);
        this.layoutWidth = -1;
    }

    private void addButton(int id, int x, int y, int w, int h, String label, String tipKey) {
        this.buttons.add(new Btn(id, x, y, w, h, label, "gui.beautifuldecor.browser." + tipKey));
    }

    private static String shownUrl() {
        String u = session == null ? "" : session.url();
        return Url.HOME.equals(u) ? "" : u;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        session.tick();
        Minecraft mc = this.minecraft;
        if (mc == null || mc.level == null || mc.player == null) {
            return;
        }
        if (!HsComputers.isOn(mc.level.getBlockState(this.computer))
                || mc.player.distanceToSqr(Vec3.atCenterOf(this.computer)) > 100.0) {
            onClose();
        }
    }

    @Override
    public void removed() {
        HsVideoPlayer.get().stop();                 // sin pantalla no hay dónde ver el video: se para con su sonido
        HsVideoPlayer.get().update();
        session.setPaused(true);                        // sin pantalla, los temporizadores de la página esperan
        this.images.close();
        super.removed();
    }

    // ------------------------------------------------------------------ acomodar la página

    private void relayout(int width, boolean keepFraction, boolean anchor) {
        float frac = keepFraction && this.page != null && this.page.height > 0 ? this.scroll / this.page.height : -1f;
        ScrollAnchor keep = anchor && frac < 0 ? ScrollAnchor.capture(this.page, this.scroll) : null;
        this.layoutVersion = session.version();
        int domVersion = Node.mutations();
        Node doc = session.doc();
        this.layoutWidth = width;
        this.layoutZoom = zoom;
        this.layoutImages = this.images.version();
        long t0 = System.currentTimeMillis();
        this.lastLayoutMs = t0;
        Page np;
        try {
            np = Layout.layout(doc, session.baseUrl(), session.sheet(), width, this.metrics, this.images, zoom);
        } catch (Throwable t) {
            np = fallbackPage(width);
        }
        this.lastLayoutCost = System.currentTimeMillis() - t0;
        np.domVersion = domVersion;
        this.page = np;
        session.publishLayout(np);
        this.images.request(np.imageSrcs, session.baseUrl());
        if (frac >= 0) {
            this.scroll = frac * np.height;
        } else if (keep != null) {
            float restored = keep.restore(np);       // lo que se estaba leyendo se queda donde estaba
            if (restored >= 0) {
                this.scroll = restored;
            }
        }
        clampScroll();
        if (anchor && System.currentTimeMillis() < this.stickUntil) {
            this.scroll = maxScroll();               // un script pidió ir al fondo y la página creció: se vuelve al fondo
        }
    }

    private Page fallbackPage(int width) {
        try {
            Node d = HtmlParser.parse(InternalPages.error(session.url(),
                    Component.translatable("gui.beautifuldecor.browser.render_error").getString()));
            return Layout.layout(d, Url.HOME, new Css.Sheet(), width, this.metrics, this.images, zoom);
        } catch (Throwable t) {
            return new Page();
        }
    }

    /** La parte viva de la página mostrada, o null si no tiene scripts (o ya se cerró). */
    private PageRuntime live() {
        PageRuntime r = session.runtime();
        return r == null || r.closed() ? null : r;
    }

    private void syncPage() {
        PageRuntime r = live();
        if (r != null) {
            if (!r.tryLock()) {
                syncView(r);                            // la página está ocupada: se sigue mostrando la anterior un cuadro más
                return;
            }
            try {
                syncLocked();
            } finally {
                r.unlock();
            }
        } else {
            syncLocked();
        }
        syncView(r);
    }

    /** Lo que los scripts ven de la ventana (tamaño y desplazamiento) y lo que piden (scrollTo). */
    private void syncView(PageRuntime r) {
        int vw = viewWidth();
        int vh = viewHeight();
        int sy = (int) this.scroll;
        if (vw != this.lastVpW || vh != this.lastVpH || sy != this.lastVpY) {
            this.lastVpW = vw;
            this.lastVpH = vh;
            this.lastVpY = sy;
            session.setViewport(vw, vh, 0, sy);
        }
        int req = session.takeScrollRequest();
        if (req >= 0) {
            this.stickUntil = req > 0 && req >= maxScroll() - 1 ? System.currentTimeMillis() + 1000 : 0;
            this.scroll = req;
            clampScroll();
        }
        this.focusNode = r == null ? null : r.focusedNode();
        this.focusText = r != null && r.textFocused();
    }

    private void syncLocked() {
        int width = viewWidth();
        int nav = session.navigation();
        boolean navigated = nav != this.lastNav;
        if (navigated) {
            this.lastNav = nav;
            this.images.clear();
            this.popup = null;
            if (!session.url().startsWith("hs:video")) {
                stopVideo();
            }
            int target = session.takeScrollTarget();
            this.stickUntil = 0;
            relayout(width, false, false);
            this.scroll = Math.max(0, target);
            clampScroll();
            updateAddress();
            this.lastShownUrl = session.url();
        } else {
            long now = System.currentTimeMillis();
            boolean imagesChanged = this.images.version() != this.layoutImages && now - this.lastLayoutMs > 250;
            boolean sizeChanged = width != this.layoutWidth || zoom != this.layoutZoom;
            boolean versionChanged = session.version() != this.layoutVersion;
            long gap = Math.max(40, 3 * this.lastLayoutCost);
            if (versionChanged && session.runtime() != null && now - this.lastLayoutMs < gap && !sizeChanged) {
                versionChanged = false;                 // una página con scripts puede cambiar a cada rato: se acomoda de a poco (más espacio si cuesta)
            }
            if (this.page == null || versionChanged || sizeChanged || imagesChanged) {
                boolean keepFraction = sizeChanged && !imagesChanged && session.version() == this.layoutVersion;
                relayout(width, keepFraction, !keepFraction && this.page != null);
            }
            if (!session.url().equals(this.lastShownUrl)) {
                this.lastShownUrl = session.url();      // un script cambió la dirección (pushState): se actualiza la barra
                updateAddress();
            }
        }
        String anchor = session.takeAnchor();
        if (anchor != null) {
            this.pendingAnchor = anchor;
        }
        if (this.pendingAnchor != null) {
            if (this.page != null) {
                Integer y = this.page.anchors.get(this.pendingAnchor);
                if (y != null) {
                    this.stickUntil = 0;
                    this.scroll = y;
                    clampScroll();
                }
            }
            this.pendingAnchor = null;
        }
    }

    private void updateAddress() {
        if (this.address != null && !this.address.isFocused()) {
            this.address.setValue(shownUrl());
            this.address.moveCursorToStart();
        }
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    public void render(GuiGraphics gui, int mx, int my, float partial) {
        HsVideoPlayer.get().update();
        syncPage();
        HsAudioPlayer audio = HsAudioPlayer.get();
        pumpImagesAndHover(mx, my);

        gui.fill(0, 0, this.width, this.height, DarkUi.DIM);
        DarkUi.panel(gui, left(), top(), panelW(), panelH(), 0);

        // franja de título
        gui.fill(left(), top(), left() + panelW(), top() + HEADER_H, DarkUi.HEADER);
        gui.fill(left(), top() + HEADER_H, left() + panelW(), top() + HEADER_H + 1, DarkUi.BORDER);
        String title = session.title();
        if (title == null || title.isEmpty()) {
            title = "HS Browser";
        }
        gui.drawString(this.font, this.font.plainSubstrByWidth(title + "  —  HS Browser", panelW() - 36), left() + 5, top() + 3,
                DarkUi.TEXT, false);

        // barra de herramientas
        gui.fill(left(), toolY(), left() + panelW(), toolY() + TOOL_H, DarkUi.PANEL);
        gui.fill(left(), toolY() + TOOL_H, left() + panelW(), toolY() + TOOL_H + 1, DarkUi.BORDER);
        String tip = null;
        for (Btn b : this.buttons) {
            boolean hover = DarkUi.inside(mx, my, b.x(), b.y(), b.w(), b.h());
            boolean enabled = enabled(b.id());
            String label = b.id() == B_RELOAD && session.loading() ? "✕" : b.label();
            if (enabled) {
                DarkUi.button(gui, this.font, b.x(), b.y(), b.w(), b.h(), label, hover, false);
            } else {
                gui.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), DarkUi.BORDER);
                gui.fill(b.x() + 1, b.y() + 1, b.x() + b.w() - 1, b.y() + b.h() - 1, DarkUi.PANEL);
                gui.drawString(this.font, label, b.x() + (b.w() - this.font.width(label)) / 2, b.y() + (b.h() - 8) / 2 + 1, 0xFF4A515E, false);
            }
            if (hover && enabled) {
                tip = b.id() == B_RELOAD && session.loading() ? "gui.beautifuldecor.browser.stop" : b.tip();
            }
        }
        if (this.zoomLabelX >= 0) {
            String z = zoom + "%";
            gui.drawString(this.font, z, this.zoomLabelX + (28 - this.font.width(z)) / 2, toolY() + 7, DarkUi.MUTED, false);
        }
        gui.fill(this.addrX - 1, this.addrY - 1, this.addrX + this.addrW + 1, this.addrY + this.addrH + 1,
                this.address != null && this.address.isFocused() ? DarkUi.ACCENT : DarkUi.BORDER);
        gui.fill(this.addrX, this.addrY, this.addrX + this.addrW, this.addrY + this.addrH, C_FIELD);
        if (this.address != null && this.address.getValue().isEmpty() && !this.address.isFocused()) {
            gui.drawString(this.font, this.font.plainSubstrByWidth(DarkUi.tr("gui.beautifuldecor.browser.address"), this.addrW - 10),
                    this.addrX + 5, this.addrY + 5, DarkUi.MUTED, false);
        }
        if (session.loading()) {
            int barW = 60;
            int bx = left() + (int) ((System.currentTimeMillis() / 5) % (panelW() + barW)) - barW;
            int x0 = Math.max(left(), bx);
            int x1 = Math.min(left() + panelW(), bx + barW);
            if (x1 > x0) {
                gui.fill(x0, toolY() + TOOL_H - 1, x1, toolY() + TOOL_H + 1, DarkUi.ACCENT);
            }
        }

        drawContent(gui);
        drawPopup(gui, mx, my);
        if (audioHeight() > 0) {
            drawAudioBar(gui, mx, my, audio);
        }
        drawStatus(gui);

        super.render(gui, mx, my, partial);
        if (tip != null) {
            gui.renderTooltip(this.font, Component.translatable(tip), mx, my);
        }
    }

    private boolean enabled(int id) {
        return switch (id) {
            case B_BACK -> session.canBack();
            case B_FORWARD -> session.canForward();
            default -> true;
        };
    }

    private void pumpImagesAndHover(int mx, int my) {
        this.images.pump();
        Page.Item hit = null;
        if (inContent(mx, my) && this.barGrab < 0) {
            hit = itemAt(mx - left(), my - viewTop() + (int) this.scroll);
        }
        this.hoverHref = hit == null ? null : hit.href;
        PageRuntime r = live();
        if (r != null && r.wantsMoves() && this.page != null && this.popup == null) {
            int px = mx - left();
            int cy = my - viewTop();
            if (inContent(mx, my) && (px != this.lastMoveX || cy != this.lastMoveY)) {
                this.lastMoveX = px;
                this.lastMoveY = cy;
                r.mouseMove(this.page.nodeAt(px, cy + (int) this.scroll), px, cy, 0);
            }
        }
    }

    private boolean inContent(double mx, double my) {
        return DarkUi.inside(mx, my, left(), viewTop(), viewWidth(), viewHeight());
    }

    private Page.Item itemAt(int px, int py) {
        if (this.page == null) {
            return null;
        }
        List<Page.Item> items = this.page.items;
        for (int i = items.size() - 1; i >= 0; i--) {
            Page.Item it = items.get(i);
            if (it.href != null && it.kind != Page.RECT && it.contains(px, py)) {
                return it;
            }
        }
        return null;
    }

    private static int colorOf(int role, boolean hover) {
        return switch (role) {
            case Page.ROLE_HEADING -> C_HEADING;
            case Page.ROLE_LINK -> hover ? C_LINK_HOVER : C_LINK;
            case Page.ROLE_MUTED -> C_MUTED;
            case Page.ROLE_CODE -> C_CODE;
            case Page.ROLE_MARK -> C_MARK;
            default -> C_TEXT;
        };
    }

    private void drawContent(GuiGraphics gui) {
        int vx = left();
        int vy = viewTop();
        int vw = viewWidth();
        int vh = viewHeight();
        gui.fill(vx, vy, vx + panelW(), vy + vh, BG);
        if (videoShown()) {
            drawVideo(gui, vx, vy, vw, vh);
            return;
        }
        if (this.page == null) {
            return;
        }
        int sy = (int) this.scroll;
        gui.enableScissor(vx, vy, vx + vw, vy + vh);
        try {
            for (Page.Item it : this.page.items) {
                if (it.y + it.h < sy || it.y > sy + vh || it.x + it.w < 0 || it.x > vw) {
                    continue;
                }
                int x = vx + it.x;
                int y = vy + it.y - sy;
                boolean hover = it.href != null && it.href.equals(this.hoverHref);
                switch (it.kind) {
                    case Page.TEXT -> drawText(gui, it, x, y, hover);
                    case Page.RECT -> drawRect(gui, it, x, y);
                    case Page.IMAGE -> drawImage(gui, it, x, y, hover);
                    case Page.CONTROL -> drawControl(gui, it, x, y, hover);
                    default -> { }
                }
            }
        } catch (RuntimeException e) {
            this.page = fallbackPage(vw);
        } finally {
            gui.disableScissor();
        }
        // barra de desplazamiento
        int bx = vx + vw;
        gui.fill(bx, vy, bx + BAR_W, vy + vh, DarkUi.PANEL);
        gui.fill(bx, vy, bx + 1, vy + vh, DarkUi.BORDER);
        float max = maxScroll();
        if (max > 0) {
            int[] thumb = thumb();
            gui.fill(bx + 2, thumb[0], bx + BAR_W - 1, thumb[0] + thumb[1], this.barGrab >= 0 ? DarkUi.ACCENT : 0xFF4A5362);
        }
    }

    private boolean videoShown() {
        return HsVideoPlayer.get().state() != HsVideoPlayer.IDLE && session.url().startsWith("hs:video");
    }

    /** El video (o el avance de la descarga, o el error) ocupa el lugar de la página, sobre fondo negro. */
    private void drawVideo(GuiGraphics gui, int vx, int vy, int vw, int vh) {
        HsVideoPlayer video = HsVideoPlayer.get();
        gui.fill(vx, vy, vx + vw, vy + vh, 0xFF000000);
        if (video.state() != HsVideoPlayer.ERROR && video.draw(gui, vx, vy, vw, vh)) {
            drawVideoNotice(gui, video.notice(), vx, vy, vw, vh);
            return;
        }
        String text;
        int color = DarkUi.TEXT;
        switch (video.state()) {
            case HsVideoPlayer.LOADING -> {
                float p = video.loadProgress();
                text = DarkUi.tr("gui.beautifuldecor.browser.video.loading") + (p >= 0 ? " " + Math.round(p * 100) + "%" : "");
            }
            case HsVideoPlayer.ERROR -> {
                text = video.message();
                color = 0xFFE0645C;
            }
            default -> text = DarkUi.tr("gui.beautifuldecor.browser.video.opening");
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        String rest = text == null ? "" : text;
        while (!rest.isEmpty() && lines.size() < 8) {
            String line = this.font.plainSubstrByWidth(rest, Math.max(40, vw - 24));
            if (line.isEmpty()) {
                break;
            }
            lines.add(line);
            rest = rest.substring(line.length()).stripLeading();
        }
        int y = vy + (vh - lines.size() * 11) / 2;
        for (String line : lines) {
            gui.drawString(this.font, line, vx + (vw - this.font.width(line)) / 2, y, color, false);
            y += 11;
        }
    }

    /** Aviso sobre el video (por ejemplo que llegó cortado y se ve solo el principio): una franja oscura abajo, con letras ámbar. */
    private void drawVideoNotice(GuiGraphics gui, String notice, int vx, int vy, int vw, int vh) {
        if (notice == null || notice.isEmpty()) {
            return;
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        String rest = notice;
        while (!rest.isEmpty() && lines.size() < 3) {
            String line = this.font.plainSubstrByWidth(rest, Math.max(40, vw - 16));
            if (line.isEmpty()) {
                break;
            }
            lines.add(line);
            rest = rest.substring(line.length()).stripLeading();
        }
        int h = lines.size() * 11 + 6;
        gui.fill(vx, vy + vh - h, vx + vw, vy + vh, 0xC0000000);
        int y = vy + vh - h + 3;
        for (String line : lines) {
            gui.drawString(this.font, line, vx + 8, y, 0xFFE0B35C, false);
            y += 11;
        }
    }

    /** {arriba, alto} de la manija de la barra de desplazamiento. */
    private int[] thumb() {
        int vh = viewHeight();
        int h = Math.max(16, (int) ((long) vh * vh / Math.max(vh, pageHeight())));
        float max = maxScroll();
        int travel = vh - h;
        int top = viewTop() + (max <= 0 ? 0 : Math.round(travel * (this.scroll / max)));
        return new int[]{top, h};
    }

    private void drawText(GuiGraphics gui, Page.Item it, int x, int y, boolean hover) {
        if (it.text == null || it.text.isEmpty()) {
            return;
        }
        Component[] cached = it.cache instanceof Component[] c ? c : null;
        if (cached == null) {
            cached = new Component[]{styled(it.text, it.flags), styled(it.text, it.flags | Page.UNDERLINE)};
            it.cache = cached;
        }
        Component c = hover ? cached[1] : cached[0];
        int color = colorOf(it.role, hover);
        if (it.scale == 100) {
            gui.drawString(this.font, c, x, y + 1, color, false);
            return;
        }
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(x, y + 1, 0);
        float s = it.scale / 100f;
        pose.scale(s, s, 1.0f);
        gui.drawString(this.font, c, 0, 0, color, false);
        pose.popPose();
    }

    private void drawRect(GuiGraphics gui, Page.Item it, int x, int y) {
        int color = switch (it.fill) {
            case Page.FILL_CODE -> C_CODE_BG;
            case Page.FILL_QUOTE -> C_QUOTE;
            case Page.FILL_BORDER -> C_EDGE;
            case Page.FILL_RULE -> DarkUi.BORDER;
            default -> C_CODE_BG;
        };
        if ((it.flags & Page.OUTLINE) != 0) {
            gui.fill(x, y, x + it.w, y + 1, color);
            gui.fill(x, y + it.h - 1, x + it.w, y + it.h, color);
            gui.fill(x, y, x + 1, y + it.h, color);
            gui.fill(x + it.w - 1, y, x + it.w, y + it.h, color);
        } else {
            gui.fill(x, y, x + it.w, y + it.h, color);
        }
    }

    private void drawImage(GuiGraphics gui, Page.Item it, int x, int y, boolean hover) {
        if (it.src != null && this.images.draw(gui, it.src, x, y, it.w, it.h)) {
            if (hover) {
                outline(gui, x, y, it.w, it.h, DarkUi.ACCENT);
            }
            return;
        }
        gui.fill(x, y, x + it.w, y + it.h, C_CODE_BG);
        outline(gui, x, y, it.w, it.h, C_EDGE);
        if (it.w > 14 && it.h > 10) {
            gui.drawString(this.font, "…", x + it.w / 2 - 3, y + it.h / 2 - 4, C_MUTED, false);
        }
    }

    private static void outline(GuiGraphics gui, int x, int y, int w, int h, int color) {
        gui.fill(x, y, x + w, y + 1, color);
        gui.fill(x, y + h - 1, x + w, y + h, color);
        gui.fill(x, y, x + 1, y + h, color);
        gui.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawControl(GuiGraphics gui, Page.Item it, int x, int y, boolean hover) {
        String kind = it.src == null ? "field" : it.src;
        boolean button = kind.equals("button");
        boolean focused = it.node != null && it.node == this.focusNode;
        if (kind.startsWith("checkbox") || kind.startsWith("radio")) {
            gui.fill(x, y, x + it.w, y + it.h, C_FIELD);
            outline(gui, x, y, it.w, it.h, focused ? DarkUi.ACCENT : C_EDGE);
            if (kind.endsWith("-on")) {
                gui.fill(x + 3, y + 3, x + it.w - 3, y + it.h - 3, DarkUi.ACCENT);
            }
            return;
        }
        gui.fill(x, y, x + it.w, y + it.h, button ? (hover ? DarkUi.BUTTON_HOVER : DarkUi.BUTTON) : C_FIELD);
        outline(gui, x, y, it.w, it.h, focused || (hover && button) ? DarkUi.ACCENT : C_EDGE);
        if (focused && this.focusText && (kind.equals("field") || kind.equals("field-hint") || kind.equals("textarea"))) {
            drawEditing(gui, it, x, y);
            return;
        }
        if (it.text == null || it.text.isEmpty()) {
            return;
        }
        float s = it.scale / 100f;
        int maxW = Math.max(4, (int) ((it.w - 8) / s));
        String label = this.font.plainSubstrByWidth(it.text, maxW);
        int color = kind.equals("field-hint") ? C_MUTED : button ? 0xFFE0E4EB : C_TEXT;
        int tw = Math.round(this.font.width(label) * s);
        int tx = button ? x + (it.w - tw) / 2 : x + 4;
        int ty = y + Math.max(0, (it.h - Math.round(8 * s)) / 2);
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(tx, ty, 0);
        pose.scale(s, s, 1.0f);
        gui.drawString(this.font, label, 0, 0, color, false);
        pose.popPose();
    }

    /** Un campo de texto con el foco: el texto completo, la selección y el cursor (que parpadea). */
    private void drawEditing(GuiGraphics gui, Page.Item it, int x, int y) {
        Node n = it.node;
        String v = n.currentValue();
        boolean area = n.is("textarea");
        String type = n.attr("type");
        if (!area && type != null && type.equalsIgnoreCase("password")) {
            v = "\u2022".repeat(v.length());
        }
        int len = v.length();
        int caret = n.caret < 0 ? len : Math.min(n.caret, len);
        int anchor = n.selAnchor < 0 ? caret : Math.min(n.selAnchor, len);
        int lo = Math.min(caret, anchor);
        int hi = Math.max(caret, anchor);
        float s = it.scale / 100f;
        int maxW = Math.max(4, (int) ((it.w - 8) / s));
        String[] lines = area ? v.replace("\r", "").split("\n", -1) : new String[] {v};
        int caretLine = 0;
        int caretCol = caret;
        int off = 0;
        for (int i = 0; i < lines.length; i++) {
            if (caret <= off + lines[i].length()) {
                caretLine = i;
                caretCol = caret - off;
                break;
            }
            off += lines[i].length() + 1;
        }
        int rows = area ? 3 : 1;
        int first = Math.max(0, caretLine - rows + 1);
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(x + 4, y + (area ? 3 : Math.max(0, (it.h - Math.round(8 * s)) / 2)), 0);
        pose.scale(s, s, 1.0f);
        off = 0;
        for (int i = 0; i < first; i++) {
            off += lines[i].length() + 1;
        }
        for (int i = first; i < lines.length && i < first + rows; i++) {
            String line = lines[i];
            int start = 0;
            if (i == caretLine) {
                while (start < caretCol && this.font.width(line.substring(start, caretCol)) > maxW - 2) {
                    start++;
                }
            }
            String vis = this.font.plainSubstrByWidth(line.substring(start), maxW);
            int ly = (i - first) * 9;
            int a = Math.max(start, lo - off);
            int b = Math.min(start + vis.length(), hi - off);
            if (b > a) {
                int sx = this.font.width(line.substring(start, a));
                int ex = this.font.width(line.substring(start, b));
                gui.fill(sx, ly - 1, ex, ly + 9, 0xFF2F5F9E);
            }
            gui.drawString(this.font, vis, 0, ly, C_TEXT, false);
            if (i == caretLine && blink && caretCol - start <= vis.length()) {
                int cx = this.font.width(line.substring(start, caretCol));
                gui.fill(cx, ly - 1, cx + 1, ly + 9, 0xFFE0E4EB);
            }
            off += line.length() + 1;
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ lista de un select

    private static final int POPUP_ROW = 12;
    private static final int POPUP_ROWS = 10;

    private void openPopup(Node select, PageRuntime r) {
        PageRuntime.SelectInfo info = r.selectInfo(select);
        if (info == null || info.labels.length == 0) {
            return;
        }
        Page.Item at = null;
        if (this.page != null) {
            for (Page.Item it : this.page.items) {
                if (it.node == select && it.kind == Page.CONTROL) {
                    at = it;
                    break;
                }
            }
        }
        int w = at == null ? 120 : at.w;
        for (String label : info.labels) {
            w = Math.max(w, this.font.width(label) + 16);
        }
        w = Math.min(w, panelW() - 12);
        int rows = Math.min(info.labels.length, POPUP_ROWS);
        int px = left() + (at == null ? 10 : at.x);
        int py = viewTop() + (at == null ? 10 : at.y - (int) this.scroll + at.h);
        if (py + rows * POPUP_ROW + 2 > viewTop() + viewHeight()) {
            py = Math.max(viewTop(), py - (at == null ? 0 : at.h) - rows * POPUP_ROW - 2);
        }
        px = Math.max(left() + 2, Math.min(px, left() + panelW() - w - 2));
        this.popup = info;
        this.popupX = px;
        this.popupY = py;
        this.popupW = w;
        this.popupFirst = 0;
        for (int i = 0; i < info.selected.length; i++) {
            if (info.selected[i]) {
                this.popupFirst = Math.max(0, Math.min(i - 2, info.labels.length - rows));
                break;
            }
        }
    }

    private int popupRows() {
        return this.popup == null ? 0 : Math.min(this.popup.labels.length, POPUP_ROWS);
    }

    private void drawPopup(GuiGraphics gui, int mx, int my) {
        PageRuntime.SelectInfo p = this.popup;
        if (p == null) {
            return;
        }
        int rows = popupRows();
        int h = rows * POPUP_ROW + 2;
        gui.fill(this.popupX - 1, this.popupY - 1, this.popupX + this.popupW + 1, this.popupY + h + 1, DarkUi.ACCENT);
        gui.fill(this.popupX, this.popupY, this.popupX + this.popupW, this.popupY + h, C_FIELD);
        for (int r = 0; r < rows; r++) {
            int i = this.popupFirst + r;
            if (i >= p.labels.length) {
                break;
            }
            int ry = this.popupY + 1 + r * POPUP_ROW;
            boolean hover = DarkUi.inside(mx, my, this.popupX, ry, this.popupW, POPUP_ROW) && !p.disabled[i];
            if (hover) {
                gui.fill(this.popupX + 1, ry, this.popupX + this.popupW - 1, ry + POPUP_ROW, DarkUi.BUTTON_HOVER);
            } else if (p.selected[i]) {
                gui.fill(this.popupX + 1, ry, this.popupX + this.popupW - 1, ry + POPUP_ROW, DarkUi.BUTTON);
            }
            String label = (p.multiple ? (p.selected[i] ? "\u2611 " : "\u2610 ") : "") + p.labels[i];
            gui.drawString(this.font, this.font.plainSubstrByWidth(label, this.popupW - 10), this.popupX + 5, ry + 2,
                    p.disabled[i] ? 0xFF4A515E : C_TEXT, false);
        }
        if (p.labels.length > rows) {
            int bh = Math.max(8, h * rows / p.labels.length);
            int by = this.popupY + (h - bh) * this.popupFirst / Math.max(1, p.labels.length - rows);
            gui.fill(this.popupX + this.popupW - 3, by, this.popupX + this.popupW - 1, by + bh, 0xFF4A5362);
        }
    }

    /** Un clic con la lista abierta: elige una opción, o la cierra si cae fuera. Siempre lo atiende. */
    private boolean popupClick(double mx, double my) {
        PageRuntime.SelectInfo p = this.popup;
        int rows = popupRows();
        if (DarkUi.inside(mx, my, this.popupX, this.popupY, this.popupW, rows * POPUP_ROW + 2)) {
            int i = this.popupFirst + ((int) my - this.popupY - 1) / POPUP_ROW;
            PageRuntime r = live();
            if (r != null && i >= 0 && i < p.labels.length && !p.disabled[i]) {
                r.chooseOption(p.select, i);
                if (p.multiple) {
                    p.selected[i] = !p.selected[i];          // se muestra el cambio ya, sin esperar a la página
                } else {
                    this.popup = null;
                }
            }
        } else {
            this.popup = null;
        }
        return true;
    }

    // ------------------------------------------------------------------ barra de audio y de estado

    private int[] audioGeometry() {
        int y = audioY();
        int sliderW = Math.min(60, panelW() / 4);
        int sliderX = left() + panelW() - 6 - sliderW;
        return new int[]{y, left() + 4, left() + 22, sliderX, sliderW};
    }

    private void drawAudioBar(GuiGraphics gui, int mx, int my, HsAudioPlayer audio) {
        int[] g = audioGeometry();
        int y = g[0];
        gui.fill(left(), y, left() + panelW(), y + AUDIO_H, DarkUi.HEADER);
        gui.fill(left(), y, left() + panelW(), y + 1, DarkUi.BORDER);
        int st = audio.state();
        boolean canPause = st == HsAudioPlayer.PLAYING || st == HsAudioPlayer.PAUSED;
        String pauseLabel = st == HsAudioPlayer.PAUSED ? "▶" : "||";
        if (canPause) {
            DarkUi.button(gui, this.font, g[1], y + 2, 16, 13, pauseLabel, DarkUi.inside(mx, my, g[1], y + 2, 16, 13), false);
        } else {
            outline(gui, g[1], y + 2, 16, 13, DarkUi.BORDER);
        }
        DarkUi.button(gui, this.font, g[2], y + 2, 16, 13, "■", DarkUi.inside(mx, my, g[2], y + 2, 16, 13), false);
        String text;
        int color = DarkUi.TEXT;
        switch (st) {
            case HsAudioPlayer.LOADING -> {
                float p = audio.loadProgress();
                text = DarkUi.tr("gui.beautifuldecor.browser.audio.loading") + (p >= 0 ? " " + Math.round(p * 100) + "%" : "") + "  " + audio.title();
                color = DarkUi.MUTED;
            }
            case HsAudioPlayer.ERROR -> {
                text = audio.message();
                color = C_ERROR;
            }
            default -> {
                long s = audio.elapsedMs() / 1000;
                text = audio.live()
                        ? "● " + DarkUi.tr("gui.beautifuldecor.browser.audio.live") + "  " + audio.title()
                        : "♪ " + audio.title() + "  " + (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
            }
        }
        int textX = g[2] + 22;
        gui.drawString(this.font, this.font.plainSubstrByWidth(text, Math.max(10, g[3] - 8 - textX)), textX, y + 5, color, false);
        // volumen
        int sx = g[3];
        int sw = g[4];
        gui.fill(sx, y + 7, sx + sw, y + 10, DarkUi.BORDER);
        int filled = Math.round(sw * audio.volume());
        gui.fill(sx, y + 7, sx + filled, y + 10, DarkUi.ACCENT);
        gui.fill(sx + filled - 1, y + 4, sx + filled + 1, y + 13, DarkUi.TEXT);
    }

    private void drawStatus(GuiGraphics gui) {
        int y = statusY();
        gui.fill(left(), y, left() + panelW(), y + STATUS_H, DarkUi.HEADER);
        gui.fill(left(), y, left() + panelW(), y + 1, DarkUi.BORDER);
        String right = Downloads.anyRunning() ? "↓ " + DarkUi.tr("gui.beautifuldecor.browser.downloading") : "";
        int rightW = right.isEmpty() ? 0 : this.font.width(right) + 8;
        String text = this.hoverHref != null ? this.hoverHref : session.status();
        if (text == null) {
            text = "";
        }
        int color = this.hoverHref != null ? C_LINK : DarkUi.MUTED;
        gui.drawString(this.font, this.font.plainSubstrByWidth(text, panelW() - 12 - rightW), left() + 5, y + 2, color, false);
        if (!right.isEmpty()) {
            gui.drawString(this.font, right, left() + panelW() - 5 - this.font.width(right), y + 2, DarkUi.WARN, false);
        }
    }

    // ------------------------------------------------------------------ acciones

    private void nav(Runnable action) {
        if (!session.loading()) {
            session.setScroll((int) this.scroll);
        }
        action.run();
    }

    private void press(int id) {
        switch (id) {
            case B_BACK -> nav(session::back);
            case B_FORWARD -> nav(session::forward);
            case B_RELOAD -> {
                if (session.loading()) {
                    session.stop();
                } else {
                    nav(session::reload);
                }
            }
            case B_HOME -> nav(() -> session.go(Url.HOME));
            case B_DOWNLOADS -> nav(() -> session.go("hs:downloads"));
            case B_ZOOM_IN -> stepZoom(1);
            case B_ZOOM_OUT -> stepZoom(-1);
            case B_CLOSE -> onClose();
            default -> { }
        }
    }

    private void stepZoom(int dir) {
        int i = 0;
        for (int k = 0; k < ZOOMS.length; k++) {
            if (ZOOMS[k] <= zoom) {
                i = k;
            }
        }
        zoom = ZOOMS[Math.max(0, Math.min(ZOOMS.length - 1, i + dir))];
    }

    private void submitAddress() {
        String text = this.address.getValue();
        this.address.setFocused(false);
        this.setFocused(null);
        nav(() -> session.go(text));
    }

    private void focusAddress() {
        this.setFocused(this.address);
        this.address.setFocused(true);
        this.address.moveCursorToEnd();
        this.address.setHighlightPos(0);
    }

    private void scrollBy(float dy) {
        this.stickUntil = 0;
        this.scroll += dy;
        clampScroll();
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.popup != null) {
            return popupClick(mx, my);
        }
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        for (Btn b : this.buttons) {
            if (enabled(b.id()) && DarkUi.inside(mx, my, b.x(), b.y(), b.w(), b.h())) {
                press(b.id());
                return true;
            }
        }
        if (this.zoomLabelX >= 0 && DarkUi.inside(mx, my, this.zoomLabelX, toolY() + 2, 28, TOOL_H - 4)) {
            zoom = 100;
            return true;
        }
        if (audioHeight() > 0) {
            int[] g = audioGeometry();
            HsAudioPlayer audio = HsAudioPlayer.get();
            if (DarkUi.inside(mx, my, g[1], g[0] + 2, 16, 13)) {
                audio.togglePause();
                return true;
            }
            if (DarkUi.inside(mx, my, g[2], g[0] + 2, 16, 13)) {
                HsVideoPlayer.get().stop();
                audio.stop();
                return true;
            }
            if (DarkUi.inside(mx, my, g[3] - 3, g[0], g[4] + 6, AUDIO_H)) {
                this.volumeDrag = true;
                audio.setVolume((float) (mx - g[3]) / g[4]);
                return true;
            }
        }
        int barX = left() + viewWidth();
        if (DarkUi.inside(mx, my, barX, viewTop(), BAR_W, viewHeight()) && maxScroll() > 0) {
            int[] t = thumb();
            if (my >= t[0] && my < t[0] + t[1]) {
                this.barGrab = (int) my - t[0];
            } else {
                scrollBy(my < t[0] ? -viewHeight() + 20 : viewHeight() - 20);
            }
            return true;
        }
        if (inContent(mx, my) && videoShown()) {
            this.address.setFocused(false);
            this.setFocused(null);
            HsVideoPlayer.get().togglePause();      // un clic sobre el video lo pausa o lo sigue
            return true;
        }
        if (inContent(mx, my)) {
            this.address.setFocused(false);
            this.setFocused(null);
            this.pressed = true;
            this.dragged = false;
            this.pressX = mx;
            this.pressY = my;
            this.pressScroll = this.scroll;
            return true;
        }
        if (!DarkUi.inside(mx, my, this.addrX, this.addrY, this.addrW, this.addrH)) {
            this.address.setFocused(false);
        } else {
            PageRuntime r = live();
            if (r != null && r.focusedNode() != null) {
                r.blurFocus();                          // el cursor pasa a la barra de direcciones
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.volumeDrag) {
            int[] g = audioGeometry();
            HsAudioPlayer.get().setVolume((float) (mx - g[3]) / g[4]);
            return true;
        }
        if (this.barGrab >= 0) {
            int vh = viewHeight();
            int[] t = thumb();
            int travel = Math.max(1, vh - t[1]);
            float frac = (float) ((my - this.barGrab - viewTop()) / travel);
            this.stickUntil = 0;
            this.scroll = Math.max(0f, Math.min(1f, frac)) * maxScroll();
            return true;
        }
        if (this.pressed) {
            if (!this.dragged && (Math.abs(mx - this.pressX) > 5 || Math.abs(my - this.pressY) > 5)) {
                this.dragged = true;
            }
            if (this.dragged) {
                this.stickUntil = 0;
                this.scroll = this.pressScroll - (float) (my - this.pressY);
                clampScroll();
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean wasPressed = this.pressed;
        boolean wasDragged = this.dragged;
        this.pressed = false;
        this.dragged = false;
        this.barGrab = -1;
        this.volumeDrag = false;
        if (button == 0 && wasPressed && !wasDragged && inContent(mx, my)) {
            int px = (int) mx - left();
            int py = (int) my - viewTop() + (int) this.scroll;
            Page.Item it = itemAt(px, py);
            PageRuntime r = live();
            if (it != null && it.href != null && (r == null || it.href.startsWith("hs:"))) {
                String href = it.href;
                nav(() -> session.open(href));
                return true;
            }
            if (r != null && this.page != null) {
                Node target = this.page.nodeAt(px, py);
                Node select = selectOf(target);
                if (select != null && !select.hasAttr("disabled")) {
                    openPopup(select, r);               // la lista de opciones la dibuja la pantalla
                    return true;
                }
                if (!session.loading()) {
                    session.setScroll((int) this.scroll);
                }
                r.click(target, px, (int) my - viewTop(), 0, domMods(0));
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (this.popup != null) {
            int max = Math.max(0, this.popup.labels.length - popupRows());
            this.popupFirst = Math.max(0, Math.min(max, this.popupFirst - (int) Math.signum(delta) * 2));
            return true;
        }
        scrollBy((float) (-delta * 28.0));
        return true;
    }

    private static Node selectOf(Node n) {
        for (int i = 0; n != null && i < 4; i++, n = n.parent) {
            if (n.is("select")) {
                return n;
            }
        }
        return null;
    }

    /** Los modificadores de GLFW convertidos a los del navegador (Ctrl, Mayús, Alt, Meta). */
    private static int domMods(int glfw) {
        int m = 0;
        if ((glfw & GLFW.GLFW_MOD_CONTROL) != 0 || Screen.hasControlDown()) {
            m |= DomHost.MOD_CTRL;
        }
        if ((glfw & GLFW.GLFW_MOD_SHIFT) != 0 || Screen.hasShiftDown()) {
            m |= DomHost.MOD_SHIFT;
        }
        if ((glfw & GLFW.GLFW_MOD_ALT) != 0 || Screen.hasAltDown()) {
            m |= DomHost.MOD_ALT;
        }
        if ((glfw & GLFW.GLFW_MOD_SUPER) != 0) {
            m |= DomHost.MOD_META;
        }
        return m;
    }

    /** El nombre de la tecla como lo ve una página (event.key), o null si no es una que se pueda nombrar. */
    private static String domKey(int key, int scan, boolean shift) {
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                return "Enter";
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                return "Backspace";
            }
            case GLFW.GLFW_KEY_DELETE -> {
                return "Delete";
            }
            case GLFW.GLFW_KEY_TAB -> {
                return "Tab";
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                return "Escape";
            }
            case GLFW.GLFW_KEY_LEFT -> {
                return "ArrowLeft";
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                return "ArrowRight";
            }
            case GLFW.GLFW_KEY_UP -> {
                return "ArrowUp";
            }
            case GLFW.GLFW_KEY_DOWN -> {
                return "ArrowDown";
            }
            case GLFW.GLFW_KEY_HOME -> {
                return "Home";
            }
            case GLFW.GLFW_KEY_END -> {
                return "End";
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                return "PageUp";
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                return "PageDown";
            }
            case GLFW.GLFW_KEY_INSERT -> {
                return "Insert";
            }
            case GLFW.GLFW_KEY_SPACE -> {
                return " ";
            }
            default -> { }
        }
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }
        String name = GLFW.glfwGetKeyName(key, scan);
        if (name == null || name.isEmpty()) {
            return null;
        }
        return shift ? name.toUpperCase(java.util.Locale.ROOT) : name;
    }

    /** event.code de la tecla (la posición física: "KeyA", "Digit1", "Enter"...). */
    private static String domCode(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return "Key" + (char) key;
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return "Digit" + (char) key;
        }
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }
        return switch (key) {
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_KP_ENTER -> "NumpadEnter";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_ESCAPE -> "Escape";
            case GLFW.GLFW_KEY_LEFT -> "ArrowLeft";
            case GLFW.GLFW_KEY_RIGHT -> "ArrowRight";
            case GLFW.GLFW_KEY_UP -> "ArrowUp";
            case GLFW.GLFW_KEY_DOWN -> "ArrowDown";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_PAGE_UP -> "PageUp";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PageDown";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            default -> "";
        };
    }

    /** event.keyCode (el código antiguo de las teclas). */
    private static int domKeyCode(int key) {
        if ((key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) || (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9)) {
            return key;
        }
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) {
            return 112 + key - GLFW.GLFW_KEY_F1;
        }
        return switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> 13;
            case GLFW.GLFW_KEY_BACKSPACE -> 8;
            case GLFW.GLFW_KEY_TAB -> 9;
            case GLFW.GLFW_KEY_ESCAPE -> 27;
            case GLFW.GLFW_KEY_SPACE -> 32;
            case GLFW.GLFW_KEY_PAGE_UP -> 33;
            case GLFW.GLFW_KEY_PAGE_DOWN -> 34;
            case GLFW.GLFW_KEY_END -> 35;
            case GLFW.GLFW_KEY_HOME -> 36;
            case GLFW.GLFW_KEY_LEFT -> 37;
            case GLFW.GLFW_KEY_UP -> 38;
            case GLFW.GLFW_KEY_RIGHT -> 39;
            case GLFW.GLFW_KEY_DOWN -> 40;
            case GLFW.GLFW_KEY_INSERT -> 45;
            case GLFW.GLFW_KEY_DELETE -> 46;
            default -> 0;
        };
    }

    private String clipboard() {
        Minecraft mc = this.minecraft;
        String c = mc == null ? "" : mc.keyboardHandler.getClipboard();
        return c == null ? "" : c;
    }

    private void copyToClipboard(String text) {
        Minecraft mc = this.minecraft;
        if (mc != null) {
            mc.keyboardHandler.setClipboard(text);
        }
    }

    /**
     * Manda una tecla a la página cuando ella la quiere (un campo de texto con foco, un elemento enfocado o un
     * escuchador de teclado). Verdadero si la tecla quedó atendida y la pantalla no debe hacer nada más con ella.
     */
    private boolean routeKey(PageRuntime r, int key, int scan, int mods) {
        boolean text = r.textFocused();
        if (!(text || r.wantsKeys() || r.focusedNode() != null || key == GLFW.GLFW_KEY_TAB)) {
            return false;
        }
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        if (text && ctrl) {
            if (key == GLFW.GLFW_KEY_V) {
                String clip = clipboard();
                if (!clip.isEmpty()) {
                    r.paste(clip);
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_C || key == GLFW.GLFW_KEY_X) {
                String sel = r.selectedText();
                if (sel != null && !sel.isEmpty()) {
                    copyToClipboard(sel);
                    if (key == GLFW.GLFW_KEY_X) {
                        r.keyDown("Backspace", "Backspace", 8, 0, false);
                    }
                }
                return true;
            }
        }
        String name = domKey(key, scan, (mods & GLFW.GLFW_MOD_SHIFT) != 0);
        if (name == null) {
            return text;
        }
        boolean used = r.keyDown(name, domCode(key), domKeyCode(key), domMods(mods), false);
        if (key == GLFW.GLFW_KEY_ESCAPE && text && !used) {
            r.blurFocus();
            used = true;
        }
        return used || text;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (this.popup != null) {
            return true;
        }
        if (!this.address.isFocused()) {
            PageRuntime r = live();
            if (r != null && r.textFocused()) {
                if (c >= 32 && c != 127) {
                    r.typeText(String.valueOf(c), domMods(0));
                }
                return true;
            }
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        PageRuntime r = live();
        if (r != null && this.popup == null && !this.address.isFocused() && (r.textFocused() || r.wantsKeys())) {
            String name = domKey(key, scan, (mods & GLFW.GLFW_MOD_SHIFT) != 0);
            if (name != null) {
                r.keyUp(name, domCode(key), domKeyCode(key), domMods(mods));
                return true;
            }
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean alt = (mods & GLFW.GLFW_MOD_ALT) != 0;
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        if (this.popup != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                this.popup = null;
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_L) {
            focusAddress();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_J) {
            boolean on = BrowserSession.toggleScripting();
            session.setStatus(InternalPages.t(on ? "Scripts activados" : "Scripts desactivados", on ? "Scripts on" : "Scripts off"));
            nav(session::reload);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_K) {
            nav(() -> session.go("hs:console"));
            return true;
        }
        if (this.address.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                this.address.setFocused(false);
                this.setFocused(null);
                updateAddress();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                submitAddress();
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        if (key == GLFW.GLFW_KEY_F5 || (ctrl && key == GLFW.GLFW_KEY_R)) {
            nav(session::reload);
            return true;
        }
        if (alt && key == GLFW.GLFW_KEY_LEFT) {
            nav(session::back);
            return true;
        }
        if (alt && key == GLFW.GLFW_KEY_RIGHT) {
            nav(session::forward);
            return true;
        }
        if (ctrl && (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD)) {
            stepZoom(1);
            return true;
        }
        if (ctrl && (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT)) {
            stepZoom(-1);
            return true;
        }
        if (ctrl && (key == GLFW.GLFW_KEY_0 || key == GLFW.GLFW_KEY_KP_0)) {
            zoom = 100;
            return true;
        }
        PageRuntime live = live();
        if (live != null && routeKey(live, key, scan, mods)) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_H && !ctrl && !alt) {
            onClose();                                  // H: salir del navegador y volver al mundo
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_DOWN -> scrollBy(20);
            case GLFW.GLFW_KEY_UP -> scrollBy(-20);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollBy(viewHeight() - 20);
            case GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-(viewHeight() - 20));
            case GLFW.GLFW_KEY_SPACE -> scrollBy(shift ? -(viewHeight() - 20) : viewHeight() - 20);
            case GLFW.GLFW_KEY_HOME -> {
                this.stickUntil = 0;
                this.scroll = 0;
            }
            case GLFW.GLFW_KEY_END -> {
                this.stickUntil = 0;
                this.scroll = maxScroll();
            }
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
        return true;
    }
}
