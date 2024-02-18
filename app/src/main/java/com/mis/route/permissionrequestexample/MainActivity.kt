package com.mis.route.permissionrequestexample

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mis.route.permissionrequestexample.databinding.ActivityMainBinding
import android.Manifest
import android.util.Log
import android.view.ViewGroup.LayoutParams
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintSet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private var _binding: ActivityMainBinding? = null
    private val binding: ActivityMainBinding get() = _binding!!
    private lateinit var cameraPermissionRequestLauncher: ActivityResultLauncher<String>
    private lateinit var cameraPermissionRationaleDialog: AlertDialog
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraPermissionRationaleDialog = createDialog(
            "Permission Required",
            "In order to start the camera, we have to grant its permission",
            "Request again",
            { cameraPermissionRequestLauncher.launch(Manifest.permission.CAMERA) },
            "No thanks"
        )

        // STEP 1- define actions if permission is granted or denied

        // Register the permissions callback, which handles the user's response to the
        // system permissions dialog. Save the return value, an instance of
        // ActivityResultLauncher. You can use either a val, as shown in this snippet,
        // or a lateinit var in your onAttach() or onCreate() method.
        cameraPermissionRequestLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                    startCamera()
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // feature requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                    if (cameraPermissionRationaleDialog.isShowing)
                        cameraPermissionRequestLauncher.launch(Manifest.permission.CAMERA)
                    else cameraPermissionRationaleDialog.show()
                }
            }


        binding.startCameraBtn.setOnClickListener { requestCameraPermissionFlow() }
        binding.takePhotoBtn.setOnClickListener { takePhoto() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {

                override fun onError(exc: ImageCaptureException) {
                    Log.e("TAG", "Photo capture failed: ${exc.message}", exc)
                }


                override fun onCaptureSuccess(image: ImageProxy) {
                    val imageView = ImageView(this@MainActivity)
//                    imageView.id = resources.getIdentifier("captured_iv", "ImageView", "com.mis.route.permissionrequestexample")
                    imageView.id = androidx.core.R.id.title // this may cause exceptions
                    imageView.layoutParams =
                        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    imageView.setImageBitmap(image.toBitmap())
                    imageView.rotation += 90
                    binding.root.addView(imageView)
                    val constraintSet = ConstraintSet()
                    constraintSet.clone(binding.root)
                    constraintSet.connect(
                        imageView.id,
                        ConstraintSet.TOP,
                        binding.root.id,
                        ConstraintSet.TOP
                    )
                    constraintSet.applyTo(binding.root)
                }
            }
        )
    }

    // STEP 2- define the permission request flow
    private fun requestCameraPermissionFlow() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
                startCamera()
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.CAMERA
            ) -> {
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected, and what
                // features are disabled if it's declined. In this UI, include a
                // "cancel" or "no thanks" button that lets the user continue
                // using your app without granting the permission.
                if (cameraPermissionRationaleDialog.isShowing)
                    cameraPermissionRequestLauncher.launch(Manifest.permission.CAMERA)
                else cameraPermissionRationaleDialog.show()
            }

            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                cameraPermissionRequestLauncher.launch(
                    Manifest.permission.CAMERA
                )
            }
        }
    }

    private fun createDialog(
        title: String,
        message: String,
        posBtnText: String? = null,
        posBtnAction: (() -> Unit)? = null,
        negBtnText: String? = null,
        negBtnAction: (() -> Unit)? = null,
        isCancelable: Boolean = false
    ): AlertDialog {
        return MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(posBtnText) { dialog, _ ->
                dialog.dismiss()
                posBtnAction?.invoke()
            }
            .setNegativeButton(negBtnText) { dialog, _ ->
                dialog.dismiss()
                negBtnAction?.invoke()
            }
            .setCancelable(isCancelable)
            .create()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e("TAG", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        cameraExecutor.shutdown()
    }
}