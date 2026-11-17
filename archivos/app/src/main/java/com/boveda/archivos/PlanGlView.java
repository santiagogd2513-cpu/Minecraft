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
        renderer.cy = sy / 4f;
        renderer.reset();
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                if (renderer.free) return true;
                renderer.dist = Math.max(renderer.radius * 0.05f, Math.min(renderer.radius * 6f, renderer.dist / d.getScaleFactor()));
                requestRender();
                return true;
            }
        });
    }

    boolean isFree() {
        return renderer.free;
    }

    void setFree(boolean on) {
        if (on) renderer.enterFree();
        else renderer.exitFree();
        requestRender();
    }

    /** Mueve la cámara libre (en bloques). */
    void moveFree(float fwd, float strafe, float up) {
        if (!renderer.free) return;
        renderer.move(fwd, strafe, up);
        requestRender();
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
            if (renderer.free) {
                // cámara libre: un dedo mira alrededor (como girar la cabeza)
                if (n == 1) {
                    renderer.fyaw -= (x - lastX) * 0.3f;
                    renderer.fpitch = Math.max(-89f, Math.min(89f, renderer.fpitch - (y - lastY) * 0.25f));
                }
            } else if (n == 1) {
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


    /**
     * El dibujo de las superficies: cada bloque se reconoce (sus bordes, su variación de tono) y cada material tiene su detalle:
     * piedra con motas, tierra rugosa, tablones con vetas, troncos rayados, ladrillos con juntas, hojas ruidosas, metal cepillado, cristal con marco…
     * De lejos se apaga el detalle fino para que no parpadee.
     */
    private static final String FRAGMENT = "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
            + "varying vec3 vWorld; varying vec3 vNrm; varying vec4 vCol; varying float vDist;\n"
            + "float h3(vec3 p){ return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453); }\n"
            + "void main(){\n"
            + "  float mat = floor(vCol.a * 255.0 + 0.5);\n"
            + "  vec3 c = vCol.rgb;\n"
            + "  if (mat < 250.0) {\n"
            + "    vec3 an = abs(vNrm);\n"
            + "    vec3 bp = floor(vWorld - 0.02 * vNrm);\n"
            + "    vec2 f = an.x > 0.5 ? fract(vWorld.zy) : (an.y > 0.5 ? fract(vWorld.xz) : fract(vWorld.xy));\n"
            + "    float far = smoothstep(30.0, 110.0, vDist);\n"
            + "    float hb = h3(bp);\n"
            + "    float vary = 0.90 + 0.20 * hb;\n"
            + "    float e = min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y));\n"
            + "    float edge = smoothstep(0.0, 0.07, e);\n"
            + "    float fine = 1.0;\n"
            + "    float es = 0.84;\n"
            + "    if (mat < 0.5) { fine = 0.94 + 0.10 * h3(vec3(floor(f * 4.0), hb * 10.0)); }\n"
            + "    else if (mat < 1.5) { fine = 0.86 + 0.26 * h3(vec3(floor(f * 5.0), bp.x + bp.y * 7.0 + bp.z * 13.0)); es = 0.80; }\n"
            + "    else if (mat < 2.5) { fine = 0.80 + 0.40 * h3(vec3(floor(f * 8.0), hb * 50.0)); es = 0.86; }\n"
            + "    else if (mat < 3.5) { float bd = fract(f.y * 4.0); float l = smoothstep(0.0, 0.07, min(bd, 1.0 - bd)); fine = (0.78 + 0.22 * l) * (0.94 + 0.06 * sin(f.x * 30.0 + bp.y * 3.0 + hb * 6.0)); es = 0.92; }\n"
            + "    else if (mat < 4.5) { fine = 0.80 + 0.20 * (0.5 + 0.5 * sin(f.x * 38.0 + hb * 6.0)); es = 0.86; }\n"
            + "    else if (mat < 5.5) { float rw = floor(f.y * 4.0); float off = mod(rw, 2.0) * 0.5; float bx = fract(f.x * 2.0 + off); float m1 = min(fract(f.y * 4.0), 1.0 - fract(f.y * 4.0)); float m2 = min(bx, 1.0 - bx);\n"
            + "      float mort = (m1 < 0.07 || m2 < 0.05) ? 1.0 : 0.0; c = mix(c, vec3(0.80, 0.78, 0.73), mort * 0.85 * (1.0 - far)); fine = 0.88 + 0.24 * h3(vec3(rw, floor(f.x * 2.0 + off), hb * 40.0)); es = 0.95; }\n"
            + "    else if (mat < 6.5) { fine = 0.97 + 0.06 * hb; es = 0.93; }\n"
            + "    else if (mat < 7.5) { c = mix(c, vec3(1.0), 0.40 * (1.0 - edge)); fine = 1.05; es = 1.0; }\n"
            + "    else if (mat < 8.5) { fine = 0.86 + 0.14 * sin(f.y * 70.0 + hb * 5.0); c += vec3(0.06); es = 0.88; }\n"
            + "    else if (mat < 9.5) { fine = 0.62 + 0.58 * h3(vec3(floor(f * 7.0), hb * 30.0)); es = 0.92; }\n"
            + "    else if (mat < 10.5) { fine = 0.90 + 0.20 * h3(vec3(floor(f * 12.0), hb * 20.0)); es = 0.92; }\n"
            + "    else if (mat < 11.5) { fine = 0.92 + 0.08 * sin(f.x * 12.0 + f.y * 9.0 + hb * 8.0); es = 1.0; }\n"
            + "    float k = vary * fine * mix(es, 1.0, edge);\n"
            + "    c = c * mix(k, vary, far);\n"
            + "  }\n"
            + "  gl_FragColor = vec4(c, 1.0);\n"
            + "}\n";

    private static final class Renderer implements GLSurfaceView.Renderer {
        float cx, cy, cz, radius, dist, yaw, pitch;
        int w = 1, h = 1;
        private int prog, aPos, aCol, aNrm, uMvp, uEye;
        private float eyeX, eyeY, eyeZ;
        private final float[] proj = new float[16], view = new float[16], mvp = new float[16];
        private final List<int[]> bufs = new ArrayList<>();      // [vbo, ibo, indexCount]
        private ShortBuffer indexPattern;
        private VoxelMesh pending;

        // cámara libre (volar por dentro del plano)
        volatile boolean free;
        float fx, fy, fz, fyaw, fpitch;
        float sx0, sy0, sz0, syaw0, spitch0;     // dónde empezó, para volver con dos toques

        void reset() {
            if (free) {
                fx = sx0;
                fy = sy0;
                fz = sz0;
                fyaw = syaw0;
                fpitch = spitch0;
                return;
            }
            yaw = 35;
            pitch = 20;                 // más baja que antes: se ven los detalles de los edificios
            dist = radius * 1.35f;
        }

        /** Entra a la cámara libre desde donde está la cámara de órbita, sin saltos. */
        void enterFree() {
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
            fx = cx + (float) (dist * Math.cos(pr) * Math.sin(yr));
            fy = cy + (float) (dist * Math.sin(pr));
            fz = cz + (float) (dist * Math.cos(pr) * Math.cos(yr));
            fyaw = yaw + 180f;
            fpitch = -pitch;
            sx0 = fx;
            sy0 = fy;
            sz0 = fz;
            syaw0 = fyaw;
            spitch0 = fpitch;
            free = true;
        }

        void exitFree() {
            free = false;
        }

        /** Mueve la cámara libre: adelante/atrás, a los lados y arriba/abajo, en bloques. */
        void move(float fwd, float strafe, float up) {
            double yr = Math.toRadians(fyaw), pr = Math.toRadians(fpitch);
            float dx = (float) (Math.sin(yr) * Math.cos(pr)), dy = (float) Math.sin(pr), dz = (float) (Math.cos(yr) * Math.cos(pr));
            float rx = (float) -Math.cos(yr), rz = (float) Math.sin(yr);
            fx += dx * fwd + rx * strafe;
            fy += dy * fwd + up;
            fz += dz * fwd + rz * strafe;
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
                ByteBuffer vb = ByteBuffer.allocateDirect(c.quads * 4 * 20).order(ByteOrder.nativeOrder());
                for (int i = 0; i < c.quads * 4; i++) {
                    vb.putFloat(c.pos[i * 3]).putFloat(c.pos[i * 3 + 1]).putFloat(c.pos[i * 3 + 2]);
                    int col = c.col[i];
                    // color y, en el último byte, qué material es; luego la normal de la cara
                    vb.put((byte) ((col >> 16) & 255)).put((byte) ((col >> 8) & 255)).put((byte) (col & 255)).put((byte) ((col >>> 24) & 255));
                    vb.put(c.nrm[i * 3]).put(c.nrm[i * 3 + 1]).put(c.nrm[i * 3 + 2]).put((byte) 0);
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
            int vs = compile(GLES20.GL_VERTEX_SHADER, "uniform mat4 uMvp; uniform vec3 uEye; attribute vec3 aPos; attribute vec4 aCol; attribute vec4 aNrm;\n"
                    + "varying vec3 vWorld; varying vec3 vNrm; varying vec4 vCol; varying float vDist;\n"
                    + "void main(){ gl_Position = uMvp * vec4(aPos, 1.0); vWorld = aPos; vNrm = aNrm.xyz; vCol = aCol; vDist = length(uEye - aPos); }");
            int fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT);
            prog = GLES20.glCreateProgram();
            GLES20.glAttachShader(prog, vs);
            GLES20.glAttachShader(prog, fs);
            GLES20.glLinkProgram(prog);
            aPos = GLES20.glGetAttribLocation(prog, "aPos");
            aCol = GLES20.glGetAttribLocation(prog, "aCol");
            aNrm = GLES20.glGetAttribLocation(prog, "aNrm");
            uMvp = GLES20.glGetUniformLocation(prog, "uMvp");
            uEye = GLES20.glGetUniformLocation(prog, "uEye");
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
            if (free) {
                Matrix.perspectiveM(proj, 0, 70f, w / (float) h, 0.05f, radius * 12f + 400f);
                double yr = Math.toRadians(fyaw), pr = Math.toRadians(fpitch);
                float dx = (float) (Math.sin(yr) * Math.cos(pr)), dy = (float) Math.sin(pr), dz = (float) (Math.cos(yr) * Math.cos(pr));
                Matrix.setLookAtM(view, 0, fx, fy, fz, fx + dx, fy + dy, fz + dz, 0f, 1f, 0f);
                eyeX = fx;
                eyeY = fy;
                eyeZ = fz;
            } else {
                Matrix.perspectiveM(proj, 0, 50f, w / (float) h, Math.max(0.05f, dist * 0.02f), dist * 20f + radius * 6f);
                double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
                float ex = cx + (float) (dist * Math.cos(pr) * Math.sin(yr)), ey = cy + (float) (dist * Math.sin(pr)), ez = cz + (float) (dist * Math.cos(pr) * Math.cos(yr));
                Matrix.setLookAtM(view, 0, ex, ey, ez, cx, cy, cz, 0f, 1f, 0f);
                eyeX = ex;
                eyeY = ey;
                eyeZ = ez;
            }
            Matrix.multiplyMM(mvp, 0, proj, 0, view, 0);
            GLES20.glUseProgram(prog);
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
            GLES20.glUniform3f(uEye, eyeX, eyeY, eyeZ);
            for (int[] b : bufs) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, b[0]);
                GLES20.glEnableVertexAttribArray(aPos);
                GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 20, 0);
                GLES20.glEnableVertexAttribArray(aCol);
                GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_UNSIGNED_BYTE, true, 20, 12);
                GLES20.glEnableVertexAttribArray(aNrm);
                GLES20.glVertexAttribPointer(aNrm, 4, GLES20.GL_BYTE, true, 20, 16);
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, b[1]);
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, b[2], GLES20.GL_UNSIGNED_SHORT, 0);
            }
        }
    }
}
