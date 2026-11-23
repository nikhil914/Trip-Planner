package app.tripplanner.fieldguide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.fieldguide.data.ShiftPreview
import app.tripplanner.fieldguide.logic.dayLabel
import app.tripplanner.fieldguide.logic.hhmm
import app.tripplanner.fieldguide.logic.shiftPlan
import java.time.Instant
import java.time.ZoneId

// ---------- S9 Running late ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningLateSheet(vm: AppViewModel, trip: TripUi, dayId: String, onDismiss: () -> Unit) {
    val day = trip.dayOf(dayId) ?: return onDismiss()
    var delta by remember { mutableIntStateOf(30) }
    var preview by remember { mutableStateOf<ShiftPreview?>(null) }
    LaunchedEffect(delta) { preview = vm.previewShift(dayId, delta) }
    val plan = shiftPlan(day.plans, day.nowMinute(), delta)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = fg.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FgDims.margin).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Running late?", style = FgType.hero, color = fg.text)
            Text("Flexible plans move. Fixed bookings stay where they are.", style = FgType.body, color = fg.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60).forEach { m ->
                    FilterChip(selected = delta == m, onClick = { delta = m }, label = { Text("+$m min") }, modifier = Modifier.heightIn(min = FgDims.touch))
                }
            }
            if (plan.moves.isEmpty()) {
                Callout(Tone.NEUTRAL, "Everything left today is a fixed booking")
            } else {
                SectionTitle("Moves")
                plan.moves.forEach { (b, to) ->
                    Row {
                        Text("${b.startMinute?.hhmm()} → ${to.hhmm()}", style = FgType.body, color = fg.primary, modifier = Modifier.width(120.dp))
                        Text(b.displayTitle, style = FgType.body, color = fg.text)
                    }
                }
            }
            if (plan.stays.isNotEmpty()) {
                SectionTitle("Stays")
                plan.stays.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PushPin, null, tint = fg.textSecondary, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${b.startMinute?.hhmm()}  ${b.displayTitle}, fixed", style = FgType.body, color = fg.textSecondary)
                    }
                }
            }
            preview?.let { pv ->
                val problems = pv.collisions.map { "${it.blockTitle} would overlap ${it.fixedTitle} (fixed, ${it.fixedStart.hhmm()}) by ${it.overlapMinutes} min." } +
                    pv.lateEnds.map {
                        val closing = it.closesMinute
                        if (closing != null && it.endMinute > closing) "${it.title} would end at ${it.endMinute.hhmm()}, after closing at ${closing.hhmm()}."
                        else "${it.title} would run past midnight."
                    }
                if (problems.isNotEmpty()) Callout(Tone.DANGER, "Check before you shift", problems)
            }
            BigAction("Shift by $delta min", null, Modifier.fillMaxWidth()) {
                vm.shift(dayId, delta)
                onDismiss()
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
        }
    }
}

// ---------- S17 Sync sheet ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSheet(vm: AppViewModel, trip: TripUi, onDismiss: () -> Unit) {
    val session by vm.session.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val outbox by vm.outboxSize.collectAsStateWithLifecycle()
    val files = trip.files

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = fg.card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = FgDims.margin).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sync", style = FgType.hero, color = fg.text)
            when {
                session.demo -> Callout(Tone.NEUTRAL, "Demo trip", listOf("Nothing leaves this phone. Pair with the planner to use your real trip."))
                session.revoked -> Callout(Tone.DANGER, "Removed in the planner", listOf("This phone was removed from the trip in the planner. Your plan stays readable, but won't update. Pair again from Settings."))
                sync.run == SyncRun.FAILED -> Callout(Tone.WARNING, "Couldn't reach the planner", listOf(sync.message ?: "Your plan still works offline."))
            }
            if (sync.run == SyncRun.SYNCING) LinearProgressIndicator(Modifier.fillMaxWidth(), color = fg.primary, trackColor = fg.cardBorder)
            KeyFacts(
                listOf(
                    "Last synced" to if (session.lastSyncAt > 0) syncTime(session.lastSyncAt) else "Not yet",
                    "Waiting to upload" to if (outbox == 0) "Nothing" else "$outbox ${if (outbox == 1) "change" else "changes"}",
                    "Files on phone" to "${files.count { it.ready }} of ${files.size}",
                    "Planner" to (session.serverUrl?.removePrefix("https://") ?: "Not paired"),
                ),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Upload on Wi-Fi only", style = FgType.body, color = fg.text)
                    Text("Each upload is a few kilobytes.", style = FgType.meta, color = fg.textSecondary)
                }
                Switch(checked = session.wifiOnly, onCheckedChange = vm::setWifiOnly)
            }
            Text("The plan is on this phone. Sync only brings changes from the planner and sends your check-offs.", style = FgType.meta, color = fg.textSecondary)
            BigAction(if (sync.run == SyncRun.SYNCING) "Syncing…" else "Sync now", null, Modifier.fillMaxWidth()) { vm.refresh() }
            Spacer(Modifier.width(8.dp))
        }
    }
}

fun syncTime(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return "${dayLabel(t.toLocalDate().toString())} ${(t.hour * 60 + t.minute).hhmm()}"
}
