package de.bgg_home.texslate.pdf

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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bgg_home.texslate.R
import de.bgg_home.texslate.synctex.PdfPoint
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
 * @param onTapPosition Tipp auf eine Seite, als [PdfPoint] in PDF-Punkten. Damit
 *                    findet die App über SyncTeX die zugehörige Quelltext-Zeile.
 */
@Composable
fun PdfPreview(
    file: File,
    reloadToken: Int,
    onTapPosition: (PdfPoint) -> Unit,
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
            Text(stringResource(R.string.pdf_none_loaded), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Scroll-Position über Reloads hinweg halten.
    val listState = rememberLazyListState()

    // clipToBounds: Die gezoomte Seite darf nicht über den Editor nebenan laufen.
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            // Skaliert wird um die Mitte: Je Seite steht also (scale - 1) / 2 der
            // Fläche über. Weiter darf man nicht schieben, sonst verschwindet die
            // Seite aus dem Fenster und lässt sich nicht mehr zurückholen.
            val maxX = (scale - 1f) * widthPx / 2f
            val maxY = (scale - 1f) * heightPx / 2f
            val moved = offset + panChange
            offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                // transformable VOR graphicsLayer: So misst die Geste in den
                // unverzerrten Koordinaten des Fensters. Andersherum verschiebt
                // jeder Zoomschritt das Koordinatensystem unter den Fingern.
                .transformable(transformState)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
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
    onTapPosition: (PdfPoint) -> Unit,
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
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f || h <= 0f) return@detectTapGestures
                        onTapPosition(
                            PdfPoint(
                                page = index + 1, // SyncTeX zählt Seiten ab 1
                                x = offset.x / w * page.widthPoints,
                                y = offset.y / h * page.heightPoints,
                            ),
                        )
                    }
                },
        )
    }
}
