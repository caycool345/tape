package com.tapemeasure.ar

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.tapemeasure.ar.databinding.ActivityMainBinding
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/**
 * Turns the phone's camera into an AR tape measure: tap a surface to drop the start point, tap
 * again to drop the end point, and the straight-line distance between them (in metric units) is
 * shown on screen. A third tap starts a new measurement.
 */
class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private lateinit var binding: ActivityMainBinding
    private lateinit var displayRotationHelper: DisplayRotationHelper

    private var session: Session? = null
    private var installRequested = false

    private val backgroundRenderer = BackgroundRenderer()
    private val pointLineRenderer = PointLineRenderer()

    private val anchors = mutableListOf<Anchor>()
    private val tapQueue = ArrayBlockingQueue<MotionEvent>(16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        displayRotationHelper = DisplayRotationHelper(this)

        binding.surfaceview.preserveEGLContextOnPause = true
        binding.surfaceview.setEGLContextClientVersion(2)
        binding.surfaceview.setRenderer(this)
        binding.surfaceview.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        binding.surfaceview.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                tapQueue.offer(MotionEvent.obtain(event))
            }
            true
        }

        binding.resetButton.setOnClickListener { clearMeasurement() }

        setStatus(getString(R.string.status_move_phone))
    }

    // ---------------------------------------------------------------------
    // Activity / session lifecycle
    // ---------------------------------------------------------------------

    override fun onResume() {
        super.onResume()

        if (session == null) {
            var message: String? = null
            try {
                if (!tryCreateSession()) {
                    return
                }
            } catch (e: UnavailableArcoreNotInstalledException) {
                message = "Please install Google Play Services for AR"
            } catch (e: UnavailableUserDeclinedInstallationException) {
                message = "Google Play Services for AR is required"
            } catch (e: UnavailableApkTooOldException) {
                message = "Please update Google Play Services for AR"
            } catch (e: UnavailableSdkTooOldException) {
                message = "Please update this app"
            } catch (e: UnavailableDeviceNotCompatibleException) {
                message = getString(R.string.arcore_unavailable)
            } catch (e: Exception) {
                message = "Failed to create AR session: ${e.message}"
            }
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                Log.e(TAG, "Session creation failed", null)
                return
            }
        }

        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            Toast.makeText(this, "Camera not available. Try restarting the app.", Toast.LENGTH_LONG)
                .show()
            session = null
            return
        }
        binding.surfaceview.onResume()
        displayRotationHelper.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (session != null) {
            displayRotationHelper.onPause()
            binding.surfaceview.onPause()
            session?.pause()
        }
    }

    override fun onDestroy() {
        session?.close()
        session = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, getString(R.string.camera_permission_needed), Toast.LENGTH_LONG)
                .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    /** Returns true once a Session exists and is ready to be resumed. */
    private fun tryCreateSession(): Boolean {
        when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                installRequested = true
                return false
            }
            ArCoreApk.InstallStatus.INSTALLED -> {}
        }

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this)
            return false
        }

        val newSession = Session(this)
        val config = Config(newSession).apply {
            focusMode = Config.FocusMode.AUTO
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
            if (newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                depthMode = Config.DepthMode.AUTOMATIC
            }
        }
        newSession.configure(config)
        session = newSession
        return true
    }

    // ---------------------------------------------------------------------
    // GLSurfaceView.Renderer
    // ---------------------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        backgroundRenderer.createOnGlThread()
        pointLineRenderer.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val session = this.session ?: return
        displayRotationHelper.updateSessionIfNeeded(session)

        try {
            session.setCameraTextureName(backgroundRenderer.textureId)
            val frame = session.update()
            val camera = frame.camera

            backgroundRenderer.draw(frame)

            if (camera.trackingState == TrackingState.TRACKING) {
                handleTap(frame, camera)
            } else {
                // Discard any taps that arrived while tracking was unavailable.
                tapQueue.poll()?.recycle()
            }

            val trackingMessage = TrackingStateHelper.statusFor(camera)
            runOnUiThread { updateStatusForTrackingState(trackingMessage) }

            if (camera.trackingState == TrackingState.TRACKING) {
                val viewMatrix = FloatArray(16)
                val projectionMatrix = FloatArray(16)
                val viewProjectionMatrix = FloatArray(16)
                camera.getViewMatrix(viewMatrix, 0)
                camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR)
                Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

                val worldPoints = synchronized(anchors) {
                    anchors.map { floatArrayOf(it.pose.tx(), it.pose.ty(), it.pose.tz()) }
                }
                pointLineRenderer.draw(worldPoints, viewProjectionMatrix)
            }
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available during onDrawFrame", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception on the OpenGL thread", e)
        }
    }

    // ---------------------------------------------------------------------
    // Measuring logic
    // ---------------------------------------------------------------------

    private fun handleTap(frame: Frame, camera: com.google.ar.core.Camera) {
        val tap = tapQueue.poll() ?: return
        try {
            if (camera.trackingState != TrackingState.TRACKING) return

            val hit = findBestHit(frame, tap) ?: return
            addPointFromHit(hit)
        } finally {
            tap.recycle()
        }
    }

    private fun findBestHit(frame: Frame, tap: MotionEvent): HitResult? {
        val cameraPose = frame.camera.pose
        val hits = frame.hitTest(tap)
        for (hit in hits) {
            when (val trackable = hit.trackable) {
                is Plane ->
                    if (trackable.isPoseInPolygon(hit.hitPose) &&
                        distanceFromPlaneToCamera(hit.hitPose, cameraPose) > 0
                    ) {
                        return hit
                    }
                is DepthPoint -> return hit
                is Point ->
                    if (trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL) {
                        return hit
                    }
            }
        }
        // No real surface found yet (planes still being detected) — fall back to an
        // approximate depth so the tape can still be used right away.
        val instantHits = frame.hitTestInstantPlacement(
            tap.x, tap.y, APPROXIMATE_DISTANCE_METERS
        )
        return instantHits.firstOrNull()
    }

    /** Dot product of the plane's normal (its local Y axis) with the vector to the camera. */
    private fun distanceFromPlaneToCamera(
        planePose: com.google.ar.core.Pose,
        cameraPose: com.google.ar.core.Pose
    ): Float {
        val normal = FloatArray(3)
        planePose.getTransformedAxis(1, 1.0f, normal, 0)
        val toCameraX = cameraPose.tx() - planePose.tx()
        val toCameraY = cameraPose.ty() - planePose.ty()
        val toCameraZ = cameraPose.tz() - planePose.tz()
        return normal[0] * toCameraX + normal[1] * toCameraY + normal[2] * toCameraZ
    }

    private fun addPointFromHit(hit: HitResult) {
        val size = synchronized(anchors) {
            if (anchors.size >= 2) {
                anchors.forEach { it.detach() }
                anchors.clear()
            }
            anchors.add(hit.createAnchor())
            anchors.size
        }

        when (size) {
            1 -> setStatus(getString(R.string.status_tap_second_point))
            2 -> showDistance()
        }
    }

    private fun showDistance() {
        val (poseA, poseB) = synchronized(anchors) { anchors[0].pose to anchors[1].pose }
        val dx = poseA.tx() - poseB.tx()
        val dy = poseA.ty() - poseB.ty()
        val dz = poseA.tz() - poseB.tz()
        val meters = sqrt(dx * dx + dy * dy + dz * dz)

        val formatted =
            if (meters < 1f) {
                String.format(Locale.getDefault(), "%.1f cm", meters * 100f)
            } else {
                String.format(Locale.getDefault(), "%.2f m", meters)
            }

        runOnUiThread {
            binding.distanceText.text = formatted
            binding.distanceText.visibility = View.VISIBLE
            setStatus(getString(R.string.status_tap_reset))
        }
    }

    private fun clearMeasurement() {
        synchronized(anchors) {
            anchors.forEach { it.detach() }
            anchors.clear()
        }
        runOnUiThread {
            binding.distanceText.visibility = View.GONE
            setStatus(getString(R.string.status_tap_first_point))
        }
    }

    /** Safe to call from the GL thread or the UI thread. */
    private fun setStatus(text: String) {
        runOnUiThread { binding.statusText.text = text }
    }

    private fun updateStatusForTrackingState(trackingMessage: String?) {
        if (trackingMessage != null) {
            setStatus(trackingMessage)
            return
        }
        if (anchors.isEmpty()) {
            setStatus(getString(R.string.status_tap_first_point))
        } else if (anchors.size == 1) {
            setStatus(getString(R.string.status_tap_second_point))
        }
    }

    companion object {
        private const val TAG = "TapeMeasure"
        private const val Z_NEAR = 0.01f
        private const val Z_FAR = 100f
        private const val APPROXIMATE_DISTANCE_METERS = 1.5f
    }
}
