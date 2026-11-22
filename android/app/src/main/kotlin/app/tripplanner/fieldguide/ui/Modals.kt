package app.tripplanner.fieldguide.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.tripplanner.fieldguide.FieldGuideApp
import app.tripplanner.fieldguide.logic.BlockKind
import app.tripplanner.fieldguide.logic.dayLabel
import app.tripplanner.fieldguide.logic.hhmm
import app.tripplanner.fieldguide.logic.money
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF000000)
private val Paper = Color(0xFFFFFFFF)
private val Grey = Color(0xFF3C4043)

/** Full brightness, screen kept on, no chrome. Ends with the dialog window. */
@Composable
private fun HandOverMode() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { }
    }
}

@Composable
private fun WhiteModal(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        HandOverMode()
        Box(Modifier.fillMaxSize().background(Paper).systemBarsPadding()) {
            content()
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Ink)
            }
        }
    }
}

@Composable
private fun PagerDots(count: Int, current: Int) {
    if (count < 2) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (i == current) Ink else Color(0xFFBDC1C6)))
        }
    }
}

// ---------- S7 Show to local ----------

@Composable
fun ShowLocalModal(m: Modal.ShowLocal, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = m.start.coerceIn(0, (m.cards.size - 1).coerceAtLeast(0))) { m.cards.size }
    WhiteModal(onDismiss) {
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                val card = m.cards[page]
                Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    val big = card.local ?: card.english
                    // Auto-fit: long names shrink, never below 32 sp; short ones grow to 72 sp.
                    val size = when {
                        big.length <= 10 -> 72
                        big.length <= 18 -> 56
                        big.length <= 30 -> 44
                        else -> 32
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        card.note?.let { Text(it, color = Grey, fontSize = 18.sp, textAlign = TextAlign.Center) }
                        Text(
                            big, color = Ink, fontSize = size.sp, lineHeight = (size * 1.25f).sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                        )
                        card.phonetic?.let { Text(it, color = Ink, fontSize = 22.sp, textAlign = TextAlign.Center) }
                        if (card.local != null) Text(card.english, color = Grey, fontSize = 16.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { PagerDots(m.cards.size, pager.currentPage) }
        }
    }
}

// ---------- S8 Ticket and document viewer ----------

private const val MAX_PAGES = 8

private suspend fun renderFile(file: File, mimeType: String, widthPx: Int): List<Bitmap> = withContext(Dispatchers.IO) {
    if (!file.exists()) return@withContext emptyList()
    runCatching {
        if (mimeType == "application/pdf") {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { pdf ->
                    (0 until minOf(pdf.pageCount, MAX_PAGES)).map { index ->
                        pdf.openPage(index).use { page ->
                            val height = (widthPx.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                            Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }
        } else {
            listOfNotNull(BitmapFactory.decodeFile(file.path))
        }
    }.getOrDefault(emptyList())
}

@Composable
fun TicketModal(m: Modal.Ticket, onDismiss: () -> Unit) {
    if (m.files.isEmpty()) {
        WhiteModal(onDismiss) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No ticket attached. Add one from the planner.", color = Ink, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        }
        return
    }
    val pager = rememberPagerState(initialPage = m.start.coerceIn(0, m.files.size - 1)) { m.files.size }
    WhiteModal(onDismiss) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 56.dp, top = 12.dp, bottom = 8.dp)) {
                val file = m.files[pager.currentPage]
                Text(file.attachment.fileName, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(if (m.files.size > 1) "File ${pager.currentPage + 1} of ${m.files.size} · swipe for the next" else "Swipe down the page to scroll", color = Grey, fontSize = 13.sp)
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page -> FilePages(m.files[page]) }
        }
    }
}

