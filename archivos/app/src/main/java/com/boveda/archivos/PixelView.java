package com.boveda.archivos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayDeque;

/**
 * El lienzo del editor de pixel art: dibuja el lienzo con zoom, pellizco para acercar y dos dedos para mover, y las
 * herramientas (lápiz, borrador, cubeta, gotero, línea, rectángulo y círculo), con espejo, grosor y deshacer/rehacer.
 */
final class PixelView extends View {
    enum Tool {PENCIL, ERASER, FILL, PICKER, LINE, RECT, RECT_FILL, CIRCLE, CIRCLE_FILL}

    interface Listener {
        void onColorPicked(int color);

        void onChanged();
    }

    /** Una lista de enteros que crece (para anotar lo que cambia en cada trazo). */
    private static final class Ints {
        int[] a = new int[256];
        int n;

        void add(int v) {
            if (n == a.length) a = java.util.Arrays.copyOf(a, n * 2);
            a[n++] = v;
        }

        int[] toArray() {
            return java.util.Arrays.copyOf(a, n);
        }
    }

    int w, h;
    int[] px;
    private Bitmap bmp;
    private float scale = 12f, ox, oy;
    private boolean placed;
    Tool tool = Tool.PENCIL;
    int color = 0xFF000000;
    int brush = 1;
    boolean grid = true, mirrorX, mirrorY, precision;
    Listener listener;

