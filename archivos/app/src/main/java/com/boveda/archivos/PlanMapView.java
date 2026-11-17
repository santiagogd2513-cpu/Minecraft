package com.boveda.archivos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** El plano en 2D: desde arriba, por capas o en cortes. Un dedo mueve, dos dedos acercan; los píxeles se ven nítidos. */
final class PlanMapView extends View {
    static final int TOP = 0, LAYER = 1, CUT_Z = 2, CUT_X = 3;

    interface OnEntity {
        void on(String label, double x, double y, double z);
    }

    OnEntity onEntity;
    int mode = TOP, level;
    boolean showEntities = true;
    private final Paint pe = new Paint(Paint.ANTI_ALIAS_FLAG), pt = new Paint(Paint.ANTI_ALIAS_FLAG), po = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private float downX, downY;
    private boolean moved;
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
        pe.setStyle(Paint.Style.FILL);
        po.setStyle(Paint.Style.STROKE);
        po.setColor(0xFFFFFFFF);
        po.setStrokeWidth(Math.max(2f, c.getResources().getDisplayMetrics().density * 1.5f));
        pt.setColor(0xFFFFFFFF);
        pt.setTextSize(c.getResources().getDisplayMetrics().density * 11);
        pt.setShadowLayer(4f, 0, 0, 0xFF000000);
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

    void show(Bitmap b, int mode, int level) {
        this.mode = mode;
        this.level = level;
        show(b);
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
        if (showEntities) drawEntities(c);
    }

    private float dens() {
        return getResources().getDisplayMetrics().density;
    }

    /**
     * Rectángulo en pantalla de una entidad según el tipo de mapa. En «arriba» y «capas» es su huella (girada); en los cortes,
     * su silueta vista de frente o de lado. Devuelve false si no se ve en este mapa.
     */
    private boolean place(BdPlan.Ent e, EntityModels.Model md, float[] out) {
        float[] c8 = new float[24];
        float mnx = 1e9f, mxx = -1e9f, mnz = 1e9f, mxz = -1e9f;
        for (EntityModels.Box b : md.boxes) {
            EntityModels.corners(e, b, c8);
            for (int i = 0; i < 8; i++) {
                mnx = Math.min(mnx, c8[i * 3]);
                mxx = Math.max(mxx, c8[i * 3]);
                mnz = Math.min(mnz, c8[i * 3 + 2]);
                mxz = Math.max(mxz, c8[i * 3 + 2]);
            }
        }
        float y0 = (float) e.y, y1 = (float) e.y + md.h;
        if (mode == LAYER && !(y0 < level + 1 && y1 > level)) return false;
        if (mode == CUT_Z) {
            if (mxz < level || mnz > level + 1) return false;
            out[0] = tx + mnx * scale;
            out[2] = tx + mxx * scale;
            out[1] = ty + (plan.sy - y1) * scale;
            out[3] = ty + (plan.sy - y0) * scale;
            return true;
        }
        if (mode == CUT_X) {
            if (mxx < level || mnx > level + 1) return false;
            out[0] = tx + mnz * scale;
            out[2] = tx + mxz * scale;
            out[1] = ty + (plan.sy - y1) * scale;
            out[3] = ty + (plan.sy - y0) * scale;
            return true;
        }
        out[0] = tx + mnx * scale;
        out[2] = tx + mxx * scale;
        out[1] = ty + mnz * scale;
        out[3] = ty + mxz * scale;
        return true;
    }

    private void drawEntities(Canvas c) {
        float d = dens(), minPx = d * 7;
        float[] r = new float[4];
        for (BdPlan.Ent e : plan.entityList) {
            EntityModels.Model md = EntityModels.of(e.id);
            if (!place(e, md, r)) continue;
            float cx = (r[0] + r[2]) / 2, cy = (r[1] + r[3]) / 2, w = Math.max(minPx, r[2] - r[0]), h = Math.max(minPx, r[3] - r[1]);
            pe.setColor((md.color & 0x00FFFFFF) | 0xDD000000);
            if (mode == TOP || mode == LAYER) {
                // la huella verdadera, girada según hacia dónde mira; con un triángulo al frente
                float fw = Math.max(minPx, md.w * scale), fl = Math.max(minPx, md.l * scale);
                c.save();
                c.translate(cx, cy);
                c.rotate(e.yaw);
                rf.set(-fw / 2, -fl / 2, fw / 2, fl / 2);
                c.drawRect(rf, pe);
                c.drawRect(rf, po);
                po.setStyle(Paint.Style.FILL);
                android.graphics.Path tri = new android.graphics.Path();
                tri.moveTo(0, fl / 2 + d * 4);
                tri.lineTo(-d * 3.5f, fl / 2 - d * 1.5f);
                tri.lineTo(d * 3.5f, fl / 2 - d * 1.5f);
                tri.close();
                c.drawPath(tri, po);
                po.setStyle(Paint.Style.STROKE);
                c.restore();
            } else {
                rf.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
                c.drawRect(rf, pe);
                c.drawRect(rf, po);
            }
            if (scale >= 3 || md.l > 6) c.drawText(EntityModels.label(e), cx + w / 2 + d * 3, cy - h / 2, pt);
        }
    }

    private void tapAt(float x, float y) {
        if (onEntity == null || !showEntities) return;
        float pad = dens() * 14, best = Float.MAX_VALUE;
        BdPlan.Ent hit = null;
        float[] r = new float[4];
        for (BdPlan.Ent e : plan.entityList) {
            EntityModels.Model md = EntityModels.of(e.id);
            if (!place(e, md, r)) continue;
            float cx = (r[0] + r[2]) / 2, cy = (r[1] + r[3]) / 2;
            float hw = Math.max(dens() * 7, (mode == TOP || mode == LAYER ? Math.max(md.w, md.l) * scale : r[2] - r[0]) / 2) + pad;
            float hh = Math.max(dens() * 7, (mode == TOP || mode == LAYER ? Math.max(md.w, md.l) * scale : r[3] - r[1]) / 2) + pad;
            if (Math.abs(x - cx) <= hw && Math.abs(y - cy) <= hh) {
                float dd = Math.abs(x - cx) + Math.abs(y - cy);
                if (dd < best) {
                    best = dd;
                    hit = e;
                }
            }
        }
        if (hit != null) onEntity.on(EntityModels.label(hit), hit.x, hit.y, hit.z);
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
        if (a == MotionEvent.ACTION_DOWN) {
            downX = x;
            downY = y;
            moved = false;
        }
        if (a == MotionEvent.ACTION_MOVE && Math.hypot(x - downX, y - downY) > dens() * 8) moved = true;
        if (a == MotionEvent.ACTION_POINTER_DOWN) moved = true;
        if (a == MotionEvent.ACTION_UP && !moved) tapAt(x, y);
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
