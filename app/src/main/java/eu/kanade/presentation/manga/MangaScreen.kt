package eu.kanade.presentation.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.ChapterDownloadAction
import eu.kanade.presentation.components.ExtendedFloatingActionButton
import eu.kanade.presentation.components.LazyColumn
import eu.kanade.presentation.components.PullRefresh
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.TwoPanelBox
import eu.kanade.presentation.components.VerticalFastScroller
import eu.kanade.presentation.manga.components.ChapterHeader
import eu.kanade.presentation.manga.components.ExpandableMangaDescription
import eu.kanade.presentation.manga.components.MangaActionRow
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.presentation.manga.components.MangaInfoBox
import eu.kanade.presentation.manga.components.MangaToolbar
import eu.kanade.presentation.util.isScrolledToEnd
import eu.kanade.presentation.util.isScrollingUp
import eu.kanade.presentation.util.padding
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.source.isLocal
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.ui.manga.ChapterItem
import eu.kanade.tachiyomi.ui.manga.MangaScreenState
import eu.kanade.tachiyomi.ui.manga.chapterDecimalFormat
import eu.kanade.tachiyomi.util.lang.toRelativeString
import java.text.DateFormat
import java.util.Date

@Composable
fun MangaScreen(
    state: MangaScreenState.Success,
    snackbarHostState: SnackbarHostState,
    dateRelativeTime: Int,
    dateFormat: DateFormat,
    isTabletUi: Boolean,
    onBackClicked: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterItem>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagClicked: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onMultiDownloadChapters: (List<Chapter>) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterItem, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
) {
    if (!isTabletUi) {
        MangaScreenSmallImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            dateRelativeTime = dateRelativeTime,
            dateFormat = dateFormat,
            onBackClicked = onBackClicked,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagClicked = onTagClicked,
            onFilterClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onMigrateClicked = onMigrateClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMultiDownloadChapters = onMultiDownloadChapters,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
        )
    } else {
        MangaScreenLargeImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            dateRelativeTime = dateRelativeTime,
            dateFormat = dateFormat,
            onBackClicked = onBackClicked,
            onChapterClicked = onChapterClicked,
            onDownloadChapter = onDownloadChapter,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onWebViewClicked = onWebViewClicked,
            onWebViewLongClicked = onWebViewLongClicked,
            onTrackingClicked = onTrackingClicked,
            onTagClicked = onTagClicked,
            onFilterButtonClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onContinueReading = onContinueReading,
            onSearch = onSearch,
            onCoverClicked = onCoverClicked,
            onShareClicked = onShareClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onMigrateClicked = onMigrateClicked,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsReadClicked = onMultiMarkAsReadClicked,
            onMultiDownloadChapters = onMultiDownloadChapters,
            onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onChapterSelected = onChapterSelected,
            onAllChapterSelected = onAllChapterSelected,
        )
    }
}

