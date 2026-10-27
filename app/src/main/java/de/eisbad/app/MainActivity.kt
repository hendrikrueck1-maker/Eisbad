package de.eisbad.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF5CC8FF),
                    background = Color(0xFF0B1620),
                    surface = Color(0xFF0B1620),
                )
            ) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val ui by SessionBus.state.collectAsState()
    when {
        ui.running -> SessionScreen(ui)
        ui.phase == Phase.DONE -> DoneScreen(ui)
        else -> SettingsScreen()
    }
}

private fun sendToService(ctx: Context, action: String) {
    val i = Intent(ctx, SessionService::class.java).setAction(action)
    if (action == SessionService.ACTION_START) ContextCompat.startForegroundService(ctx, i)
    else ctx.startService(i)
}

private fun fmt(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

private fun displayName(ctx: Context, uri: String): String {
    if (uri.isBlank()) return "Keine Musik"
    return try {
        ctx.contentResolver.query(Uri.parse(uri), null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else "Gewählte Datei"
        } ?: "Gewählte Datei"
    } catch (e: Exception) {
        "Gewählte Datei"
    }
}

@Composable
fun SettingSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(valueText, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf(AppSettings.load(ctx)) }
    LaunchedEffect(s) { s.save(ctx) }

    val musicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            s = s.copy(musicUri = uri.toString())
        }
    }

    fun begin() {
        SessionBus.state.value = UiState(true, Phase.PREP, "Vorbereitung", "", "")
        sendToService(ctx, SessionService.ACTION_START)
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { begin() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Eisbad", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Atmung, Atemhalten und Kälte – geführt per Stimme")

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sicherheitshinweis", fontWeight = FontWeight.Bold)
                Text(
                    "Die Atemübung nie im oder direkt am Wasser und nie im Stehen machen – " +
                        "nur sitzend oder liegend an Land. Nicht allein ins Eiswasser. " +
                        "Bei Herz-Kreislauf-Erkrankungen, Schwangerschaft oder Epilepsie vorher " +
                        "ärztlich abklären. Bei Schwindel oder Kribbeln sofort abbrechen."
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = s.acknowledged,
                        onCheckedChange = { s = s.copy(acknowledged = it) },
                    )
                    Text("Gelesen und verstanden")
                }
            }
        }

        Text("Atmung", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        SettingSlider("Runden", "${s.rounds}", s.rounds.toFloat(), 1f..6f, 4) {
            s = s.copy(rounds = it.roundToInt())
        }
        SettingSlider("Atemzüge pro Runde", "${s.breaths}", s.breaths.toFloat(), 20f..40f, 3) {
            s = s.copy(breaths = (it / 5).roundToInt() * 5)
        }
        SettingSlider("Atemtempo (pro Atemzug)", "%.1f s".format(s.breathSec), s.breathSec, 2.5f..6f, 6) {
            s = s.copy(breathSec = (it * 2).roundToInt() / 2f)
        }
        SettingSlider("Atem anhalten", fmt(s.holdSec), s.holdSec.toFloat(), 30f..300f, 17) {
            s = s.copy(holdSec = (it / 15).roundToInt() * 15)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = s.breathCues, onCheckedChange = { s = s.copy(breathCues = it) })
            Text("  Atemtöne (ein/aus)")
        }

        Text("Kälte", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        SettingSlider("Zeit bis zum Einstieg", "${s.entrySec} s", s.entrySec.toFloat(), 15f..120f, 6) {
            s = s.copy(entrySec = (it / 15).roundToInt() * 15)
        }
        SettingSlider("Dauer im Wasser", fmt(s.coldSec), s.coldSec.toFloat(), 30f..600f, 18) {
            s = s.copy(coldSec = (it / 30).roundToInt() * 30)
        }

        Text("Stimme & Musik", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = s.lang == "de", onClick = { s = s.copy(lang = "de") }, label = { Text("Deutsch") })
            FilterChip(selected = s.lang == "en", onClick = { s = s.copy(lang = "en") }, label = { Text("English") })
        }
        SettingSlider("Lautstärke Stimme", "${(s.voiceVol * 100).roundToInt()} %", s.voiceVol, 0f..1f, 0) {
            s = s.copy(voiceVol = it)
        }
        SettingSlider("Lautstärke Musik", "${(s.musicVol * 100).roundToInt()} %", s.musicVol, 0f..1f, 0) {
            s = s.copy(musicVol = it)
        }
        Text(displayName(ctx, s.musicUri))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { musicPicker.launch(arrayOf("audio/*")) }) { Text("Musik wählen") }
            if (s.musicUri.isNotBlank()) {
                OutlinedButton(onClick = { s = s.copy(musicUri = "") }) { Text("Entfernen") }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                s.save(ctx)
                if (Build.VERSION.SDK_INT >= 33) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    begin()
                }
            },
            enabled = s.acknowledged,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { Text("Start", fontSize = 20.sp) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SessionScreen(ui: UiState) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val w = (ctx as? Activity)?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(ui.title, fontSize = 22.sp)
        Spacer(Modifier.height(16.dp))
        Text(ui.big, fontSize = 96.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(ui.sub, fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.phase == Phase.HOLD || ui.phase == Phase.BREATH) {
                Button(onClick = { sendToService(ctx, SessionService.ACTION_SKIP) }) { Text("Weiter") }
            }
            OutlinedButton(onClick = { sendToService(ctx, SessionService.ACTION_STOP) }) { Text("Beenden") }
        }
    }
}

@Composable
fun DoneScreen(ui: UiState) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(ui.title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(ui.big, fontSize = 96.sp)
        Text(ui.sub, fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { SessionBus.state.value = UiState() }) { Text("Zurück") }
    }
}
