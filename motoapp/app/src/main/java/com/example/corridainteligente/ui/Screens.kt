package com.example.corridainteligente.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.corridainteligente.domain.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.example.corridainteligente.R
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.*

private val Green = Color(0xFF2E7D32)
private val Red = Color(0xFFC62828)

// ===================== TESTE MANUAL (sem botões de aceitar/recusar) =====================
@Composable
fun AnalyzeScreen(vm: MainViewModel) {
    val form = vm.form
    val criteria by vm.criteria.collectAsState()
    val analysis = remember(form, criteria) { form.toOffer()?.let { Evaluator.evaluate(it, criteria) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::runOcr)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Teste manual", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Serve para conferir como o app lê um print ou simular valores. " +
            "O histórico é alimentado só pelo monitoramento automático.", fontSize = 13.sp
        )

        Button(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !vm.reading, modifier = Modifier.fillMaxWidth(),
        ) { Text(if (vm.reading) "Lendo..." else "Ler print da oferta (OCR)") }

        vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        NumField("Valor da corrida (R$)", form.fare) { vm.updateForm(form.copy(fare = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Busca (km)", form.pickupKm, Modifier.weight(1f)) { vm.updateForm(form.copy(pickupKm = it)) }
            NumField("Busca (min)", form.pickupMin, Modifier.weight(1f)) { vm.updateForm(form.copy(pickupMin = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Viagem (km)", form.tripKm, Modifier.weight(1f)) { vm.updateForm(form.copy(tripKm = it)) }
            NumField("Viagem (min)", form.tripMin, Modifier.weight(1f)) { vm.updateForm(form.copy(tripMin = it)) }
        }

        if (vm.reading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!vm.reading && analysis != null) AnalysisCard(analysis)
        TextButton(onClick = { vm.updateForm(OfferForm()) }) { Text("Limpar campos") }
    }
}

// ===================== HISTÓRICO =====================
@Composable
fun HistoryScreen(vm: MainViewModel) {
    val stats by vm.stats.collectAsState()
    val rides by vm.rides.collectAsState()
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Histórico", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Corridas realizadas: ${stats.count}")
                    Text("Total recebido: ${stats.total.brl()}")
                    Text("Média por km: ${stats.avgPerKm.brl()}")
                    Text("Média por minuto: ${stats.avgPerMin.brl()}")
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Melhores horários", fontWeight = FontWeight.Bold)
                    if (stats.bestHours.isEmpty()) Text("Sem dados ainda")
                    stats.bestHours.forEach {
                        Text("%02d:00 – %s/min (%d corridas)".format(it.hour, it.perMin.brl(), it.count))
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Melhores regiões", fontWeight = FontWeight.Bold)
                    if (stats.bestRegions.isEmpty()) Text("Sem dados (permita a localização)")
                    stats.bestRegions.take(5).forEach {
                        Text("(%.2f, %.2f) – %s/km (%d corridas)".format(it.lat, it.lng, it.perKm.brl(), it.count))
                    }
                }
            }
        }
        item { Text("Corridas", fontWeight = FontWeight.Bold) }
        items(rides, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${r.fare.brl()}  •  ${fmt.format(Date(r.timestamp))}")
                        Text("${r.totalKm.clean()} km  •  ${r.totalMin.clean()} min  •  " +
                            "${r.perKm.brl()}/km  •  ${r.perMin.brl()}/min", fontSize = 12.sp)
                    }
                    TextButton(onClick = { vm.deleteRide(r) }) { Text("Excluir") }
                }
            }
        }
    }
}

// ===================== CONFIGURAÇÕES =====================
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val c by vm.criteria.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Meus critérios", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        SettingField("Mínimo R$/km", c.minPerKm) { vm.saveCriteria(c.copy(minPerKm = it)) }
        SettingField("Mínimo R$/min", c.minPerMin) { vm.saveCriteria(c.copy(minPerMin = it)) }
        SettingField("Valor mínimo da corrida (R$)", c.minFare) { vm.saveCriteria(c.copy(minFare = it)) }
        SettingField("Distância máx. até o passageiro (km)", c.maxPickupKm) { vm.saveCriteria(c.copy(maxPickupKm = it)) }
        SettingField("Custo por km (combustível + manutenção)", c.costPerKm) { vm.saveCriteria(c.copy(costPerKm = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = c.useNet, onCheckedChange = { vm.saveCriteria(c.copy(useNet = it)) })
            Spacer(Modifier.width(8.dp))
            Text("Calcular ganho líquido (descontar custo por km)")
        }

        HorizontalDivider()
        Text("Detecção de corrida aceita", fontWeight = FontWeight.Bold)
        Text(
            "Depois de uma oferta, se algum destes textos aparecer na tela, a corrida é gravada no histórico. " +
            "Separe por vírgula e ajuste conforme a tela do seu app logo depois de aceitar.",
            fontSize = 13.sp,
        )
        var signals by remember(c.acceptSignals) { mutableStateOf(c.acceptSignals) }
        OutlinedTextField(
            value = signals, onValueChange = { signals = it },
            label = { Text("Textos de \"corrida aceita\"") }, modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.saveCriteria(c.copy(acceptSignals = signals)) }) { Text("Salvar textos") }
    }
}

@Composable
private fun SettingField(label: String, value: Double, onValid: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.f()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; it.replace(',', '.').toDoubleOrNull()?.let(onValid) },
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

// ===================== MAPA =====================
@Composable
fun MapScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val mapStyle = remember(context) { MapStyleOptions.loadRawResourceStyle(context, R.raw.map_dark) }
    val stats by vm.stats.collectAsState()
    val c by vm.criteria.collectAsState()
    val start = stats.bestRegions.firstOrNull()?.let { LatLng(it.lat, it.lng) } ?: LatLng(-23.5505, -46.6333)
    val camera = rememberCameraPositionState(key = "${start.latitude},${start.longitude}") {
        position = CameraPosition.fromLatLngZoom(start, 11f)
    }

    Column(Modifier.fillMaxSize()) {
        Text("Regiões (verde = acima do seu mínimo R$/km)", Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
        GoogleMap(Modifier.fillMaxSize(), cameraPositionState = camera,
            properties = MapProperties(mapStyleOptions = mapStyle)) {
            stats.bestRegions.forEach { r ->
                val good = r.perKm >= c.minPerKm
                Circle(
                    center = LatLng(r.lat, r.lng), radius = 600.0,
                    fillColor = (if (good) Green else Red).copy(alpha = 0.35f),
                    strokeColor = if (good) Green else Red,
                )
                Marker(state = MarkerState(LatLng(r.lat, r.lng)),
                    title = "${r.perKm.brl()}/km", snippet = "${r.count} corridas")
            }
        }
    }
}
