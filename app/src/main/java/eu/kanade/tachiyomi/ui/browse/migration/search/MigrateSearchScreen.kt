package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastForEachIndexed
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.chapter.interactor.UpdateChapter
import eu.kanade.domain.chapter.model.toChapterUpdate
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.track.interactor.GetTracks
import eu.kanade.domain.track.interactor.InsertTrack
import eu.kanade.presentation.browse.MigrateSearchScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

class MigrateSearchScreen(private val mangaId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MigrateSearchScreenModel(mangaId = mangaId) }
        val state by screenModel.state.collectAsState()

        MigrateSearchScreen(
            navigateUp = navigator::pop,
            state = state,
            getManga = { source, manga ->
                screenModel.getManga(source = source, initialManga = manga)
            },
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = screenModel::search,
            onClickSource = {
                if (!screenModel.incognitoMode.get()) {
                    screenModel.lastUsedSourceId.set(it.id)
                }
                navigator.push(SourceSearchScreen(state.manga!!, it.id, state.searchQuery))
            },
            onClickItem = screenModel::showMigrateDialog,
            onLongClickItem = { navigator.push(MangaScreen(it.id, true)) },
        )

        val onDismissRequest = { screenModel.dismissDialog() }
        when (val dialog = state.dialog) {
            null -> {}
            is MigrateSearchScreenModel.Dialog.Migrate -> {
                MigrateDialog(
                    oldManga = state.manga!!,
                    newManga = dialog.manga,
                    screenModel = rememberScreenModel { MigrateDialogScreenModel() },
                    onDismissRequest = onDismissRequest,
                    onClickTitle = {
                        navigator.push(MangaScreen(dialog.manga.id, true))
                    },
                    onPopScreen = {
                        if (navigator.lastItem is MangaScreen) {
                            val lastItem = navigator.lastItem
                            navigator.popUntil { navigator.items.contains(lastItem) }
                            navigator.push(MangaScreen(dialog.manga.id))
                        } else {
                            navigator.replace(MangaScreen(dialog.manga.id))
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun MigrateDialog(
    oldManga: Manga,
    newManga: Manga,
    screenModel: MigrateDialogScreenModel,
    onDismissRequest: () -> Unit,
    onClickTitle: () -> Unit,
    onPopScreen: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeFlags = remember { MigrationFlags.getEnabledFlagsPositions(screenModel.migrateFlags.get()) }
    val items = remember {
        MigrationFlags.titles(oldManga)
            .map { context.getString(it) }
            .toList()
    }
    val selected = remember {
        mutableStateListOf(*List(items.size) { i -> activeFlags.contains(i) }.toTypedArray())
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(R.string.migration_dialog_what_to_include))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                items.forEachIndexed { index, title ->
                    val onChange: () -> Unit = {
                        selected[index] = !selected[index]
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onChange),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selected[index], onCheckedChange = { onChange() })
                        Text(text = title)
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    onClickTitle()
                    onDismissRequest()
                },) {
                    Text(text = stringResource(R.string.action_show_manga))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    scope.launchIO {
                        screenModel.migrateManga(oldManga, newManga, false)
                        launchUI {
                            onPopScreen()
                        }
                    }
                },) {
                    Text(text = stringResource(R.string.copy))
                }
                TextButton(onClick = {
                    scope.launchIO {
                        val selectedIndices = mutableListOf<Int>()
                        selected.fastForEachIndexed { i, b -> if (b) selectedIndices.add(i) }
                        val newValue = MigrationFlags.getFlagsFromPositions(selectedIndices.toTypedArray())
                        screenModel.migrateFlags.set(newValue)
                        screenModel.migrateManga(oldManga, newManga, true)
                        launchUI {
                            onPopScreen()
                        }
                    }
                },) {
                    Text(text = stringResource(R.string.migrate))
                }
            }
        },
    )
}

class MigrateDialogScreenModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getChapterByMangaId: GetChapterByMangaId = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : ScreenModel {

