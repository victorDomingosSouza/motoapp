package com.example.corridainteligente.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.corridainteligente.domain.OfferParser
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrInstrumentedTest {
    private fun fixture(): Bitmap {
        val imagePath = InstrumentationRegistry.getArguments().getString("ocrImagePath")
        if (imagePath != null) return requireNotNull(BitmapFactory.decodeFile(imagePath))

        val bitmap = Bitmap.createBitmap(1080, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(30, 34, 40))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 56f }
        listOf("R$7,90", "R$2,01/km", "R$1,13 Tarifa base dinâmica incl.", "5 min (866 m)", "7 min (3,1 km)")
            .forEachIndexed { index, line -> canvas.drawText(line, 50f, 200f + index * 160f, paint) }
        return bitmap
    }

    private fun assertOffer(text: String) {
        val args = InstrumentationRegistry.getArguments()
        val offer = OfferParser.parse(text).toOffer()
        if (args.getString("expectedOffer", "true") == "false") {
            assertNull("Tela sem oferta foi interpretada como corrida\n$text", offer)
            return
        }
        assertNotNull("Oferta incompleta no texto reconhecido\n$text", offer)
        assertEquals(text, args.getString("expectedFare", "7.9")!!.toDouble(), offer!!.fare, 0.000001)
        assertEquals(text, args.getString("expectedPickupKm", "0.866")!!.toDouble(), offer.pickupKm, 0.000001)
        assertEquals(text, args.getString("expectedTripKm", "3.1")!!.toDouble(), offer.tripKm, 0.000001)
        assertEquals(text, args.getString("expectedTotalMin", "12")!!.toDouble(), offer.totalMin, 0.0)
        assertEquals(text, args.getString("expectedStops", "0")!!.toInt(), offer.stopCount)
        assertEquals(text, args.getString("expectedNegotiable", "false")!!.toBoolean(), offer.negotiable)
    }

    @Test fun recognizesCapturedBitmapWithMeters() = runBlocking {
        val bitmap = fixture()
        try {
            assertOffer(Ocr.recognize(bitmap))
        } finally { bitmap.recycle() }
    }

    @Test fun recognizesSelectedImageWithMeters() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("ocr-offer", ".png", context.cacheDir)
        val bitmap = fixture()
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertOffer(Ocr.recognize(context, Uri.fromFile(file)))
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }
}
