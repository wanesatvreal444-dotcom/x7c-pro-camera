package com.x7cpro.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var captureButton: Button
    private lateinit var recordButton: Button
    private lateinit var modeLabel: TextView
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: androidx.camera.video.Recording? = null
    private var camera: Camera? = null
    private var frontCamera = false
    private var torchOn = false
    private var videoMode = false
    private var cinematic = false
    private var selectedStyle = "NATURAL"
    private var selectedQuality = Quality.FHD
    private var exposureMin = 0
    private var exposureMax = 0
    private var zoomMax = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        buildInterface()
        if (hasPermissions()) startCamera() else ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 10
        )
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val header = TextView(this).apply {
            text = "X7c PRO CAMERA  •  PRO IMAGE LAB"
            textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(8, 13, 8, 10)
        }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) focusAt(event.x, event.y)
            true
        }
        modeLabel = TextView(this).apply {
            text = "PHOTO  •  NATURAL"; textSize = 12f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(8, 9, 8, 4)
        }
        status = TextView(this).apply {
            text = "Tap preview to focus"; textSize = 11f; setTextColor(0xFFBBBBBB.toInt())
            gravity = Gravity.CENTER; setPadding(6, 4, 6, 5)
        }
        root.addView(header)
        root.addView(previewView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(modeLabel)
        root.addView(status)
        root.addView(makeRow(
            makeButton("PHOTO") { setVideoMode(false) },
            makeButton("VIDEO") { setVideoMode(true) },
            makeButton("CINEMATIC") { cinematic = !cinematic; updateModeLabel(); toast(if (cinematic) "Cinematic preset selected" else "Cinematic preset off") },
            makeButton("FLIP") { frontCamera = !frontCamera; startCamera() }
        ))
        root.addView(makeRow(
            makeButton("NATURAL") { selectedStyle = "NATURAL"; updateModeLabel() },
            makeButton("DSLR") { selectedStyle = "DSLR"; updateModeLabel() },
            makeButton("MACRO") { selectedStyle = "MACRO"; updateModeLabel(); status.text = "Move close, tap the subject to focus" },
            makeButton("NIGHT") { selectedStyle = "NIGHT"; applyNightExposure(); updateModeLabel() }
        ))
        root.addView(makeRow(
            makeButton("HD") { selectedQuality = Quality.HD; if (videoMode) startCamera(); status.text = "HD video selected" },
            makeButton("FHD") { selectedQuality = Quality.FHD; if (videoMode) startCamera(); status.text = "Full HD video selected" },
            makeButton("UHD / 4K") { selectedQuality = Quality.UHD; if (videoMode) startCamera(); status.text = "4K selected if supported; device may fall back" },
            makeButton("FLASH") { torchOn = !torchOn; camera?.cameraControl?.enableTorch(torchOn); status.text = if (torchOn) "Torch on" else "Torch off" }
        ))
        val zoomText = TextView(this).apply { text = "ZOOM"; setTextColor(Color.LTGRAY); textSize = 10f; gravity = Gravity.CENTER }
        val zoom = SeekBar(this).apply { max = 100; progress = 0; setOnSeekBarChangeListener(simpleSeek { value ->
            val max = zoomMax.coerceAtLeast(1f)
            camera?.cameraControl?.setZoomRatio(1f + (max - 1f) * value / 100f)
        }) }
        val exposureText = TextView(this).apply { text = "EXPOSURE"; setTextColor(Color.LTGRAY); textSize = 10f; gravity = Gravity.CENTER }
        val exposure = SeekBar(this).apply { max = 100; progress = 50; setOnSeekBarChangeListener(simpleSeek { value ->
            if (exposureMax >= exposureMin) {
                val index = exposureMin + ((exposureMax - exposureMin) * value / 100f).toInt()
                camera?.cameraControl?.setExposureCompensationIndex(index)
            }
        }) }
        root.addView(zoomText); root.addView(zoom, LinearLayout.LayoutParams(-1, 34))
        root.addView(exposureText); root.addView(exposure, LinearLayout.LayoutParams(-1, 34))
        captureButton = makeButton("TAKE PHOTO") { takePhoto() }
        recordButton = makeButton("START VIDEO") { toggleVideo() }
        root.addView(makeRow(captureButton, recordButton))
        setContentView(root)
        setVideoMode(false)
    }

    private fun simpleSeek(action: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) action(progress) }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun makeRow(vararg views: android.view.View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(3, 2, 3, 2)
        views.forEach { addView(it, LinearLayout.LayoutParams(0, 43, 1f).apply { setMargins(2, 1, 2, 1) }) }
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 9f; isAllCaps = false; setPadding(1, 0, 1, 0)
        setOnClickListener { action() }
    }

    private fun hasPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun setVideoMode(enabled: Boolean) {
        if (recording != null) return
        videoMode = enabled
        captureButton.isEnabled = !enabled
        recordButton.isEnabled = enabled
        updateModeLabel()
        status.text = if (enabled) "Video mode • ${qualityName(selectedQuality)}" else "Photo mode • maximum quality request"
    }

    private fun updateModeLabel() {
        modeLabel.text = (if (videoMode) "VIDEO" else "PHOTO") + "  •  " + selectedStyle + if (cinematic) "  •  CINEMATIC" else ""
    }

    private fun qualityName(q: Quality) = when (q) { Quality.UHD -> "UHD"; Quality.FHD -> "FHD"; else -> "HD" }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(100)
                    .build()
                val recorder = Recorder.Builder().setQualitySelector(
                    QualitySelector.from(selectedQuality, QualitySelector.FALLBACK_STRATEGY_LOWER_QUALITY)
                ).build()
                videoCapture = VideoCapture.withOutput(recorder)
                camera = provider.bindToLifecycle(this, selector, preview, imageCapture, videoCapture)
                val state = camera!!.cameraInfo.exposureState
                exposureMin = state.exposureCompensationRange.lower
                exposureMax = state.exposureCompensationRange.upper
                zoomMax = camera!!.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                camera?.cameraControl?.enableTorch(torchOn)
                status.text = "Ready • Tap preview to focus • ${qualityName(selectedQuality)} video"
                updateModeLabel()
            } catch (e: Exception) { status.text = "Camera error: ${e.localizedMessage}" }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun focusAt(x: Float, y: Float) {
        val factory: MeteringPointFactory = previewView.meteringPointFactory
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        camera?.cameraControl?.startFocusAndMetering(action)
        status.text = "Focusing…"
    }

    private fun applyNightExposure() {
        if (exposureMin <= exposureMax) camera?.cameraControl?.setExposureCompensationIndex(
            (exposureMax.coerceAtMost(2)).coerceAtLeast(exposureMin)
        )
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "X7cPro_${selectedStyle}_$stamp.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/X7c Pro Camera")
        }
        val options = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        capture.takePicture(options, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) { runOnUiThread { toast("Photo saved • $selectedStyle") } }
            override fun onError(exception: ImageCaptureException) { runOnUiThread { toast(exception.message ?: "Photo failed") } }
        })
    }

    private fun toggleVideo() {
        val capture = videoCapture ?: return
        if (recording != null) { recording?.stop(); status.text = "Finishing video…"; return }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val folder = if (cinematic) "Movies/X7c Pro Camera/Cinematic" else "Movies/X7c Pro Camera"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "X7cPro_${if (cinematic) "Cinematic_" else ""}$stamp.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, folder)
        }
        val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        recording = capture.output.prepareRecording(this, output).withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> { recordButton.text = "STOP VIDEO"; status.text = "Recording • ${if (cinematic) "Cinematic preset" else qualityName(selectedQuality)}" }
                    is VideoRecordEvent.Finalize -> {
                        recording = null; recordButton.text = "START VIDEO"
                        status.text = if (event.hasError()) "Video error ${event.error}" else "Video saved"
                        if (!event.hasError()) toast("Video saved")
                    }
                }
            }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && hasPermissions()) startCamera()
        else toast("Camera and microphone permissions are required")
    }

    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown() }
}