    val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)
    }

    private val enhancedServices by lazy { Injekt.get<TrackManager>().services.filterIsInstance<EnhancedTrackService>() }

    suspend fun migrateManga(oldManga: Manga, newManga: Manga, replace: Boolean) {
        val source = sourceManager.get(newManga.source) ?: return
        val prevSource = sourceManager.get(oldManga.source)

        try {
            val chapters = source.getChapterList(newManga.toSManga())

            migrateMangaInternal(
                oldSource = prevSource,
                newSource = source,
                oldManga = oldManga,
                newManga = newManga,
                sourceChapters = chapters,
                replace = replace,
            )
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
        }
    }

    private suspend fun migrateMangaInternal(
        oldSource: Source?,
        newSource: Source,
        oldManga: Manga,
        newManga: Manga,
        sourceChapters: List<SChapter>,
        replace: Boolean,
    ) {
        val flags = migrateFlags.get()

        val migrateChapters = MigrationFlags.hasChapters(flags)
        val migrateCategories = MigrationFlags.hasCategories(flags)
        val migrateTracks = MigrationFlags.hasTracks(flags)
        val migrateCustomCover = MigrationFlags.hasCustomCover(flags)

        try {
            syncChaptersWithSource.await(sourceChapters, newManga, newSource)
        } catch (e: Exception) {
            // Worst case, chapters won't be synced
        }

        // Update chapters read, bookmark and dateFetch
        if (migrateChapters) {
            val prevMangaChapters = getChapterByMangaId.await(oldManga.id)
            val mangaChapters = getChapterByMangaId.await(newManga.id)

            val maxChapterRead = prevMangaChapters
                .filter { it.read }
                .maxOfOrNull { it.chapterNumber }

            val updatedMangaChapters = mangaChapters.map { mangaChapter ->
                var updatedChapter = mangaChapter
                if (updatedChapter.isRecognizedNumber) {
                    val prevChapter = prevMangaChapters
                        .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }

                    if (prevChapter != null) {
                        updatedChapter = updatedChapter.copy(
                            dateFetch = prevChapter.dateFetch,
                            bookmark = prevChapter.bookmark,
                        )
                    }

                    if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                        updatedChapter = updatedChapter.copy(read = true)
                    }
                }

                updatedChapter
            }

            val chapterUpdates = updatedMangaChapters.map { it.toChapterUpdate() }
            updateChapter.awaitAll(chapterUpdates)
        }

        // Update categories
        if (migrateCategories) {
            val categoryIds = getCategories.await(oldManga.id).map { it.id }
            setMangaCategories.await(newManga.id, categoryIds)
        }

        // Update track
        if (migrateTracks) {
            val tracks = getTracks.await(oldManga.id).mapNotNull { track ->
                val updatedTrack = track.copy(mangaId = newManga.id)

                val service = enhancedServices
                    .firstOrNull { it.isTrackFrom(updatedTrack, oldManga, oldSource) }

                if (service != null) {
                    service.migrateTrack(updatedTrack, newManga, newSource)
                } else {
                    updatedTrack
                }
            }
            insertTrack.awaitAll(tracks)
        }

        if (replace) {
            updateManga.await(MangaUpdate(oldManga.id, favorite = false, dateAdded = 0))
        }

        // Update custom cover (recheck if custom cover exists)
        if (migrateCustomCover && oldManga.hasCustomCover()) {
            @Suppress("BlockingMethodInNonBlockingContext")
            coverCache.setCustomCoverToCache(newManga, coverCache.getCustomCoverFile(oldManga.id).inputStream())
        }

        updateManga.await(
            MangaUpdate(
                id = newManga.id,
                favorite = true,
                chapterFlags = oldManga.chapterFlags,
                viewerFlags = oldManga.viewerFlags,
                dateAdded = if (replace) oldManga.dateAdded else Date().time,
            ),
        )
    }
}
