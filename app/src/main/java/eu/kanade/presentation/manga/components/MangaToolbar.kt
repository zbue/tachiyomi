package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.presentation.components.OverflowMenu
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.manga.ChapterItem

@Composable
fun MangaToolbar(
    selected: List<ChapterItem>,
    isLocal: Boolean,
    isLocalOrStub: Boolean,
    modifier: Modifier = Modifier,
    title: String,
    titleAlphaProvider: () -> Float,
    backgroundAlphaProvider: () -> Float = titleAlphaProvider,
    onBackClicked: () -> Unit,
    onSelectAll: () -> Unit,
    onBookmarkUnbookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMarkAsReadUnreadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onDownloadClicked: ((List<Chapter>) -> Unit),
    onDeleteClicked: (List<Chapter>) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,
    onClickShare: (() -> Unit)?,
    onClickEditCategory: (() -> Unit)?,
    onClickMigrate: (() -> Unit)?,
) {
    Column(
        modifier = modifier,
    ) {
        val chapters = selected.map { it.chapter }
        val actionModeCounter = selected.count { it.selected }
        val isActionMode = actionModeCounter > 0

        TopAppBar(
            title = {
                Text(
                    text = if (isActionMode) actionModeCounter.toString() else title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(if (isActionMode) 1f else titleAlphaProvider()),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClicked) {
                    Icon(
                        imageVector = if (isActionMode) Icons.Outlined.Close else Icons.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.abc_action_bar_up_description),
                    )
                }
            },
            actions = {
                if (isActionMode) {
                    IconButton(onClick = { onDeleteClicked(chapters) }, enabled = selected.fastAny { it.isDownloaded } && !isLocal) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                        )
                    }
                    IconButton(onClick = onSelectAll) {
                        Icon(
                            imageVector = Icons.Outlined.SelectAll,
                            contentDescription = stringResource(R.string.action_select_all),
                        )
                    }
                    IconButton(onClick = { onDownloadClicked(chapters) }, enabled = !isLocalOrStub) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = stringResource(R.string.manga_download),
                        )
                    }
                    OverflowMenu { closeMenu ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.action_bookmark)) },
                            onClick = {
                                onBookmarkUnbookmarkClicked(chapters, true)
                                closeMenu()
                            },
                            enabled = chapters.fastAny { !it.bookmark },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.action_remove_bookmark)) },
                            onClick = {
                                onBookmarkUnbookmarkClicked(chapters, false)
                                closeMenu()
                            },
                            enabled = chapters.fastAny { it.bookmark },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.action_mark_as_read)) },
                            onClick = {
                                onMarkAsReadUnreadClicked(chapters, true)
                                closeMenu()
                            },
                            enabled = chapters.fastAny { !it.read },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.action_mark_as_unread)) },
                            onClick = {
                                onMarkAsReadUnreadClicked(chapters, false)
                                closeMenu()
                            },
                            enabled = chapters.fastAny { it.read || it.lastPageRead > 0L },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.action_mark_previous_as_read)) },
                            onClick = {
                                onMarkPreviousAsReadClicked(chapters[0])
                                closeMenu()
                            },
                            enabled = actionModeCounter == 1,
                        )
                    }
                } else {
                    if (onClickEditCategory != null || onClickMigrate != null) {
                        OverflowMenu { closeMenu ->
                            if (onClickEditCategory != null) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_edit_categories)) },
                                    onClick = {
                                        onClickEditCategory()
                                        closeMenu()
                                    },
                                )
                            }
                            if (onClickMigrate != null) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_migrate)) },
                                    onClick = {
                                        onClickMigrate()
                                        closeMenu()
                                    },
                                )
                            }
                            if (onClickShare != null) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.action_share)) },
                                    onClick = {
                                        onClickShare()
                                        closeMenu()
                                    },
                                )
                            }
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = MaterialTheme.colorScheme
                    .surfaceColorAtElevation(3.dp)
                    .copy(alpha = if (isActionMode) 1f else backgroundAlphaProvider()),
            ),
        )
    }
}
