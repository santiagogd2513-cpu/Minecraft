package com.boveda.archivos;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Imagen con zoom (pellizcar o dos toques), arrastre, giro y deslizar para pasar a la siguiente. */
final class ZoomImageView extends View {
    interface Listener {
        void onTap();

        void onSwipe(int dir);   // +1 siguiente, -1 anterior
    }

    private Drawable img;
    private int iw = 1, ih = 1, rot;
    private float scale = 1, tx, ty;
    private Listener listener;
    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;
    private ValueAnimator anim;

    ZoomImageView(Context c) {
        super(c);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoomAround(scale * d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                if (anim != null) anim.cancel();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (listener != null) listener.onTap();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float fit = fit();
                animateTo(scale > fit * 1.05f ? fit : fit * 2.6f, e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                if (scaler.isInProgress()) return false;
                tx -= dx;
                ty -= dy;
                clamp();
                invalidate();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || listener == null || zoomed()) return false;
                if (Math.abs(vx) > Math.abs(vy) * 1.3f && Math.abs(vx) > 900) {
                    listener.onSwipe(vx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setImage(Drawable d) {
        if (img != null) {
            img.setCallback(null);
            if (img instanceof Animatable) ((Animatable) img).stop();
        }
        img = d;
        rot = 0;
        if (d != null) {
            iw = Math.max(1, d.getIntrinsicWidth());
            ih = Math.max(1, d.getIntrinsicHeight());
            d.setBounds(0, 0, iw, ih);
            d.setCallback(this);
            if (d instanceof Animatable) ((Animatable) d).start();
        }
        reset();
    }

    void rotate() {
        rot = (rot + 90) % 360;
        reset();
    }

    private void reset() {
        scale = fit();
        tx = ty = 0;
        invalidate();
    }

    private boolean zoomed() {
        return scale > fit() * 1.02f;
    }

    private float fit() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return 1;
        boolean side = rot % 180 != 0;
        float cw = side ? ih : iw, ch = side ? iw : ih;
        return Math.min(w / cw, h / ch);
    }

    private void zoomAround(float ns, float fx, float fy) {
        float fit = fit();
        ns = Math.max(fit, Math.min(fit * 12, ns));
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        tx = fx - cx - (fx - cx - tx) * ns / scale;
        ty = fy - cy - (fy - cy - ty) * ns / scale;
        scale = ns;
        clamp();
        invalidate();
    }

    private void animateTo(float target, float fx, float fy) {
        if (anim != null) anim.cancel();
        float from = scale;
        anim = ValueAnimator.ofFloat(0, 1);
        anim.setDuration(220);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(v -> zoomAround(from + (target - from) * (float) v.getAnimatedValue(), fx, fy));
        anim.start();
    }

    private void clamp() {
        boolean side = rot % 180 != 0;
        float cw = (side ? ih : iw) * scale, ch = (side ? iw : ih) * scale;
        float mx = Math.max(0, (cw - getWidth()) / 2f), my = Math.max(0, (ch - getHeight()) / 2f);
        tx = Math.max(-mx, Math.min(mx, tx));
        ty = Math.max(-my, Math.min(my, ty));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        reset();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        gestures.onTouchEvent(e);
        if (e.getPointerCount() > 1 || zoomed()) getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (img == null) return;
        c.save();
        c.translate(getWidth() / 2f + tx, getHeight() / 2f + ty);
        c.scale(scale, scale);
        c.rotate(rot);
        c.translate(-iw / 2f, -ih / 2f);
        img.draw(c);
        c.restore();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == img || super.verifyDrawable(who);
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        invalidate();
    }
}