@Composable
private fun MangaScreenSmallImpl(
    state: MangaScreenState.Success,
    snackbarHostState: SnackbarHostState,
    dateRelativeTime: Int,
    dateFormat: DateFormat,
    onBackClicked: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterItem>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagClicked: (String) -> Unit,
    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onMultiDownloadChapters: (List<Chapter>) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterItem, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
) {
    val chapterListState = rememberLazyListState()

    val chapters = remember(state) { state.processedChapters.toList() }

    val internalOnBackPressed = {
        if (chapters.fastAny { it.selected }) {
            onAllChapterSelected(false)
        } else {
            onBackClicked()
        }
    }
    BackHandler(onBack = internalOnBackPressed)

    Scaffold(
        topBar = {
            val firstVisibleItemIndex by remember {
                derivedStateOf { chapterListState.firstVisibleItemIndex }
            }
            val firstVisibleItemScrollOffset by remember {
                derivedStateOf { chapterListState.firstVisibleItemScrollOffset }
            }
            val animatedTitleAlpha by animateFloatAsState(
                if (firstVisibleItemIndex > 0) 1f else 0f,
            )
            val animatedBgAlpha by animateFloatAsState(
                if (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0) 1f else 0f,
            )
            MangaToolbar(
                selected = chapters.filter { it.selected },
                isLocal = state.source.isLocal(),
                isLocalOrStub = state.source.isLocalOrStub(),
                title = state.manga.title,
                titleAlphaProvider = { animatedTitleAlpha },
                backgroundAlphaProvider = { animatedBgAlpha },
                onBackClicked = internalOnBackPressed,
                onSelectAll = { onAllChapterSelected(true) },
                onClickShare = onShareClicked,
                onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                onBookmarkUnbookmarkClicked = onMultiBookmarkClicked,
                onMarkAsReadUnreadClicked = onMultiMarkAsReadClicked,
                onDownloadClicked = onMultiDownloadChapters,
                onDeleteClicked = onMultiDeleteClicked,
                onClickEditCategory = onEditCategoryClicked,
                onClickMigrate = onMigrateClicked,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = chapters.fastAny { !it.chapter.read } && chapters.fastAll { !it.selected },
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    text = {
                        val id = if (chapters.fastAny { it.chapter.read }) {
                            R.string.action_resume
                        } else {
                            R.string.action_start
                        }
                        Text(text = stringResource(id))
                    },
                    icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = onContinueReading,
                    expanded = chapterListState.isScrollingUp() || chapterListState.isScrolledToEnd(),
                )
            }
        },
    ) { contentPadding ->
        val topPadding = contentPadding.calculateTopPadding()

        PullRefresh(
            refreshing = state.isRefreshingData,
            onRefresh = onRefresh,
            enabled = chapters.fastAll { !it.selected },
            indicatorPadding = contentPadding,
        ) {
            val layoutDirection = LocalLayoutDirection.current
            VerticalFastScroller(
                listState = chapterListState,
                topContentPadding = topPadding,
                endContentPadding = contentPadding.calculateEndPadding(layoutDirection),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    state = chapterListState,
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                ) {
                    item(
                        key = MangaScreenItem.INFO_BOX,
                        contentType = MangaScreenItem.INFO_BOX,
                    ) {
                        MangaInfoBox(
                            isTabletUi = false,
                            appBarPadding = topPadding,
                            title = state.manga.title,
                            author = state.manga.author,
                            artist = state.manga.artist,
                            sourceName = remember { state.source.getNameForMangaInfo() },
                            isStubSource = remember { state.source is SourceManager.StubSource },
                            coverDataProvider = { state.manga },
                            status = state.manga.status,
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                        )
                    }

                    item(
                        key = MangaScreenItem.ACTION_ROW,
                        contentType = MangaScreenItem.ACTION_ROW,
                    ) {
                        MangaActionRow(
                            favorite = state.manga.favorite,
                            trackingCount = state.trackingCount,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onWebViewLongClicked = onWebViewLongClicked,
                            onTrackingClicked = onTrackingClicked,
                            onEditCategory = onEditCategoryClicked,
                        )
                    }

                    item(
                        key = MangaScreenItem.DESCRIPTION_WITH_TAG,
                        contentType = MangaScreenItem.DESCRIPTION_WITH_TAG,
                    ) {
                        ExpandableMangaDescription(
                            defaultExpandState = state.isFromSource,
                            description = state.manga.description,
                            tagsProvider = { state.manga.genre },
                            onTagClicked = onTagClicked,
                        )
                    }

                    item(
                        key = MangaScreenItem.CHAPTER_HEADER,
                        contentType = MangaScreenItem.CHAPTER_HEADER,
                    ) {
                        ChapterHeader(
                            enabled = chapters.fastAll { !it.selected },
                            hasActiveFilters = state.manga.chaptersFiltered(),
                            chapterCount = chapters.size,
                            onClick = onFilterClicked,
                        )
                    }

                    sharedChapterItems(
                        manga = state.manga,
                        chapters = chapters,
                        dateRelativeTime = dateRelativeTime,
                        dateFormat = dateFormat,
                        onChapterClicked = onChapterClicked,
                        onDownloadChapter = onDownloadChapter,
                        onChapterSelected = onChapterSelected,
                    )
                }
            }
        }
    }
}

