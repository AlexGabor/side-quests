package com.alexgabor.design.riso.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SmallProgressTest {

    @Test
    fun progressIsClampedIntoRange() {
        assertEquals(0f, (-0.5f).clampProgress())
        assertEquals(0.4f, 0.4f.clampProgress())
        assertEquals(1f, 1.7f.clampProgress())
        assertEquals(0f, Float.NaN.clampProgress())
        assertEquals(1f, Float.POSITIVE_INFINITY.clampProgress())
    }

    @Test
    fun reportsItsValueAsAProgressBar() = assertSemanticsProgress(given = 0.25f, reported = 0.25f)

    @Test
    fun reportsAValueAboveOneAsComplete() = assertSemanticsProgress(given = 1.7f, reported = 1f)

    @Test
    fun reportsNaNAsNoProgress() = assertSemanticsProgress(given = Float.NaN, reported = 0f)

    private fun assertSemanticsProgress(given: Float, reported: Float) = runComposeUiTest {
        setContent {
            RisoTheme(effectsEnabled = false) {
                SmallProgress(progress = given, modifier = Modifier.width(100.dp).testTag("progress"))
            }
        }
        onNodeWithTag("progress").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(reported, 0f..1f),
            )
        )
    }
}
