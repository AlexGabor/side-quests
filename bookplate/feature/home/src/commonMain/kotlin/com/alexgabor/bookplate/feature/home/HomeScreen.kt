package com.alexgabor.bookplate.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alexgabor.bookplate.core.library.BookId
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.attributes.Heading1
import com.alexgabor.design.riso.attributes.Heading2
import com.alexgabor.design.riso.attributes.Heading3
import com.alexgabor.design.riso.attributes.Text
import com.alexgabor.design.riso.components.BottomSheet
import com.alexgabor.design.riso.components.Button
import com.alexgabor.design.riso.components.FlatButton
import com.alexgabor.design.riso.components.IconType
import com.alexgabor.design.riso.components.SearchField
import com.alexgabor.design.riso.components.SmallProgress
import com.alexgabor.design.riso.components.Toolbar
import com.alexgabor.design.riso.layout.contentWidth
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.design.riso.risograph.paper.risoPaper
import com.alexgabor.lib.documentaccess.DocumentPicker
import com.alexgabor.lib.documentaccess.DocumentPickerLauncher
import org.koin.compose.koinInject

/**
 * Bookplate's Home: the library, searchable, with a button to add a book.
 *
 * @param onOpenBook called with the id of the book whose row, or the mini player, was tapped.
 */
@Composable
fun HomeScreen(
    onOpenBook: (BookId) -> Unit,
    modifier: Modifier = Modifier,
    state: HomeState = rememberHomeState(),
    miniPlayer: MiniPlayerState = rememberMiniPlayerState(),
    picker: DocumentPicker = koinInject(),
) {
    val launcher = picker.rememberLauncher(state::onPicked)
    val addSheetOpen by state.addSheetOpen

    HomeContent(
        query = state.query.value,
        library = state.library.value,
        importStatus = state.importStatus.value,
        onQueryChange = state::onQueryChange,
        onAddClick = state::onAddClick,
        onStatusClosed = state::onStatusClosed,
        onOpenBook = onOpenBook,
        mini = miniPlayer.mini.value,
        onMiniPlayPause = miniPlayer::onPlayPause,
        modifier = modifier,
    )

    if (addSheetOpen) {
        AddBookSheet(launcher = launcher, onDismissRequest = state::onAddSheetDismissed)
    }
}

@Composable
private fun HomeContent(
    query: String,
    library: Library,
    importStatus: ImportStatus?,
    onQueryChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onStatusClosed: () -> Unit,
    onOpenBook: (BookId) -> Unit,
    mini: MiniPlayerUi?,
    onMiniPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val padding = RisoTheme.dimens.screenPadding

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .contentWidth(available = maxWidth, max = RisoTheme.dimens.contentMaxWidth)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
        ) {
            Column(
                Modifier.padding(start = padding, end = padding, top = padding),
                verticalArrangement = Arrangement.spacedBy(padding),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = "Search title or author",
                        modifier = Modifier.weight(1f),
                    )
                    FlatButton(icon = IconType.Add, contentDescription = "Add book", onClick = onAddClick)
                }
                if (importStatus != null) {
                    ImportStatusPanel(importStatus, onClose = onStatusClosed)
                }
            }

            // The list runs under the bottom inset unless the mini player takes it.
            val bottomInset = if (mini == null) {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
            } else {
                0.dp
            }
            val listPadding = PaddingValues(start = padding, end = padding, top = padding, bottom = padding + bottomInset)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (library) {
                    Library.Loading -> Unit
                    Library.Empty -> Message(
                        title = "Your library is empty",
                        body = "Tap + to add a book from a folder or a single file.",
                        modifier = Modifier.padding(listPadding),
                    )
                    is Library.NoResults -> Message(
                        title = "No books match “${library.query}”",
                        body = null,
                        modifier = Modifier.padding(listPadding),
                    )
                    is Library.Books -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = listPadding,
                    ) {
                        items(library.rows, key = { it.id.value }) { row ->
                            BookRowItem(row = row, onClick = { onOpenBook(row.id) })
                        }
                    }
                }
            }
            if (mini != null) {
                MiniPlayer(mini = mini, onClick = { onOpenBook(mini.bookId) }, onPlayPause = onMiniPlayPause)
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String?, modifier: Modifier = Modifier) {
    val ink = Modifier.risoInk(RisoTheme.colors.content)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading3(text = title, modifier = ink)
        if (body != null) Body(text = body, modifier = ink)
    }
}

@Composable
private fun ImportStatusPanel(status: ImportStatus, onClose: () -> Unit) {
    val colors = RisoTheme.colors
    val ink = Modifier.risoInk(colors.content)
    when (status) {
        is ImportStatus.Running -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Body(text = "Adding “${status.name}”", modifier = ink)
            Body(text = status.step, modifier = ink)
            SmallProgress(progress = status.fraction ?: 0f, modifier = Modifier.fillMaxWidth())
        }
        is ImportStatus.Finished -> Row(verticalAlignment = Alignment.CenterVertically) {
            val color = if (status.isError) colors.accent else colors.content
            Text(
                text = status.message,
                color = color,
                textStyle = RisoTheme.typography.body,
                modifier = Modifier.weight(1f).risoInk(color),
            )
            FlatButton(icon = IconType.Close, contentDescription = "Close", onClick = onClose)
        }
    }
}