@Composable
fun MangaScreenLargeImpl(
    state: MangaScreenState.Success,
    snackbarHostState: SnackbarHostState,
    dateRelativeTime: Int,
    dateFormat: DateFormat,
    onBackClicked: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterItem>, ChapterDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagClicked: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueReading: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,

    // For cover dialog
    onCoverClicked: () -> Unit,

    // For top action menu
    onShareClicked: (() -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,

    // For bottom action menu
    onMultiBookmarkClicked: (List<Chapter>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, markAsRead: Boolean) -> Unit,
    onMultiDeleteClicked: (List<Chapter>) -> Unit,
    onMultiDownloadChapters: (List<Chapter>) -> Unit,
    onMarkPreviousAsReadClicked: (Chapter) -> Unit,

    // Chapter selection
    onChapterSelected: (ChapterItem, Boolean, Boolean, Boolean) -> Unit,
    onAllChapterSelected: (Boolean) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    val chapters = remember(state) { state.processedChapters.toList() }

    val insetPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    var topBarHeight by remember { mutableStateOf(0) }
    PullRefresh(
        refreshing = state.isRefreshingData,
        onRefresh = onRefresh,
        enabled = chapters.fastAll { !it.selected },
        indicatorPadding = PaddingValues(
            start = insetPadding.calculateStartPadding(layoutDirection),
            top = with(density) { topBarHeight.toDp() },
            end = insetPadding.calculateEndPadding(layoutDirection),
        ),
    ) {
        val chapterListState = rememberLazyListState()

        val internalOnBackPressed = {
            if (chapters.fastAny { it.selected }) {
                onAllChapterSelected(false)
            } else {
                onBackClicked()
            }
        }
        BackHandler(onBack = internalOnBackPressed)

        Scaffold(
            topBar = {
                MangaToolbar(
                    selected = chapters.filter { it.selected },
                    modifier = Modifier.onSizeChanged { topBarHeight = it.height },
                    isLocal = state.source.isLocal(),
                    isLocalOrStub = state.source.isLocalOrStub(),
                    title = state.manga.title,
                    titleAlphaProvider = { if (chapters.fastAny { it.selected }) 1f else 0f },
                    backgroundAlphaProvider = { 1f },
                    onBackClicked = internalOnBackPressed,
                    onSelectAll = { onAllChapterSelected(true) },
                    onClickShare = onShareClicked,
                    onMarkPreviousAsReadClicked = onMarkPreviousAsReadClicked,
                    onDownloadClicked = onMultiDownloadChapters,
                    onBookmarkUnbookmarkClicked = onMultiBookmarkClicked,
                    onMarkAsReadUnreadClicked = onMultiMarkAsReadClicked,
                    onDeleteClicked = onMultiDeleteClicked,
                    onClickEditCategory = onEditCategoryClicked,
                    onClickMigrate = onMigrateClicked,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = chapters.fastAny { !it.chapter.read } && chapters.fastAll { !it.selected },
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        text = {
                            val id = if (chapters.fastAny { it.chapter.read }) {
                                R.string.action_resume
                            } else {
                                R.string.action_start
                            }
                            Text(text = stringResource(id))
                        },
                        icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = onContinueReading,
                        expanded = chapterListState.isScrollingUp() || chapterListState.isScrolledToEnd(),
                    )
                }
            },
        ) { contentPadding ->
            TwoPanelBox(
                modifier = Modifier.padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                ),
                startContent = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = contentPadding.calculateBottomPadding()),
                    ) {
                        MangaInfoBox(
                            isTabletUi = true,
                            appBarPadding = contentPadding.calculateTopPadding(),
                            title = state.manga.title,
                            author = state.manga.author,
                            artist = state.manga.artist,
                            sourceName = remember { state.source.getNameForMangaInfo() },
                            isStubSource = remember { state.source is SourceManager.StubSource },
                            coverDataProvider = { state.manga },
                            status = state.manga.status,
                            onCoverClick = onCoverClicked,
                            doSearch = onSearch,
                        )
                        MangaActionRow(
                            favorite = state.manga.favorite,
                            trackingCount = state.trackingCount,
                            onAddToLibraryClicked = onAddToLibraryClicked,
                            onWebViewClicked = onWebViewClicked,
                            onWebViewLongClicked = onWebViewLongClicked,
                            onTrackingClicked = onTrackingClicked,
                            onEditCategory = onEditCategoryClicked,
                        )
                        ExpandableMangaDescription(
                            defaultExpandState = true,
                            description = state.manga.description,
                            tagsProvider = { state.manga.genre },
                            onTagClicked = onTagClicked,
                        )
                    }
                },
                endContent = {
                    VerticalFastScroller(
                        listState = chapterListState,
                        topContentPadding = contentPadding.calculateTopPadding(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxHeight(),
                            state = chapterListState,
                            contentPadding = PaddingValues(
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                        ) {
                            item(
                                key = MangaScreenItem.CHAPTER_HEADER,
                                contentType = MangaScreenItem.CHAPTER_HEADER,
                            ) {
                                ChapterHeader(
                                    enabled = chapters.fastAll { !it.selected },
                                    hasActiveFilters = state.manga.chaptersFiltered(),
                                    chapterCount = chapters.size,
                                    onClick = onFilterButtonClicked,
                                )
                            }

                            sharedChapterItems(
                                manga = state.manga,
                                chapters = chapters,
                                dateRelativeTime = dateRelativeTime,
                                dateFormat = dateFormat,
                                onChapterClicked = onChapterClicked,
                                onDownloadChapter = onDownloadChapter,
                                onChapterSelected = onChapterSelected,
                            )
                        }
                    }
                },
            )
        }
    }
}