    private final ArrayDeque<int[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private Ints cur;
    private int lastX = -1, lastY = -1, startX, startY;
    private boolean drawing, shaping;
    private Ints preview;
    private Tool beforePicker = Tool.PENCIL;
    private float touchX, touchY;
    private boolean touching;
    private final Paint pBmp = new Paint(), pGrid = new Paint(), pBox = new Paint(Paint.ANTI_ALIAS_FLAG), pShade = new Paint(), pCursor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final RectF dst = new RectF();
    private final ScaleGestureDetector scaler;
    private float lastFx, lastFy;
    private int lastCount;
    private Bitmap checker;
    private BitmapShader checkerShader;

    PixelView(Context c) {
        super(c);
        pBmp.setFilterBitmap(false);
        pBmp.setAntiAlias(false);
        pGrid.setColor(0x33000000);
        pGrid.setStrokeWidth(1f);
        pBox.setStyle(Paint.Style.STROKE);
        pBox.setColor(0xFF7BC4FF);
        pBox.setStrokeWidth(Ui.dp(c, 2));
        pCursor.setStyle(Paint.Style.STROKE);
        pCursor.setStrokeWidth(Ui.dp(c, 2));
        checker = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        checker.setPixel(0, 0, 0xFFD6D6D6);
        checker.setPixel(1, 1, 0xFFD6D6D6);
        checker.setPixel(1, 0, 0xFFA8A8A8);
        checker.setPixel(0, 1, 0xFFA8A8A8);
        checkerShader = new BitmapShader(checker, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float f = d.getScaleFactor();
                float ns = Math.max(2f, Math.min(80f, scale * f));
                f = ns / scale;
                ox = d.getFocusX() - (d.getFocusX() - ox) * f;
                oy = d.getFocusY() - (d.getFocusY() - oy) * f;
                scale = ns;
                invalidate();
                return true;
            }
        });
    }

    // ------------------------------------------------------------------ lienzo

    /** Pone un lienzo nuevo (o uno abierto) y lo centra. */
    void setCanvas(int width, int height, int[] pixels) {
        w = width;
        h = height;
        px = pixels != null ? pixels : new int[w * h];
        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.setPixels(px, 0, w, 0, 0, w, h);
        undo.clear();
        redo.clear();
        placed = false;
        invalidate();
    }

    Bitmap snapshot() {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    boolean canUndo() {
        return !undo.isEmpty();
    }

    boolean canRedo() {
        return !redo.isEmpty();
    }

    boolean dirty;

    private void place() {
        if (placed || getWidth() == 0 || w == 0) return;
        placed = true;
        scale = Math.max(2f, Math.min(48f, Math.min((getWidth() - 24f) / w, (getHeight() - 24f) / h)));
        scale = (float) Math.floor(scale);
        if (scale < 2f) scale = 2f;
        ox = (getWidth() - w * scale) / 2f;
        oy = (getHeight() - h * scale) / 2f;
    }

    void fit() {
        placed = false;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        if (!placed) invalidate();
    }

    // ------------------------------------------------------------------ dibujo en pantalla

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF0B0F18);
        if (bmp == null) return;
        place();
        dst.set(ox, oy, ox + w * scale, oy + h * scale);
        // cuadros del fondo (para ver lo transparente)
        m.reset();
        m.setScale(scale, scale);
        m.postTranslate(ox, oy);
        checkerShader.setLocalMatrix(m);
        Paint pc = new Paint();
        pc.setShader(checkerShader);
        pc.setFilterBitmap(false);
        c.drawRect(dst, pc);
        c.drawBitmap(bmp, null, dst, pBmp);
        // vista previa de línea, rectángulo o círculo mientras se arrastra
        if (shaping && preview != null) {
            pShade.setColor(tool == Tool.ERASER ? 0x66FFFFFF : (color & 0x00FFFFFF) | 0xAA000000);
            for (int i = 0; i < preview.n; i++) {
                int idx = preview.a[i];
                float x = ox + (idx % w) * scale, y = oy + (idx / w) * scale;
                c.drawRect(x, y, x + scale, y + scale, pShade);
            }
        }
        if (grid && scale >= 6f) {
            int x0 = Math.max(0, (int) Math.floor(-ox / scale)), x1 = Math.min(w, (int) Math.ceil((getWidth() - ox) / scale));
            int y0 = Math.max(0, (int) Math.floor(-oy / scale)), y1 = Math.min(h, (int) Math.ceil((getHeight() - oy) / scale));
            for (int x = x0; x <= x1; x++) c.drawLine(ox + x * scale, oy + y0 * scale, ox + x * scale, oy + y1 * scale, pGrid);
            for (int y = y0; y <= y1; y++) c.drawLine(ox + x0 * scale, oy + y * scale, ox + x1 * scale, oy + y * scale, pGrid);
        }
        c.drawRect(dst, pBox);
        // líneas del espejo
        if (mirrorX || mirrorY) {
            Paint pm = new Paint();
            pm.setColor(0x887BC4FF);
            pm.setStrokeWidth(Ui.dp(getContext(), 1.5f));
            if (mirrorX) c.drawLine(ox + w * scale / 2f, oy, ox + w * scale / 2f, oy + h * scale, pm);
            if (mirrorY) c.drawLine(ox, oy + h * scale / 2f, ox + w * scale, oy + h * scale / 2f, pm);
        }
        // cruz de precisión: el píxel que se va a pintar queda a la vista, arriba del dedo
        if (precision && touching) {
            float ty = touchY - Ui.dp(getContext(), 56);
            pCursor.setColor(0xFFFFFFFF);
            c.drawCircle(touchX, ty, Ui.dp(getContext(), 14), pCursor);
            c.drawLine(touchX - 22, ty, touchX + 22, ty, pCursor);
            c.drawLine(touchX, ty - 22, touchX, ty + 22, pCursor);
        }
    }

    // ------------------------------------------------------------------ dedos

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bmp == null) return false;
        scaler.onTouchEvent(e);
        int count = e.getPointerCount();
        int action = e.getActionMasked();
        if (count >= 2) {
            // dos dedos: mover y acercar; si se estaba dibujando, se termina el trazo
            if (drawing) endStroke(false);
            float fx = 0, fy = 0;
            for (int i = 0; i < count; i++) {
                fx += e.getX(i);
                fy += e.getY(i);
            }
            fx /= count;
            fy /= count;
            if (lastCount >= 2 && action == MotionEvent.ACTION_MOVE && !scaler.isInProgress()) {
                ox += fx - lastFx;
                oy += fy - lastFy;
                invalidate();
            } else if (lastCount >= 2 && action == MotionEvent.ACTION_MOVE) {
                ox += fx - lastFx;
                oy += fy - lastFy;
                invalidate();
            }
            lastFx = fx;
            lastFy = fy;
            lastCount = count;
            touching = false;
            return true;
        }
        if (lastCount >= 2) {
            // acaba de levantarse uno de los dos dedos: se espera al siguiente toque
            lastCount = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ? 0 : 1;
            if (action != MotionEvent.ACTION_DOWN) return true;
        }
        lastCount = 1;
        float x = e.getX(), y = e.getY();
        touchX = x;
        touchY = y;
        float ay = precision ? y - Ui.dp(getContext(), 56) : y;
        int cx = (int) Math.floor((x - ox) / scale), cy = (int) Math.floor((ay - oy) / scale);
        switch (action) {
            case MotionEvent.ACTION_DOWN -> {
                touching = true;
                beginStroke(cx, cy);
            }
            case MotionEvent.ACTION_MOVE -> {
                if (drawing) moveStroke(cx, cy);
            }
            case MotionEvent.ACTION_UP -> {
                touching = false;
                if (drawing) {
                    moveStroke(cx, cy);
                    endStroke(true);
                }
            }
            case MotionEvent.ACTION_CANCEL -> {
                touching = false;
                if (drawing) endStroke(true);
            }
            default -> {
            }
        }
        invalidate();
        return true;
    }

    private boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    private void beginStroke(int x, int y) {
        drawing = true;
        cur = new Ints();
        shaping = false;
        lastX = x;
        lastY = y;
        startX = x;
        startY = y;
        switch (tool) {
            case PENCIL, ERASER -> stamp(x, y);
            case FILL -> {
                if (inside(x, y)) {
                    int target = px[y * w + x];
                    int[] ch = PixelOps.floodFill(px, w, h, x, y, color);
                    for (int idx : ch) {
                        cur.add(idx);
                        cur.add(target);
                        cur.add(color);
                    }
                    if (ch.length > 0) bmp.setPixels(px, 0, w, 0, 0, w, h);
                }
            }
            case PICKER -> pick(x, y);
            default -> {
                shaping = true;
                preview = new Ints();
                updatePreview(x, y);
            }
        }
    }

    private void moveStroke(int x, int y) {
        switch (tool) {
            case PENCIL, ERASER -> {
                if (x != lastX || y != lastY) {
                    PixelOps.line(lastX, lastY, x, y, (px2, py2) -> stamp(px2, py2));
                    lastX = x;
                    lastY = y;
                }
            }
            case PICKER -> pick(x, y);
            case FILL -> {
            }
            default -> updatePreview(x, y);
        }
    }

    private void endStroke(boolean commitShape) {
        if (!drawing) return;
        drawing = false;
        if (shaping) {
            if (commitShape && preview != null) {
                for (int i = 0; i < preview.n; i++) setPx(preview.a[i], tool == Tool.ERASER ? 0 : color);
            }
            shaping = false;
            preview = null;
        }
        if (tool == Tool.PICKER) {
            tool = beforePicker;
            if (listener != null) listener.onChanged();
        }
        if (cur != null && cur.n > 0) {
            undo.push(cur.toArray());
            while (undo.size() > 200) undo.removeLast();
            redo.clear();
            dirty = true;
            if (listener != null) listener.onChanged();
        }
        cur = null;
    }

    private void pick(int x, int y) {
        if (!inside(x, y)) return;
        int c = px[y * w + x];
        if ((c >>> 24) == 0) return;
        color = c;
        if (listener != null) listener.onColorPicked(c);
    }

    /** Pinta (o borra) un píxel y su reflejo, con el grosor elegido. */
    private void stamp(int x, int y) {
        int val = tool == Tool.ERASER ? 0 : color;
        int from = -((brush - 1) / 2), to = brush / 2;
        for (int dy = from; dy <= to; dy++) {
            for (int dx = from; dx <= to; dx++) {
                put(x + dx, y + dy, val);
                if (mirrorX) put(w - 1 - (x + dx), y + dy, val);
                if (mirrorY) put(x + dx, h - 1 - (y + dy), val);
                if (mirrorX && mirrorY) put(w - 1 - (x + dx), h - 1 - (y + dy), val);
            }
        }
    }

    private void put(int x, int y, int val) {
        if (!inside(x, y)) return;
        setPx(y * w + x, val);
    }

    private void setPx(int idx, int val) {
        int old = px[idx];
        if (old == val) return;
        if (cur != null) {
            cur.add(idx);
            cur.add(old);
            cur.add(val);
        }
        px[idx] = val;
        bmp.setPixel(idx % w, idx / w, val);
    }

    private void updatePreview(int x, int y) {
        preview = new Ints();
        PixelOps.Plot p = (px2, py2) -> {
            addPreview(px2, py2);
            if (mirrorX) addPreview(w - 1 - px2, py2);
            if (mirrorY) addPreview(px2, h - 1 - py2);
            if (mirrorX && mirrorY) addPreview(w - 1 - px2, h - 1 - py2);
        };
        switch (tool) {
            case LINE -> PixelOps.line(startX, startY, x, y, p);
            case RECT -> PixelOps.rect(startX, startY, x, y, false, p);
            case RECT_FILL -> PixelOps.rect(startX, startY, x, y, true, p);
            case CIRCLE -> PixelOps.ellipse(startX, startY, x, y, false, p);
            case CIRCLE_FILL -> PixelOps.ellipse(startX, startY, x, y, true, p);
            default -> {
            }
        }
    }

    private void addPreview(int x, int y) {
        if (inside(x, y)) preview.add(y * w + x);
    }

    // ------------------------------------------------------------------ deshacer y rehacer

    void setTool(Tool t) {
        if (t == Tool.PICKER && tool != Tool.PICKER) beforePicker = tool;
        tool = t;
    }

    void undo() {
        if (undo.isEmpty()) return;
        int[] s = undo.pop();
        for (int i = s.length - 3; i >= 0; i -= 3) {
            px[s[i]] = s[i + 1];
            bmp.setPixel(s[i] % w, s[i] / w, s[i + 1]);
        }
        redo.push(s);
        dirty = true;
        invalidate();
        if (listener != null) listener.onChanged();
    }

    void redo() {
        if (redo.isEmpty()) return;
        int[] s = redo.pop();
        for (int i = 0; i + 2 < s.length; i += 3) {
            px[s[i]] = s[i + 2];
            bmp.setPixel(s[i] % w, s[i] / w, s[i + 2]);
        }
        undo.push(s);
        dirty = true;
        invalidate();
        if (listener != null) listener.onChanged();
    }

    /** Borra todo el lienzo (se puede deshacer). */
    void clear() {
        cur = new Ints();
        for (int i = 0; i < px.length; i++) setPx(i, 0);
        if (cur.n > 0) {
            undo.push(cur.toArray());
            redo.clear();
            dirty = true;
        }
        cur = null;
        invalidate();
        if (listener != null) listener.onChanged();
    }
}
