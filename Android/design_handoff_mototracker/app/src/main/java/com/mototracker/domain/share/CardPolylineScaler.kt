package com.mototracker.domain.share

/**
 * Pure, Android-free layout math for scaling route track points from the
 * [com.mototracker.core.format.RouteThumbnail] 320×200 viewBox into an arbitrary
 * target rectangle on the share card canvas.
 *
 * Kept separate from [RideShareCardRenderer] so this function is unit-testable
 * without any Android framework dependencies.
 */
object CardPolylineScaler {

    private const val SOURCE_W = 320f
    private const val SOURCE_H = 200f

    /**
     * Scales (x, y) pixel pairs from the 320×200 viewBox coordinate space into
     * the card's track rectangle.
     *
     * @param points       Source points in the 320×200 viewBox, as produced by
     *                     [com.mototracker.core.format.RouteThumbnail.parsePathD].
     * @param targetLeft   Left edge of the destination rectangle in canvas pixels.
     * @param targetTop    Top edge of the destination rectangle in canvas pixels.
     * @param targetWidth  Width of the destination rectangle in canvas pixels.
     * @param targetHeight Height of the destination rectangle in canvas pixels.
     * @return Scaled (x, y) float pairs mapped into the target rectangle's coordinate
     *         space. Returns an empty list when [points] is empty.
     */
    fun scale(
        points: List<Pair<Float, Float>>,
        targetLeft: Float,
        targetTop: Float,
        targetWidth: Float,
        targetHeight: Float,
    ): List<Pair<Float, Float>> {
        if (points.isEmpty()) return emptyList()
        val scaleX = targetWidth / SOURCE_W
        val scaleY = targetHeight / SOURCE_H
        return points.map { (x, y) ->
            targetLeft + x * scaleX to targetTop + y * scaleY
        }
    }
}
