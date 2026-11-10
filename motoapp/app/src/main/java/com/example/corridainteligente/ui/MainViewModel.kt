package com.example.corridainteligente.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.corridainteligente.data.*
import com.example.corridainteligente.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).rideDao()
    private val settings = SettingsRepository(app)

    val criteria = settings.criteria
        .stateIn(viewModelScope, SharingStarted.Eagerly, Criteria())
    val rides = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val stats = rides.map { StatsCalculator.compute(it) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Stats())

    // Teste manual (print). As corridas só entram no histórico pelo monitoramento automático.
    var form by mutableStateOf(OfferForm()); private set
    var message by mutableStateOf<String?>(null); private set
    var reading by mutableStateOf(false); private set

    fun updateForm(f: OfferForm) { form = f }

    fun runOcr(uri: Uri) = viewModelScope.launch {
        reading = true
        try {
            val parsed = OfferParser.parse(Ocr.recognize(getApplication(), uri))
            form = parsed
            message = if (parsed.toOffer() == null)
                "Não consegui ler todos os campos. Complete manualmente." else null
        } catch (e: Exception) {
            message = "Falha no OCR: ${e.message}"
        } finally { reading = false }
    }

    fun deleteRide(r: Ride) = viewModelScope.launch { dao.delete(r) }
    fun saveCriteria(c: Criteria) = viewModelScope.launch { settings.save(c) }
}
