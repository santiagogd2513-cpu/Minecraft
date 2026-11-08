/* Diferencias entre la libc de Android (bionic) y la de Linux que necesita ntfs-3g. */
#ifndef FORJA_COMPAT_H
#define FORJA_COMPAT_H
#include <strings.h>      /* ffs() */
#include <sys/stat.h>
#ifndef S_IREAD
#define S_IREAD S_IRUSR
#endif
#ifndef S_IWRITE
#define S_IWRITE S_IWUSR
#endif
#ifndef S_IEXEC
#define S_IEXEC S_IXUSR
#endif
#endif
