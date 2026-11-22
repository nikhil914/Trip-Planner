package app.tripplanner.fieldguide.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.fieldguide.BuildConfig
import app.tripplanner.fieldguide.briefing.BriefingScheduler
import app.tripplanner.fieldguide.data.ThemeMode
import app.tripplanner.fieldguide.logic.hhmm

@Composable
private fun SettingRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = FgDims.touch), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FgType.body, color = fg.text)
            subtitle?.let { Text(it, style = FgType.meta, color = fg.textSecondary) }
        }
        trailing()
    }
}

// ---------- S18 Settings ----------

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val nav = LocalNav.current
    val session by vm.session.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val trip by vm.trip.collectAsStateWithLifecycle()
    val outbox by vm.outboxSize.collectAsStateWithLifecycle()
    var confirmUnpair by remember { mutableStateOf(false) }
    var lockError by remember { mutableStateOf<String?>(null) }
    val check = rememberOwnerCheck { ok -> if (ok) vm.setLock(true) }

    var exactAllowed by remember { mutableStateOf(BriefingScheduler.canScheduleExact(context)) }
    LifecycleResumeEffect(Unit) {
        exactAllowed = BriefingScheduler.canScheduleExact(context)
        onPauseOrDispose { }
    }

    LazyColumn(contentPadding = PaddingValues(FgDims.margin), verticalArrangement = Arrangement.spacedBy(FgDims.gap)) {
        item {
            FgCard {
                SectionTitle("Trip")
                Text(trip.trip?.title ?: "No trip", style = FgType.cardTitle, color = fg.text)
                Text(
                    when {
                        session.demo -> "Demo trip. Nothing leaves this phone."
                        session.revoked -> "Removed in the planner. Pair again to sync."
                        else -> session.serverUrl.orEmpty()
                    },
                    style = FgType.meta, color = fg.textSecondary,
                )
                OutlinedButton(onClick = vm::pairAnother, modifier = Modifier.fillMaxWidth()) {
                    Text("Pair a different trip")
                }
            }
        }
        item {
            FgCard {
                SectionTitle("Security")
                SettingRow("App lock", if (canLock(context)) "Fingerprint, face or phone PIN on open" else "Set a screen lock on the phone first") {
                    Switch(
                        checked = settings.lockEnabled,
                        enabled = canLock(context),
                        onCheckedChange = { on ->
                            lockError = null
                            if (on) check.run(onOk = { vm.setLock(true) }, onError = { lockError = it }) else vm.setLock(false)
                        },
                    )
                }
                lockError?.let { Text(it, style = FgType.meta, color = fg.danger) }
                if (settings.lockEnabled) {
                    Text("Lock after", style = FgType.meta, color = fg.textSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(FgDims.gap / 2)) {
                        items(listOf(0, 1, 5, 15)) { m ->
                            FilterChip(selected = settings.lockAfterMinutes == m, onClick = { vm.setLockAfter(m) }, label = { Text(if (m == 0) "At once" else "$m min") })
                        }
                    }
                    Text("Emergency calls work without unlocking.", style = FgType.meta, color = fg.textSecondary)
                }
            }
        }
        item {
            FgCard {
                SectionTitle("Display")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(FgDims.gap / 2)) {
                    items(ThemeMode.entries.toList()) { mode ->
                        FilterChip(
                            selected = settings.theme == mode, onClick = { vm.setTheme(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "System"
                                        ThemeMode.DARK -> "Dark"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.SUNLIGHT -> "Sunlight"
                                    },
                                )
                            },
                        )
                    }
                }
                Text("Sunlight is high contrast for bright days. Text size follows the phone's setting.", style = FgType.meta, color = fg.textSecondary)
            }
        }
        item {
            FgCard {
                SectionTitle("Morning briefing")
                trip.trip?.let { Text("Each trip morning at ${it.wakeMinute.hhmm()} destination time. Change it in the planner.", style = FgType.body, color = fg.text) }
                Text(
                    if (exactAllowed) "Exact alarms are allowed. The briefing arrives on the minute."
                    else "Exact alarms are off, so the briefing can arrive a few minutes late.",
                    style = FgType.meta, color = fg.textSecondary,
                )
                if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }) { Text("Allow exact alarms") }
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("Notification settings") }
            }
        }
        item {
            FgCard {
                SectionTitle("Sync")
                SettingRow("Upload on Wi-Fi only", "Off: check-offs also upload on mobile data") {
                    Switch(checked = session.wifiOnly, onCheckedChange = vm::setWifiOnly)
                }
                TextButton(onClick = { nav.sheet = Sheet.Sync }) { Text("Sync details") }
            }
        }
        item {
            FgCard {
                SectionTitle("Remove from this phone")
                Text("Deletes the plan, tickets and any changes not yet uploaded.", style = FgType.body, color = fg.text)
                OutlinedButton(onClick = { confirmUnpair = true }, modifier = Modifier.fillMaxWidth()) { Text("Remove trip and unpair", color = fg.danger) }
            }
        }
        item {
            Text(
                "Version ${BuildConfig.VERSION_NAME}. No location permission. No tracking. Lists, theme and lock settings stay on this phone.",
                style = FgType.meta, color = fg.textSecondary,
            )
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Remove this trip?") },
            text = {
                Text(
                    if (outbox > 0) "$outbox ${if (outbox == 1) "change has" else "changes have"} not uploaded and will be lost. You can pair again afterwards."
                    else "The plan and tickets will be deleted from this phone. You can pair again afterwards.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    nav.stack.clear()
                    vm.unpair()
                }) { Text("Remove", color = fg.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Keep") } },
        )
    }
}
