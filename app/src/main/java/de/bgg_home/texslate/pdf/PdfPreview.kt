// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.bgg_home.texslate.R
import de.bgg_home.texslate.synctex.PdfPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.Job
import kotlin.math.sqrt

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

    //get all the pages' aspect ratios as to size the lazyColumn's place-holder items correctly
    data class PageSize(
        val widthPoints: Int,
        val heightPoints: Int,
    ) {
        val aspectRatio: Float
            get() = widthPoints.toFloat() / heightPoints
    }
    private val pageSizes = arrayOfNulls<PageSize>(renderer.pageCount)
    fun loadPageSizes(): List<PageSize> = synchronized(lock) {
        for (index in pageSizes.indices) {
            if (pageSizes[index] == null) {
                renderer.openPage(index).use { page ->
                    pageSizes[index] = PageSize(
                        widthPoints = page.width,
                        heightPoints = page.height,
                    )
                }
            }
        }

        pageSizes.map { requireNotNull(it) }
    }

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
            Text(
                stringResource(R.string.pdf_none_loaded),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Scroll-Position über Reloads hinweg halten.
    val listState = rememberLazyListState()

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val currentContainerSize by rememberUpdatedState(containerSize)

    val coroutineScope = rememberCoroutineScope()

    val density = LocalDensity.current
    val decay = splineBasedDecay<Float>(density)
    val flingAll = remember { Animatable(0f) }
    var flingJob by remember { mutableStateOf<Job?>(null) }

    // clipToBounds: Die gezoomte Seite darf nicht über den Editor nebenan laufen.
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size -> containerSize = size }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val velocityTracker = VelocityTracker()
                    var wasZooming = false
                    var wasDragging = false
                    var completedNormally = false
                    var previousPointerCount = 0

                    try {
                        awaitFirstDown(requireUnconsumed = false)

                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.any { it.pressed }
                            val pointerCount = event.changes.count { it.pressed }

                            // Avoid treating a change in finger count as a fling velocity.
                            if (pointerCount != previousPointerCount && pressed) {
                                velocityTracker.resetTracking()
                                previousPointerCount = pointerCount
                                flingJob?.cancel()
                                //Log.d("PDF_FLING", "cancelled!")

                            }

                            if (pressed) {

                                val centroid = event.calculateCentroid(
                                    useCurrent = true
                                )

                                //Used to calculate fling velocity once the pan finishes
                                velocityTracker.addPosition(
                                    event.changes.first().uptimeMillis,
                                    centroid
                                )

                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()

                                if (pointerCount >= 2 && zoom != 1f) {
                                    wasZooming = true
                                }

                                if (pan.getDistance() > 3f) {
                                    wasDragging = true
                                }

                                val oldScale = scale
                                val newScale = (scale * zoom).coerceIn(1f, 5f)

                                val widthPx = currentContainerSize.width.toFloat()
                                val oldOffset = offset
                                offset =
                                    (offset + centroid / oldScale) - (centroid / newScale + pan / oldScale)
                                val maxX =
                                    (widthPx - widthPx / newScale).coerceAtLeast(0f)

                                offset = Offset(offset.x.coerceIn(0f, maxX), offset.y)
                                scale = newScale

                                val drag = oldOffset - offset
                                coroutineScope.launch {
                                    listState.scrollBy(-drag.y)
                                }
                                if (wasZooming || wasDragging) { //avoid the event to be considered as a tap in the image's pointerInput modifier
                                    event.changes.forEach {it.consume()}
                                }
                            }
                        } while (pressed)

                        completedNormally = true
                    } finally {
                        if (completedNormally && wasDragging && !wasZooming) {
                            val velocity = velocityTracker.calculateVelocity()
                            val speed = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
                            val minFlingVelocity = 100f
                            if (speed > minFlingVelocity) { //avoid dividing by 0 if speed is close to 0
                                flingJob?.cancel()
                                flingJob = coroutineScope.launch {
                                    launch {
                                        var previous = 0f
                                        flingAll.snapTo(0f)
                                        flingAll.animateDecay(
                                            initialVelocity = speed,
                                            animationSpec = decay
                                        ) {
                                            val delta =  value - previous
                                            previous = value

                                            val currentScale = scale
                                            val widthPx = currentContainerSize.width.toFloat()
                                            val maxX = (widthPx - widthPx / scale).coerceAtLeast(0f)

                                            val deltaX = delta * (velocity.x / speed)
                                            val deltaY = delta * (velocity.y / speed)
                                            //Log.d("PDF_FLING", "speed: $speed, delta: $delta, velocityX: "+velocity.x.toString()+"currentScale: $currentScale")
                                            offset = offset.copy((offset.x - deltaX / currentScale).coerceIn(0f, maxX), offset.y)
                                            coroutineScope.launch {
                                                listState.scrollBy(-deltaY / scale)
                                            }

                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = -offset.x * scale
                transformOrigin = TransformOrigin(0f, 0f)
            }) {
        val widthPx = currentContainerSize.width

        val density = LocalDensity.current

        val pageSizes by produceState<List<PdfDocument.PageSize>?>(
            initialValue = null,
            document,
        ) {
            value = withContext(Dispatchers.IO) {
                document.loadPageSizes()
            }
        }

        if (pageSizes == null) {
            Box(modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false, //scrollBy is used earlier as to distribute the pan motion on the x and y-axis
                modifier = Modifier
                    .fillMaxSize()
            ) {
                items(
                    count = document.pageCount,
                    key = { index -> "page$index" }
                ) { index ->
                    PdfPageItem(
                        document = document,
                        index = index,
                        targetWidthPx = widthPx - with(density) {16.dp.toPx()}.toInt(), //changed to exactly match the showing pixels
                        onTapPosition = onTapPosition,
                        pageAspectRatio = pageSizes!![index].aspectRatio,
                    )
                }
                item{
                    //The zoom is made at the top of the showing screen. As a result the column has
                    //to scroll further when zoomed so the end of the column can reach en end of the
                    //zoomed rectangle
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) {
                                (containerSize.height - (containerSize.height / scale)).toInt().coerceAtLeast(0).toDp()
                            })
                    )

                }
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
    pageAspectRatio: Float,
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

    val pageModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp)
        .aspectRatio(pageAspectRatio)
    if (page == null) {
        Box(
            modifier = pageModifier.background(androidx.compose.ui.graphics.Color.White),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        Image(
            bitmap = page.bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.pdf_page, index + 1),
            contentScale = ContentScale.FillWidth,
            modifier = pageModifier
                //.padding(horizontal = 4.dp)
                // Ganz am Ende der Kette: So bekommt der Tipp Koordinaten
                // relativ zum Bild selbst – Zoom und Scroll der Liste rechnet
                // Compose bereits heraus, das Bild füllt die Breite exakt aus.
                .pointerInput(onTapPosition, page) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation()

                        if (up != null) {
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            if (w > 0f && h > 0f) {
                                onTapPosition(
                                    PdfPoint(
                                        page = index + 1, // SyncTeX zählt Seiten ab 1
                                        x = up.position.x / w * page.widthPoints,
                                        y = up.position.y / h * page.heightPoints,
                                    ),
                                )
                            }
                        }
                    }
                },
            //filterQuality = FilterQuality.None //uncomment to remove blurry effect of subpixel interpolation
        )
    }
}