@Composable
private fun FilePages(file: FileUi) {
    val context = LocalContext.current
    val vault = (context.applicationContext as FieldGuideApp).graph.vault
    val width = context.resources.displayMetrics.widthPixels.coerceAtMost(1440)
    val pages by produceState<List<Bitmap>?>(initialValue = null, file.attachment.id) {
        value = renderFile(vault.file(file.attachment.id), file.attachment.mimeType, width)
    }
    // Double tap zooms; the zoomed page scrolls sideways natively, so vertical scrolling keeps working.
    var zoomed by remember { mutableStateOf(false) }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val loaded = pages
    when {
        loaded == null -> Box(Modifier.fillMaxSize())
        loaded.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                if (!file.ready) "This file isn't on the phone yet. Connect and pull down to refresh." else "This file can't be opened on the phone.",
                color = Ink, fontSize = 18.sp, textAlign = TextAlign.Center,
            )
        }
        else -> LazyColumn(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoomed = !zoomed }) },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(loaded) { i, bitmap ->
                Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = "${file.attachment.fileName}, page ${i + 1} of ${loaded.size}. Double tap to zoom.",
                        modifier = Modifier.width(if (zoomed) screenWidth * 2.5f else screenWidth),
                        contentScale = ContentScale.FillWidth,
                    )
                }
                if (loaded.size > 1) {
                    Text("Page ${i + 1} of ${loaded.size}", color = Grey, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ---------- S14 Booking card, counter mode ----------

@Composable
fun CounterModal(b: BlockUi, trip: TripUi, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val nav = LocalNav.current
    val facts = refFacts(b.e.bookingRef)
    val code = facts.firstOrNull { it.first.equals("PNR", true) || it.first.startsWith("Booking", true) || it.first.equals("Ref", true) }
    val rest = facts.filter { it != code }
    val t = trip.trip
    val stay = trip.stays.firstOrNull { it.checkIn.id == b.id || it.checkOut?.id == b.id }
    val showId = b.e.subtitleOrTip?.lines()?.firstOrNull { it.contains("show photo id", true) || it.contains("passport", true) && it.contains("carry", true) }

    WhiteModal(onDismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(24.dp))
            Text(b.plan.displayTitle.removePrefix("Check in:").removePrefix("Check out:").trim(), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            val whenLine = listOfNotNull(b.day?.let { dayLabel(it.dateIso) }, b.e.startMinute?.hhmm(), stay?.nights?.let { "$it ${if (it == 1) "night" else "nights"}" })
            Text(whenLine.joinToString(" · "), color = Grey, fontSize = 18.sp)
            if (code != null) {
                Column(Modifier.fillMaxWidth().border(2.dp, Ink, RoundedCornerShape(16.dp)).padding(20.dp)) {
                    Text(code.first, color = Grey, fontSize = 16.sp)
                    Text(code.second, color = Ink, fontSize = 40.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
            rest.forEach { (label, value) ->
                Column {
                    Text(label, color = Grey, fontSize = 15.sp)
                    Text(value, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (t != null && b.plan.flags.cashOnly) {
                b.e.estimatedCostLocal?.let { Text("Pay in cash: ${money(it, t.exchangeRate, t.localCurrency, t.homeCurrency)}", color = Ink, fontSize = 20.sp) }
            }
            showId?.let { Text(it.trim(), color = Ink, fontSize = 18.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (b.files.isNotEmpty()) {
                    OutlinedButton(onClick = { nav.modal = Modal.Ticket(b.files, ticketTitles(b.files)) }) {
                        Icon(Icons.Rounded.Description, null, tint = Ink); Spacer(Modifier.width(6.dp)); Text(if (b.plan.kind == BlockKind.TRAIN) "Show QR" else "Show file", color = Ink)
                    }
                }
                if (hotelAddress(b) != null || b.e.localScriptName != null) {
                    OutlinedButton(onClick = { nav.modal = Modal.ShowLocal(localCards(b)) }) {
                        Icon(Icons.Rounded.Translate, null, tint = Ink); Spacer(Modifier.width(6.dp)); Text("Address", color = Ink)
                    }
                }
                if (code != null) {
                    OutlinedButton(onClick = { copyText(context, code.first, code.second) }) {
                        Icon(Icons.Rounded.ContentCopy, null, tint = Ink); Spacer(Modifier.width(6.dp)); Text("Copy", color = Ink)
                    }
                }
            }
        }
    }
}
