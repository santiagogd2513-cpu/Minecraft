/*
 * Puente entre mkntfs (de ntfs-3g) y Forja: en vez de abrir /dev/sdX, mkntfs lee y escribe
 * a través de estas funciones, que Forja conecta con la memoria USB (o con un archivo en las
 * pruebas). Los mensajes de mkntfs se guardan para mostrarlos si algo falla.
 */
#include "config.h"
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

#include "types.h"
#include "device.h"
#include "logging.h"
#include "forja_ntfs.h"

static forja_rw_fn g_rw;
static void *g_ctx;
static int64_t g_size, g_pos;
static char g_log[8192];
static size_t g_log_len;

static int f_open(struct ntfs_device *dev, int flags) {
    (void) flags;
    g_pos = 0;
    NDevSetOpen(dev);
    return 0;
}

static int f_close(struct ntfs_device *dev) {
    NDevClearOpen(dev);
    return 0;
}

static s64 f_seek(struct ntfs_device *dev, s64 offset, int whence) {
    (void) dev;
    switch (whence) {
        case SEEK_SET: g_pos = offset; break;
        case SEEK_CUR: g_pos += offset; break;
        case SEEK_END: g_pos = g_size + offset; break;
        default: errno = EINVAL; return -1;
    }
    return g_pos;
}

static s64 f_pread(struct ntfs_device *dev, void *buf, s64 count, s64 offset) {
    (void) dev;
    if (offset >= g_size) return 0;
    if (offset + count > g_size) count = g_size - offset;
    s64 r = g_rw(g_ctx, 0, buf, count, offset);
    if (r < 0) errno = EIO;
    return r;
}

static s64 f_pwrite(struct ntfs_device *dev, const void *buf, s64 count, s64 offset) {
    (void) dev;
    if (offset + count > g_size) { errno = ENOSPC; return -1; }
    s64 r = g_rw(g_ctx, 1, (void *) buf, count, offset);
    if (r < 0) errno = EIO;
    return r;
}

static s64 f_read(struct ntfs_device *dev, void *buf, s64 count) {
    s64 r = f_pread(dev, buf, count, g_pos);
    if (r > 0) g_pos += r;
    return r;
}

static s64 f_write(struct ntfs_device *dev, const void *buf, s64 count) {
    s64 r = f_pwrite(dev, buf, count, g_pos);
    if (r > 0) g_pos += r;
    return r;
}

static int f_sync(struct ntfs_device *dev) {
    (void) dev;
    return g_rw(g_ctx, 2, NULL, 0, 0) < 0 ? -1 : 0;
}

static int f_stat(struct ntfs_device *dev, struct stat *st) {
    (void) dev;
    memset(st, 0, sizeof(*st));
    st->st_mode = S_IFREG | 0600;     /* con -F, mkntfs lo acepta y usa el tamaño que le damos */
    st->st_size = g_size;
    return 0;
}

static int f_ioctl(struct ntfs_device *dev, unsigned long request, void *argp) {
    (void) dev; (void) request; (void) argp;
    errno = EOPNOTSUPP;
    return -1;
}

struct ntfs_device_operations forja_io_ops = {
    .open = f_open, .close = f_close, .seek = f_seek, .read = f_read, .write = f_write,
    .pread = f_pread, .pwrite = f_pwrite, .sync = f_sync, .stat = f_stat, .ioctl = f_ioctl,
};

/* Reemplaza ntfs_log_handler_outerr de mkntfs: guarda los mensajes. */
int forja_log_handler(const char *function, const char *file, int line, u32 level, void *data,
                      const char *format, va_list args) {
    (void) function; (void) file; (void) line; (void) data;
    if (level & (NTFS_LOG_LEVEL_DEBUG | NTFS_LOG_LEVEL_TRACE)) return 0;
    char tmp[1024];
    int n = vsnprintf(tmp, sizeof(tmp), format, args);
    if (n < 0) return 0;
    if ((size_t) n >= sizeof(tmp)) n = sizeof(tmp) - 1;
    if (g_log_len + n >= sizeof(g_log)) {           /* se queda con lo último */
        size_t keep = sizeof(g_log) / 2;
        memmove(g_log, g_log + g_log_len - keep, keep);
        g_log_len = keep;
    }
    memcpy(g_log + g_log_len, tmp, n);
    g_log_len += n;
    g_log[g_log_len] = 0;
    return n;
}

extern int forja_mkntfs_main(int argc, char *argv[]);

int forja_mkntfs(forja_rw_fn rw, void *ctx, int64_t size, int argc, char **argv) {
    g_rw = rw;
    g_ctx = ctx;
    g_size = size;
    g_pos = 0;
    g_log_len = 0;
    g_log[0] = 0;
    optind = 0;                     /* getopt desde cero en cada llamada */
    return forja_mkntfs_main(argc, argv);
}

const char *forja_mkntfs_log(void) {
    return g_log;
}
