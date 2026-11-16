package com.boveda.archivos;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** El plano en 3D con OpenGL: una malla de caras de colores que se gira con un dedo y se acerca con dos. */
final class PlanGlView extends GLSurfaceView {
    private final Renderer renderer = new Renderer();
    private final ScaleGestureDetector scaler;
    private float lastX, lastY;
    private int lastCount;
    private long lastTap;

    PlanGlView(Context c, int sx, int sy, int sz) {
        super(c);
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        renderer.cx = sx / 2f;
        renderer.cy = sy / 3f;
        renderer.cz = sz / 2f;
        renderer.radius = (float) Math.sqrt(sx * (double) sx + sz * (double) sz) / 2f;
        renderer.reset();
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                renderer.dist = Math.max(renderer.radius * 0.05f, Math.min(renderer.radius * 6f, renderer.dist / d.getScaleFactor()));
                requestRender();
                return true;
            }
        });
    }

    void setMesh(VoxelMesh mesh) {
        queueEvent(() -> renderer.upload(mesh));
        requestRender();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        int a = e.getActionMasked();
        int skip = a == MotionEvent.ACTION_POINTER_UP ? e.getActionIndex() : -1;
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
            long now = System.currentTimeMillis();
            if (now - lastTap < 320) {
                renderer.reset();
                requestRender();
            }
            lastTap = now;
        }
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN || a == MotionEvent.ACTION_POINTER_UP) {
            lastX = x;
            lastY = y;
            lastCount = n;
        } else if (a == MotionEvent.ACTION_MOVE && n == lastCount && !scaler.isInProgress()) {
            if (n == 1) {
                renderer.yaw -= (x - lastX) * 0.45f;
                renderer.pitch = Math.max(-89f, Math.min(89f, renderer.pitch + (y - lastY) * 0.35f));
            } else {
                // dos dedos: mover el punto que se mira
                float k = renderer.dist * 0.0016f;
                double yr = Math.toRadians(renderer.yaw);
                renderer.cx += (float) (-(x - lastX) * Math.cos(yr) * k);
                renderer.cz += (float) ((x - lastX) * Math.sin(yr) * k);
                renderer.cy += (y - lastY) * k;
            }
            lastX = x;
            lastY = y;
            requestRender();
        }
        return true;
    }

    private static final class Renderer implements GLSurfaceView.Renderer {
        float cx, cy, cz, radius, dist, yaw, pitch;
        int w = 1, h = 1;
        private int prog, aPos, aCol, uMvp;
        private final float[] proj = new float[16], view = new float[16], mvp = new float[16];
        private final List<int[]> bufs = new ArrayList<>();      // [vbo, ibo, indexCount]
        private ShortBuffer indexPattern;
        private VoxelMesh pending;

        void reset() {
            yaw = 35;
            pitch = 38;
            dist = radius * 1.7f;
        }

        void upload(VoxelMesh mesh) {
            pending = mesh;
            applyPending();
        }

        private void applyPending() {
            if (pending == null || prog == 0) return;
            VoxelMesh mesh = pending;
            pending = null;
            for (int[] b : bufs) GLES20.glDeleteBuffers(2, b, 0);
            bufs.clear();
            if (indexPattern == null) {
                ByteBuffer ib = ByteBuffer.allocateDirect(16000 * 6 * 2).order(ByteOrder.nativeOrder());
                indexPattern = ib.asShortBuffer();
                for (int q = 0; q < 16000; q++) {
                    int v = q * 4;
                    indexPattern.put((short) v).put((short) (v + 1)).put((short) (v + 2)).put((short) v).put((short) (v + 2)).put((short) (v + 3));
                }
            }
            for (VoxelMesh.Chunk c : mesh.chunks) {
                ByteBuffer vb = ByteBuffer.allocateDirect(c.quads * 4 * 16).order(ByteOrder.nativeOrder());
                for (int i = 0; i < c.quads * 4; i++) {
                    vb.putFloat(c.pos[i * 3]).putFloat(c.pos[i * 3 + 1]).putFloat(c.pos[i * 3 + 2]);
                    int col = c.col[i];
                    vb.put((byte) ((col >> 16) & 255)).put((byte) ((col >> 8) & 255)).put((byte) (col & 255)).put((byte) 255);
                }
                vb.position(0);
                int[] ids = new int[3];
                GLES20.glGenBuffers(2, ids, 0);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0]);
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vb.capacity(), vb, GLES20.GL_STATIC_DRAW);
                indexPattern.position(0);
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ids[1]);
                GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, c.quads * 6 * 2, indexPattern, GLES20.GL_STATIC_DRAW);
                ids[2] = c.quads * 6;
                bufs.add(ids);
            }
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        }

        private static int compile(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            return s;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.012f, 0.02f, 0.05f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            int vs = compile(GLES20.GL_VERTEX_SHADER, "uniform mat4 uMvp; attribute vec3 aPos; attribute vec4 aCol; varying vec4 vCol;\n"
                    + "void main(){ gl_Position = uMvp * vec4(aPos, 1.0); vCol = aCol; }");
            int fs = compile(GLES20.GL_FRAGMENT_SHADER, "precision mediump float; varying vec4 vCol; void main(){ gl_FragColor = vCol; }");
            prog = GLES20.glCreateProgram();
            GLES20.glAttachShader(prog, vs);
            GLES20.glAttachShader(prog, fs);
            GLES20.glLinkProgram(prog);
            aPos = GLES20.glGetAttribLocation(prog, "aPos");
            aCol = GLES20.glGetAttribLocation(prog, "aCol");
            uMvp = GLES20.glGetUniformLocation(prog, "uMvp");
            bufs.clear();            // los buffers viejos se perdieron con el contexto
            indexPattern = null;
            applyPending();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            w = Math.max(1, width);
            h = Math.max(1, height);
            GLES20.glViewport(0, 0, w, h);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (bufs.isEmpty()) return;
            Matrix.perspectiveM(proj, 0, 50f, w / (float) h, Math.max(0.05f, dist * 0.02f), dist * 20f + radius * 6f);
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            float ex = cx + (float) (dist * Math.cos(pr) * Math.sin(yr)), ey = cy + (float) (dist * Math.sin(pr)), ez = cz + (float) (dist * Math.cos(pr) * Math.cos(yr));
            Matrix.setLookAtM(view, 0, ex, ey, ez, cx, cy, cz, 0f, 1f, 0f);
            Matrix.multiplyMM(mvp, 0, proj, 0, view, 0);
            GLES20.glUseProgram(prog);
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
            for (int[] b : bufs) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, b[0]);
                GLES20.glEnableVertexAttribArray(aPos);
                GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 16, 0);
                GLES20.glEnableVertexAttribArray(aCol);
                GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_UNSIGNED_BYTE, true, 16, 12);
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, b[1]);
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, b[2], GLES20.GL_UNSIGNED_SHORT, 0);
            }
        }
    }
}
