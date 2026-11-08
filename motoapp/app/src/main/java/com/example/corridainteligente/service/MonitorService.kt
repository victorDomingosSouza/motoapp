package com.example.corridainteligente.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.corridainteligente.data.*
import com.example.corridainteligente.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.min

/**
 * Monitora a tela continuamente: captura frames (MediaProjection) -> OCR (ML Kit) ->
 * interpreta a oferta -> avalia com seus critérios -> mostra o veredito num overlay.
 */
class MonitorService : Service() {

    companion object {
        val running = MutableStateFlow(false)
        const val ACTION_STOP = "com.example.corridainteligente.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "monitor"
        private const val NOTIF_ID = 42
        private const val FRAME_INTERVAL_MS = 600L
        private const val ABSENT_FRAMES_TO_HIDE = 4
        private const val STABLE_FRAMES_TO_SHOW = 2

        fun start(context: Context, resultCode: Int, data: Intent) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitorService::class.java)
                    .putExtra(EXTRA_CODE, resultCode).putExtra(EXTRA_DATA, data)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MonitorService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: OverlayView? = null
    private var loopJob: Job? = null
    @Volatile private var criteria = Criteria()
    @Volatile private var current: Analysis? = null
    private var width = 0
    private var height = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (running.value) return START_NOT_STICKY

        // Android 14+: precisa estar em foreground ANTES de obter a MediaProjection
        startAsForeground()

        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, main)

        setupCapture()
        overlay = OverlayView(this) { onAccepted() }
        running.value = true

        scope.launch { SettingsRepository(this@MonitorService).criteria.collect { criteria = it } }
        loopJob = scope.launch { captureLoop() }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Monitoramento de corridas", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Corrida Inteligente ativo")
            .setContentText("Analisando ofertas na tela")
            .setOngoing(true)
            .addAction(0, "Parar", stopIntent)
            .build()

        val hasLocation = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val type = if (Build.VERSION.SDK_INT >= 29) {
            var t = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (hasLocation) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            t
        } else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    private fun setupCapture() {
        val dm = resources.displayMetrics
        val scale = min(1f, 1080f / dm.widthPixels)   // limita a resolução p/ OCR mais leve
        width = (dm.widthPixels * scale).toInt()
        height = (dm.heightPixels * scale).toInt()
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection!!.createVirtualDisplay(
            "corrida-monitor", width, height, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, null
        )
    }

    private fun grabFrame(): Bitmap? {
        val image = reader?.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * width
            Bitmap.createBitmap(width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
                .also { it.copyPixelsFromBuffer(plane.buffer) }
        } finally { image.close() }
    }

    private suspend fun captureLoop() {
        var lastForm: OfferForm? = null   // último resultado de OCR (reutilizado se não houver frame novo)
        var candidate: OfferForm? = null
        var seen = 0
        var shown: OfferForm? = null
        var absent = 0

        while (currentCoroutineContext().isActive) {
            delay(FRAME_INTERVAL_MS)

            val bmp = grabFrame()
            if (bmp != null) {
                lastForm = try { OfferParser.parse(Ocr.recognize(bmp)) } catch (_: Exception) { null }
                bmp.recycle()
            }
            val form = lastForm
            val offer = form?.toOffer()?.takeIf { plausible(it) }

            if (offer == null) {
                candidate = null; seen = 0
                if (++absent >= ABSENT_FRAMES_TO_HIDE && shown != null) {
                    shown = null
                    withContext(Dispatchers.Main) { overlay?.hide() }
                }
                continue
            }
            absent = 0
            if (form == shown) continue                       // já mostrada
            if (form == candidate) seen++ else { candidate = form; seen = 1 }

            if (seen >= STABLE_FRAMES_TO_SHOW) {              // leitura estável = menos erro de OCR
                shown = form
                val analysis = Evaluator.evaluate(offer, criteria)
                current = analysis
                withContext(Dispatchers.Main) { overlay?.show(analysis) }
            }
        }
    }

    private fun plausible(o: Offer) =
        o.fare in 2.0..1000.0 && o.totalKm in 0.5..300.0 && o.totalMin in 2.0..400.0

    private fun onAccepted() {
        val a = current ?: return
        overlay?.hide()
        scope.launch {
            val loc = lastKnownLocation(this@MonitorService)
            AppDatabase.get(this@MonitorService).rideDao().insert(
                Ride(
                    timestamp = System.currentTimeMillis(), fare = a.offer.fare,
                    totalKm = a.offer.totalKm, totalMin = a.offer.totalMin,
                    perKm = a.perKm, perHour = a.perHour,
                    lat = loc?.latitude, lng = loc?.longitude,
                )
            )
        }
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        main.post { overlay?.hide() }
        virtualDisplay?.release()
        reader?.close()
        projection?.stop()
        running.value = false
        super.onDestroy()
    }
}
