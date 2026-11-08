package com.example.corridainteligente.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object Ocr {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognize(context: Context, uri: Uri): String =
        recognizer.process(InputImage.fromFilePath(context, uri)).await().text

    /** Usado pelo monitoramento automático (frames da tela). */
    suspend fun recognize(bitmap: Bitmap): String =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
}

@SuppressLint("MissingPermission")
suspend fun lastKnownLocation(context: Context): Location? {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return null
    return runCatching {
        LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
    }.getOrNull()
}
