package com.alexgabor.pacer

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.layout.WindowHeightSizeClass
import com.alexgabor.design.riso.layout.WindowSizeClass
import com.alexgabor.design.riso.layout.WindowWidthSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowSizeClassTest {

    @Test
    fun `width buckets at their boundaries`() {
        assertEquals(WindowWidthSizeClass.Compact, widthClassOf(0.dp))
        assertEquals(WindowWidthSizeClass.Compact, widthClassOf(599.dp))
        assertEquals(WindowWidthSizeClass.Medium, widthClassOf(600.dp))
        assertEquals(WindowWidthSizeClass.Medium, widthClassOf(839.dp))
        assertEquals(WindowWidthSizeClass.Expanded, widthClassOf(840.dp))
    }

    @Test
    fun `height buckets at their boundaries`() {
        assertEquals(WindowHeightSizeClass.Compact, heightClassOf(0.dp))
        assertEquals(WindowHeightSizeClass.Compact, heightClassOf(479.dp))
        assertEquals(WindowHeightSizeClass.Medium, heightClassOf(480.dp))
        assertEquals(WindowHeightSizeClass.Medium, heightClassOf(899.dp))
        assertEquals(WindowHeightSizeClass.Expanded, heightClassOf(900.dp))
    }

    @Test
    fun `unbounded constraints fall back to expanded`() {
        val sizeClass = WindowSizeClass.compute(Dp.Infinity, Dp.Infinity)

        assertEquals(WindowWidthSizeClass.Expanded, sizeClass.width)
        assertEquals(WindowHeightSizeClass.Expanded, sizeClass.height)
    }

    /** The windows the two layouts are actually chosen by. */
    @Test
    fun `known windows land in the expected buckets`() {
        assertEquals(
            WindowSizeClass(WindowWidthSizeClass.Compact, WindowHeightSizeClass.Medium),
            WindowSizeClass.compute(411.dp, 891.dp),
        )
        assertEquals(
            WindowSizeClass(WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Compact),
            WindowSizeClass.compute(891.dp, 411.dp),
        )
        assertEquals(
            WindowSizeClass(WindowWidthSizeClass.Medium, WindowHeightSizeClass.Compact),
            WindowSizeClass.compute(640.dp, 360.dp),
        )
        assertEquals(
            WindowSizeClass(WindowWidthSizeClass.Expanded, WindowHeightSizeClass.Expanded),
            WindowSizeClass.compute(1920.dp, 1080.dp),
        )
    }

    private fun widthClassOf(width: Dp) = WindowSizeClass.compute(width, 891.dp).width

    private fun heightClassOf(height: Dp) = WindowSizeClass.compute(411.dp, height).height
}
