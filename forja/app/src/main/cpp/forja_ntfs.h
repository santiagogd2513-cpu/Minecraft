#ifndef FORJA_NTFS_H
#define FORJA_NTFS_H
#include <stdint.h>
#include <getopt.h>
/* op: 0 leer, 1 escribir, 2 sincronizar. Devuelve los bytes procesados o -1. */
typedef int64_t (*forja_rw_fn)(void *ctx, int op, void *buf, int64_t len, int64_t off);
int forja_mkntfs(forja_rw_fn rw, void *ctx, int64_t size, int argc, char **argv);
const char *forja_mkntfs_log(void);
#endif
