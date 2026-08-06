package com.alexgabor.design.riso.paper

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Applies a static, procedural paper/cardboard texture on top of whatever the composable draws,
 * ported to AGSL from the paper.design `PaperTexture` WebGL shader
 * (https://shaders.paper.design/paper-texture).
 *
 * The composable's own rendered output is fed into the shader as its image input, so paper folds,
 * crumples, fiber and roughness subtly distort and shade the content, like a paper print.
 */
expect fun Modifier.paperTexture(params: PaperTextureParams = PaperTextureParams()): Modifier

/**
 * Parameters for [com.alexgabor.design.riso.paper.paperTexture], mirroring the props of the paper.design `PaperTexture`
 * WebGL shader (https://shaders.paper.design/paper-texture).
 */
data class PaperTextureParams(
    val colorFront: Color = Color(0xFFFBF9F3),
    val colorBack: Color = Color(0xFFFBF9F3),
    /** Sharper vs smoother color transitions (0..1). */
    val contrast: Float = 0.12f,
    /** Pixel noise intensity (0..1). */
    val roughness: Float = 0.12f,
    /** Curly-shaped fiber noise intensity (0..1). */
    val fiber: Float = 0.1f,
    /** Curly-shaped fiber noise scale (0..1). */
    val fiberSize: Float = 0.29f,
    /** Cell-based crumple pattern intensity (0..1). */
    val crumples: Float = 0f,
    /** Cell-based crumple pattern scale (0..1). */
    val crumpleSize: Float = 0f,
    /** Depth of the folds (0..1). */
    val folds: Float = 0f,
    /** Number of folds (1..15). */
    val foldCount: Float = 0f,
    /** Visibility of the speckle/drops pattern (0..1). */
    val drops: Float = 0f,
    /** Big-scale noise mask applied to the pattern (0..1). */
    val fade: Float = 0.5f,
    /** Seed applied to folds, crumples and dots. */
    val seed: Float = 5.8f,
    /** Overall zoom level of the texture (0.01..4). */
    val scale: Float = 0.1f,
)
