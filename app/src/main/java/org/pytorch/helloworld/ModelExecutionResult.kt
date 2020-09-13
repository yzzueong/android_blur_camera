package org.pytorch.helloworld

import android.graphics.Bitmap

data class ModelExecutionResult(
        val bitmapResult: Bitmap,
        val bitmapOriginal: Bitmap,
        val bitmapMaskOnly: Bitmap,
        val executionLog: String,
        val itemsFound: Set<Int>
)