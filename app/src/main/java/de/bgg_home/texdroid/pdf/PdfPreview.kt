package de.bgg_home.texdroid.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Hält einen offenen [PdfRenderer] auf einer PDF-Datei und rendert Seiten on demand.
 *
 * Wichtig: [PdfRenderer] erlaubt immer nur EINE geöffnete Seite gleichzeitig und
 * ist nicht thread-safe → alle Zugriffe laufen synchronisiert über [lock].
 */
class PdfDocument(file: File) : Closeable {
    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val lock = Any()

    val pageCount: Int get() = renderer.pageCount

    /**
     * Rendert Seite [index] auf [targetWidthPx] Breite (Höhe seitenverhältnistreu).
     * Der weiße Hintergrund wird explizit gesetzt – PDF-Seiten sind sonst transparent.
     */
    fun renderPage(index: Int, targetWidthPx: Int): Bitmap = synchronized(lock) {
        renderer.openPage(index).use { page ->
            val width = targetWidthPx.coerceAtLeast(1)
            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    override fun close() = synchronized(lock) {
        renderer.close()
        pfd.close()
    }
}

/**
 * Zeigt ein PDF mehrseitig (eine Seite pro [LazyColumn]-Item) mit Pinch-to-Zoom
 * und Pan (Zwei-Finger). Einzelfinger-Wischen scrollt weiterhin die Seitenliste –
 * `transformable` reagiert nur auf Mehrfinger-Gesten, daher kein Gestenkonflikt.
 *
 * @param file        das anzuzeigende PDF.
 * @param reloadToken bei jedem erfolgreichen Compile erhöhen → Preview lädt neu,
 *                    die Scroll-Position bleibt (gemerkter [rememberLazyListState]).
 */
@Composable
fun PdfPreview(
    file: File,
    reloadToken: Int,
    modifier: Modifier = Modifier,
) {
    // Bei neuem File ODER neuem reloadToken das Dokument neu öffnen.
    val document = remember(file.absolutePath, reloadToken) {
        runCatching { PdfDocument(file) }.getOrNull()
    }
    DisposableEffect(document) {
        onDispose { document?.close() }
    }

    if (document == null || document.pageCount == 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Kein PDF geladen", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }
    // Scroll-Position über Reloads hinweg halten.
    val listState = rememberLazyListState()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(transformState),
        ) {
            items(document.pageCount) { index ->
                PdfPageItem(document = document, index = index, targetWidthPx = widthPx)
            }
        }
    }
}

@Composable
private fun PdfPageItem(document: PdfDocument, index: Int, targetWidthPx: Int) {
    // Seite asynchron auf dem IO-Dispatcher rendern; solange Platzhalter zeigen.
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        document,
        index,
        targetWidthPx,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { document.renderPage(index, targetWidthPx) }.getOrNull()
        }
    }

    val bmp = bitmap
    if (bmp == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.414f) // ~A4 als Platzhalter
                .background(androidx.compose.ui.graphics.Color.White),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "PDF-Seite ${index + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}
