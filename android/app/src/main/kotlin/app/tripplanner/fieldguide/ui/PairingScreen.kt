package app.tripplanner.fieldguide.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.fieldguide.briefing.BriefingScheduler
import app.tripplanner.fieldguide.data.PairPhase
import app.tripplanner.fieldguide.logic.hhmm

// ---------- S1 Welcome and pairing ----------

@Composable
fun PairingScreen(vm: AppViewModel, offer: PairingOffer?, canCancel: Boolean) {
    val context = LocalContext.current
    val phase by vm.pairPhase.collectAsStateWithLifecycle()
    var server by rememberSaveable(offer) { mutableStateOf(offer?.serverUrl.orEmpty()) }
    var code by rememberSaveable(offer) { mutableStateOf(offer?.code.orEmpty()) }
    var advanced by rememberSaveable { mutableStateOf(server.isBlank()) }
    var error by remember { mutableStateOf<String?>(null) }

    phase?.let { PairingProgress(it); return }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Field Guide", style = FgType.hero, color = fg.text)
        Text("Your trip, offline, in your pocket.", style = FgType.cardTitle, color = fg.textSecondary)
        if (offer != null && offer.code.isNotEmpty()) {
            Callout(Tone.PRIMARY, "Pair this phone with your trip?", listOf("From the QR code in the planner."))
        }
        if (canCancel) Callout(Tone.WARNING, "Pairing replaces the trip now on this phone")

        BigAction("Scan QR code", Icons.Rounded.QrCodeScanner, Modifier.fillMaxWidth()) {
            try {
                context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                error = "No camera app. Enter the code instead."
            }
        }
        Text("Point the camera at the QR in the planner's Pair phone dialog, then tap the link it shows.", style = FgType.meta, color = fg.textSecondary)

        Text("Or enter the 6-digit code", style = FgType.section, color = fg.textSecondary)
        OutlinedTextField(
            value = code,
            onValueChange = { input -> code = input.filter(Char::isDigit).take(6); error = null },
            singleLine = true,
            textStyle = TextStyle(fontSize = 32.sp, letterSpacing = 12.sp, textAlign = TextAlign.Center, color = fg.text, fontFeatureSettings = "tnum"),
            placeholder = { Text("000000", fontSize = 32.sp, letterSpacing = 12.sp, color = fg.cardBorder, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        if (advanced) {
            OutlinedTextField(
                value = server,
                onValueChange = { server = it; error = null },
                label = { Text("Planner address") },
                placeholder = { Text("https://your-planner.example") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextButton(onClick = { advanced = true }) { Text("Advanced: planner address") }
        }
        error?.let { Text(it, style = FgType.body, color = fg.danger) }
        BigAction("Pair and download trip", null, Modifier.fillMaxWidth(), primary = code.length == 6) { error = vm.pair(server, code) }
        Text("Pairing needs internet once. After that the trip works offline.", style = FgType.meta, color = fg.textSecondary)

        if (canCancel) {
            TextButton(onClick = vm::cancelOffer, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        } else {
            TextButton(onClick = vm::loadDemo, modifier = Modifier.fillMaxWidth()) { Text("Try the demo trip") }
            Text("The demo starts today, stays on this phone and never syncs.", style = FgType.meta, color = fg.textSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PairingProgress(phase: PairPhase) {
    val steps = listOf(PairPhase.CONNECTING to "Connecting", PairPhase.PLAN to "Downloading plan", PairPhase.FILES to "Downloading tickets", PairPhase.READY to "Ready")
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Text("Getting your trip", style = FgType.hero, color = fg.text)
        steps.forEach { (p, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    p.ordinal < phase.ordinal || phase == PairPhase.READY -> Icon(Icons.Rounded.CheckCircle, null, tint = fg.success, modifier = Modifier.size(24.dp))
                    p == phase -> CircularProgressIndicator(Modifier.size(24.dp), color = fg.primary, strokeWidth = 3.dp)
                    else -> Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = fg.cardBorder, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(label, style = FgType.cardTitle, color = if (p.ordinal <= phase.ordinal) fg.text else fg.textSecondary)
            }
        }
        Text("Keep the app open. This takes a few seconds on Wi-Fi.", style = FgType.meta, color = fg.textSecondary)
    }
}

// ---------- S2 Permissions primer ----------

@Composable
fun PrimerScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val trip by vm.trip.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var notifOk by remember { mutableStateOf(notificationsAllowed(context)) }
    var exactOk by remember { mutableStateOf(BriefingScheduler.canScheduleExact(context)) }
    LifecycleResumeEffect(Unit) {
        notifOk = notificationsAllowed(context)
        exactOk = BriefingScheduler.canScheduleExact(context)
        onPauseOrDispose { }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }
    val check = rememberOwnerCheck { ok -> if (ok) vm.setLock(true) }
    val wake = trip.trip?.wakeMinute?.hhmm() ?: "06:30"
    val needsExact = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val steps = buildList { add(0); if (needsExact) add(1); add(2); add(3) }
    val current = steps.getOrElse(step) { 3 }
    val next = { if (step < steps.lastIndex) step++ else vm.finishPrimer() }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Step ${step + 1} of ${steps.size}", style = FgType.section, color = fg.textSecondary)
        when (current) {
            0 -> PrimerCard(Icons.Rounded.Notifications, "Morning briefing", "Get a briefing each morning at $wake destination time with when to leave, what to carry and the cash you need.") {
                if (notifOk) Callout(Tone.SUCCESS, "Notifications are on")
                else BigAction("Allow notifications", null, Modifier.fillMaxWidth()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else notifOk = true
                }
            }
            1 -> PrimerCard(Icons.Rounded.Alarm, "On the minute", "Exact alarms let the briefing arrive on time, not 10 minutes late.") {
                if (exactOk) Callout(Tone.SUCCESS, "Exact alarms are allowed")
                else BigAction("Open alarm setting", null, Modifier.fillMaxWidth()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}")))
                    }
                }
            }
            2 -> PrimerCard(Icons.Rounded.Fingerprint, "App lock", "Lock tickets and bookings with your fingerprint, face or phone PIN. Emergency calls still work while locked.") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (canLock(context)) "Lock with fingerprint" else "Set a screen lock on the phone first", style = FgType.body, color = fg.text, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.lockEnabled, enabled = canLock(context),
                        onCheckedChange = { on -> if (on) check.run(onOk = { vm.setLock(true) }, onError = {}) else vm.setLock(false) },
                    )
                }
            }
            else -> {
                val t = trip
                PrimerCard(Icons.Rounded.CheckCircle, "Everything is on this phone", "You can go offline now.") {
                    KeyFacts(
                        listOf(
                            "Days" to "${t.days.size}",
                            "Plans" to "${t.scheduled.size}",
                            "Lists" to "${t.lists.size}",
                            "Files" to "${t.files.count { it.ready }} of ${t.files.size}",
                        ),
                    )
                }
            }
        }
        BigAction(if (current == 3) "Open my trip" else "Next", null, Modifier.fillMaxWidth()) { next() }
        if (current != 3) TextButton(onClick = { next() }, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
    }
}

private fun notificationsAllowed(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PrimerCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, content: @Composable () -> Unit) {
    FgCard {
        Icon(icon, null, tint = fg.primary, modifier = Modifier.size(36.dp))
        Text(title, style = FgType.hero, color = fg.text)
        Text(body, style = FgType.body, color = fg.textSecondary)
        Spacer(Modifier.height(4.dp))
        content()
    }
}
