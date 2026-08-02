package de.bgg_home.texdroid.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bgg_home.texdroid.R
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
     * Eine gerenderte Seite samt ihrer Maße in PDF-Punkten.
     *
     * Die Punktmaße kommen mit, weil SyncTeX in genau dieser Einheit rechnet:
     * Ein Tipp auf das Bitmap lässt sich nur dann in eine PDF-Position umrechnen,
     * wenn man weiß, wie groß die Seite in Punkten ist (siehe [PdfPreview]).
     */
    class RenderedPage(val bitmap: Bitmap, val widthPoints: Int, val heightPoints: Int)

    /**
     * Rendert Seite [index] auf [targetWidthPx] Breite (Höhe seitenverhältnistreu).
     * Der weiße Hintergrund wird explizit gesetzt – PDF-Seiten sind sonst transparent.
     */
    fun renderPage(index: Int, targetWidthPx: Int): RenderedPage = synchronized(lock) {
        renderer.openPage(index).use { page ->
            val width = targetWidthPx.coerceAtLeast(1)
            val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            // getWidth()/getHeight() liefern die Seitenmaße in Punkten (1/72 Zoll).
            RenderedPage(bitmap, page.width, page.height)
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
 * @param onTapPosition Tipp auf eine Seite, als Position in PDF-Punkten (Ursprung
 *                    links oben, 1-basierte Seitennummer). Damit findet die App
 *                    über SyncTeX die zugehörige Quelltext-Zeile.
 */
@Composable
fun PdfPreview(
    file: File,
    reloadToken: Int,
    modifier: Modifier = Modifier,
    onTapPosition: ((page: Int, x: Float, y: Float) -> Unit)? = null,
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
            Text(stringResource(R.string.pdf_none_loaded), style = MaterialTheme.typography.bodyMedium)
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
                PdfPageItem(
                    document = document,
                    index = index,
                    targetWidthPx = widthPx,
                    onTapPosition = onTapPosition,
                )
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    document: PdfDocument,
    index: Int,
    targetWidthPx: Int,
    onTapPosition: ((page: Int, x: Float, y: Float) -> Unit)?,
) {
    // Seite asynchron auf dem IO-Dispatcher rendern; solange Platzhalter zeigen.
    val rendered by produceState<PdfDocument.RenderedPage?>(
        initialValue = null,
        document,
        index,
        targetWidthPx,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { document.renderPage(index, targetWidthPx) }.getOrNull()
        }
    }

    val page = rendered
    if (page == null) {
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
            bitmap = page.bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.pdf_page, index + 1),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                // Ganz am Ende der Kette: So bekommt der Tipp Koordinaten
                // relativ zum Bild selbst – Zoom und Scroll der Liste rechnet
                // Compose bereits heraus, das Bild füllt die Breite exakt aus.
                .pointerInput(onTapPosition, page) {
                    if (onTapPosition == null) return@pointerInput
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f || h <= 0f) return@detectTapGestures
                        onTapPosition(
                            index + 1, // SyncTeX zählt Seiten ab 1
                            offset.x / w * page.widthPoints,
                            offset.y / h * page.heightPoints,
                        )
                    }
                },
        )
    }
}
