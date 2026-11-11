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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.corridainteligente.data.*
import com.example.corridainteligente.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Captura da tela -> detecção de mudança (barata) -> OCR (cara, só quando preciso)
 * -> avaliação -> faixa flutuante. Grava a corrida quando detecta "corrida aceita".
 */
class MonitorService : Service() {

    companion object {
        val running = MutableStateFlow(false)
        val error = MutableStateFlow<String?>(null)
        val currentAnalysis = MutableStateFlow<Analysis?>(null)
        const val ACTION_STOP = "com.example.corridainteligente.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "monitor"
        private const val NOTIF_ID = 42

        // --- Eficiência: o OCR só roda quando a tela muda de forma relevante ---
        private const val SAMPLE_INTERVAL_MS = 100L    // amostragem leve independente do OCR
        private const val CHANGE_IDLE = 0.10f           // 10% da tela mudou de uma vez (ex.: cartão da oferta surgiu)
        private const val CHANGE_ACTIVE = 0.015f        // com oferta na tela, mudança menor já vale (números trocaram)
        private const val HEARTBEAT_IDLE_MS = 6000L     // rede de segurança: 1 OCR a cada 6 s mesmo sem mudança
        private const val HEARTBEAT_ACTIVE_MS = 750L
        private const val GRID_W = 32
        private const val GRID_H = 56

        private const val CARD_SCENE_CHANGE = 0.18f
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
    private var captureMask: Rect? = null

    private data class CapturedFrame(val bitmap: Bitmap, val timestamp: Long, val generation: Long)

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
        error.value = null
        currentAnalysis.value = null

        scope.launch { SettingsRepository(this@MonitorService).criteria.collect { criteria = it } }
        loopJob = scope.launch {
            try {
                captureLoop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MonitorService", "Monitoramento interrompido", e)
                error.value = "Monitoramento interrompido. Inicie novamente."
                withContext(Dispatchers.Main) { stopSelf() }
            }
        }
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

        val hasLocation = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ).any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
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
    private fun maskRect(): Rect? {
        overlay?.bounds?.let { b ->
            val rect = Rect((b.left * scale).toInt(), (b.top * scale).toInt(),
                (b.right * scale).toInt() + 1, (b.bottom * scale).toInt() + 1)
            captureMask = captureMask?.apply { union(rect) } ?: rect
        }
        return captureMask
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

    /** Mascara somente a faixa visível, liberando os pixels quando ela está oculta. */
    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * width
        val tmp = Bitmap.createBitmap(width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
        tmp.copyPixelsFromBuffer(plane.buffer)
        overlay?.bounds?.let { b ->
            val rect = Rect((b.left * scale).toInt(), (b.top * scale).toInt(),
                (b.right * scale).toInt() + 1, (b.bottom * scale).toInt() + 1)
            Canvas(tmp).drawRect(rect, Paint().apply { color = Color.BLACK })
        }
        if (rowPadding == 0) return tmp
        return Bitmap.createBitmap(tmp, 0, 0, width, height).also { tmp.recycle() }
    }

    // ---------- laço principal ----------

    private suspend fun captureLoop(): Unit = coroutineScope {
        val frames = Channel<CapturedFrame>(Channel.CONFLATED, onUndeliveredElement = { it.bitmap.recycle() })
        val changes = ScreenChangeDetector(ignoreTopHalf = true)
        val cardChanges = ScreenChangeDetector(ignoreTopHalf = true)
        val forceOcr = AtomicBoolean(false)
        val active = AtomicBoolean(false)
        val sampler = launch {
            var previousMask: Rect? = null
            var lastFrame = Long.MIN_VALUE
            var lastPostedAt = 0L
            while (isActive) {
                delay(SAMPLE_INTERVAL_MS)
                val image = reader?.acquireLatestImage() ?: continue
                try {
                    if (image.timestamp <= lastFrame) continue
                    lastFrame = image.timestamp
                    val sig = signature(image)
                    val mask = captureMask?.let { Rect(it) }
                    val maskChanged = mask != previousMask
                    previousMask = mask
                    val changed = changes.sample(
                        sig, if (active.get()) CHANGE_ACTIVE else CHANGE_IDLE, maskChanged,
                    )
                    // A metade inferior contém os trechos da oferta nos layouts da 99.
                    // Animações do mapa e a contagem regressiva não invalidam o OCR.
                    val sceneChanged = cardChanges.sample(sig, CARD_SCENE_CHANGE, maskChanged)
                    val now = SystemClock.elapsedRealtime()
                    val heartbeat = now - lastPostedAt >=
                        (if (active.get()) HEARTBEAT_ACTIVE_MS else HEARTBEAT_IDLE_MS)
                    if (sceneChanged) {
                        currentAnalysis.value = null
                        withContext(Dispatchers.Main) { overlay?.hide() }
                    }
                    if (changed || sceneChanged || maskChanged || lastPostedAt == 0L || heartbeat || forceOcr.getAndSet(false)) {
                        changes.remember(sig)
                        lastPostedAt = now
                        val frame = CapturedFrame(toBitmap(image), image.timestamp, cardChanges.generation)
                        if (frames.trySend(frame).isFailure) frame.bitmap.recycle()
                    }
                } finally { image.close() }
            }
        }
        var processedGeneration = -1L

        val display = OfferDisplayTracker()
        val acceptance = AcceptanceTracker(ACCEPT_WINDOW_MS)
        var ignored: OfferForm? = null      // oferta já gravada: não reexibir

        try {
            for (frame in frames) {
                if (frame.generation != cardChanges.generation) {
                    frame.bitmap.recycle()
                    continue
                }
                val now = SystemClock.elapsedRealtime()
                if (frame.generation != processedGeneration) {
                    processedGeneration = frame.generation
                    display.clear()
                }
                val text = try {
                    withContext(NonCancellable) { Ocr.recognize(frame.bitmap) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("MonitorService", "Falha ao reconhecer captura", e)
                    null
                } finally { frame.bitmap.recycle() }
                currentCoroutineContext().ensureActive()
                if (text == null || frame.generation != cardChanges.generation) continue

                val form = OfferParser.parse(text)
                val offer = form.toOffer()?.takeIf { plausible(it) }

                // 3) sinal de corrida aceita -> grava UMA vez e esconde a faixa
                val event = acceptance.observe(
                    form.takeIf {
                        form != ignored && (form.fare.isNotBlank() || form.tripKm.isNotBlank())
                    },
                    offer?.let { Evaluator.evaluate(it, criteria) },
                    hasAcceptSignal(text), now, frame.timestamp,
                )
                active.set(offer != null || display.visible != null || acceptance.hasPending)
                if (event != null) {
                    currentAnalysis.value = null
                    withContext(Dispatchers.Main) { overlay?.hide() }
                    try {
                        saveRide(event)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("MonitorService", "Falha ao salvar aceite", e)
                        error.value = "Falha ao salvar aceite. Tentando novamente."
                        forceOcr.set(true)
                        continue
                    }
                    acceptance.recorded()
                    error.value = null
                    ignored = event.form
                    display.clear()
                    forceOcr.set(false)
                    withContext(Dispatchers.Main) { overlay?.hide() }
                    continue
                }

                val visible = display.observe(form.takeIf { offer != null && form != ignored }, now, frame.timestamp, form)
                if (form != ignored || offer == null) ignored = null
                val analysis = visible?.toOffer()?.let { Evaluator.evaluate(it, criteria) }
                if (analysis != currentAnalysis.value) {
                    withContext(Dispatchers.Main) {
                        if (frame.generation == cardChanges.generation) {
                            currentAnalysis.value = analysis
                            if (analysis == null) overlay?.hide() else overlay?.show(analysis)
                        }
                    }
                }
                forceOcr.set(display.needsConfirmation)
            }
        } finally {
            sampler.cancel()
            frames.cancel()
            currentAnalysis.value = null
            withContext(NonCancellable + Dispatchers.Main) { overlay?.hide() }
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

    private suspend fun saveRide(event: AcceptanceTracker.Event) {
        withContext(NonCancellable + Dispatchers.IO) {
            val a = event.analysis
            val loc = withTimeoutOrNull(2000L) { lastKnownLocation(this@MonitorService) }
            AppDatabase.get(this@MonitorService).rideDao().insert(
                Ride(
                    timestamp = System.currentTimeMillis(), fare = a.offer.fare,
                    totalKm = a.offer.totalKm, totalMin = a.offer.totalMin,
                    perKm = a.perKm, perMin = a.perMin,
                    lat = loc?.latitude, lng = loc?.longitude,
                    acceptanceId = event.id,
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
        currentAnalysis.value = null
        super.onDestroy()
    }
}
