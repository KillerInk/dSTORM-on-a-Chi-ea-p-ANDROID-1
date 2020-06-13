package de.nanoimaging.stormimager.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;

import de.nanoimaging.stormimager.StormApplication;
import de.nanoimaging.stormimager.acquisition.AcquireActivity;

public class PermissionUtil {
    /**
     * Request code for camera permissions.
     */
    private static final int REQUEST_CAMERA_PERMISSIONS = 1;
    /**
     * Permissions required to take a picture.
     */
    private static final String[] CAMERA_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
    };

    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSIONS) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    //showMissingPermissionError();
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Requests permissions necessary to use camera and save pictures.
     */
    public void requestCameraPermissions(Activity activity) {
        ActivityCompat.requestPermissions(activity, CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSIONS);
    }

    /**
     * Tells whether all the necessary permissions are granted to this app.
     *
     * @return True if all the required permissions are granted.
     */
    public boolean hasAllPermissionsGranted() {
        for (String permission : CAMERA_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(StormApplication.getContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
