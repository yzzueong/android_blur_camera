package org.pytorch.helloworld


import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

// Your IDE likely can auto-import these classes, but there are several
// different implementations so we list them here to disambiguate
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.view.*
import android.widget.ImageButton
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

// This is an arbitrary number we are using to keep tab of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts
private const val REQUEST_CODE_PERMISSIONS = 10

// This is an array of all the permission specified in the manifest
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

class CameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_camera)
        supportActionBar?.hide()

        // Add this at the end of onCreate function

        viewFinder = findViewById(R.id.view_finder_camera)

        // Request camera permissions
        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Every time the provided texture view changes, recompute layout
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }
    }

    // Add this after onCreate

    private lateinit var viewFinder: TextureView

    private var lensFacing = CameraX.LensFacing.BACK
    private var imageCapture: ImageCapture? = null
    private lateinit var switchButton:ImageButton

    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        bindCameraUseCases()

        // Listener for button used to switch cameras
        switchButton = findViewById(R.id.imagebutton_revert)
        switchButton.setOnClickListener {
            lensFacing = if (CameraX.LensFacing.FRONT == lensFacing) {
                CameraX.LensFacing.BACK
            } else {
                CameraX.LensFacing.FRONT
            }
            try {
                // Only bind use cases if we can query a camera with this orientation
                CameraX.getCameraWithLensFacing(lensFacing)
                bindCameraUseCases()
            } catch (exc: Exception) {
                // Do nothing
            }
        }
    }

    private fun bindCameraUseCases() {
        // Make sure that there are no other use cases bound to CameraX
        CameraX.unbindAll()

        val previewConfig = PreviewConfig.Builder().apply {
            setLensFacing(lensFacing)
        }.build()
        val preview = Preview(previewConfig)

        preview.setOnPreviewOutputUpdateListener {

            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)

            viewFinder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        val imageCaptureConfig = ImageCaptureConfig.Builder().apply {
            setLensFacing(lensFacing)
        }.build()
        imageCapture = ImageCapture(imageCaptureConfig)

        findViewById<ImageButton>(R.id.imagebutton).setOnClickListener {
            val file = File(externalMediaDirs.first(),
                    "${System.currentTimeMillis()}.jpg")
            imageCapture!!.takePicture(file,
                    object : ImageCapture.OnImageSavedListener {
                        override fun onError(error: ImageCapture.UseCaseError,
                                             message: String, exc: Throwable?) {
                            val msg = "Photo capture failed: $message"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            exc?.printStackTrace()
                        }

                        override fun onImageSaved(file: File) {
                            //Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            intent = Intent()
                            intent.putExtra("filename",file.absolutePath)
                            setResult(1,intent)
                            finish()
                        }
                    })

            //intent = Intent()
            //intent.putExtra("filename",file.absolutePath)
            //setResult(1,intent)
            //finish()
        }

        // Apply declared configs to CameraX using the same lifecycle owner
        CameraX.bindToLifecycle(this, preview, imageCapture)
    }

    private fun updateTransform() {
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when(viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix)
    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.post { startCamera() }
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}
