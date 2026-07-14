package com.mototracker.core.format

import kotlin.math.roundToInt

/**
 * Uniform-stride downsampler for GPS coordinate lists.
 *
 * Used to build the bounded [com.mototracker.data.local.entity.RouteEntity.thumbnailPathD]
 * string (≈ 120 points → tiny SVG path) without reading the full trace from chunks.
 *
 * All functions are pure (no side effects, no Android dependencies).
 */
object TraceDownsampler {

    /**
     * Reduces [points] to at most [maxPoints] elements using uniform stride sampling.
     *
     * Guarantees that the first and last elements of the original list are always included
     * (unless [points] is empty). When `points.size <= maxPoints` the list is returned unchanged.
     *
     * @param points    Ordered list of (latitude, longitude) pairs.
     * @param maxPoints Maximum number of output points; must be >= 2 for non-trivial output.
     * @return Downsampled list; never larger than [maxPoints].
     */
    fun downsample(points: List<Pair<Double, Double>>, maxPoints: Int): List<Pair<Double, Double>> {
        if (points.size <= maxPoints) return points
        if (maxPoints <= 0) return emptyList()
        if (maxPoints == 1) return listOf(points.first())

        val stride = (points.size - 1).toDouble() / (maxPoints - 1)
        val result = ArrayList<Pair<Double, Double>>(maxPoints)
        for (i in 0 until maxPoints) {
            val idx = (i * stride).roundToInt().coerceIn(0, points.size - 1)
            result.add(points[idx])
        }
        // Always guarantee first and last are exact originals.
        result[0] = points.first()
        result[result.size - 1] = points.last()
        return result
    }
}
