package com.alexgabor.design.riso.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import com.alexgabor.design.riso.RisoTheme
import kotlinx.coroutines.launch

/** How strongly a pressed element is overprinted with the indication color. */
private const val PressedAlpha = 0.25f

/** How long the overprint takes to lift once the press ends, so a quick tap still shows. */
private const val ReleaseMillis = 150

/**
 * The press feedback for plain `clickable`/`selectable` elements: a flat overprint of [color] at a
 * quarter strength while pressed. [RisoTheme] provides it with the content color, so call this only
 * where the content is printed in another color.
 */
@Composable
fun risoIndication(color: Color = RisoTheme.colors.content): Indication =
    remember(color) { RisoIndication(color) }

internal class RisoIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        RisoIndicationNode(interactionSource, color)

    override fun equals(other: Any?): Boolean = other is RisoIndication && other.color == color

    override fun hashCode(): Int = color.hashCode()
}

private class RisoIndicationNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : Modifier.Node(), DrawModifierNode {
    private val alpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var presses = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses++
                    is PressInteraction.Release, is PressInteraction.Cancel -> presses--
                    else -> return@collect
                }
                coroutineScope.launch {
                    if (presses > 0) {
                        alpha.snapTo(PressedAlpha)
                    } else {
                        alpha.animateTo(0f, tween(ReleaseMillis))
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val overprint = alpha.value
        if (overprint > 0f) drawRect(color.copy(alpha = overprint))
    }
}
