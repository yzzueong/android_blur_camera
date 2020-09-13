package org.pytorch.helloworld

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.ColorUtils
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*

class ImageSegmentationModelExecutor(
        context: Context,
        private var useGPU: Boolean = false
) {
    private var gpuDelegate: GpuDelegate? = null

    private val segmentationMasks: ByteBuffer
    private val interpreter: Interpreter

    private var fullTimeExecutionTime = 0L
    private var preprocessTime = 0L
    private var imageSegmentationTime = 0L
    private var maskFlatteningTime = 0L

    private var numberThreads = 4

    init {

        interpreter = getInterpreter(context, imageSegmentationModel, useGPU)
        segmentationMasks = ByteBuffer.allocateDirect(1 * imageSize * imageSize * NUM_CLASSES * 4)
        segmentationMasks.order(ByteOrder.nativeOrder())
    }

    fun execute(data: Bitmap): ModelExecutionResult {
        try {
            fullTimeExecutionTime = SystemClock.uptimeMillis()

            preprocessTime = SystemClock.uptimeMillis()
            val resizeRatio: Float = (imageSize.toFloat() / Math.max(data.width, data.height))
            val rw : Int = Math.round(data.width * resizeRatio)
            val rh : Int = Math.round(data.height * resizeRatio)
            val scaledBitmap =
                    ImageUtils.scaleBitmapAndKeepRatio(
                            data,
                            imageSize, imageSize
                    )

            val contentArray =
                    ImageUtils.bitmapToByteBuffer(
                            scaledBitmap,
                            imageSize,
                            imageSize,
                            IMAGE_MEAN,
                            IMAGE_STD
                    )
            preprocessTime = SystemClock.uptimeMillis() - preprocessTime

            imageSegmentationTime = SystemClock.uptimeMillis()
            interpreter.run(contentArray, segmentationMasks)
            imageSegmentationTime = SystemClock.uptimeMillis() - imageSegmentationTime
            Log.d(TAG, "Time to run the model $imageSegmentationTime")

            maskFlatteningTime = SystemClock.uptimeMillis()
            var (maskImageApplied, maskOnly, itensFound) =
                    convertBytebufferMaskToBitmap(
                            segmentationMasks, imageSize, imageSize, scaledBitmap,
                            segmentColors
                    )
            maskFlatteningTime = SystemClock.uptimeMillis() - maskFlatteningTime
            Log.d(TAG, "Time to flatten the mask result $maskFlatteningTime")

            fullTimeExecutionTime = SystemClock.uptimeMillis() - fullTimeExecutionTime
            Log.d(TAG, "Total time execution $fullTimeExecutionTime")

            maskImageApplied = maskOnly;

            maskOnly = Bitmap.createBitmap(maskOnly,
                    (maskOnly.width - rw) / 2,
                    (maskOnly.height - rh) / 2,
                    rw, rh)
            maskOnly = scaleBitmap(maskOnly, data.width, data.height)

            return ModelExecutionResult(
                    maskImageApplied,
                    scaledBitmap,
                    maskOnly,
                    formatExecutionLog(),
                    itensFound
            )
        } catch (e: Exception) {
            val exceptionLog = "something went wrong: ${e.message}"
            Log.d(TAG, exceptionLog)

            val emptyBitmap =
                    ImageUtils.createEmptyBitmap(
                            imageSize,
                            imageSize
                    )
            return ModelExecutionResult(
                    emptyBitmap,
                    emptyBitmap,
                    emptyBitmap,
                    exceptionLog,
                    HashSet(0)
            )
        }
    }

    // base: https://github.com/tensorflow/tensorflow/blob/master/tensorflow/lite/java/demo/app/src/main/java/com/example/android/tflitecamerademo/ImageClassifier.java
    @Throws(IOException::class)
    private fun loadModelFile(context: Context, modelFile: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val retFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        fileDescriptor.close()
        return retFile
    }

    @Throws(IOException::class)
    private fun getInterpreter(
            context: Context,
            modelName: String,
            useGpu: Boolean = false
    ): Interpreter {
        val tfliteOptions = Interpreter.Options()
        tfliteOptions.setNumThreads(numberThreads)

        gpuDelegate = null
        if (useGpu) {
            gpuDelegate = GpuDelegate()
            tfliteOptions.addDelegate(gpuDelegate)
        }

        return Interpreter(loadModelFile(context, modelName), tfliteOptions)
    }

    private fun formatExecutionLog(): String {
        val sb = StringBuilder()
        sb.append("Input Image Size: $imageSize x $imageSize\n")
        sb.append("GPU enabled: $useGPU\n")
        sb.append("Number of threads: $numberThreads\n")
        sb.append("Pre-process execution time: $preprocessTime ms\n")
        sb.append("Model execution time: $imageSegmentationTime ms\n")
        sb.append("Mask flatten time: $maskFlatteningTime ms\n")
        sb.append("Full execution time: $fullTimeExecutionTime ms\n")
        return sb.toString()
    }

    fun close() {
        interpreter.close()
        if (gpuDelegate != null) {
            gpuDelegate!!.close()
        }
    }

    private fun convertBytebufferMaskToBitmap(
            inputBuffer: ByteBuffer,
            imageWidth: Int,
            imageHeight: Int,
            backgroundImage: Bitmap,
            colors: IntArray
    ): Triple<Bitmap, Bitmap, Set<Int>> {
        val conf = Bitmap.Config.ARGB_8888
        val maskBitmap = Bitmap.createBitmap(imageWidth, imageHeight, conf)
        val resultBitmap = Bitmap.createBitmap(imageWidth, imageHeight, conf)
        val scaledBackgroundImage =
                ImageUtils.scaleBitmapAndKeepRatio(
                        backgroundImage,
                        imageWidth,
                        imageHeight
                )
        val mSegmentBits = Array(imageWidth) { IntArray(imageHeight) }
        val itemsFound = HashSet<Int>()
        inputBuffer.rewind()

        for (y in 0 until imageHeight) {
            for (x in 0 until imageWidth) {
                var maxVal = 0f
                mSegmentBits[x][y] = 0

                for (c in 0 until NUM_CLASSES) {
                    val value = inputBuffer
                            .getFloat((y * imageWidth * NUM_CLASSES + x * NUM_CLASSES + c) * 4)
                    if (c == 0 || value > maxVal) {
                        maxVal = value
                        mSegmentBits[x][y] = c
                    }
                }

                //only process person
                if (mSegmentBits[x][y] != 15){
                    mSegmentBits[x][y] = 0
                    maskBitmap.setPixel(x, y, Color.WHITE)
                }else{
                    maskBitmap.setPixel(x, y, Color.BLACK)
                }

                itemsFound.add(mSegmentBits[x][y])
                val newPixelColor = ColorUtils.compositeColors(
                        colors[mSegmentBits[x][y]],
                        scaledBackgroundImage.getPixel(x, y)
                )
                resultBitmap.setPixel(x, y, newPixelColor)
            }
        }

        return Triple(resultBitmap, maskBitmap, itemsFound)
    }

    companion object {

        private const val TAG = "ImageSegmentationMExec"
        private const val imageSegmentationModel = "deeplabv3_257_mv_gpu_models.tflite"
        private const val imageSize = 257
        const val NUM_CLASSES = 21
        private const val IMAGE_MEAN = 128.0f
        private const val IMAGE_STD = 128.0f

        val segmentColors = IntArray(NUM_CLASSES)
        val labelsArrays = arrayOf(
                "background", "aeroplane", "bicycle", "bird", "boat", "bottle", "bus",
                "car", "cat", "chair", "cow", "dining table", "dog", "horse", "motorbike",
                "person", "potted plant", "sheep", "sofa", "train", "tv"
        )

        init {

            val random = Random(System.currentTimeMillis())
            segmentColors[0] = Color.TRANSPARENT
            for (i in 1 until NUM_CLASSES) {
                segmentColors[i] = Color.argb(
                        (128),
                        getRandomRGBInt(
                                random
                        ),
                        getRandomRGBInt(
                                random
                        ),
                        getRandomRGBInt(
                                random
                        )
                )
            }
        }

        private fun getRandomRGBInt(random: Random) = (255 * random.nextFloat()).toInt()
    }

    fun scaleBitmap(bitmap: Bitmap, destWidth: Int, destHeight: Int): Bitmap {
        if (destWidth > 0 && destHeight > 0) {
            var var3 = bitmap
            val width = bitmap.width
            val height = bitmap.height
            if (width > destWidth && height > destHeight) {
                val var8 = destWidth.toFloat() / width.toFloat()
                val var9 = destHeight.toFloat() / height.toFloat()
                val var10: Bitmap
                if (var8 > var9) {
                    var10 = a(bitmap, var8, width, height)
                    if (var10 != null) {
                        var3 = createClippedBitmap(var10, 0, (var10.height - destHeight) / 2, destWidth, destHeight)
                    }
                } else {
                    var10 = a(bitmap, var9, width, height)
                    if (var10 != null) {
                        var3 = createClippedBitmap(var10, (var10.width - destWidth) / 2, 0, destWidth, destHeight)
                    }
                }
            } else {
                var3 = Bitmap.createBitmap(destWidth, destHeight, bitmap.config)
                val var11 = Canvas(var3)
                val var12 = Paint(1)
                var11.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), Rect(0, 0, destWidth, destHeight), var12)
            }
            return var3
        } else {
            return bitmap
        }
    }
    private fun a(var0: Bitmap, var1: Float, var2: Int, var3: Int): Bitmap {
        val width = Matrix()
        width.postScale(var1, var1)
        return Bitmap.createBitmap(var0, 0, 0, var2, var3, width, true)
    }
    fun createClippedBitmap(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }
    private fun cropBitmapWithMask(original: Bitmap, mask: Bitmap): Bitmap {
        val w = original.width
        val h = original.height
        val cropped = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cropped)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(original, 0f, 0f, null)
        canvas.drawBitmap(mask, 0f, 0f, paint)
        paint.xfermode = null
        return cropped
    }
}