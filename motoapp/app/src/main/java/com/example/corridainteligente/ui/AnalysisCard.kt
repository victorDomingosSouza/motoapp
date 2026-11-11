package com.example.corridainteligente.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.corridainteligente.domain.*

@Composable
fun AnalysisCard(analysis: Analysis?, modifier: Modifier = Modifier, waiting: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PanelColor),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Assistente de corridas", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(
                    if (analysis != null) "Oferta detectada" else if (waiting) "Lendo tela" else "Pronto",
                    color = AccentGreen,
                    fontSize = 11.sp,
                    modifier = Modifier.background(AccentGreen.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Valor da corrida", analysis?.offer?.fare?.brl() ?: "—", Modifier.weight(1f), AccentGreen)
                MetricTile("Distância total", analysis?.offer?.totalKm?.let { "${it.clean()} km" } ?: "—", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Ganho por km", analysis?.perKm?.brl() ?: "—", Modifier.weight(1f))
                MetricTile("Ganho por hora", analysis?.let { (it.perMin * 60).brl() } ?: "—", Modifier.weight(1f))
            }
            if (analysis != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tempo total", color = colors.onSurfaceVariant, fontSize = 13.sp)
                    Text("${analysis.offer.totalMin.clean()} min · ${analysis.perMin.brl()}/min", fontSize = 13.sp)
                }
                Text(
                    "Busca ${analysis.offer.pickupKm.clean()} km · Viagem ${analysis.offer.tripKm.clean()} km",
                    color = colors.onSurfaceVariant, fontSize = 12.sp,
                )
                if (analysis.offer.negotiable) Text("Oferta Negocia", color = AccentGreen, fontSize = 12.sp)
                if (analysis.offer.stopCount > 0) {
                    Text("${analysis.offer.stopCount} parada(s)", color = colors.onSurfaceVariant, fontSize = 12.sp)
                }
                val (accent, title) = when (analysis.verdict) {
                    Verdict.ACCEPT -> AccentGreen to "CORRIDA BOA"
                    Verdict.MAYBE -> Color(0xFFF2C568) to "ATENÇÃO"
                    Verdict.REJECT -> Color(0xFFFF8585) to "RECUSAR"
                }
                Column(
                    Modifier.fillMaxWidth().background(accent.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(title, color = accent, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(analysis.reasons.joinToString("\n"), fontSize = 12.sp)
                    Text("Ganho considerado ${analysis.earned.brl()}", color = colors.onSurfaceVariant, fontSize = 12.sp)
                }
            } else {
                Text(
                    if (waiting) "Aguardando uma oferta na tela…" else "Inicie para acompanhar as ofertas.",
                    modifier = Modifier.padding(top = 4.dp),
                    color = colors.onSurfaceVariant, fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(
        modifier.background(TileColor, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = (-0.5).sp))
    }
}
