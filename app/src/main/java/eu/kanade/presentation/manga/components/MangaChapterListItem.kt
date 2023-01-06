package eu.kanade.presentation.manga.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.ChapterDownloadAction
import eu.kanade.presentation.components.ChapterDownloadIndicator
import eu.kanade.presentation.util.ReadItemAlpha
import eu.kanade.presentation.util.SecondaryItemAlpha
import eu.kanade.presentation.util.padding
import eu.kanade.presentation.util.selectedBackground
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import me.saket.swipe.rememberSwipeableActionsState
import kotlin.math.abs

@Composable
fun MangaChapterListItem(
    modifier: Modifier = Modifier,
    title: String,
    date: String?,
    readProgress: String?,
    scanlator: String?,
    read: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    swipeActionsEnabled: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownloadClick: ((ChapterDownloadAction) -> Unit)?,
    onSwipeToBookmarkChapter: () -> Unit,
    onSwipeToUnbookmarkChapter: () -> Unit,
    onSwipeToMarkChapterRead: () -> Unit,
    onSwipeToMarkChapterUnread: () -> Unit,
) {
    MangaChapterListItemSwipeActions(
        read = read,
        bookmark = bookmark,
        swipeActionsEnabled = swipeActionsEnabled,
        onSwipeToBookmarkChapter = onSwipeToBookmarkChapter,
        onSwipeToUnbookmarkChapter = onSwipeToUnbookmarkChapter,
        onSwipeToMarkChapterRead = onSwipeToMarkChapterRead,
        onSwipeToMarkChapterUnread = onSwipeToMarkChapterUnread,
    ) {
        MangaChapterListItemContent(
            modifier = modifier,
            title = title,
            date = date,
            readProgress = readProgress,
            scanlator = scanlator,
            read = read,
            bookmark = bookmark,
            selected = selected,
            downloadIndicatorEnabled = downloadIndicatorEnabled,
            downloadStateProvider = downloadStateProvider,
            downloadProgressProvider = downloadProgressProvider,
            onLongClick = onLongClick,
            onClick = onClick,
            onDownloadClick = onDownloadClick,
        )
    }
}

@Composable
fun MangaChapterListItemSwipeActions(
    read: Boolean,
    bookmark: Boolean,
    swipeActionsEnabled: Boolean,
    onSwipeToBookmarkChapter: () -> Unit,
    onSwipeToUnbookmarkChapter: () -> Unit,
    onSwipeToMarkChapterRead: () -> Unit,
    onSwipeToMarkChapterUnread: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeableActionsState()

    val thresholdCrossed = abs(state.offset.value) > with(LocalDensity.current) { SwipeThreshold.toPx() }

    val contentAlpha = if (isSystemInDarkTheme()) 0.16f else 0.22f

    val iconScale by animateFloatAsState(
        targetValue = if (thresholdCrossed) 1.07f else .87f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
        ),
    )

    val swipeToBookmarkChapter = SwipeAction(
        onSwipe = if (bookmark) onSwipeToUnbookmarkChapter else onSwipeToBookmarkChapter,
        icon = {
            val (bookmarkIcon, bookmarkIconText) = if (bookmark) {
                Icons.Filled.BookmarkRemove to R.string.action_remove_bookmark
            } else {
                Icons.Filled.BookmarkAdd to R.string.action_bookmark
            }
            val bookmarkColorContent by animateColorAsState(
                when (thresholdCrossed) {
                    true -> MaterialTheme.colorScheme.onPrimary
                    false -> MaterialTheme.colorScheme.primary
                },
            )

            MangaChapterListItemSwipeActionsIcon(bookmarkIcon, bookmarkIconText, bookmarkColorContent, iconScale)
        },
        background = MaterialTheme.colorScheme.primary,
        isUndo = bookmark,
    )

    val swipeToMarkChapterRead = SwipeAction(
        onSwipe = if (read) onSwipeToMarkChapterUnread else onSwipeToMarkChapterRead,
        icon = {
            val (readIcon, readIconText) = if (read) {
                Icons.Filled.RemoveDone to R.string.action_mark_as_unread
            } else {
                Icons.Filled.DoneAll to R.string.action_mark_as_read
            }

            val readColorContent: Color = MaterialTheme.colorScheme.onSurfaceVariant

            MangaChapterListItemSwipeActionsIcon(readIcon, readIconText, readColorContent, iconScale)
        },
        background = MaterialTheme.colorScheme.onBackground.copy(alpha = contentAlpha),
        isUndo = read,
    )

    SwipeableActionsBox(
        state = state,
        startActions = listOf(swipeToBookmarkChapter).takeIf { swipeActionsEnabled } ?: emptyList(),
        endActions = listOf(swipeToMarkChapterRead).takeIf { swipeActionsEnabled } ?: emptyList(),
        swipeThreshold = SwipeThreshold,
        backgroundUntilSwipeThreshold = if (swipeActionsEnabled) MaterialTheme.colorScheme.secondary.copy(alpha = contentAlpha) else MaterialTheme.colorScheme.background,
    ) {
        content()
    }
}

private val SwipeThreshold = 142.dp

@Composable
fun MangaChapterListItemContent(
    modifier: Modifier = Modifier,
    title: String,
    date: String?,
    readProgress: String?,
    scanlator: String?,
    read: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownloadClick: ((ChapterDownloadAction) -> Unit)?,
) {
    Row(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(MaterialTheme.colorScheme.background)
            .then(
                modifier
                    .selectedBackground(selected)
                    .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            ),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val textColor = if (bookmark && !read) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            val textAlpha = remember(read) { if (read) ReadItemAlpha else 1f }
            val textSubtitleAlpha = remember(read) { if (read) ReadItemAlpha else SecondaryItemAlpha }
            Row(verticalAlignment = Alignment.CenterVertically) {
                var textHeight by remember { mutableStateOf(0) }
                if (bookmark) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = stringResource(R.string.action_filter_bookmarked),
                        modifier = Modifier
                            .sizeIn(maxHeight = with(LocalDensity.current) { textHeight.toDp() - 2.dp }),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = title,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textHeight = it.size.height },
                    modifier = Modifier.alpha(textAlpha),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.alpha(textSubtitleAlpha)) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.bodyMedium
                        .copy(color = textColor, fontSize = 12.sp),
                ) {
                    if (date != null) {
                        Text(
                            text = date,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (readProgress != null || scanlator != null) DotSeparatorText()
                    }
                    if (readProgress != null) {
                        Text(
                            text = readProgress,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(ReadItemAlpha),
                        )
                        if (scanlator != null) DotSeparatorText()
                    }
                    if (scanlator != null) {
                        Text(
                            text = scanlator,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Download view
        if (onDownloadClick != null) {
            ChapterDownloadIndicator(
                enabled = downloadIndicatorEnabled,
                modifier = Modifier.padding(start = 4.dp),
                downloadStateProvider = downloadStateProvider,
                downloadProgressProvider = downloadProgressProvider,
                onClick = onDownloadClick,
            )
        }
    }
}

@Composable
private fun MangaChapterListItemSwipeActionsIcon(
    imageVector: ImageVector,
    @StringRes textRes: Int,
    contentColor: Color,
    iconScale: Float,
) {
    Column(modifier = Modifier.padding(horizontal = MaterialTheme.padding.large)) {
        Icon(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .scale(iconScale),
            imageVector = imageVector,
            tint = contentColor,
            contentDescription = null,
        )
        Text(
            text = stringResource(textRes),
            textAlign = TextAlign.Center,
            color = contentColor,
        )
    }
}