private fun LazyListScope.sharedChapterItems(
    manga: Manga,
    chapters: List<ChapterItem>,
    dateRelativeTime: Int,
    dateFormat: DateFormat,
    onChapterClicked: (Chapter) -> Unit,
    onDownloadChapter: ((List<ChapterItem>, ChapterDownloadAction) -> Unit)?,
    onChapterSelected: (ChapterItem, Boolean, Boolean, Boolean) -> Unit,
) {
    itemsIndexed(
        items = chapters,
        key = { _, chapterItem -> "chapter-${chapterItem.chapter.id}" },
        contentType = { _, _ -> MangaScreenItem.CHAPTER },
    ) { index, chapterItem ->
        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current

        Column {
            if (index == 0) {
                Divider(modifier = Modifier.padding(start = MaterialTheme.padding.medium))
            }
            MangaChapterListItem(
                title = if (manga.displayMode == Manga.CHAPTER_DISPLAY_NUMBER) {
                    stringResource(
                        R.string.display_mode_chapter,
                        chapterDecimalFormat.format(chapterItem.chapter.chapterNumber.toDouble()),
                    )
                } else {
                    chapterItem.chapter.name
                },
                date = chapterItem.chapter.dateUpload
                    .takeIf { it > 0L }
                    ?.let {
                        Date(it).toRelativeString(
                            context,
                            dateRelativeTime,
                            dateFormat,
                        )
                    },
                readProgress = chapterItem.chapter.lastPageRead
                    .takeIf { !chapterItem.chapter.read && it > 0L }
                    ?.let {
                        stringResource(
                            R.string.chapter_progress,
                            it + 1,
                        )
                    },
                scanlator = chapterItem.chapter.scanlator.takeIf { !it.isNullOrBlank() },
                read = chapterItem.chapter.read,
                bookmark = chapterItem.chapter.bookmark,
                selected = chapterItem.selected,
                downloadIndicatorEnabled = chapters.fastAll { !it.selected },
                downloadStateProvider = { chapterItem.downloadState },
                downloadProgressProvider = { chapterItem.downloadProgress },
                onLongClick = {
                    onChapterSelected(chapterItem, !chapterItem.selected, true, true)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onClick = {
                    onChapterItemClick(
                        chapterItem = chapterItem,
                        chapters = chapters,
                        onToggleSelection = { onChapterSelected(chapterItem, !chapterItem.selected, true, false) },
                        onChapterClicked = onChapterClicked,
                    )
                },
                onDownloadClick = if (onDownloadChapter != null) {
                    { onDownloadChapter(listOf(chapterItem), it) }
                } else {
                    null
                },
            )

            if (index < chapters.lastIndex) {
                Divider(modifier = Modifier.padding(start = MaterialTheme.padding.medium))
            }
        }
    }
}

private fun onChapterItemClick(
    chapterItem: ChapterItem,
    chapters: List<ChapterItem>,
    onToggleSelection: (Boolean) -> Unit,
    onChapterClicked: (Chapter) -> Unit,
) {
    when {
        chapterItem.selected -> onToggleSelection(false)
        chapters.fastAny { it.selected } -> onToggleSelection(true)
        else -> onChapterClicked(chapterItem.chapter)
    }
}
