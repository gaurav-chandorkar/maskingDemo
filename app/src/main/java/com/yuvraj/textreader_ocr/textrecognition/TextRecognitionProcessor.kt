package com.yuvraj.textreader_ocr.textrecognition

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer
import com.google.firebase.samples.apps.mlkit.kotlin.textrecognition.TextGraphic
import com.yuvraj.textreader_ocr.CameraScannerActivity
import com.yuvraj.textreader_ocr.common.CameraImageGraphic
import com.yuvraj.textreader_ocr.common.FrameMetadata
import com.yuvraj.textreader_ocr.common.GraphicOverlay
import java.io.IOException
import java.util.regex.Pattern

/** Processor for the text recognition demo.  */
class TextRecognitionProcessor(var activity: CameraScannerActivity,var imageCaptureListener:CaptureImageAndRectListener) : VisionProcessorBase<FirebaseVisionText>() {

    private val detector: FirebaseVisionTextRecognizer = FirebaseVision.getInstance().onDeviceTextRecognizer

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Exception thrown while trying to close Text Detector: $e")
        }
    }

    override fun detectInImage(image: FirebaseVisionImage): Task<FirebaseVisionText> {
        return detector.processImage(image)
    }

    override fun onSuccess(
        originalCameraImage: Bitmap?,
        results: FirebaseVisionText,
        frameMetadata: FrameMetadata,
        graphicOverlay: GraphicOverlay
    ) {
        graphicOverlay.clear()
        originalCameraImage.let { image ->
            val imageGraphic = CameraImageGraphic(graphicOverlay, image)
            graphicOverlay.add(imageGraphic)
            //  activity.setBitmapImage(image)
        }

        val blocks = results.textBlocks
//        if(blocks.size > 1){
        imageCaptureListener.onCaptureOriginalImage(results.text, originalCameraImage)
//        }


        for (i in blocks.indices) {
            val lines = blocks[i].lines
            for (j in lines.indices) {
                val elements = lines[j].elements
               Log.e(TAG," grv element line  ${elements.toString()}")
                for (k in elements.indices) {
                    val textGraphic = TextGraphic(graphicOverlay, elements[k])

                    //   Log.e(TAG," element ${elements[k].text} checkForAadharCardNumber ${checkForAadharCardNumber(elements[k].text)}")
                   try {
                     //  Log.e(TAG,"checkForAadharCardNumber ${checkForAadharCardNumber(elements[k].text)} && ${checkForAadharCardNumber(elements[k+1].text)}")
                       if (checkForAadharCardNumber(elements[k].text) && checkForAadharCardNumber(elements[k+1].text)) {
                           graphicOverlay.add(textGraphic)
                            imageCaptureListener.addRect(elements[k].boundingBox)}
                   }catch ( e:java.lang.Exception){

                   }

                }
            }
        }
        graphicOverlay.postInvalidate()
    }

    private fun checkForAadharCardNumber(text: String): Boolean {
        val regex = "^\\D*(\\d{4})\\D*\$" // "^\\d{4}\\s\\d{4}\\s\\d{4}$"
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(text.trim())
        return matcher.matches()
    }


    override fun onFailure(e: Exception) {
        Log.w(TAG, "Text detection failed.$e")
    }

    public interface CaptureImageAndRectListener {
        abstract fun onCaptureOriginalImage(textMsg: String, image: Bitmap?)
        abstract fun  addRect(rect: Rect?)
    }
    companion object {

        private const val TAG = "TextRecProc"
    }
}
