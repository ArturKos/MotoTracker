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
import com.mototracker.domain.recording.TrackPoint
import com.mototracker.domain.recording.TrackSegmenter
import com.mototracker.domain.recording.TrackSmoother
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
 * GPS dropouts are handled via [TrackSegmenter]: the track is split into continuous
 * segments and one [Polyline] is drawn per segment, so no straight line appears across
 * a tunnel or signal-loss gap. Start/end markers are placed at the overall first and last
 * points of all segments combined.
 *
 * Actual tile rendering and polyline drawing are on-device-only concerns (🔬).
 *
 * @param points              Ordered GPS track points forming the route.
 * @param modifier            Standard Compose modifier.
 * @param showStartEndMarkers When `true`, places default markers at the overall start and end
 *                            points across all segments. Ignored when [followLatest] is `true`.
 * @param followLatest        When `true`, places a position marker at the last point and
 *                            centres the camera there at zoom 15. When `false`, the camera
 *                            is fitted to [TrackGeometry.bounds] of all points.
 */
@Composable
fun OsmTrackMap(
    points: List<TrackPoint>,
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

            val rendered = TrackSmoother.smooth(points)
            val allGeoPoints = rendered.map { GeoPoint(it.lat, it.lng) }

            // Draw one polyline per segment so GPS dropout gaps are not connected by a straight line.
            val segments = TrackSegmenter.split(rendered)
            for (segment in segments) {
                if (segment.isEmpty()) continue
                val polyline = Polyline(mv).apply {
                    setPoints(segment.map { GeoPoint(it.lat, it.lng) })
                    outlinePaint.color = accentArgb
                    outlinePaint.strokeWidth = 8f
                }
                mv.overlays.add(polyline)
            }

            // Overlays (markers) can be added immediately; camera positioning must wait until the
            // MapView has real dimensions (see applyCamera below).
            if (followLatest) {
                mv.overlays.add(
                    Marker(mv).apply {
                        position = allGeoPoints.last()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    },
                )
            } else if (showStartEndMarkers) {
                mv.overlays.add(
                    Marker(mv).apply {
                        position = allGeoPoints.first()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    },
                )
                if (allGeoPoints.size > 1) {
                    mv.overlays.add(
                        Marker(mv).apply {
                            position = allGeoPoints.last()
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
                        mv.controller.setCenter(allGeoPoints.last())
                        mv.controller.setZoom(15.0)
                    }
                    allGeoPoints.size > 1 -> {
                        val allGeoCoords = rendered.map { GeoCoord(it.lat, it.lng) }
                        val b = TrackGeometry.bounds(allGeoCoords)
                        if (b != null) {
                            mv.zoomToBoundingBox(
                                BoundingBox(b.north, b.east, b.south, b.west),
                                false,
                            )
                        }
                    }
                    else -> {
                        mv.controller.setCenter(allGeoPoints.first())
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
