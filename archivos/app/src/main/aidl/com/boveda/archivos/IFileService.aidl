package com.boveda.archivos;

import android.os.ParcelFileDescriptor;

// Servicio que corre como "shell" a través de Shizuku: puede entrar a Android/data y Android/obb.
interface IFileService {
    void destroy() = 16777114;
    String list(String path) = 1;
    String stat(String path) = 2;
    boolean mkdirs(String path) = 3;
    boolean delete(String path) = 4;
    boolean rename(String from, String to) = 5;
    ParcelFileDescriptor open(String path, String mode) = 6;
    boolean setModified(String path, long time) = 7;
}
