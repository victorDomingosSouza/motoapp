package com.example.corridainteligente.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
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
        textSize = 26f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
    }
    private val details = TextView(ctx).apply { textSize = 14f; setTextColor(Color.WHITE) }

    private val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        addView(title); addView(details)
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
    ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(48) }

    fun show(a: Analysis) {
        val (color, text) = when (a.verdict) {
            Verdict.ACCEPT -> 0xFF2E7D32.toInt() to "ACEITAR"
            Verdict.MAYBE -> 0xFFF9A825.toInt() to "ATENÇÃO"
            Verdict.REJECT -> 0xFFC62828.toInt() to "RECUSAR"
        }
        val o = a.offer
        root.background = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat() }
        title.text = text
        details.text = buildString {
            append("${a.perKm.brl()}/km  •  ${a.perMin.brl()}/min\n")
            append("Lido: ${o.fare.brl()}  •  ${o.totalKm.clean()} km  •  ${o.totalMin.clean()} min\n")
            append("Busca ${o.pickupMin.clean()} min / ${o.pickupKm.clean()} km  •  ")
            append("Viagem ${o.tripMin.clean()} min / ${o.tripKm.clean()} km\n")
            append("Ganho considerado: ${a.earned.brl()}")
            a.reasons.forEach { append("\n• $it") }
        }
        if (!attached) { wm.addView(root, params); attached = true }
    }

    fun hide() {
        if (attached) { runCatching { wm.removeView(root) }; attached = false }
        bounds = null
    }
}
