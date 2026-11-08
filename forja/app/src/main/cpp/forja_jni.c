/* JNI: Ntfs.mkntfs(dispositivo, tamaño, argumentos) llama a mkntfs escribiendo por el dispositivo de Java. */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "forja_ntfs.h"

typedef struct {
    JNIEnv *env;
    jobject dev;
    jmethodID read, write, sync;
    jbyteArray buf;
    jint bufLen;
} ctx_t;

static int64_t jni_rw(void *p, int op, void *buf, int64_t len, int64_t off) {
    ctx_t *c = (ctx_t *) p;
    JNIEnv *env = c->env;
    if (op == 2) {
        jint r = (*env)->CallIntMethod(env, c->dev, c->sync);
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); return -1; }
        return r;
    }
    int64_t done = 0;
    while (done < len) {
        jint n = (jint) ((len - done) < c->bufLen ? (len - done) : c->bufLen);
        jint r;
        if (op == 1) {
            (*env)->SetByteArrayRegion(env, c->buf, 0, n, (const jbyte *) buf + done);
            r = (*env)->CallIntMethod(env, c->dev, c->write, (jlong) (off + done), c->buf, n);
        } else {
            r = (*env)->CallIntMethod(env, c->dev, c->read, (jlong) (off + done), c->buf, n);
            if (r > 0) (*env)->GetByteArrayRegion(env, c->buf, 0, r, (jbyte *) buf + done);
        }
        if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); return -1; }
        if (r <= 0) return done > 0 ? done : -1;
        done += r;
        if (r < n) break;
    }
    return done;
}

JNIEXPORT jint JNICALL
Java_com_forja_formateador_Ntfs_mkntfs(JNIEnv *env, jclass cls, jobject dev, jlong size, jobjectArray args) {
    (void) cls;
    ctx_t c;
    c.env = env;
    c.dev = dev;
    jclass dc = (*env)->GetObjectClass(env, dev);
    c.read = (*env)->GetMethodID(env, dc, "read", "(J[BI)I");
    c.write = (*env)->GetMethodID(env, dc, "write", "(J[BI)I");
    c.sync = (*env)->GetMethodID(env, dc, "sync", "()I");
    if (!c.read || !c.write || !c.sync) return -100;
    c.bufLen = 1024 * 1024;
    c.buf = (*env)->NewByteArray(env, c.bufLen);
    if (!c.buf) return -101;

    int argc = (*env)->GetArrayLength(env, args) + 1;
    char **argv = calloc(argc + 1, sizeof(char *));
    argv[0] = strdup("mkntfs");
    for (int i = 1; i < argc; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, args, i - 1);
        const char *u = (*env)->GetStringUTFChars(env, s, NULL);
        argv[i] = strdup(u);
        (*env)->ReleaseStringUTFChars(env, s, u);
        (*env)->DeleteLocalRef(env, s);
    }
    int r = forja_mkntfs(jni_rw, &c, (int64_t) size, argc, argv);
    for (int i = 0; i < argc; i++) free(argv[i]);
    free(argv);
    (*env)->DeleteLocalRef(env, c.buf);
    return r;
}

JNIEXPORT jstring JNICALL
Java_com_forja_formateador_Ntfs_log(JNIEnv *env, jclass cls) {
    (void) cls;
    return (*env)->NewStringUTF(env, forja_mkntfs_log());
}
