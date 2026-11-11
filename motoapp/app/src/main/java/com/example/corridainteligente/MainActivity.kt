package com.example.corridainteligente

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.corridainteligente.ui.*

private enum class Tab(val label: String, val icon: ImageVector) {
    Auto("Início", Icons.Default.Home),
    Analyze("Manual", Icons.Default.CheckCircle),
    History("Histórico", Icons.Default.DateRange),
    Map("Mapa", Icons.Default.Place),
    Settings("Critérios", Icons.Default.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            CorridaTheme {
                val vm: MainViewModel = viewModel()
                var tab by remember { mutableStateOf(Tab.Auto) }
                Scaffold(bottomBar = {
                    NavigationBar(containerColor = PanelColor) {
                        Tab.entries.forEach {
                            NavigationBarItem(
                                selected = tab == it, onClick = { tab = it },
                                icon = { Icon(it.icon, it.label) }, label = { Text(it.label) },
                            )
                        }
                    }
                }) { padding ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                        when (tab) {
                            Tab.Auto -> MonitorScreen()
                            Tab.Analyze -> AnalyzeScreen(vm)
                            Tab.History -> HistoryScreen(vm)
                            Tab.Map -> MapScreen(vm)
                            Tab.Settings -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
