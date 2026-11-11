package com.example.corridainteligente.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.corridainteligente.R
import com.example.corridainteligente.domain.Analysis
import com.example.corridainteligente.domain.Verdict
import com.example.corridainteligente.domain.brl
import com.example.corridainteligente.domain.clean

/**
 * Faixa flutuante só de leitura (sem botões; toques passam para o app de corridas).
 * Chamar show/hide sempre na thread principal.
 *
 * [bounds] guarda a área da faixa na tela. O serviço apaga essa área do frame antes do
 * OCR/detecção de mudança, então os números mostrados aqui nunca são lidos como oferta.
 */
class OverlayView(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var attached = false
    @Volatile var bounds: Rect? = null
        private set

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private val title = TextView(ctx).apply {
        textSize = 19f; setTypeface(null, Typeface.BOLD)
    }
    private val details = TextView(ctx).apply {
        textSize = 11f
        setTextColor(0xFFBBC4BE.toInt())
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    private val heading = TextView(ctx).apply {
        textSize = 12f
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        setPadding(dp(2), 0, 0, dp(6))
    }
    private val fare = MetricTile("Valor da corrida", 0xFF39CE7B.toInt())
    private val distance = MetricTile("Distância total")
    private val perKm = MetricTile("Ganho por km")
    private val perHour = MetricTile("Ganho por hora")

    private inner class MetricTile(label: String, accent: Int = Color.WHITE) : LinearLayout(ctx) {
        val value = TextView(ctx).apply {
            textSize = 20f
            setTextColor(accent)
            setTypeface(null, Typeface.BOLD)
        }

        init {
            orientation = VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                setColor(0xFF111413.toInt())
                cornerRadius = dp(10).toFloat()
            }
            addView(TextView(ctx).apply {
                text = label
                textSize = 10f
                setTextColor(0xFFADB7B0.toInt())
            })
            addView(value)
        }
    }

    private fun metricRow(first: MetricTile, second: MetricTile) = LinearLayout(ctx).apply {
        addView(first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(android.view.View(ctx), LinearLayout.LayoutParams(dp(8), 1))
        addView(second, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = GradientDrawable().apply {
            setColor(0xFF191D1A.toInt())
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), 0xFF354239.toInt())
        }
        addView(heading)
        addView(metricRow(fare, distance))
        addView(android.view.View(ctx), LinearLayout.LayoutParams(1, dp(6)))
        addView(metricRow(perKm, perHour))
        addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })
        addView(details)
        addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val pad = dp(6)
            bounds = Rect(loc[0] - pad, loc[1] - pad, loc[0] + v.width + pad, loc[1] + v.height + pad)
        }
    }

    private val params = WindowManager.LayoutParams(
        (realScreenSize(ctx).first * 0.94).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = dp(8)
        alpha = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
        } else 0.8f
    }

    fun show(a: Analysis) {
        val (color, text) = when (a.verdict) {
            Verdict.ACCEPT -> 0xFF39CE7B.toInt() to "CORRIDA BOA"
            Verdict.MAYBE -> 0xFFF2C568.toInt() to "ATENÇÃO"
            Verdict.REJECT -> 0xFFFF8585.toInt() to "RECUSAR"
        }
        val o = a.offer
        heading.text = ctx.getString(if (o.negotiable) R.string.assistant_negotiated else R.string.assistant_title)
        fare.value.text = o.fare.brl()
        distance.value.text = ctx.getString(R.string.distance_km, o.totalKm.clean())
        perKm.value.text = a.perKm.brl()
        perHour.value.text = (a.perMin * 60).brl()
        title.setTextColor(color)
        title.text = text
        details.text = buildString {
            append("${o.totalMin.clean()} min · ${a.perMin.brl()}/min")
            if (o.stopCount > 0) append(" · " + ctx.resources.getQuantityString(R.plurals.offer_stops, o.stopCount, o.stopCount))
            append("\n")
            append(a.reasons.joinToString("\n"))
        }
        if (!attached) {
            wm.addView(root, params)
            attached = true
            Log.d("CorridaOverlay", "Faixa exibida")
        }
    }

    fun hide() {
        if (attached) {
            runCatching { wm.removeView(root) }
            attached = false
            Log.d("CorridaOverlay", "Faixa oculta")
        }
        bounds = null
    }
}
