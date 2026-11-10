package com.example.corridainteligente.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.corridainteligente.data.*
import com.example.corridainteligente.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.min

/**
 * Captura da tela -> detecção de mudança (barata) -> OCR (cara, só quando preciso)
 * -> avaliação -> faixa flutuante. Grava a corrida quando detecta "corrida aceita".
 */
class MonitorService : Service() {

    companion object {
        val running = MutableStateFlow(false)
        const val ACTION_STOP = "com.example.corridainteligente.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "monitor"
        private const val NOTIF_ID = 42

        // --- Eficiência: o OCR só roda quando a tela muda de forma relevante ---
        private const val TICK_IDLE_MS = 1000L          // sem oferta: olha a tela 1x/s (só amostra, sem OCR)
        private const val TICK_ACTIVE_MS = 500L         // com oferta/aceite pendente: 2x/s
        private const val CHANGE_IDLE = 0.10f           // 10% da tela mudou de uma vez (ex.: cartão da oferta surgiu)
        private const val CHANGE_ACTIVE = 0.015f        // com oferta na tela, mudança menor já vale (números trocaram)
        private const val HEARTBEAT_IDLE_MS = 6000L     // rede de segurança: 1 OCR a cada 6 s mesmo sem mudança
        private const val HEARTBEAT_ACTIVE_MS = 1500L
        private const val GRID_W = 32
        private const val GRID_H = 56
        private const val PIXEL_DELTA = 28              // diferença de brilho (0..255) para contar como "mudou"

        private const val STABLE_READS_TO_SHOW = 2      // oferta nova só aparece após 2 leituras iguais
        private const val ABSENT_READS_TO_HIDE = 2
        private const val ACCEPT_WINDOW_MS = 60_000L    // tempo para aparecer o sinal de "aceita" após a oferta

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

    private class Pending(val analysis: Analysis, val form: OfferForm, var lastSeen: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: OverlayView? = null
    private var loopJob: Job? = null
    @Volatile private var criteria = Criteria()
    private var width = 0
    private var height = 0
    private var scale = 1f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (running.value) return START_NOT_STICKY

        startAsForeground() // Android 14+: antes de obter a MediaProjection

        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, main)

        setupCapture()
        overlay = OverlayView(this)
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
        val (sw, sh) = realScreenSize(this)
        scale = min(1f, 1080f / sw)                    // limita a resolução: OCR mais leve
        width = (sw * scale).toInt()
        height = (sh * scale).toInt()
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection!!.createVirtualDisplay(
            "corrida-monitor", width, height, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, null
        )
    }

    // ---------- captura / detecção de mudança ----------

    /** Área da faixa (em pixels do frame) que deve ser ignorada. */
    private fun maskRect(): Rect? = overlay?.bounds?.let { b ->
        Rect((b.left * scale).toInt(), (b.top * scale).toInt(),
            (b.right * scale).toInt() + 1, (b.bottom * scale).toInt() + 1)
    }

    /** Miniatura de brilho (32x56) lida direto do buffer, sem copiar a imagem inteira. */
    private fun signature(image: Image): IntArray {
        val plane = image.planes[0]
        val buf = plane.buffer
        val rs = plane.rowStride
        val ps = plane.pixelStride
        val mask = maskRect()
        val sig = IntArray(GRID_W * GRID_H)
        for (gy in 0 until GRID_H) for (gx in 0 until GRID_W) {
            val cx = ((gx + 0.5f) * width / GRID_W).toInt()
            val cy = ((gy + 0.5f) * height / GRID_H).toInt()
            if (mask != null && mask.contains(cx, cy)) continue   // faixa nossa = 0 (não conta)
            var sum = 0
            for (dy in 0..1) for (dx in 0..1) {
                val x = (cx + (dx * 4 - 2)).coerceIn(0, width - 1)
                val y = (cy + (dy * 4 - 2)).coerceIn(0, height - 1)
                val o = y * rs + x * ps
                val r = buf.get(o).toInt() and 0xFF
                val g = buf.get(o + 1).toInt() and 0xFF
                val b = buf.get(o + 2).toInt() and 0xFF
                sum += (r * 77 + g * 150 + b * 29) shr 8
            }
            sig[gy * GRID_W + gx] = sum / 4
        }
        return sig
    }

    private fun diffFraction(a: IntArray, b: IntArray): Float {
        var n = 0
        for (i in a.indices) if (abs(a[i] - b[i]) > PIXEL_DELTA) n++
        return n.toFloat() / a.size
    }

    /** Frame completo para OCR, com a área da faixa pintada de preto. */
    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * width
        val tmp = Bitmap.createBitmap(width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
        tmp.copyPixelsFromBuffer(plane.buffer)
        maskRect()?.let { Canvas(tmp).drawRect(it, Paint().apply { color = Color.BLACK }) }
        if (rowPadding == 0) return tmp
        return Bitmap.createBitmap(tmp, 0, 0, width, height).also { tmp.recycle() }
    }

