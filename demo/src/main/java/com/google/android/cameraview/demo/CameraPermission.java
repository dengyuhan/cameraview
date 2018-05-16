package com.google.android.cameraview.demo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

/**
 * @author dengyuhan
 *         created 2018/5/15 17:15
 */
public class CameraPermission {

    public static void camera(Activity activity) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.CAMERA);
        }
    }
}
