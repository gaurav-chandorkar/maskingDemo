package com.grv.text_ocr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Camera
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.ml.common.FirebaseMLException
import com.grv.text_ocr.common.CameraSource
import com.grv.text_ocr.textrecognition.TextRecognitionProcessor
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.regex.Pattern


class CameraScannerActivity : AppCompatActivity(),
    ActivityCompat.OnRequestPermissionsResultCallback,
    TextRecognitionProcessor.CaptureImageAndRectListener {


    private var cameraSource: CameraSource? = null
    private var selectedModel = TEXT_DETECTION
    var isChecked = false
    var isEnableFlash = false
    var sheetBehavior: BottomSheetBehavior<View>? = null
    var PERMISSION_REQUEST = 1
    private var menu: Menu? = null
    var mTextExtracted = ""
    var image: Bitmap? = null

    var rectList = mutableListOf<Rect?>()
    private var backgroundJob = Job()
    private var uiScope = CoroutineScope(Dispatchers.Main + backgroundJob)


    override fun onCaptureOriginalImage(textMsg: String, image: Bitmap?) {
        showTextFromImage(textMsg, image)
    }

    var isEven = true
    override fun addRect(rect: Rect?) {
        if (rectList.size < 2) {
            rectList.add(rect)
        } else if (isEven) {
            rectList.set(0, rect)
            isEven = false
        } else {
            isEven = true
            rectList.set(1, rect)
        }
    }

    private val requiredPermissions: Array<String?>
        get() {
            return try {
                val info = this.packageManager
                    .getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
                val ps = info.requestedPermissions
                if (ps != null && ps.isNotEmpty()) {
                    ps
                } else {
                    arrayOfNulls(0)
                }
            } catch (e: Exception) {
                arrayOfNulls(0)
            }
        }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setContentView(R.layout.activity_main)

        initBottomSheet()

        if (allPermissionsGranted()) {
            createCameraSource(selectedModel)
        } else {
            checkAppPermission()
        }

        btn_start.setOnClickListener {
            rectList.clear()
            toggleCamera(1)
            toggleBottomSheet()
        }

        btn_stop.setOnClickListener {
            toggleCamera(0)
            toggleBottomSheet()


        }

        image_select.setOnClickListener {
            if (rectList.size>0){
                showToast("Aadhar  found")
            }else{
                showToast("Aadhar not found")

            }
        }
    }

    private fun showToast(msg:String){
        Toast.makeText(
            this@CameraScannerActivity,
            msg,
            Toast.LENGTH_SHORT
        ).show()
    }
    private fun drawRectOverImage(image: Bitmap?): Bitmap? {

        image?.let {
            val paint = Paint()
            paint.color = Color.BLACK
            val tempBitmap = Bitmap.createBitmap(image.width, image.height, image.config)
            val canvas = Canvas(tempBitmap)
            canvas.drawBitmap(image, 0f, 0f, null)
            for (rect in rectList)
                canvas.drawRect(rect!!, paint)

            try {
                val file = File(getExternalFilesDir(null), "compressImage.jpg")
                tempBitmap.compress(Bitmap.CompressFormat.JPEG, 50, FileOutputStream(file))
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, " Exception while compressing")
            }
            return tempBitmap
        } ?: kotlin.run {
            return image
        }


    }

    fun onCameraSwitch(isChecked: Boolean) {
        Log.d(TAG, "Set facing")

        this.isChecked = isChecked

        cameraSource?.let {
            if (isChecked) {
                it.setFacing(CameraSource.CAMERA_FACING_FRONT)
            } else {
                it.setFacing(CameraSource.CAMERA_FACING_BACK)
            }
        }
        firePreview?.stop()
        startCameraSource()
    }

    private fun createCameraSource(model: String) {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = CameraSource(this, fireFaceOverlay)
        }

        cameraSource?.run {
            mFlashMode = Camera.Parameters.FLASH_MODE_OFF
            mFocusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            autoFocus(CameraAutoFocusCallback())
            setAutoFocusMoveCallback(CameraAutoFocusMoveCallback())
        }

        try {
            when (model) {
                TEXT_DETECTION -> {
                    Log.i(TAG, "Using Text Detector Processor")
                    cameraSource?.setMachineLearningFrameProcessor(
                        TextRecognitionProcessor(
                            this,
                            this
                        )
                    )
                }
                else -> Log.e(TAG, "Unknown model: $model")
            }
        } catch (e: FirebaseMLException) {
            Log.e(TAG, "can not create camera source: $model")
        }
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private fun startCameraSource() {
        cameraSource?.let {
            try {
                if (firePreview == null) {
                    Log.d(TAG, "resume: Preview is null")
                }
                if (fireFaceOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null")
                }
                firePreview?.start(cameraSource, fireFaceOverlay)
            } catch (e: IOException) {
                Log.e(TAG, "Unable to start camera source.", e)
                cameraSource?.release()
                cameraSource = null
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
//        toggleBottomSheet()
        startCameraSource()
    }

    /** Stops the camera.  */
    override fun onPause() {
        super.onPause()
        firePreview?.stop()
    }

    public override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
        backgroundJob.cancel()
    }

    private fun allPermissionsGranted(): Boolean {
        for (permission in requiredPermissions) {
            if (!isPermissionGranted(this, permission!!)) {
                return false
            }
        }
        return true
    }

    private fun checkAppPermission() {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.READ_CONTACTS
                )
            ) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                Snackbar.make(textView, "Kindly enable all the permissions", Snackbar.LENGTH_LONG)
                    .setAction("Settings", null).show()

            } else {

                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA),
                    PERMISSION_REQUEST
                )

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Log.i(TAG, "Permission granted!")
        if (allPermissionsGranted()) {
            createCameraSource(selectedModel)
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        private const val TEXT_DETECTION = "Text Detection"
        private const val TAG = "CameraScannerActivity"
        private const val PERMISSION_REQUESTS = 1

        private fun isPermissionGranted(context: Context, permission: String): Boolean {
            if (ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Permission granted: $permission")
                return true
            }
            Log.i(TAG, "Permission NOT granted: $permission")
            return false
        }
    }

    /**
     * Take screen shot of the View
     *
     * @param v the view
     * @param width_dp
     * @param height_dp
     *
     * @return screenshot of the view as bitmap
     */
    fun takeScreenShotOfView(v: View, width_dp: Int, height_dp: Int): Bitmap {

        v.isDrawingCacheEnabled = true

        // this is the important code :)
        v.measure(
            View.MeasureSpec.makeMeasureSpec(dpToPx(v.context, width_dp), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dpToPx(v.context, height_dp), View.MeasureSpec.EXACTLY)
        )
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)

        v.buildDrawingCache(true)

        // creates immutable clone
        val bitmap = Bitmap.createBitmap(v.drawingCache)
        v.isDrawingCacheEnabled = false // clear drawing cache
        return bitmap
    }

    fun dpToPx(context: Context, dp: Int): Int {
        val r = context.resources
        return Math.round(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                r.displayMetrics
            )
        )
    }

    private fun initBottomSheet() {
        sheetBehavior = BottomSheetBehavior.from(layoutBottomSheet);

        /**
         * bottom sheet state change listener
         * we are changing button text when sheet changed state
         * */
        sheetBehavior?.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        displayImage()
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        mTextExtracted = ""
                    }
                    BottomSheetBehavior.STATE_DRAGGING -> {
                    }
                    BottomSheetBehavior.STATE_SETTLING -> {
                    }
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {

            }
        })
    }

    private fun displayImage() {
        uiScope.launch {

            image = drawRectOverImage(image)
            image_text.invalidate()
            withContext(Dispatchers.Main) {
                Glide.with(this@CameraScannerActivity).load(image).into(image_text)

            }
            Log.e(TAG, " rectList size ${rectList.size}")
        }
    }

    /**
     * manually opening / closing bottom sheet on button click
     */
    public fun toggleBottomSheet() {
        if (sheetBehavior?.state != BottomSheetBehavior.STATE_EXPANDED) {
            sheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED;
//            sheetBehavior?.setPeekHeight(500)
//                textView.text = "Close sheet";
        } else {
            sheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED;
            sheetBehavior?.setPeekHeight(0)
//                textView.text = "Expand sheet";
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleCamera(state: Int) {

//        Handler().postDelayed({
        when (state) {
            0 -> {
                cameraSource?.stop()
            }
            1 -> {
                cameraSource?.start()
            }
        }
//        }, 500)
    }

    private fun toggleFlash() {
        try {


            isEnableFlash = !isEnableFlash
            if (isEnableFlash) {
                menu?.getItem(0)?.icon = ContextCompat.getDrawable(this, R.drawable.ic_flash_off);
                cameraSource?.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
            } else {
                menu?.getItem(0)?.icon = ContextCompat.getDrawable(this, R.drawable.ic_flash_on);
                cameraSource?.setFlashMode(Camera.Parameters.FLASH_MODE_OFF)
            }
        } catch (e: Exception) {
            showToast("Flash Mode Not Supported ")

            e.printStackTrace()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (sheetBehavior?.state == BottomSheetBehavior.STATE_EXPANDED) {

                val outRect = Rect()
                layoutBottomSheet.getGlobalVisibleRect(outRect)

                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    toggleBottomSheet()
                    toggleCamera(1)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_camera, menu)
        this.menu = menu;
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.action_camera -> {
                onCameraSwitch(!isChecked)
                true
            }

            R.id.action_flash -> {
                toggleFlash()
                true
            }
            R.id.action_print -> {
                //   printScreen(image)
                //   saveToInternalStorage(this@CameraScannerActivity, image)
                var cw = ContextWrapper(this);
                // path to /data/data/yourapp/app_data/imageDir
                var directory = cw.getExternalFilesDir(null);
                // Create imageDir
                var mypath = File(directory, "image.jpg");
                Log.e(TAG, "image path $mypath")
                image_text.setImageURI(Uri.fromFile(mypath))

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    override fun onBackPressed() {
        if (sheetBehavior?.state != BottomSheetBehavior.STATE_COLLAPSED) {
            toggleBottomSheet()
            toggleCamera(1)
        } else {
            super.onBackPressed()
        }
    }



    fun toggleViews(state: Int) {
        when (state) {
            0 -> {
                relative_text.visibility = View.GONE
                relative_empty_text.visibility = View.VISIBLE
            }
            1 -> {
                relative_text.visibility = View.VISIBLE
                relative_empty_text.visibility = View.GONE
            }
        }
    }

    fun showTextFromImage(textMsg: String, image: Bitmap?) {
        this.image = image
        Log.e("Tag", "image $image")

        if (sheetBehavior?.state == BottomSheetBehavior.STATE_COLLAPSED) {
            textView?.run {
                Log.e("TEXT", textMsg)
                if (TextUtils.isEmpty(textMsg)) {
                    toggleViews(0)
                    textView.text = getString(R.string.empty_text)
                    image_no_text.setImageBitmap(image)
                } else {
                    mTextExtracted = extractOnlyNumber(textMsg)
                    toggleViews(1)
                    textView.text =""
                }
            }
        }
    }

    private fun extractOnlyNumber(result: String): String {
        var onlyNumber = ""
        val p = Pattern.compile("-?\\d+")
        val m = p.matcher(result)
        while (m.find()) {
            System.out.println(m.group())
            onlyNumber = onlyNumber + " \n " + m.group()
            Log.e("Gaurav", "strArray  $onlyNumber")
        }
        return onlyNumber
    }


}