    // ---------- laço principal ----------

    private suspend fun captureLoop() {
        var prevSample: IntArray? = null
        var lastBmp: Bitmap? = null
        var lastOcrAt = 0L

        var shown: OfferForm? = null        // oferta que está na faixa agora
        var candidate: OfferForm? = null    // oferta nova aguardando confirmação
        var seen = 0
        var absent = 0                      // leituras seguidas sem oferta (faixa visível)
        var pending: Pending? = null        // última oferta exibida, aguardando sinal de "aceita"
        var ignored: OfferForm? = null      // oferta já gravada: não reexibir
        var forceOcr = false

        try {
            while (currentCoroutineContext().isActive) {
                val active = shown != null || candidate != null || pending != null || absent > 0
                delay(if (active) TICK_ACTIVE_MS else TICK_IDLE_MS)
                val now = SystemClock.elapsedRealtime()

                pending?.let { if (now - it.lastSeen > ACCEPT_WINDOW_MS) pending = null }

                // 1) amostra barata; só vira bitmap/OCR se a tela mudou "bruscamente"
                var bmp: Bitmap? = null
                val image = reader?.acquireLatestImage()
                if (image != null) {
                    try {
                        val sig = signature(image)
                        val prev = prevSample
                        val changed = prev == null ||
                            diffFraction(sig, prev) >= (if (active) CHANGE_ACTIVE else CHANGE_IDLE)
                        val heartbeat = now - lastOcrAt >= (if (active) HEARTBEAT_ACTIVE_MS else HEARTBEAT_IDLE_MS)
                        prevSample = sig
                        if (changed || heartbeat || forceOcr) bmp = toBitmap(image)
                    } finally { image.close() }
                } else if (forceOcr) {
                    bmp = lastBmp   // tela parada: relê o último frame para confirmar
                }
                if (bmp == null) continue

                // 2) OCR
                lastOcrAt = now
                val text = try { Ocr.recognize(bmp) } catch (_: Exception) { null }
                if (bmp !== lastBmp) { lastBmp?.recycle(); lastBmp = bmp }
                if (text == null) continue

                val form = OfferParser.parse(text)
                val offer = form.toOffer()?.takeIf { plausible(it) }

                // 3) sinal de corrida aceita -> grava UMA vez e esconde a faixa
                val p = pending
                if (p != null && hasAcceptSignal(text)) {
                    saveRide(p.analysis)
                    ignored = p.form
                    pending = null; shown = null; candidate = null
                    seen = 0; absent = 0; forceOcr = false
                    withContext(Dispatchers.Main) { overlay?.hide() }
                    continue
                }

                if (offer != null) {
                    absent = 0
                    if (form == ignored) continue                 // ainda é a oferta já aceita
                    ignored = null
                    if (form == shown) {                          // mesma oferta, nada a fazer
                        candidate = null; seen = 0; forceOcr = false
                        pending?.lastSeen = now
                        continue
                    }
                    if (shown != null) {                          // oferta DIFERENTE: some com a faixa antiga já
                        shown = null
                        withContext(Dispatchers.Main) { overlay?.hide() }
                    }
                    if (form == candidate) seen++ else { candidate = form; seen = 1 }
                    if (seen >= STABLE_READS_TO_SHOW) {           // confirmada em 2 leituras
                        val analysis = Evaluator.evaluate(offer, criteria)
                        shown = form; candidate = null; seen = 0; forceOcr = false
                        pending = Pending(analysis, form, now)
                        withContext(Dispatchers.Main) { overlay?.show(analysis) }
                    } else forceOcr = true                        // pede a 2ª leitura logo
                } else {
                    candidate = null; seen = 0; ignored = null
                    if (shown != null) {
                        if (++absent >= ABSENT_READS_TO_HIDE) {   // oferta saiu da tela
                            shown = null; absent = 0; forceOcr = false
                            withContext(Dispatchers.Main) { overlay?.hide() }
                        } else forceOcr = true
                    } else { absent = 0; forceOcr = false }
                }
            }
        } finally {
            lastBmp?.recycle()
        }
    }

    private fun plausible(o: Offer) =
        o.fare in 2.0..1000.0 && o.totalKm in 0.5..300.0 && o.totalMin in 2.0..400.0

    private fun hasAcceptSignal(text: String): Boolean {
        val t = text.normalized()
        return criteria.acceptSignals.split(',', ';', '\n')
            .map { it.normalized().trim() }
            .filter { it.length >= 3 }
            .any { t.contains(it) }
    }

    private fun saveRide(a: Analysis) {
        scope.launch {
            val loc = lastKnownLocation(this@MonitorService)
            AppDatabase.get(this@MonitorService).rideDao().insert(
                Ride(
                    timestamp = System.currentTimeMillis(), fare = a.offer.fare,
                    totalKm = a.offer.totalKm, totalMin = a.offer.totalMin,
                    perKm = a.perKm, perMin = a.perMin,
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
