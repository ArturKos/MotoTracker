package com.mototracker.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mototracker.ui.theme.MotoTracker
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Displays a GPS track on an OpenStreetMap tile map using the osmdroid library.
 *
 * The tile source is MAPNIK (standard OSM). Multi-touch zoom is enabled; built-in
 * zoom buttons are hidden. Map lifecycle (resume/pause/detach) is tied to the
 * hosting [androidx.lifecycle.LifecycleOwner] via [DisposableEffect].
 *
 * Actual tile rendering and polyline drawing are on-device-only concerns (🔬).
 *
 * @param points              Ordered GPS coordinates forming the track.
 * @param modifier            Standard Compose modifier.
 * @param showStartEndMarkers When `true`, places default markers at the start and end points.
 *                            Ignored when [followLatest] is `true`.
 * @param followLatest        When `true`, places a position marker at the last point and
 *                            centres the camera there at zoom 15. When `false`, the camera
 *                            is fitted to [TrackGeometry.bounds] of all points.
 */
@Composable
fun OsmTrackMap(
    points: List<GeoCoord>,
    modifier: Modifier = Modifier,
    showStartEndMarkers: Boolean = false,
    followLatest: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accentArgb = MotoTracker.colors.accent.toArgb()

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            controller.setZoom(14.0)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { mv ->
            mv.overlays.clear()

            if (points.isEmpty()) {
                mv.invalidate()
                return@AndroidView
            }

            val geoPoints = points.map { GeoPoint(it.lat, it.lon) }

            val polyline = Polyline(mv).apply {
                setPoints(geoPoints)
                outlinePaint.color = accentArgb
                outlinePaint.strokeWidth = 8f
            }
            mv.overlays.add(polyline)

            // Overlays (markers) can be added immediately; camera positioning must wait until the
            // MapView has real dimensions (see applyCamera below).
            if (followLatest) {
                mv.overlays.add(
                    Marker(mv).apply {
                        position = geoPoints.last()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    },
                )
            } else if (showStartEndMarkers) {
                mv.overlays.add(
                    Marker(mv).apply {
                        position = geoPoints.first()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    },
                )
                if (geoPoints.size > 1) {
                    mv.overlays.add(
                        Marker(mv).apply {
                            position = geoPoints.last()
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        },
                    )
                }
            }

            // osmdroid's zoomToBoundingBox / zoom math needs the view's width & height. On the first
            // AndroidView update pass the MapView has not been laid out yet (width == height == 0), so
            // the call is a no-op and the map stays at the world-level default zoom — leaving the track
            // a sub-pixel dot and only low-zoom world tiles visible. Defer camera positioning until the
            // view has real dimensions (immediately if already laid out, else on first layout).
            val applyCamera: () -> Unit = {
                when {
                    followLatest -> {
                        mv.controller.setCenter(geoPoints.last())
                        mv.controller.setZoom(15.0)
                    }
                    geoPoints.size > 1 -> {
                        val b = TrackGeometry.bounds(points)
                        if (b != null) {
                            mv.zoomToBoundingBox(
                                BoundingBox(b.north, b.east, b.south, b.west),
                                false,
                            )
                        }
                    }
                    else -> {
                        mv.controller.setCenter(geoPoints.first())
                        mv.controller.setZoom(15.0)
                    }
                }
                mv.invalidate()
            }

            if (mv.width > 0 && mv.height > 0) {
                applyCamera()
            } else {
                mv.addOnFirstLayoutListener { _, _, _, _, _ -> applyCamera() }
            }
        },
    )
}
