package eu.kanade.tachiyomi.ui.updates

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.ConfirmActionDialog
import eu.kanade.presentation.updates.UpdateScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel.Event
import kotlinx.coroutines.flow.collectLatest

object UpdatesTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            return TabOptions(
                index = 1u,
                title = stringResource(R.string.label_recent_updates),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(DownloadQueueScreen)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { UpdatesScreenModel() }
        val state by screenModel.state.collectAsState()

        UpdateScreen(
            state = state,
            snackbarHostState = screenModel.snackbarHostState,
            lastUpdated = screenModel.lastUpdated,
            relativeTime = screenModel.relativeTime,
            onClickCover = { item -> navigator.push(MangaScreen(item.update.mangaId)) },
            onSelectAll = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onUpdateLibrary = screenModel::updateLibrary,
            onDownloadChapter = screenModel::downloadChapters,
            onMultiBookmarkClicked = screenModel::showBookmarkUpdatesDialog,
            onMultiMarkAsReadClicked = screenModel::showMarkUpdatesReadDialog,
            onMultiDeleteClicked = screenModel::showConfirmDeleteChapters,
            onUpdateSelected = screenModel::toggleSelection,
            onOpenChapter = {
                val intent = ReaderActivity.newIntent(context, it.update.mangaId, it.update.chapterId)
                context.startActivity(intent)
            },
        )

        val onDismissRequest = { screenModel.dismissDialog() }
        when (val dialog = state.dialog) {
            is UpdatesScreenModel.Dialog.BookmarkUpdates -> {
                val dialogTextRes = if (dialog.bookmarked) R.string.confirm_bookmark_chapters else R.string.confirm_remove_bookmark_chapters
                val confirmTextRes = if (dialog.bookmarked) R.string.action_bookmark_plain else R.string.action_remove_bookmark_plain

                ConfirmActionDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.bookmarkUpdates(dialog.updates, dialog.bookmarked) },
                    dialogText = context.getString(dialogTextRes),
                    confirmText = context.getString(confirmTextRes),
                )
            }
            is UpdatesScreenModel.Dialog.MarkUpdatesRead -> {
                val dialogTextRes = if (dialog.markAsRead) R.string.confirm_mark_as_read_chapters else R.string.confirm_mark_as_unread_chapters
                val confirmTextRes = if (dialog.markAsRead) R.string.action_mark_as_read else R.string.action_mark_as_unread

                ConfirmActionDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.markUpdatesRead(dialog.updates, dialog.markAsRead) },
                    dialogText = context.getString(dialogTextRes),
                    confirmText = context.getString(confirmTextRes),
                )
            }
            is UpdatesScreenModel.Dialog.DeleteChapter -> {
                ConfirmActionDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.deleteChapters(dialog.updatesToDelete) },
                    dialogText = context.getString(R.string.confirm_delete_chapters),
                    confirmText = context.getString(R.string.action_delete),
                )
            }
            null -> {}
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                when (event) {
                    Event.InternalError -> screenModel.snackbarHostState.showSnackbar(context.getString(R.string.internal_error))
                    is Event.LibraryUpdateTriggered -> {
                        val msg = if (event.started) {
                            R.string.updating_library
                        } else {
                            R.string.update_already_running
                        }
                        screenModel.snackbarHostState.showSnackbar(context.getString(msg))
                    }
                }
            }
        }

        LaunchedEffect(state.selectionMode) {
            HomeScreen.showBottomNav(!state.selectionMode)
        }

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }
        DisposableEffect(Unit) {
            screenModel.resetNewUpdatesCount()

            onDispose {
                screenModel.resetNewUpdatesCount()
            }
        }
    }
}
