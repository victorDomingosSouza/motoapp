package com.example.corridainteligente.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.corridainteligente.domain.Analysis
import com.example.corridainteligente.domain.Verdict
import com.example.corridainteligente.domain.f

/**
 * Faixa flutuante com o veredito. Chamar sempre na thread principal.
 *
 * IMPORTANTE: o texto daqui também aparece nos frames capturados. Por isso NÃO usamos
 * "R$ <número>" nem o padrão "<n> min ... <n> km", para o OCR não confundir o overlay
 * com a oferta real.
 */
class OverlayView(
    private val ctx: Context,
    private val onAccept: () -> Unit,
) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var attached = false

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private val title = TextView(ctx).apply {
        textSize = 26f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
    }
    private val details = TextView(ctx).apply { textSize = 15f; setTextColor(Color.WHITE) }

    private val acceptBtn = Button(ctx).apply { text = "Aceitei"; setOnClickListener { onAccept() } }
    private val closeBtn = Button(ctx).apply { text = "Fechar"; setOnClickListener { hide() } }

    private val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        addView(title); addView(details)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(acceptBtn, LinearLayout.LayoutParams(0, -2, 1f))
            addView(closeBtn, LinearLayout.LayoutParams(0, -2, 1f))
        })
    }

    private val params = WindowManager.LayoutParams(
        (ctx.resources.displayMetrics.widthPixels * 0.94).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(48) }

    fun show(a: Analysis) {
        val (color, text) = when (a.verdict) {
            Verdict.ACCEPT -> 0xFF2E7D32.toInt() to "ACEITAR"
            Verdict.MAYBE -> 0xFFF9A825.toInt() to "ATENÇÃO"
            Verdict.REJECT -> 0xFFC62828.toInt() to "RECUSAR"
        }
        root.background = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat() }
        title.text = text
        details.text = "${a.perKm.f()} por km  •  ${a.perHour.f()} por hora\n" +
            a.reasons.joinToString("\n") { "• $it" }
        if (!attached) { wm.addView(root, params); attached = true }
    }

    fun hide() {
        if (attached) { runCatching { wm.removeView(root) }; attached = false }
    }
}
