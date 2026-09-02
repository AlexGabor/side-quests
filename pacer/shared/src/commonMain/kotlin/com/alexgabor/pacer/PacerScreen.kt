package com.alexgabor.pacer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.attributes.Heading1
import com.alexgabor.design.riso.components.Icon
import com.alexgabor.design.riso.components.IconType
import com.alexgabor.design.riso.layout.WindowHeightSizeClass
import com.alexgabor.design.riso.layout.WindowWidthSizeClass
import com.alexgabor.design.riso.layout.computeWindowSizeClass
import com.alexgabor.design.riso.layout.contentWidth
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.pacer.slider.MetricCards
import com.alexgabor.pacer.slider.PaceCalculator
import com.alexgabor.pacer.slider.PaceCalculatorState
import com.alexgabor.pacer.slider.UnitSelector
import com.alexgabor.pacer.slider.rememberPaceCalculatorState


private val TwoPaneMaxWidth = 1280.dp

private const val LeftPaneWeight = 0.4f
private const val RightPaneWeight = 0.6f

@Composable
fun PacerScreen(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberPaceCalculatorState()
    val listState = rememberLazyListState()
    val leftPaneScrollState = rememberScrollState()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val sizeClass = computeWindowSizeClass()

        // Short windows can't stack a header above three sliders, so the sliders move alongside it.
        // Compact width is excluded because two panes of a phone-width window are narrower than a
        // single track.
        val twoPane = sizeClass.height == WindowHeightSizeClass.Compact &&
                sizeClass.width != WindowWidthSizeClass.Compact

        if (twoPane) {
            PacerTwoPane(
                state = state,
                onSettingsClick = onSettingsClick,
                listState = listState,
                leftPaneScrollState = leftPaneScrollState,
            )
        } else {
            PacerSinglePane(
                state = state,
                onSettingsClick = onSettingsClick,
                listState = listState,
            )
        }
    }
}

@Composable
private fun PacerHeader(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectionContainer {
        Column(modifier.risoInk(RisoTheme.colors.content, RisoTheme.colors.accent)) {
            Box {
                Heading1(
                    text = "Pacer",
                    modifier = Modifier.fillMaxWidth()
                        .padding(RisoTheme.dimens.screenPadding)
                )
                Icon(
                    type = IconType.Settings,
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .padding(horizontal = 8.dp)
                        .clickable(onClick = onSettingsClick)
                        .padding(8.dp)
                )
            }

            Body(
                text = "Tap the card you want to solve for, then scroll the other two. Pacer keeps them in sync.",
                modifier = Modifier.padding(horizontal = RisoTheme.dimens.screenPadding)
            )
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.PacerSinglePane(
    state: PaceCalculatorState,
    onSettingsClick: () -> Unit,
    listState: LazyListState,
) {
    Column(
        modifier = Modifier.align(Alignment.TopCenter)
            .contentWidth(available = maxWidth, max = RisoTheme.dimens.contentMaxWidth)
            .fillMaxSize()
    ) {
        PacerHeader(
            onSettingsClick = onSettingsClick,
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            PaceCalculator(
                state = state,
                listState = listState,
                contentPadding = listContentPadding(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                ),
                maxCardWidth = RisoTheme.dimens.contentMaxWidth,
            )
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.PacerTwoPane(
    state: PaceCalculatorState,
    onSettingsClick: () -> Unit,
    listState: LazyListState,
    leftPaneScrollState: ScrollState,
) {
    Row(
        modifier = Modifier.align(Alignment.Center)
            .contentWidth(available = maxWidth, max = TwoPaneMaxWidth)
            .fillMaxSize()
    ) {
        LeftPane(
            state = state,
            onSettingsClick = onSettingsClick,
            scrollState = leftPaneScrollState,
        )

        MetricCards(
            state = state,
            modifier = Modifier.weight(RightPaneWeight).fillMaxHeight(),
            listState = listState,
            contentPadding = listContentPadding(
                WindowInsetsSides.End + WindowInsetsSides.Vertical
            ),
            maxCardWidth = RisoTheme.dimens.contentMaxWidth,
        )
    }
}

@Composable
private fun RowScope.LeftPane(
    state: PaceCalculatorState,
    onSettingsClick: () -> Unit,
    scrollState: ScrollState,
) {
    Column(
        modifier = Modifier.weight(LeftPaneWeight)
            .fillMaxHeight()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Start + WindowInsetsSides.Vertical
                )
            )
            .verticalScroll(scrollState)
    ) {
        PacerHeader(onSettingsClick = onSettingsClick)

        UnitSelector(
            state = state,
            modifier = Modifier.align(Alignment.End)
                .padding(RisoTheme.dimens.screenPadding),
        )
    }
}

/**
 * Screen padding plus the insets this list should scroll under. Each pane names the sides it owns,
 * so no edge is padded twice even though the header and the list both take some.
 */
@Composable
private fun listContentPadding(sides: WindowInsetsSides): PaddingValues =
    WindowInsets.safeDrawing.only(sides)
        .add(
            WindowInsets(
                top = RisoTheme.dimens.screenPadding,
                bottom = RisoTheme.dimens.screenPadding,
            )
        )
        .asPaddingValues()
