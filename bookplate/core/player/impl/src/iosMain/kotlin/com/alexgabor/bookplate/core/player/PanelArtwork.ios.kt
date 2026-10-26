package com.alexgabor.bookplate.core.player

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightBold
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIRectFill
import platform.UIKit.drawAtPoint
import platform.UIKit.sizeWithAttributes

/** The [PanelArtwork] of a book without a cover, the title's [letter] printed in [inks]. */
@OptIn(ExperimentalForeignApi::class)
internal fun renderPanelArtwork(letter: String, inks: PanelInks): UIImage {
    val size = PanelArtwork.SIZE_PX.toDouble()
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { scale = 1.0 }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(size, size), format = format)
    return renderer.imageWithActions { _ ->
        inks.paper.uiColor().setFill()
        UIRectFill(CGRectMake(0.0, 0.0, size, size))

        val inset = size * PanelArtwork.BORDER_INSET
        val box = UIBezierPath.bezierPathWithRoundedRect(
            rect = CGRectMake(inset, inset, size - 2 * inset, size - 2 * inset),
            cornerRadius = size * PanelArtwork.CORNER_RADIUS,
        )
        inks.accentVariant.uiColor(PanelArtwork.WASH_ALPHA).setFill()
        box.fill()
        box.lineWidth = size * PanelArtwork.BORDER_WIDTH
        inks.accent.uiColor().setStroke()
        box.stroke()

        val attributes = mapOf<Any?, Any?>(
            NSFontAttributeName to UIFont.monospacedSystemFontOfSize(size * PanelArtwork.LETTER_SIZE, UIFontWeightBold),
            NSForegroundColorAttributeName to inks.content.uiColor(),
        )
        val text = NSString.create(string = letter)
        text.sizeWithAttributes(attributes).useContents {
            text.drawAtPoint(CGPointMake((size - width) / 2, (size - height) / 2), withAttributes = attributes)
        }
    }
}

private fun Int.uiColor(alpha: Float = 1f): UIColor = UIColor.colorWithRed(
    red = ((this shr 16) and 0xFF) / 255.0,
    green = ((this shr 8) and 0xFF) / 255.0,
    blue = (this and 0xFF) / 255.0,
    alpha = alpha.toDouble(),
)
