package com.example.notetaker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notetaker.data.InkThickness
import com.example.notetaker.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val hw: HandwritingViewModel = viewModel()
    val modelState by hw.state.collectAsStateWithLifecycle()
    var delaySec by remember { mutableFloatStateOf(SettingsRepository.DEFAULT_COMMIT_DELAY_SEC) }
    var thickness by remember { mutableStateOf(InkThickness.Medium) }

    LaunchedEffect(Unit) {
        delaySec = repo.commitDelaySec.first()
        thickness = repo.inkThickness.first()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Commit delay", style = MaterialTheme.typography.titleMedium)
            Text(
                "Handwriting is recognized ${String.format(Locale.US, "%.1f", delaySec)} s after you stop writing.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Slider(
                value = delaySec,
                onValueChange = { delaySec = (Math.round(it * 10) / 10f) },
                onValueChangeFinished = { scope.launch { repo.setCommitDelaySec(delaySec) } },
                valueRange = SettingsRepository.MIN_COMMIT_DELAY_SEC..SettingsRepository.MAX_COMMIT_DELAY_SEC,
                steps = 24 // 0.1 s steps
            )

            Text(
                "Ink thickness",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InkThickness.entries.forEach { option ->
                    FilterChip(
                        selected = thickness == option,
                        onClick = {
                            thickness = option
                            scope.launch { repo.setInkThickness(option) }
                        },
                        label = { Text(option.label) }
                    )
                }
            }

            Text(
                "Handwriting model",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                when (modelState) {
                    ModelState.Ready -> "Downloaded"
                    ModelState.Downloading, ModelState.Checking -> "Checking / downloading…"
                    ModelState.Offline -> "Not downloaded"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (modelState == ModelState.Offline) {
                Button(onClick = hw::prepare, modifier = Modifier.padding(top = 8.dp)) { Text("Download") }
            }
            Text(
                "Needs the internet once. After that, recognition works fully offline.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
