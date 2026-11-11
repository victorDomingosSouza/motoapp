package com.example.corridainteligente.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

object Ocr {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognize(context: Context, uri: Uri): String =
        withContext(Dispatchers.IO) {
            recognizer.process(InputImage.fromFilePath(context, uri)).await().text
        }

    /** Usado pelo monitoramento automático (frames da tela). */
    suspend fun recognize(bitmap: Bitmap): String =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
}

@SuppressLint("MissingPermission")
suspend fun lastKnownLocation(context: Context): Location? {
    val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    if (!granted) return null
    return try {
        LocationServices.getFusedLocationProviderClient(context).lastLocation.await()?.takeIf {
            val ageNanos = SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos
            ageNanos in 0L..120_000_000_000L && it.hasAccuracy() && it.accuracy in 0f..1000f
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
