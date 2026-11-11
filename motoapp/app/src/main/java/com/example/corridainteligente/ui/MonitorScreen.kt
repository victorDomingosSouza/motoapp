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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.corridainteligente.service.MonitorService

@Composable
fun MonitorScreen() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val running by MonitorService.running.collectAsState()
    val error by MonitorService.error.collectAsState()
    val analysis by MonitorService.currentAnalysis.collectAsState()
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var requesting by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, ctx) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasOverlay = Settings.canDrawOverlays(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        requesting = false
        val data = it.data
        if (it.resultCode == Activity.RESULT_OK && data != null) MonitorService.start(ctx, it.resultCode, data)
    }
    val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasOverlay = Settings.canDrawOverlays(ctx)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Corrida Inteligente", fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
                Text("Seu assistente na direção", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Box(Modifier.size(9.dp).background(if (running) AccentGreen else Color(0xFF65716A), CircleShape))
        }

        AnalysisCard(analysis, waiting = running)

        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (running) Color(0xFF332222) else Color(0xFF14281D),
                border = BorderStroke(1.dp, if (running) Color(0xFF644040) else Color(0xFF285D3D)),
                modifier = Modifier.size(176.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            when {
                                running -> MonitorService.stop(ctx)
                                !hasOverlay -> overlayLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                                )
                                else -> {
                                    requesting = true
                                    val perms = buildList {
                                        if (Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
                                        add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                                        add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }
                                    permissionsLauncher.launch(perms.toTypedArray())
                                }
                            }
                        },
                        enabled = !requesting,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (running) Color(0xFFFF8585) else AccentGreen,
                            contentColor = Color(0xFF092313),
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(144.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (requesting) {
                                CircularProgressIndicator(Modifier.size(30.dp), color = AccentGreen, strokeWidth = 2.dp)
                            } else if (running) {
                                Box(Modifier.size(24.dp).background(Color(0xFF321515), MaterialTheme.shapes.extraSmall))
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(42.dp))
                            }
                            Text(if (running) "PARAR" else "INICIAR", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
            Text(
                if (requesting) "Aguardando autorização…" else if (running) "Monitoramento ativo" else "Monitoramento pausado",
                color = if (running) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        Text(
            if (!hasOverlay) "Toque em iniciar para permitir a exibição sobre outros apps."
            else "Aceite a corrida no app de viagens. O assistente acompanha a oferta e avalia seus critérios.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}
