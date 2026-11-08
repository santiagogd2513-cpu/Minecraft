# copia la partición 1 (MBR o GPT) a un archivo disperso, leyendo solo los tramos con datos
import os, sys, struct
img, out, ss = sys.argv[1], sys.argv[2], int(sys.argv[3])
f = open(img, 'rb'); s0 = f.read(512)
if s0[450] == 0xEE:
    f.seek(ss); h = f.read(92); ent = struct.unpack('<Q', h[72:80])[0]
    f.seek(ent * ss); e = f.read(128); start, end = struct.unpack('<QQ', e[32:48]); count = end - start + 1
else:
    start, count = struct.unpack('<II', s0[454:462])
a, b = start * ss, (start + count) * ss
fd = f.fileno(); o = open(out, 'wb'); o.truncate(b - a)
pos = a
while pos < b:
    try: d = os.lseek(fd, pos, os.SEEK_DATA)
    except OSError: break
    if d >= b: break
    h = min(os.lseek(fd, d, os.SEEK_HOLE), b)
    f.seek(d); o.seek(d - a)
    while d < h:
        n = min(8 << 20, h - d); o.write(f.read(n)); d += n
    pos = h
print(start, count)
