package app.tripplanner.fieldguide.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.tripplanner.fieldguide.FieldGuideApp
import app.tripplanner.fieldguide.data.AttachmentEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Flashcard(val localText: String, val phonetic: String?, val english: String)

/** Full brightness and screen on while someone else is reading the phone. Ends with the dialog window. */
@Composable
private fun HandOverMode() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { }
    }
}

/** The name in local script, large enough to read at arm's length. Works with no connection. */
@Composable
fun FlashcardDialog(card: Flashcard, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HandOverMode()
        Column(
            Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss).padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                card.localText,
                color = Color.White,
                fontSize = 56.sp,
                lineHeight = 70.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
            )
            card.phonetic?.let {
                Spacer(Modifier.height(28.dp))
                Text(it, color = Amber, fontSize = 28.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            Text(card.english, color = Dim, fontSize = 18.sp, textAlign = TextAlign.Center)
        }
    }
}

private const val MAX_PAGES = 8

private suspend fun renderTicket(file: File, mimeType: String, widthPx: Int): List<Bitmap> = withContext(Dispatchers.IO) {
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

/** Boarding passes and vouchers from app storage, drawn with the platform PDF renderer. */
@Composable
fun TicketDialog(attachment: AttachmentEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file = (context.applicationContext as FieldGuideApp).graph.vault.file(attachment.id)
    val width = context.resources.displayMetrics.widthPixels.coerceAtMost(1440)
    val pages by produceState<List<Bitmap>?>(initialValue = null, attachment.id) {
        value = renderTicket(file, attachment.mimeType, width)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HandOverMode()
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            val loaded = pages
            when {
                loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Opening…", color = Dim) }
                loaded.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "${attachment.fileName} is not on this phone yet. Pull to refresh while online to download it.",
                        color = Dim,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize().background(Color.White), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(loaded) { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = attachment.fileName,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
        }
    }
}
