package com.alexgabor.bookplate.feature.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Heading1
import com.alexgabor.design.riso.risograph.inks.risoFadeAsAlpha
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.lib.coroutine.CoroutineDispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * A book's cover, read from [coverPath] in app storage and drawn as a plain image: covers are not
 * printed through Riso inks. Without a readable cover, a printed placeholder with [title]'s first letter.
 *
 * Having no ink to thin, the image takes the enclosing `risoFade` (a screen crossfading, the page
 * behind a bottom sheet) as transparency, so it leaves with the print around it.
 *
 * A copy of Home's: both features need it, and it reads app storage, so it can't live in `design/riso`.
 */
@Composable
internal fun BookCover(
    coverPath: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val cover by produceState<ImageBitmap?>(initialValue = null, coverPath) {
        value = coverPath?.let { loadCover(it) }
    }
    val shape = RisoTheme.shapes.standardShape
    val image = cover
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.risoFadeAsAlpha().clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .risoInk(RisoTheme.colors.content)
                .border(RisoTheme.dimens.lineWidth, RisoTheme.colors.content, shape),
            contentAlignment = Alignment.Center,
        ) {
            Heading1(text = title.trim().take(1).uppercase())
        }
    }
}

private suspend fun loadCover(path: String): ImageBitmap? = withContext(CoroutineDispatchers.IO) {
    runCatching { FileSystem.SYSTEM.read(path.toPath()) { readByteArray() }.decodeToImageBitmap() }.getOrNull()
}
