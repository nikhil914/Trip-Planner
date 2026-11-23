package app.tripplanner.fieldguide.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import app.tripplanner.fieldguide.logic.BlockKind
import app.tripplanner.fieldguide.logic.Health
import app.tripplanner.fieldguide.logic.amount
import app.tripplanner.fieldguide.logic.calmCaps
import app.tripplanner.fieldguide.logic.health
import app.tripplanner.fieldguide.nav.openInGoogleMaps

data class QuickAction(val label: String, val icon: ImageVector, val run: () -> Unit)

fun ticketTitles(files: List<FileUi>): List<String> = files.map { it.fileName }

fun openTicket(nav: Nav, b: BlockUi, start: Int = 0) {
    nav.modal = Modal.Ticket(b.files, ticketTitles(b.files), start)
}

/** Hands one file to a PDF or image app, read-only, for features the built-in viewer lacks (search, long files). */
fun openExternally(context: Context, file: FileUi) {
    if (!file.ready) {
        Toast.makeText(context, "This file isn't on the phone yet", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.files", file.path, file.fileName)
    } catch (_: IllegalArgumentException) {
        Toast.makeText(context, "This file can't be shared", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, file.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, if (file.mimeType == "application/pdf") "No PDF app on this phone" else "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

fun navigateTo(context: Context, b: BlockUi) {
    val lat = b.e.lat ?: return
    val lng = b.e.lng ?: return
    openInGoogleMaps(context, lat, lng, b.e.googlePlaceId, b.plan.displayTitle)
}

private val phoneTail = Regex("""\.?\s*Phone\b.*$""", RegexOption.IGNORE_CASE)

/** The first line of a hotel's notes, when it reads like a street address. */
fun hotelAddress(b: BlockUi): String? {
    if (b.plan.kind != BlockKind.CHECK_IN && b.plan.kind != BlockKind.CHECK_OUT) return null
    val first = b.e.subtitleOrTip?.lines()?.firstOrNull()?.trim().orEmpty()
    val address = first.replace(phoneTail, "").trim().trimEnd('.')
    return address.takeIf { it.any(Char::isDigit) && it.length in 8..120 }
}

/** Cards for "Show to local": the place in Georgian script, then the address if there is one. */
fun localCards(b: BlockUi): List<LocalCard> {
    val out = mutableListOf<LocalCard>()
    val name = b.plan.displayTitle.removePrefix("Check in:").removePrefix("Check out:").trim()
    if (b.e.localScriptName != null) out += LocalCard(b.e.localScriptName, b.e.phoneticPronunciation, name)
    hotelAddress(b)?.let { out += LocalCard(null, null, it, "Please take me to this address") }
    b.items.filter { it.localScriptText != null }.forEach { out += LocalCard(it.localScriptText, it.phoneticPronunciation, it.title, it.shopOrVenueName) }
    if (out.isEmpty()) out += LocalCard(null, null, name, "No Georgian name saved")
    return out
}

/** One quick action per card: ticket first, then the local name, then directions. */
fun quickAction(context: Context, nav: Nav, b: BlockUi): QuickAction? = when {
    b.files.isNotEmpty() -> QuickAction(if (b.plan.kind == BlockKind.FLIGHT) "Boarding pass" else "Ticket", Icons.Rounded.ConfirmationNumber) { openTicket(nav, b) }
    b.e.localScriptName != null -> QuickAction("Show local", Icons.Rounded.Translate) { nav.modal = Modal.ShowLocal(localCards(b)) }
    b.plan.kind == BlockKind.CHECK_IN && !b.e.bookingRef.isNullOrBlank() -> QuickAction("Booking card", Icons.Rounded.ConfirmationNumber) { nav.modal = Modal.Counter(b.id) }
    b.hasPin -> QuickAction("Navigate", Icons.Rounded.Directions) { navigateTo(context, b) }
    else -> null
}

fun copyText(context: Context, label: String, text: String) {
    val clip = context.getSystemService(ClipboardManager::class.java) ?: return
    clip.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

fun dial(context: Context, number: String) {
    val digits = number.filter { it.isDigit() || it == '+' }
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No phone app", Toast.LENGTH_SHORT).show()
    }
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser", Toast.LENGTH_SHORT).show()
    }
}

/** The one most useful fact for a collapsed card. */
fun keyFact(b: BlockUi, localCurrency: String): String? {
    b.plan.flags.incomplete?.let { return it }
    b.e.bookingRef?.takeIf { it.isNotBlank() }?.let { return calmCaps(it) }
    b.e.estimatedCostLocal?.let { if (it > 0) return "~${amount(it)} $localCurrency" }
    return b.plan.parsed.steps.firstOrNull()?.let { "${it.time} ${it.text}" } ?: b.plan.parsed.info.firstOrNull()
}

fun isProblem(b: BlockUi): Boolean = health(b.plan) == Health.NOT_BOOKED || health(b.plan) == Health.INCOMPLETE
