package app.tripplanner.fieldguide.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.fieldguide.BuildConfig
import app.tripplanner.fieldguide.briefing.BriefingScheduler

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val session by vm.session.collectAsStateWithLifecycle()
    val outbox by vm.outboxSize.collectAsStateWithLifecycle()
    var confirmUnpair by remember { mutableStateOf(false) }

    // Re-read after returning from the system grant screen.
    var exactAllowed by remember { mutableStateOf(BriefingScheduler.canScheduleExact(context)) }
    LifecycleResumeEffect(Unit) {
        exactAllowed = BriefingScheduler.canScheduleExact(context)
        vm.onAppOpened()
        onPauseOrDispose { }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Panel {
            Text("Sync", fontWeight = FontWeight.Bold)
            Text(
                when {
                    session.demo -> "Demo trip. Nothing leaves this phone."
                    session.revoked -> "This phone was unpaired on the web. Pair again to sync."
                    else -> session.serverUrl.orEmpty()
                },
                color = Dim,
            )
            Text(
                if (outbox == 0) "No changes waiting to upload." else "$outbox ${if (outbox == 1) "change" else "changes"} waiting to upload.",
                color = Dim,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Upload on Wi-Fi only")
                    Text("Off: check-offs also upload on mobile data. Each upload is a few kilobytes.", color = Dim, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = session.wifiOnly, onCheckedChange = vm::setWifiOnly)
            }
        }

        Panel {
            Text("Morning briefing", fontWeight = FontWeight.Bold)
            Text(
                if (exactAllowed) "Exact alarms are allowed. The briefing arrives at the wake-up time."
                else "Exact alarms are not allowed, so the briefing can arrive a few minutes late.",
                color = Dim,
            )
            if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("Allow exact alarms") }
            }
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }) { Text("Notification settings") }
        }

        Panel {
            Text("Trip on this phone", fontWeight = FontWeight.Bold)
            Text("Removing it deletes the plan, tickets, and any changes not yet uploaded.", color = Dim)
            OutlinedButton(onClick = { confirmUnpair = true }) { Text("Remove trip and unpair", color = MaterialTheme.colorScheme.error) }
        }

        Text("Version ${BuildConfig.VERSION_NAME}. No location permission. No background service.", color = Dim, style = MaterialTheme.typography.bodySmall)
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Remove this trip?") },
            text = {
                Text(
                    if (outbox > 0) "$outbox ${if (outbox == 1) "change has" else "changes have"} not uploaded and will be lost."
                    else "The plan and tickets will be deleted from this phone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    vm.unpair()
                    onBack()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Keep") } },
        )
    }
}
