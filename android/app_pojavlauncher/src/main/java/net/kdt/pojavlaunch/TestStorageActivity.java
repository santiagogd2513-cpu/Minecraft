package net.kdt.pojavlaunch;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;

public class TestStorageActivity extends Activity {
    private final int REQUEST_STORAGE_REQUEST_CODE = 1;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(Build.VERSION.SDK_INT >= 30) {
            // Amatista: para guardar todo en /AmatistaLauncher hace falta "Acceso a todos los archivos"
            if(android.os.Environment.isExternalStorageManager() || getSharedPreferences("amatista", MODE_PRIVATE).getBoolean("skip_all_files", false)) exit();
            else askAllFilesAccess();
            return;
        }
        if(Build.VERSION.SDK_INT >= 23 && !isStorageAllowed(this)) requestStoragePermission();
        else exit();
    }

    private boolean mWaitingAllFiles = false;

    private void askAllFilesAccess() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.amatista_storage_title)
                .setMessage(R.string.amatista_storage_message)
                .setCancelable(false)
                .setPositiveButton(R.string.amatista_storage_allow, (d, w) -> {
                    mWaitingAllFiles = true;
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                android.net.Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    }
                })
                .setNegativeButton(R.string.amatista_storage_skip, (d, w) -> {
                    getSharedPreferences("amatista", MODE_PRIVATE).edit().putBoolean("skip_all_files", true).apply();
                    exit();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!mWaitingAllFiles || Build.VERSION.SDK_INT < 30) return;
        mWaitingAllFiles = false;
        if(android.os.Environment.isExternalStorageManager()) exit();
        else askAllFilesAccess();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exit();
            } else {
                Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_LONG).show();
                requestStoragePermission();
            }
        }
    }

    public static boolean isStorageAllowed(Context context) {
        //Getting the permission status
        int result1 = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int result2 = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);


        //If permission is granted returning true
        return result1 == PackageManager.PERMISSION_GRANTED &&
                result2 == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {

        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_REQUEST_CODE);
    }

    private void exit() {
        if(!Tools.checkStorageRoot(this)) {
            startActivity(new Intent(this, MissingStorageActivity.class));
            return;
        }
        //Initialize constants (implicitly) and preferences after we confirm that we have storage.
        Tools.initStorageConstants(this);
        LauncherPreferences.loadPreferences(this);
        AsyncAssetManager.unpackComponents(this);
        AsyncAssetManager.unpackSingleFiles(this);

        Intent intent =  new Intent(this, LauncherActivity.class);
        startActivity(intent);
        finish();
    }
}
