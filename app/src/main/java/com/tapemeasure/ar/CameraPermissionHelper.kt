package com.tapemeasure.ar

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Handles the runtime camera permission required to run ARCore. */
object CameraPermissionHelper {
    private const val CAMERA_PERMISSION = android.Manifest.permission.CAMERA
    private const val CAMERA_PERMISSION_CODE = 1001

    fun hasCameraPermission(activity: Activity): Boolean =
        ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    fun requestCameraPermission(activity: Activity) {
        ActivityCompat.requestPermissions(activity, arrayOf(CAMERA_PERMISSION), CAMERA_PERMISSION_CODE)
    }

    fun isCameraPermissionRequest(requestCode: Int): Boolean = requestCode == CAMERA_PERMISSION_CODE

    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA_PERMISSION)

    /** Opens this app's system settings page so the user can grant camera access manually. */
    fun launchPermissionSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", activity.packageName, null)
        activity.startActivity(intent)
    }
}
