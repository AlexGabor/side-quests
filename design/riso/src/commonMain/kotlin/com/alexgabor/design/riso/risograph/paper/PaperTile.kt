package com.alexgabor.design.riso.risograph.paper

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The paper's surface, as the two textures it is baked into.
 *
 * A sheet's surface is three raw noise fields — roughness, fiber and the large-scale fade — and a
 * handful of strengths that mix them. Only the fields are baked. How strongly each one shows, and
 * what color the stock is, are applied per frame (see [SHEET_SURFACE_SKSL]), so none of them ever
 * costs a bake.
 *
 * The fields are periodic, so each is baked once as a tile and repeated across the sheet at whatever
 * size it is laid out at. The fine tile carries the detail, the coarse one the fade that modulates
 * it, and their periods do not divide one another: the detail repeats every [FINE_PERIOD_DP], but
 * under a different part of the fade each time, so the page as a whole repeats only at their least
 * common multiple.
 */

/** Period of the fine tile — roughness and fiber — in dp. */
internal const val FINE_PERIOD_DP = 256

/** Period of the coarse tile — the fade — in dp. Chosen not to share a small factor with [FINE_PERIOD_DP]. */
internal const val COARSE_PERIOD_DP = 1400

/** The fade is smooth, so the coarse tile stores one pixel per this many dp, whatever the density. */
internal const val COARSE_DP_PER_PIXEL = 4

/**
 * The height, in dp, that the ported shader's pattern was sized against.
 *
 * paper.design sizes its pattern relative to the layer — `5 / scale` units across its height — which
 * stretches the grain with the window. Anchored in dp instead, the pattern keeps the size it had on a
 * sheet this tall, whatever size it is laid out at.
 */
internal const val PATTERN_REFERENCE_DP = 800f

/** Roughness lattice cells per dp: the ported shader's `1.5 / density` scale times its `0.1`. */
internal const val ROUGH_CELLS_PER_DP = 0.15f

/**
 * Bumped whenever anything that decides a baked tile's pixels changes — a shader, an encoding, a
 * period. Cached tiles are filed under it, so a stale one is never read back.
 */
internal const val SURFACE_VERSION = 1

/** The density a tile is baked at: the next whole density up, so a handful of tiles cover every screen. */
internal fun densityBucket(density: Float): Int = ceil(density - 1e-3f).toInt().coerceIn(1, 4)

/** One baked tile's identity. Two sheets whose surfaces round to the same lattice share one tile. */
internal sealed interface TileKey {
    /** Width and height of the tile, in pixels. Tiles are square. */
    val pixels: Int

    /** Where the tile is filed, on disk and among the shipped resources. */
    val fileName: String
}

/**
 * The fine tile: roughness in `r`, fiber in `g`.
 *
 * [roughCells] and [fiberCells] are how many lattice cells of each field fit in one period — whole
 * numbers, since that is what makes the field repeat. Rounding them nudges the fiber's scale by less
 * than half a cell per period, which is invisible.
 */
internal data class FineTileKey(val roughCells: Int, val fiberCells: Int, val bucket: Int) : TileKey {
    override val pixels: Int get() = FINE_PERIOD_DP * bucket
    override val fileName: String get() = "fine-r$roughCells-f$fiberCells@${bucket}x.png"
}

/** The coarse tile: the fade's raw noise in `r`. [seedOffset] is where in the noise the fade starts. */
internal data class CoarseTileKey(val fadeCells: Int, val seedOffset: Float) : TileKey {
    override val pixels: Int get() = COARSE_PERIOD_DP / COARSE_DP_PER_PIXEL
    override val fileName: String get() = "coarse-f$fadeCells-s${seedOffset.toBits().toUInt().toString(16)}.png"
}

/** Pattern units per dp, for a stock at [scale]: the ported shader's `5 / scale` across the reference height. */
private fun patternUnitsPerDp(scale: Float): Float =
    5f / (scale.coerceIn(0.01f, 4f) * PATTERN_REFERENCE_DP)

internal fun RisoPaper.fineTileKey(density: Float): FineTileKey {
    val fiberCellsPerDp = 2f / fiberSize.coerceAtLeast(0.01f) * patternUnitsPerDp(scale)
    return FineTileKey(
        roughCells = cellsPerPeriod(ROUGH_CELLS_PER_DP, FINE_PERIOD_DP),
        fiberCells = cellsPerPeriod(fiberCellsPerDp, FINE_PERIOD_DP),
        bucket = densityBucket(density),
    )
}

internal fun RisoPaper.coarseTileKey(): CoarseTileKey = CoarseTileKey(
    fadeCells = cellsPerPeriod(0.17f * patternUnitsPerDp(scale), COARSE_PERIOD_DP),
    seedOffset = 10f * seed,
)

private fun cellsPerPeriod(cellsPerDp: Float, periodDp: Int): Int =
    (cellsPerDp * periodDp).roundToInt().coerceAtLeast(1)
