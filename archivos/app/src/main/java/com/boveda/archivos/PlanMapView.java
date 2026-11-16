package com.boveda.archivos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** El plano en 2D: desde arriba, por capas o en cortes. Un dedo mueve, dos dedos acercan; los píxeles se ven nítidos. */
final class PlanMapView extends View {
    static final int TOP = 0, LAYER = 1, CUT_Z = 2, CUT_X = 3;

    private final BdPlan plan;
    private final int[] argb;     // color de cada bloque de la paleta
    private final byte[] kind;
    private Bitmap bmp;
    private final Matrix m = new Matrix();
    private final Paint paint = new Paint();
    private float scale = 1, tx, ty;
    private boolean placed;
    private final ScaleGestureDetector scaler;
    private float lastX, lastY;
    private int lastCount;

    PlanMapView(Context c, BdPlan plan) {
        super(c);
        this.plan = plan;
        paint.setFilterBitmap(false);
        paint.setAntiAlias(false);
        setBackgroundColor(0xFF03050D);
        int n = plan.palette.length;
        argb = new int[n];
        kind = new byte[n];
        for (int i = 0; i < n; i++) {
            argb[i] = BlockColors.color(plan.palette[i]);
            kind[i] = (byte) BlockColors.kind(plan.palette[i]);
        }
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float f = d.getScaleFactor(), ns = Math.max(0.2f, Math.min(80f, scale * f));
                f = ns / scale;
                tx = d.getFocusX() - (d.getFocusX() - tx) * f;
                ty = d.getFocusY() - (d.getFocusY() - ty) * f;
                scale = ns;
                invalidate();
                return true;
            }
        });
    }

    /** Dibuja el mapa pedido (se llama desde un hilo aparte: solo arma el bitmap). */
    Bitmap render(int mode, int level) {
        int sx = plan.sx, sy = plan.sy, sz = plan.sz;
        short[] b = plan.blocks;
        int w, h;
        if (mode == CUT_Z) { w = sx; h = sy; } else if (mode == CUT_X) { w = sz; h = sy; } else { w = sx; h = sz; }
        int[] px = new int[w * h];
        int bg = 0xFF03050D;
        if (mode == TOP) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int yy = -1;
                    for (int y = sy - 1; y >= 0; y--) {
                        if (kind[b[(y * sz + z) * sx + x]] != BlockColors.AIR) {
                            yy = y;
                            break;
                        }
                    }
                    px[z * w + x] = yy < 0 ? bg : shade(argb[b[(yy * sz + z) * sx + x]], 0.55 + 0.45 * yy / Math.max(1, sy - 1));
                }
            }
        } else if (mode == LAYER) {
            int y = Math.max(0, Math.min(sy - 1, level));
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int id = b[(y * sz + z) * sx + x];
                    if (kind[id] != BlockColors.AIR) {
                        px[z * w + x] = argb[id];
                    } else {
                        // vacío: se ve apagado lo primero que hay debajo
                        int c = bg;
                        for (int yy = y - 1; yy >= 0; yy--) {
                            int idb = b[(yy * sz + z) * sx + x];
                            if (kind[idb] != BlockColors.AIR) {
                                c = shade(argb[idb], 0.28);
                                break;
                            }
                        }
                        px[z * w + x] = c;
                    }
                }
            }
        } else if (mode == CUT_Z) {
            int z = Math.max(0, Math.min(sz - 1, level));
            for (int y = 0; y < sy; y++) for (int x = 0; x < sx; x++) {
                int id = b[(y * sz + z) * sx + x];
                px[(sy - 1 - y) * w + x] = kind[id] == BlockColors.AIR ? bg : argb[id];
            }
        } else {
            int x = Math.max(0, Math.min(sx - 1, level));
            for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
                int id = b[(y * sz + z) * sx + x];
                px[(sy - 1 - y) * w + z] = kind[id] == BlockColors.AIR ? bg : argb[id];
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    static int levels(BdPlan p, int mode) {
        return mode == LAYER ? p.sy : mode == CUT_Z ? p.sz : mode == CUT_X ? p.sx : 1;
    }

    private static int shade(int c, double f) {
        int r = (int) (((c >> 16) & 255) * f), g = (int) (((c >> 8) & 255) * f), bl = (int) ((c & 255) * f);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    void show(Bitmap b) {
        boolean changed = bmp == null || bmp.getWidth() != b.getWidth() || bmp.getHeight() != b.getHeight();
        bmp = b;
        if (changed) placed = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bmp == null) return;
        if (!placed && getWidth() > 0) {
            placed = true;
            scale = Math.min(getWidth() / (float) bmp.getWidth(), getHeight() / (float) bmp.getHeight());
            tx = (getWidth() - bmp.getWidth() * scale) / 2f;
            ty = (getHeight() - bmp.getHeight() * scale) / 2f;
        }
        m.reset();
        m.postScale(scale, scale);
        m.postTranslate(tx, ty);
        c.drawBitmap(bmp, m, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        int a = e.getActionMasked();
        int skip = a == MotionEvent.ACTION_POINTER_UP ? e.getActionIndex() : -1;   // el dedo que se levanta ya no cuenta
        int n = 0;
        float x = 0, y = 0;
        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i == skip) continue;
            x += e.getX(i);
            y += e.getY(i);
            n++;
        }
        if (n == 0) return true;
        x /= n;
        y /= n;
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN || a == MotionEvent.ACTION_POINTER_UP) {
            lastX = x;
            lastY = y;
            lastCount = n;
        } else if (a == MotionEvent.ACTION_MOVE && n == lastCount) {
            tx += x - lastX;
            ty += y - lastY;
            lastX = x;
            lastY = y;
            invalidate();
        }
        return true;
    }
}
