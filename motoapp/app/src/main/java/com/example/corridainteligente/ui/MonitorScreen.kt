package com.example.corridainteligente.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.corridainteligente.service.MonitorService

@Composable
fun MonitorScreen() {
    val ctx = LocalContext.current
    val running by MonitorService.running.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val hasOverlay = remember(tick) { Settings.canDrawOverlays(ctx) }

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val data = it.data
        if (it.resultCode == Activity.RESULT_OK && data != null) MonitorService.start(ctx, it.resultCode, data)
    }
    val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Monitoramento automático", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            if (running) "🟢 Ativo — a faixa aparece sozinha quando uma oferta surgir, e a corrida é gravada quando você aceitar no app de corridas."
            else "🔴 Parado. Ao iniciar, o app acompanha a tela e mostra ACEITAR / ATENÇÃO / RECUSAR sobre o app de corridas."
        )

        if (!hasOverlay) {
            Text("1) Permita \"Exibir sobre outros apps\" para a faixa flutuante.")
            Button(onClick = {
                overlayLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                )
            }, Modifier.fillMaxWidth()) { Text("Conceder permissão de sobreposição") }
        } else if (!running) {
            Button(onClick = {
                val perms = buildList {
                    if (Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
                    add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                }
                permissionsLauncher.launch(perms.toTypedArray())
            }, Modifier.fillMaxWidth()) { Text("Iniciar monitoramento") }
        } else {
            Button(
                onClick = { MonitorService.stop(ctx) }, Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Parar monitoramento") }
        }

        Text(
            "Como funciona: você aceita ou recusa direto no app de corridas. O Corrida Inteligente só observa: " +
            "se, depois de uma oferta, aparecer na tela um dos textos de \"corrida aceita\" (aba Critérios), " +
            "ele grava a corrida uma única vez e fecha a faixa. A captura pede sua autorização a cada início " +
            "(regra do Android).",
            fontSize = 13.sp,
        )
    }
}
