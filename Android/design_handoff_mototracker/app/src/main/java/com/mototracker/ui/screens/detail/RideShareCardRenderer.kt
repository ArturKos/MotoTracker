package com.mototracker.ui.screens.detail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.mototracker.R
import com.mototracker.core.format.RouteThumbnail
import com.mototracker.domain.share.CardPolylineScaler
import com.mototracker.domain.share.RideShareCard
import java.io.File

/**
 * Renders a [RideShareCard] into a PNG file stored in the app's cache directory.
 *
 * The resulting file is suitable for sharing via [android.content.Intent.ACTION_SEND]
 * through a [androidx.core.content.FileProvider].
 *
 * On-device rendering is marked 🔬 — the pure layout math is extracted into
 * [CardPolylineScaler] so it remains unit-testable without an Android runtime.
 */
object RideShareCardRenderer {

    private const val CARD_W = 900
    private const val CARD_H = 500

    // Track polyline target rectangle (right-side panel)
    private const val TRACK_LEFT = 480f
    private const val TRACK_TOP = 60f
    private const val TRACK_RIGHT = 860f
    private const val TRACK_BOTTOM = 440f

    // Brand colours (cockpit theme panel)
    private const val COLOR_PANEL = 0xFF12121C.toInt()
    private const val COLOR_ACCENT = 0xFFE8B84B.toInt()
    private const val COLOR_TEXT = 0xFFECECEC.toInt()
    private const val COLOR_DIM = 0xFF6B6B80.toInt()
    private const val COLOR_TRACK = 0xFFE8B84B.toInt()

    /**
     * Draws a share card bitmap for [card] and compresses it as a PNG to
     * `<cacheDir>/share-cards/route-<routeId>.png`.
     *
     * This function performs disk I/O and must be called on [kotlinx.coroutines.Dispatchers.IO].
     *
     * @param context  Android context used to resolve the cache directory.
     * @param card     Pre-built card data; all display strings are already formatted.
     * @param routeId  Route UUID used as the filename component, e.g. `"abc12345"`.
     * @return         The written [File] ready for FileProvider URI generation.
     */
    fun render(context: Context, card: RideShareCard, routeId: String): File {
        val dir = File(context.cacheDir, "share-cards")
        dir.mkdirs()
        val file = File(dir, "route-$routeId.png")

        val bitmap = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas)
        drawLeftPanel(canvas, card, context)
        drawTrack(canvas, card.thumbnailPathD)
        drawBranding(canvas)

        file.outputStream().buffered().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        bitmap.recycle()
        return file
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(COLOR_PANEL)
        // Subtle separator between text panel and track panel
        val sep = Paint().apply {
            color = COLOR_DIM
            alpha = 60
            strokeWidth = 1f
        }
        canvas.drawLine(TRACK_LEFT - 20f, 40f, TRACK_LEFT - 20f, CARD_H - 40f, sep)
    }

    private fun drawLeftPanel(canvas: Canvas, card: RideShareCard, context: Context) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_DIM
            textSize = 22f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ACCENT
            textSize = 22f
        }

        // Accent bar
        val barPaint = Paint().apply { color = COLOR_ACCENT }
        canvas.drawRect(40f, 50f, 48f, 130f, barPaint)

        // Route title
        canvas.drawText(card.title, 60f, 100f, titlePaint)

        // Date
        canvas.drawText(card.dateDisplay, 60f, 140f, labelPaint)

        // Bike name
        canvas.drawText(card.bikeName, 60f, 175f, accentPaint)

        // Stat rows
        drawStat(canvas, 60f, 250f, context.getString(R.string.share_card_stat_distance), card.distanceDisplay, labelPaint, valuePaint)
        drawStat(canvas, 60f, 330f, context.getString(R.string.share_card_stat_duration), card.durationDisplay, labelPaint, valuePaint)
        drawStat(canvas, 60f, 410f, context.getString(R.string.share_card_stat_top_speed), card.maxSpeedDisplay, labelPaint, valuePaint)
    }

    private fun drawStat(
        canvas: Canvas,
        x: Float,
        y: Float,
        label: String,
        value: String,
        labelPaint: Paint,
        valuePaint: Paint,
    ) {
        canvas.drawText(label.uppercase(), x, y, labelPaint)
        canvas.drawText(value, x, y + 40f, valuePaint)
    }

    private fun drawTrack(canvas: Canvas, thumbnailPathD: String) {
        val raw = RouteThumbnail.parsePathD(thumbnailPathD)
        if (raw.size < 2) return

        val scaled = CardPolylineScaler.scale(
            raw,
            TRACK_LEFT,
            TRACK_TOP,
            TRACK_RIGHT - TRACK_LEFT,
            TRACK_BOTTOM - TRACK_TOP,
        )

        val path = Path()
        scaled.forEachIndexed { i, (x, y) ->
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TRACK
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, trackPaint)
    }

    private fun drawBranding(canvas: Canvas) {
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_DIM
            textSize = 20f
        }
        canvas.drawText("MotoTracker", 60f, CARD_H - 30f, brandPaint)
    }
}
