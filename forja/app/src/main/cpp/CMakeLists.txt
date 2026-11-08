# mkntfs (de ntfs-3g, GPLv2+) dentro de Forja, con un puente que escribe en la USB.
cmake_minimum_required(VERSION 3.18)
project(forjantfs C)

set(N ${CMAKE_CURRENT_SOURCE_DIR}/ntfs-3g)
file(GLOB LIBNTFS ${N}/libntfs-3g/*.c)
add_library(forjantfs SHARED
    ${LIBNTFS}
    ${N}/ntfsprogs/attrdef.c ${N}/ntfsprogs/boot.c ${N}/ntfsprogs/sd.c ${N}/ntfsprogs/utils.c
    ${N}/ntfsprogs/mkntfs.c
    forja_ntfs.c forja_jni.c)
target_include_directories(forjantfs PRIVATE ntfs-config ${N}/include/ntfs-3g ${N}/ntfsprogs .)
target_compile_definitions(forjantfs PRIVATE HAVE_CONFIG_H _FILE_OFFSET_BITS=64)
target_compile_options(forjantfs PRIVATE -O2 -w -fno-strict-aliasing)
# mkntfs: su main se llama desde Java, escribe por el puente y guarda los mensajes
set_source_files_properties(${N}/ntfsprogs/mkntfs.c PROPERTIES COMPILE_DEFINITIONS
    "main=forja_mkntfs_main;ntfs_device_unix_io_ops=forja_io_ops;ntfs_log_handler_outerr=forja_log_handler")
target_link_libraries(forjantfs log)
