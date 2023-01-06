package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.presentation.components.ChangeCategoryDialog
import eu.kanade.presentation.components.DuplicateMangaDialog
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.components.showSnack
import eu.kanade.presentation.manga.ChapterSettingsDialog
import eu.kanade.presentation.manga.EditCoverAction
import eu.kanade.presentation.manga.MangaScreen
import eu.kanade.presentation.manga.components.ConfirmActionDialog
import eu.kanade.presentation.manga.components.DownloadCustomAmountDialog
import eu.kanade.presentation.manga.components.MangaCoverDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.isLocalOrStub
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import logcat.LogPriority

class MangaScreen(
    private val mangaId: Long,
    val fromSource: Boolean = false,
) : Screen, AssistContentScreen {

    private var assistUrl: String? = null

    override val key = uniqueScreenKey

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { MangaInfoScreenModel(context, mangaId, fromSource) }

        val state by screenModel.state.collectAsState()

        if (state is MangaScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as MangaScreenState.Success
        val isHttpSource = remember { successState.source is HttpSource }

        LaunchedEffect(successState.manga, screenModel.source) {
            if (isHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getMangaUrl(screenModel.manga, screenModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get manga URL" }
                }
            }
        }

        MangaScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            isTabletUi = isTabletUi(),
            onBackClicked = navigator::pop,
            onChapterClicked = { openChapter(context, it) },
            onDownloadChapter = screenModel::runChapterDownloadActions.takeIf { !successState.source.isLocalOrStub() },
            onAddToLibraryClicked = {
                screenModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onWebViewClicked = { openMangaInWebView(context, screenModel.manga, screenModel.source) }.takeIf { isHttpSource },
            onWebViewLongClicked = { copyMangaUrl(context, screenModel.manga, screenModel.source) }.takeIf { isHttpSource },
            onTrackingClicked = screenModel::showTrackDialog.takeIf { successState.trackingAvailable },
            onTagClicked = { scope.launch { performGenreSearch(navigator, it, screenModel.source!!) } },
            onFilterButtonClicked = screenModel::showSettingsDialog,
            onRefresh = screenModel::fetchAllFromSource,
            onContinueReading = { continueReading(context, screenModel.getNextUnreadChapter()) },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            onCoverClicked = screenModel::showCoverDialog,
            onShareClicked = { shareManga(context, screenModel.manga, screenModel.source) }.takeIf { isHttpSource },
            onDownloadActionClicked = screenModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },
            onEditCategoryClicked = screenModel::promptChangeCategories.takeIf { successState.manga.favorite },
            onMigrateClicked = { navigator.push(MigrateSearchScreen(successState.manga.id)) }.takeIf { successState.manga.favorite },
            onMultiBookmarkClicked = screenModel::showBookmarkChaptersDialog,
            onMultiMarkAsReadClicked = screenModel::showMarkChaptersReadDialog,
            onMarkPreviousAsReadClicked = screenModel::showMarkPreviousChapterReadDialog,
            onMultiDeleteClicked = screenModel::showDeleteChapterDialog,
            onChapterSelected = screenModel::toggleSelection,
            onAllChapterSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onSwipeToBookmarkChapter = screenModel::swipeToBookmarkChapter,
            onSwipeToMarkChapterRead = screenModel::swipeToMarkChapterRead,
        )

        LaunchedEffect(Unit) {
            screenModel.snackbar.collectLatest { snackbar ->
                when (snackbar) {
                    is MangaInfoScreenModel.Snackbar.InternalError -> {
                        screenModel.snackbarHostState.showSnack(snackbar.error)
                    }
                    is MangaInfoScreenModel.Snackbar.SwipeToBookmarkChapter -> {
                        val msgRes = if (snackbar.bookmarked) R.string.action_bookmark else R.string.action_remove_bookmark
                        val result = screenModel.snackbarHostState.showSnack(
                            message = context.getString(msgRes),
                            actionLabel = context.getString(R.string.action_undo),
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            screenModel.swipeToBookmarkChapter(snackbar.chapter, !snackbar.bookmarked, false)
                        }
                    }
                    is MangaInfoScreenModel.Snackbar.SwipeToMarkChapterRead -> {
                        val msgRes = if (snackbar.markAsRead) R.string.action_mark_as_read else R.string.action_mark_as_unread
                        val result = screenModel.snackbarHostState.showSnack(
                            message = context.getString(msgRes),
                            actionLabel = context.getString(R.string.action_undo),
                        )

                        when (result) {
                            SnackbarResult.ActionPerformed -> {
                                screenModel.swipeToMarkChapterRead(snackbar.chapter, !snackbar.markAsRead, snackbar.lastPageRead, showSnackbar = false)
                            }
                            SnackbarResult.Dismissed -> {
                                screenModel.swipeToMarkChapterRead(snackbar.chapter, snackbar.markAsRead, snackbar.lastPageRead, showSnackbar = false)
                            }
                        }
                    }
                    is MangaInfoScreenModel.Snackbar.FetchChaptersFromSourceError -> {
                        screenModel.snackbarHostState.showSnack(snackbar.error)
                    }
                    is MangaInfoScreenModel.Snackbar.AddToLibrary -> {
                        val result = screenModel.snackbarHostState.showSnack(
                            message = context.getString(R.string.snack_add_to_library),
                            actionLabel = context.getString(R.string.action_add),
                            duration = SnackbarDuration.Indefinite,
                        )
                        if (result == SnackbarResult.ActionPerformed && !snackbar.isFavoritedManga) {
                            screenModel.toggleFavorite()
                        }
                    }
                    MangaInfoScreenModel.Snackbar.DeleteDownloadedChapters -> {
                        val result = screenModel.snackbarHostState.showSnack(
                            message = context.getString(R.string.delete_downloads_for_manga),
                            actionLabel = context.getString(R.string.action_delete),
                            duration = SnackbarDuration.Indefinite,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            screenModel.deleteDownloads()
                        }
                    }
                    MangaInfoScreenModel.Snackbar.UpdateDefaultChapterSettings -> {
                        screenModel.snackbarHostState.showSnack(context.getString(R.string.chapter_settings_updated))
                    }
                }
            }
        }

        val onDismissRequest = { screenModel.dismissDialog() }
        when (val dialog = (state as? MangaScreenState.Success)?.dialog) {
            null -> {}
            is MangaInfoScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is MangaInfoScreenModel.Dialog.BookmarkChapters -> {
                ConfirmActionDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.bookmarkChapters(dialog.chapters, dialog.bookmarked, action = {})
                    },
                    textRes = R.string.confirm_delete_chapters,
                )
            }
            is MangaInfoScreenModel.Dialog.MarkChaptersRead -> {
                ConfirmActionDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.markChaptersRead(dialog.chapters, dialog.markAsRead, isFinalized = dialog.markAsRead, action = {})
                    },
                    textRes = R.string.confirm_delete_chapters,
                )
            }
            is MangaInfoScreenModel.Dialog.MarkPreviousChapterReadDialog -> {
                ConfirmActionDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.markPreviousChapterRead(dialog.pointer)
                    },
                    textRes = R.string.confirm_delete_chapters,
                )
            }
            is MangaInfoScreenModel.Dialog.DeleteChapters -> {
                ConfirmActionDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteChapters(dialog.chapters)
                    },
                    textRes = R.string.confirm_delete_chapters,
                )
            }
            is MangaInfoScreenModel.Dialog.DownloadCustomAmount -> {
                DownloadCustomAmountDialog(
                    maxAmount = dialog.max,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { amount ->
                        val chaptersToDownload = screenModel.getUnreadChaptersSorted().take(amount)
                        if (chaptersToDownload.isNotEmpty()) {
                            screenModel.startDownload(chapters = chaptersToDownload, startNow = false)
                        }
                    },
                )
            }
            is MangaInfoScreenModel.Dialog.DuplicateManga -> DuplicateMangaDialog(
                onDismissRequest = onDismissRequest,
                onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                onOpenManga = { navigator.push(MangaScreen(dialog.duplicate.id)) },
                duplicateFrom = screenModel.getSourceOrStub(dialog.duplicate),
            )
            MangaInfoScreenModel.Dialog.SettingsSheet -> ChapterSettingsDialog(
                onDismissRequest = onDismissRequest,
                manga = successState.manga,
                onDownloadFilterChanged = screenModel::setDownloadedFilter,
                onUnreadFilterChanged = screenModel::setUnreadFilter,
                onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                onSortModeChanged = screenModel::setSorting,
                onDisplayModeChanged = screenModel::setDisplayMode,
                onSetAsDefault = screenModel::setCurrentSettingsAsDefault,
            )
            MangaInfoScreenModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = TrackInfoDialogHomeScreen(
                        mangaId = successState.manga.id,
                        mangaTitle = successState.manga.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is TrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }
            MangaInfoScreenModel.Dialog.FullCover -> {
                val sm = rememberScreenModel { MangaCoverScreenModel(successState.manga.id) }
                val manga by sm.state.collectAsState()
                if (manga != null) {
                    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editCover(context, it)
                    }
                    MangaCoverDialog(
                        coverDataProvider = { manga!! },
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(manga) { manga!!.hasCustomCover() },
                        onShareClick = { sm.shareCover(context) },
                        onSaveClick = { sm.saveCover(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.deleteCustomCover()
                            }
                        },
                        onDismissRequest = onDismissRequest,
                    )
                    LaunchedEffect(Unit) {
                        sm.snackbar.collectLatest { snackbar ->
                            when (snackbar) {
                                MangaCoverScreenModel.Snackbar.CoverSaved -> {
                                    sm.snackbarHostState.showSnack(context.getString(R.string.cover_saved))
                                }
                                MangaCoverScreenModel.Snackbar.SaveCoverError -> {
                                    sm.snackbarHostState.showSnack(context.getString(R.string.error_saving_cover))
                                }
                                MangaCoverScreenModel.Snackbar.ShareCoverError -> {
                                    sm.snackbarHostState.showSnack(context.getString(R.string.error_sharing_cover))
                                }
                                MangaCoverScreenModel.Snackbar.CoverUpdated -> {
                                    sm.snackbarHostState.showSnack(context.getString(R.string.cover_updated))
                                }
                                MangaCoverScreenModel.Snackbar.UpdateCoverFailed -> {
                                    sm.snackbarHostState.showSnack(context.getString(R.string.notification_cover_update_failed))
                                }
                            }
                        }
                    }
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }
        }
    }

    private fun continueReading(context: Context, unreadChapter: Chapter?) {
        if (unreadChapter != null) openChapter(context, unreadChapter)
    }

    private fun openChapter(context: Context, chapter: Chapter) {
        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
    }

    private fun getMangaUrl(manga_: Manga?, source_: Source?): String? {
        val manga = manga_ ?: return null
        val source = source_ as? HttpSource ?: return null

        return try {
            source.getMangaUrl(manga.toSManga())
        } catch (e: Exception) {
            null
        }
    }

    private fun openMangaInWebView(context: Context, manga_: Manga?, source_: Source?) {
        getMangaUrl(manga_, source_)?.let { url ->
            val intent = WebViewActivity.newIntent(context, url, source_?.id, manga_?.title)
            context.startActivity(intent)
        }
    }

    private fun shareManga(context: Context, manga_: Manga?, source_: Source?) {
        try {
            getMangaUrl(manga_, source_)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        context.getString(R.string.action_share),
                    ),
                )
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(GlobalSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                navigator.pop()
                previousController.search(query)
            }
            is BrowseSourceScreen -> {
                navigator.pop()
                previousController.search(query)
            }
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     *
     * @param genreName the search genre to the parent controller
     */
    private suspend fun performGenreSearch(navigator: Navigator, genreName: String, source: Source) {
        if (navigator.size < 2) {
            return
        }

        val previousController = navigator.items[navigator.size - 2]
        if (previousController is BrowseSourceScreen && source is HttpSource) {
            navigator.pop()
            previousController.searchGenre(genreName)
        } else {
            performSearch(navigator, genreName, global = false)
        }
    }

    /**
     * Copy Manga URL to Clipboard
     */
    private fun copyMangaUrl(context: Context, manga_: Manga?, source_: Source?) {
        val manga = manga_ ?: return
        val source = source_ as? HttpSource ?: return
        val url = source.getMangaUrl(manga.toSManga())
        context.copyToClipboard(url, url)
    }
}