/**
 * One book. The cover sits outside every ink pass so it stays a plain image; the text and the rule
 * under the row are printed.
 */
@Composable
private fun BookRowItem(row: BookRow, onClick: () -> Unit) {
    val colors = RisoTheme.colors
    val ink = Modifier.risoInk(colors.content)

    Box(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(coverPath = row.coverPath, title = row.title, modifier = Modifier.size(64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = row.title,
                    textStyle = RisoTheme.typography.heading3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = ink,
                )
                Text(
                    text = row.author,
                    textStyle = RisoTheme.typography.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = ink,
                )
                Row {
                    Body(text = row.duration, modifier = ink.weight(1f))
                    Body(text = row.percent, modifier = ink)
                }
                SmallProgress(progress = row.progress, modifier = Modifier.padding(top = 4.dp).fillMaxWidth())
            }
        }
    }
}

/**
 * The last-played book at the bottom of Home, under a printed rule: tapping it opens Player, its button
 * plays or pauses. It takes the bottom inset.
 */
@Composable
private fun MiniPlayer(mini: MiniPlayerUi, onClick: () -> Unit, onPlayPause: () -> Unit) {
    val colors = RisoTheme.colors
    val lineWidth = RisoTheme.dimens.lineWidth
    val padding = RisoTheme.dimens.screenPadding
    val ink = Modifier.risoInk(colors.content)

    Box(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        Box(
            ink.matchParentSize().drawBehind {
                drawLine(colors.content, Offset.Zero, Offset(size.width, 0f), strokeWidth = lineWidth.toPx() * 2)
            },
        )
        Column(
            Modifier.padding(start = padding, end = padding, top = 8.dp, bottom = padding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mini.title,
                    textStyle = RisoTheme.typography.heading3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = ink.weight(1f),
                )
                FlatButton(
                    icon = if (mini.isPlaying) IconType.Pause else IconType.Play,
                    contentDescription = if (mini.isPlaying) "Pause" else "Play",
                    onClick = onPlayPause,
                )
            }
            SmallProgress(progress = mini.progress, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AddBookSheet(launcher: DocumentPickerLauncher, onDismissRequest: () -> Unit) {
    BottomSheet(onDismissRequest = onDismissRequest) {
        val sheet = this
        Column(
            Modifier.fillMaxWidth().padding(RisoTheme.dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(RisoTheme.dimens.screenPadding),
        ) {
            Heading2(text = "Add a book", modifier = Modifier.risoInk(RisoTheme.colors.content))
            Button(
                text = "Add a folder",
                onClick = {
                    launcher.pickFolder()
                    sheet.dismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                text = "Add a single file",
                onClick = {
                    launcher.pickFile(AudioMimeTypes)
                    sheet.dismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** What the file picker offers. */
internal val AudioMimeTypes = listOf("audio/*")

private val PreviewRows = listOf(
    BookRow(BookId(1), "Dune", "Frank Herbert", "21:02:00", 0.25f, "25%", coverPath = null),
    BookRow(BookId(2), "Emma", "Jane Austen", "16:10:42", 0f, "0%", coverPath = null),
    BookRow(BookId(3), "Single", "Unknown author", "0:42", 1f, "100%", coverPath = null),
)

@Composable
private fun HomePreview(
    library: Library,
    importStatus: ImportStatus? = null,
    query: String = "",
    mini: MiniPlayerUi? = null,
) {
    RisoTheme {
        HomeContent(
            query = query,
            library = library,
            importStatus = importStatus,
            onQueryChange = {},
            onAddClick = {},
            onStatusClosed = {},
            onOpenBook = {},
            mini = mini,
            onMiniPlayPause = {},
            modifier = Modifier.risoPaper(),
        )
    }
}

@Preview
@Composable
private fun HomeLibraryPreview() {
    HomePreview(
        library = Library.Books(PreviewRows),
        importStatus = ImportStatus.Running("Sample Book", "Reading file 1 of 2", 0.5f),
        mini = MiniPlayerUi(BookId(1), "Dune", progress = 0.25f, isPlaying = true),
    )
}

@Preview
@Composable
private fun HomeEmptyPreview() {
    HomePreview(library = Library.Empty)
}

@Preview
@Composable
private fun HomeNoResultsPreview() {
    HomePreview(
        library = Library.NoResults("zzz"),
        importStatus = ImportStatus.Finished("Notes", "No audio found in “Notes”", isError = true),
        query = "zzz",
    )
}
