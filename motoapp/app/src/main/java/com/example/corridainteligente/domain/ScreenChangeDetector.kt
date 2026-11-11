package com.example.corridainteligente.domain

import kotlin.math.abs

/** A geração invalida resultados de OCR quando o conteúdo capturado muda. */
class ScreenChangeDetector(private val pixelDelta: Int = 28, private val ignoreTopHalf: Boolean = false) {
    private var baseline: IntArray? = null
    @Volatile var generation = 0L
        private set

    fun sample(signature: IntArray, threshold: Float, resetBaseline: Boolean = false): Boolean {
        val previous = baseline
        if (resetBaseline || previous == null || previous.size != signature.size) {
            baseline = signature
            return false
        }
        val firstIndex = if (ignoreTopHalf) signature.size / 2 else 0
        val changedPixels = (firstIndex until signature.size).count { abs(signature[it] - previous[it]) > pixelDelta }
        val changed = changedPixels.toFloat() / (signature.size - firstIndex) >= threshold
        if (changed) {
            baseline = signature
            generation++
        }
        return changed
    }

    fun remember(signature: IntArray) {
        baseline = signature
    }
}
