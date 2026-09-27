package com.tapemeasure.ar

import com.google.ar.core.Camera
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState

/** Turns ARCore's tracking state into a short, human-readable status message. */
object TrackingStateHelper {

    fun statusFor(camera: Camera): String? {
        return when (camera.trackingState) {
            TrackingState.TRACKING -> null
            TrackingState.PAUSED ->
                when (camera.trackingFailureReason) {
                    TrackingFailureReason.NONE -> "Point your camera at a surface"
                    TrackingFailureReason.BAD_STATE -> "Tracking lost — move your phone slowly"
                    TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark — find a brighter area"
                    TrackingFailureReason.EXCESSIVE_MOTION -> "Move your phone more slowly"
                    TrackingFailureReason.INSUFFICIENT_FEATURES ->
                        "Point at a surface with more detail or texture"
                    TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera is unavailable"
                    else -> "Move your phone slowly to resume tracking"
                }
            TrackingState.STOPPED -> "Tracking stopped"
            else -> null
        }
    }
}
