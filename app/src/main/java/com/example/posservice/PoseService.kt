package com.example.posservice

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.AudioManager
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseService : Service() {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var poseLandmarker: PoseLandmarker? = null
    private var lastGestureTime = 0L
    private val gestureCooldown = 3000


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "start service")

        createNotificationChannel()
        startForeground(1, notification)
        initBackgroundThread()
        initPoseLandmarker()
        openCamera()
    }

    private val notification: Notification
        get() = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pose Service Running")
            .setSmallIcon(R.drawable.ic_media_play)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Pose Foreground Service", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun initBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun initPoseLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_full.task")
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init pose landmarker", e)
        }
    }

    private fun openCamera() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        Log.d(TAG, "openCamera")

        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val previewSize = map?.getOutputSizes(ImageFormat.YUV_420_888)?.firstOrNull() ?: continue

                    imageReader = ImageReader.newInstance(
                        previewSize.width, previewSize.height,
                        ImageFormat.YUV_420_888, 2
                    ).apply {
                        setOnImageAvailableListener({ reader ->
                            reader.acquireLatestImage()?.use { image ->
                                val bitmap = YuvToBitmapConverter.convertYUVToBitmap(image)
                                bitmap?.let {
                                    val mpImage = BitmapImageBuilder(it).build()
                                    val result: PoseLandmarkerResult = poseLandmarker?.detectForVideo(mpImage, System.currentTimeMillis()) ?: return@use
                                    val landmarks = result.landmarks().firstOrNull()
                                    if (landmarks != null && landmarks.size > 16) {
                                        val leftWristY = landmarks[15].y()
                                        val rightWristY = landmarks[16].y()
                                        val leftShoulderY = landmarks[11].y()
                                        val rightShoulderY = landmarks[12].y()

                                        val now = System.currentTimeMillis()

                                        if (leftWristY < leftShoulderY && rightWristY < rightShoulderY) {
                                            if (now - lastGestureTime > gestureCooldown) {
                                                lastGestureTime = now
                                                Log.d(TAG, "Detected: BOTH HANDS UP → theme change")
                                                //adjustVolume(AudioManager.ADJUST_RAISE)
                                                notifyGesture(1)

                                            }
                                        } else if (rightWristY < rightShoulderY && leftWristY >= leftShoulderY) {
                                            if (now - lastGestureTime > gestureCooldown) {
                                                lastGestureTime = now
                                                Log.d(TAG, "Detected: RIGHT HAND UP → Volume DOWN")
                                                adjustVolume(AudioManager.ADJUST_LOWER)
                                                notifyGesture(2)

                                            }
                                        }
                                        else if (rightWristY >= rightShoulderY && leftWristY < leftShoulderY) {
                                            if (now - lastGestureTime > gestureCooldown) {
                                                lastGestureTime = now
                                                Log.d(TAG, "왼손 판별 조건 만족: leftWristY=$leftWristY, leftShoulderY=$leftShoulderY, rightWristY=$rightWristY, rightShoulderY=$rightShoulderY")
                                                Log.d(TAG, "Detected: Left HAND UP → Volume UP")
                                                //notifyLeftHandGesture()
                                                adjustVolume(AudioManager.ADJUST_RAISE)
                                                notifyGesture(3)

                                            }
                                        }
                                    }
                                }
                            }
                        }, backgroundHandler)
                    }

                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                        return

                    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            cameraDevice = camera
                            try {
                                camera.createCaptureSession(
                                    listOf(imageReader!!.surface),
                                    object : CameraCaptureSession.StateCallback() {
                                        override fun onConfigured(session: CameraCaptureSession) {
                                            captureSession = session
                                            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                                addTarget(imageReader!!.surface)
                                            }
                                            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                                        }

                                        override fun onConfigureFailed(session: CameraCaptureSession) {
                                            Log.e(TAG, "Camera configure failed")
                                        }
                                    }, backgroundHandler
                                )
                            } catch (e: CameraAccessException) {
                                Log.e(TAG, "Failed to create session", e)
                            }
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            camera.close()
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            camera.close()
                        }
                    }, backgroundHandler)

                    break
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        backgroundThread?.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "PoseService"
        private const val CHANNEL_ID = "PoseServiceChannel"
    }
    @SuppressLint("ServiceCast")
    private fun adjustVolume(direction: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun notifyLeftHandGesture() {
        val intent = Intent("com.example.ACTION_LEFT_HAND_UP")
        intent.setPackage("com.example.customerlauncher")
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast: LEFT HAND UP sent")
    }

    private fun notifyGesture(type: Int) {
        val intent = Intent("com.example.ACTION_GESTURE")
        intent.setPackage("com.example.customerlauncher")
        intent.putExtra("gesture_type", type)  // 1=LEFT, 2=RIGHT, 3=BOTH
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast: GESTURE type=$type sent")
    }


}