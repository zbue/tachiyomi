package eu.kanade.tachiyomi.ui.library

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.core.prefs.CheckboxState
import eu.kanade.core.prefs.PreferenceMutableState
import eu.kanade.core.prefs.asState
import eu.kanade.core.util.fastDistinctBy
import eu.kanade.core.util.fastFilter
import eu.kanade.core.util.fastFilterNot
import eu.kanade.core.util.fastMapNotNull
import eu.kanade.core.util.fastPartition
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.SetMangaCategories
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.chapter.interactor.GetChapterByMangaId
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.history.interactor.GetNextChapters
import eu.kanade.domain.library.model.LibraryManga
import eu.kanade.domain.library.model.LibrarySort
import eu.kanade.domain.library.model.sort
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.manga.interactor.GetLibraryManga
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.model.isLocal
import eu.kanade.domain.track.interactor.GetTracksPerManga
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchNonCancellable
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.Collator
import java.util.Collections
import java.util.Locale

/**
 * Typealias for the library manga, using the category as keys, and list of manga as values.
 */
typealias LibraryMap = Map<Category, List<LibraryItem>>

class LibraryScreenModel(
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getTracksPerManga: GetTracksPerManga = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val getChaptersByMangaId: GetChapterByMangaId = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
) : StateScreenModel<LibraryScreenModel.State>(State()) {

    var activeCategoryIndex: Int by libraryPreferences.lastUsedCategory().asState(coroutineScope)

    init {
        coroutineScope.launchIO {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged(),
                getLibraryFlow(),
                getTracksPerManga.subscribe(),
                getTrackingFilterFlow(),
                downloadCache.changes,
            ) { searchQuery, library, tracks, loggedInTrackServices, _ ->
                library
                    .applyFilters(tracks, loggedInTrackServices)
                    .applySort()
                    .mapValues { (_, value) ->
                        if (searchQuery != null) {
                            // Filter query
                            value.filter { it.matches(searchQuery) }
                        } else {
                            // Don't do anything
                            value
                        }
                    }
            }
                .collectLatest {
                    mutableState.update { state ->
                        state.copy(
                            isLoading = false,
                            library = it,
                        )
                    }
                }
        }

        combine(
            libraryPreferences.categoryTabs().changes(),
            libraryPreferences.categoryNumberOfItems().changes(),
            libraryPreferences.showContinueReadingButton().changes(),
        ) { a, b, c -> arrayOf(a, b, c) }
            .onEach { (showCategoryTabs, showMangaCount, showMangaContinueButton) ->
                mutableState.update { state ->
                    state.copy(
                        showCategoryTabs = showCategoryTabs,
                        showMangaCount = showMangaCount,
                        showMangaContinueButton = showMangaContinueButton,
                    )
                }
            }
            .launchIn(coroutineScope)

        combine(
            getLibraryItemPreferencesFlow(),
            getTrackingFilterFlow(),
        ) { prefs, trackFilter ->
            val a = (
                prefs.filterDownloaded or
                    prefs.filterUnread or
                    prefs.filterStarted or
                    prefs.filterBookmarked or
                    prefs.filterCompleted
                ) != TriStateGroup.State.IGNORE.value
            val b = trackFilter.values.any { it != TriStateGroup.State.IGNORE.value }
            a || b
        }
            .distinctUntilChanged()
            .onEach {
                mutableState.update { state ->
                    state.copy(hasActiveFilters = it)
                }
            }
            .launchIn(coroutineScope)
    }

    /**
     * Applies library filters to the given map of manga.
     */
    private suspend fun LibraryMap.applyFilters(
        trackMap: Map<Long, List<Long>>,
        loggedInTrackServices: Map<Long, Int>,
    ): LibraryMap {
        val prefs = getLibraryItemPreferencesFlow().first()
        val downloadedOnly = prefs.globalFilterDownloaded
        val filterDownloaded = prefs.filterDownloaded
        val filterUnread = prefs.filterUnread
        val filterStarted = prefs.filterStarted
        val filterBookmarked = prefs.filterBookmarked
        val filterCompleted = prefs.filterCompleted

        val isNotLoggedInAnyTrack = loggedInTrackServices.isEmpty()

        val excludedTracks = loggedInTrackServices.mapNotNull { if (it.value == TriStateGroup.State.EXCLUDE.value) it.key else null }
        val includedTracks = loggedInTrackServices.mapNotNull { if (it.value == TriStateGroup.State.INCLUDE.value) it.key else null }
        val trackFiltersIsIgnored = includedTracks.isEmpty() && excludedTracks.isEmpty()

        val filterFnDownloaded: (LibraryItem) -> Boolean = downloaded@{
            if (!downloadedOnly && filterDownloaded == TriStateGroup.State.IGNORE.value) return@downloaded true

            val isDownloaded = it.libraryManga.manga.isLocal() ||
                it.downloadCount > 0 ||
                downloadManager.getDownloadCount(it.libraryManga.manga) > 0
            return@downloaded if (downloadedOnly || filterDownloaded == TriStateGroup.State.INCLUDE.value) {
                isDownloaded
            } else {
                !isDownloaded
            }
        }

        val filterFnUnread: (LibraryItem) -> Boolean = unread@{
            if (filterUnread == TriStateGroup.State.IGNORE.value) return@unread true

            val isUnread = it.libraryManga.unreadCount > 0
            return@unread if (filterUnread == TriStateGroup.State.INCLUDE.value) {
                isUnread
            } else {
                !isUnread
            }
        }

        val filterFnStarted: (LibraryItem) -> Boolean = started@{
            if (filterStarted == TriStateGroup.State.IGNORE.value) return@started true

            val hasStarted = it.libraryManga.hasStarted
            return@started if (filterStarted == TriStateGroup.State.INCLUDE.value) {
                hasStarted
            } else {
                !hasStarted
            }
        }

        val filterFnBookmarked: (LibraryItem) -> Boolean = bookmarked@{
            if (filterBookmarked == TriStateGroup.State.IGNORE.value) return@bookmarked true

            val hasBookmarks = it.libraryManga.hasBookmarks
            return@bookmarked if (filterBookmarked == TriStateGroup.State.INCLUDE.value) {
                hasBookmarks
            } else {
                !hasBookmarks
            }
        }

        val filterFnCompleted: (LibraryItem) -> Boolean = completed@{
            if (filterCompleted == TriStateGroup.State.IGNORE.value) return@completed true

            val isCompleted = it.libraryManga.manga.status.toInt() == SManga.COMPLETED
            return@completed if (filterCompleted == TriStateGroup.State.INCLUDE.value) {
                isCompleted
            } else {
                !isCompleted
            }
        }

        val filterFnTracking: (LibraryItem) -> Boolean = tracking@{ item ->
            if (isNotLoggedInAnyTrack || trackFiltersIsIgnored) return@tracking true

            val mangaTracks = trackMap[item.libraryManga.id].orEmpty()

            val exclude = mangaTracks.fastFilter { it in excludedTracks }
            val include = mangaTracks.fastFilter { it in includedTracks }

            // TODO: Simplify the filter logic
            if (includedTracks.isNotEmpty() && excludedTracks.isNotEmpty()) {
                return@tracking if (exclude.isNotEmpty()) false else include.isNotEmpty()
            }

            if (excludedTracks.isNotEmpty()) return@tracking exclude.isEmpty()

            if (includedTracks.isNotEmpty()) return@tracking include.isNotEmpty()

            return@tracking false
        }

        val filterFn: (LibraryItem) -> Boolean = filter@{
            return@filter !(
                !filterFnDownloaded(it) ||
                    !filterFnUnread(it) ||
                    !filterFnStarted(it) ||
                    !filterFnBookmarked(it) ||
                    !filterFnCompleted(it) ||
                    !filterFnTracking(it)
                )
        }

        return this.mapValues { entry -> entry.value.fastFilter(filterFn) }
    }

    /**
     * Applies library sorting to the given map of manga.
     */
    private fun LibraryMap.applySort(): LibraryMap {
        val locale = Locale.getDefault()
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }
        val sortAlphabetically: (LibraryItem, LibraryItem) -> Int = { i1, i2 ->
            collator.compare(i1.libraryManga.manga.title.lowercase(locale), i2.libraryManga.manga.title.lowercase(locale))
        }

        val sortFn: (LibraryItem, LibraryItem) -> Int = { i1, i2 ->
            val sort = keys.find { it.id == i1.libraryManga.category }!!.sort
            when (sort.type) {
                LibrarySort.Type.Alphabetical -> {
                    sortAlphabetically(i1, i2)
                }
                LibrarySort.Type.LastRead -> {
                    i1.libraryManga.lastRead.compareTo(i2.libraryManga.lastRead)
                }
                LibrarySort.Type.LastUpdate -> {
                    i1.libraryManga.manga.lastUpdate.compareTo(i2.libraryManga.manga.lastUpdate)
                }
                LibrarySort.Type.UnreadCount -> when {
                    // Ensure unread content comes first
                    i1.libraryManga.unreadCount == i2.libraryManga.unreadCount -> 0
                    i1.libraryManga.unreadCount == 0L -> if (sort.isAscending) 1 else -1
                    i2.libraryManga.unreadCount == 0L -> if (sort.isAscending) -1 else 1
                    else -> i1.libraryManga.unreadCount.compareTo(i2.libraryManga.unreadCount)
                }
                LibrarySort.Type.TotalChapters -> {
                    i1.libraryManga.totalChapters.compareTo(i2.libraryManga.totalChapters)
                }
                LibrarySort.Type.LatestChapter -> {
                    i1.libraryManga.latestUpload.compareTo(i2.libraryManga.latestUpload)
                }
                LibrarySort.Type.ChapterFetchDate -> {
                    i1.libraryManga.chapterFetchedAt.compareTo(i2.libraryManga.chapterFetchedAt)
                }
                LibrarySort.Type.DateAdded -> {
                    i1.libraryManga.manga.dateAdded.compareTo(i2.libraryManga.manga.dateAdded)
                }
            }
        }

        return this.mapValues { entry ->
            val comparator = if (keys.find { it.id == entry.key.id }!!.sort.isAscending) {
                Comparator(sortFn)
            } else {
                Collections.reverseOrder(sortFn)
            }

            entry.value.sortedWith(comparator.thenComparator(sortAlphabetically))
        }
    }

    private fun getLibraryItemPreferencesFlow(): Flow<ItemPreferences> {
        return combine(
            libraryPreferences.downloadBadge().changes(),
            libraryPreferences.unreadBadge().changes(),
            libraryPreferences.localBadge().changes(),
            libraryPreferences.languageBadge().changes(),

            preferences.downloadedOnly().changes(),
            libraryPreferences.filterDownloaded().changes(),
            libraryPreferences.filterUnread().changes(),
            libraryPreferences.filterStarted().changes(),
            libraryPreferences.filterBookmarked().changes(),
            libraryPreferences.filterCompleted().changes(),
            transform = {
                ItemPreferences(
                    downloadBadge = it[0] as Boolean,
                    unreadBadge = it[1] as Boolean,
                    localBadge = it[2] as Boolean,
                    languageBadge = it[3] as Boolean,
                    globalFilterDownloaded = it[4] as Boolean,
                    filterDownloaded = it[5] as Int,
                    filterUnread = it[6] as Int,
                    filterStarted = it[7] as Int,
                    filterBookmarked = it[8] as Int,
                    filterCompleted = it[9] as Int,
                )
            },
        )
    }

    /**
     * Get the categories and all its manga from the database.
     */
    private fun getLibraryFlow(): Flow<LibraryMap> {
        val libraryMangasFlow = combine(
            getLibraryManga.subscribe(),
            getLibraryItemPreferencesFlow(),
            downloadCache.changes,
        ) { libraryMangaList, prefs, _ ->
            libraryMangaList
                .map { libraryManga ->
                    // Display mode based on user preference: take it from global library setting or category
                    LibraryItem(libraryManga).apply {
                        downloadCount = if (prefs.downloadBadge) {
                            downloadManager.getDownloadCount(libraryManga.manga).toLong()
                        } else {
                            0
                        }
                        unreadCount = if (prefs.unreadBadge) libraryManga.unreadCount else 0
                        isLocal = if (prefs.localBadge) libraryManga.manga.isLocal() else false
                        sourceLanguage = if (prefs.languageBadge) {
                            sourceManager.getOrStub(libraryManga.manga.source).lang
                        } else {
                            ""
                        }
                    }
                }
                .groupBy { it.libraryManga.category }
        }

        return combine(getCategories.subscribe(), libraryMangasFlow) { categories, libraryManga ->
            val displayCategories = if (libraryManga.isNotEmpty() && !libraryManga.containsKey(0)) {
                categories.fastFilterNot { it.isSystemCategory }
            } else {
                categories
            }

            displayCategories.associateWith { libraryManga[it.id] ?: emptyList() }
        }
    }

    /**
     * Flow of tracking filter preferences
     *
     * @return map of track id with the filter value
     */
    private fun getTrackingFilterFlow(): Flow<Map<Long, Int>> {
        val loggedServices = trackManager.services.filter { it.isLogged }
        return if (loggedServices.isNotEmpty()) {
            val prefFlows = loggedServices
                .map { libraryPreferences.filterTracking(it.id.toInt()).changes() }
                .toTypedArray()
            combine(*prefFlows) {
                loggedServices
                    .mapIndexed { index, trackService -> trackService.id to it[index] }
                    .toMap()
            }
        } else {
            flowOf(emptyMap())
        }
    }

    /**
     * Returns the common categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getCommonCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        return mangas
            .map { getCategories.await(it.id).toSet() }
            .reduce { set1, set2 -> set1.intersect(set2) }
    }

    suspend fun getNextUnreadChapter(manga: Manga): Chapter? {
        return getChaptersByMangaId.await(manga.id).getNextUnread(manga, downloadManager)
    }

    /**
     * Returns the mix (non-common) categories for the given list of manga.
     *
     * @param mangas the list of manga.
     */
    private suspend fun getMixCategories(mangas: List<Manga>): Collection<Category> {
        if (mangas.isEmpty()) return emptyList()
        val mangaCategories = mangas.map { getCategories.await(it.id).toSet() }
        val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2) }
        return mangaCategories.flatten().distinct().subtract(common)
    }

    fun runDownloadActionSelection(action: DownloadAction) {
        val selection = state.value.selection
        val mangas = selection.map { it.manga }.toList()
        when (action) {
            DownloadAction.NEXT_1_CHAPTER -> downloadUnreadChapters(mangas, 1)
            DownloadAction.NEXT_5_CHAPTERS -> downloadUnreadChapters(mangas, 5)
            DownloadAction.NEXT_10_CHAPTERS -> downloadUnreadChapters(mangas, 10)
            DownloadAction.UNREAD_CHAPTERS -> downloadUnreadChapters(mangas, null)
            DownloadAction.CUSTOM -> {
                mutableState.update { state ->
                    state.copy(
                        dialog = Dialog.DownloadCustomAmount(
                            mangas,
                            selection.maxOf { it.unreadCount }.toInt(),
                        ),
                    )
                }
                return
            }
            else -> {}
        }
        clearSelection()
    }

    /**
     * Queues the amount specified of unread chapters from the list of mangas given.
     *
     * @param mangas the list of manga.
     * @param amount the amount to queue or null to queue all
     */
    fun downloadUnreadChapters(mangas: List<Manga>, amount: Int?) {
        coroutineScope.launchNonCancellable {
            mangas.forEach { manga ->
                val chapters = getNextChapters.await(manga.id)
                    .fastFilterNot { chapter ->
                        downloadManager.getQueuedDownloadOrNull(chapter.id) != null ||
                            downloadManager.isChapterDownloaded(
                                chapter.name,
                                chapter.scanlator,
                                manga.title,
                                manga.source,
                            )
                    }
                    .let { if (amount != null) it.take(amount) else it }

                downloadManager.downloadChapters(manga, chapters)
            }
        }
    }

    /**
     * Marks mangas' chapters read status.
     */
    fun markReadSelection(read: Boolean) {
        val mangas = state.value.selection.toList()
        coroutineScope.launchNonCancellable {
            mangas.forEach { manga ->
                setReadStatus.await(
                    manga = manga.manga,
                    read = read,
                )
            }
        }
        clearSelection()
    }

    /**
     * Remove the selected manga.
     *
     * @param mangaList the list of manga to delete.
     * @param deleteFromLibrary whether to delete manga from library.
     * @param deleteChapters whether to delete downloaded chapters.
     */
    fun removeMangas(mangaList: List<Manga>, deleteFromLibrary: Boolean, deleteChapters: Boolean) {
        coroutineScope.launchNonCancellable {
            val mangaToDelete = mangaList.distinctBy { it.id }

            if (deleteFromLibrary) {
                val toDelete = mangaToDelete.map {
                    it.removeCovers(coverCache)
                    MangaUpdate(
                        favorite = false,
                        id = it.id,
                    )
                }
                updateManga.awaitAll(toDelete)
            }

            if (deleteChapters) {
                mangaToDelete.forEach { manga ->
                    val source = sourceManager.get(manga.source) as? HttpSource
                    if (source != null) {
                        downloadManager.deleteManga(manga, source)
                    }
                }
            }
        }
    }

    /**
     * Bulk update categories of manga using old and new common categories.
     *
     * @param mangaList the list of manga to move.
     * @param addCategories the categories to add for all mangas.
     * @param removeCategories the categories to remove in all mangas.
     */
    fun setMangaCategories(mangaList: List<Manga>, addCategories: List<Long>, removeCategories: List<Long>) {
        coroutineScope.launchNonCancellable {
            mangaList.forEach { manga ->
                val categoryIds = getCategories.await(manga.id)
                    .map { it.id }
                    .subtract(removeCategories.toSet())
                    .plus(addCategories)
                    .toList()

                setMangaCategories.await(manga.id, categoryIds)
            }
        }
    }

    fun getColumnsPreferenceForCurrentOrientation(isLandscape: Boolean): PreferenceMutableState<Int> {
        return (if (isLandscape) libraryPreferences.landscapeColumns() else libraryPreferences.portraitColumns()).asState(coroutineScope)
    }

    suspend fun getRandomLibraryItemForCurrentCategory(): LibraryItem? {
        return withIOContext {
            state.value
                .getLibraryItemsByCategoryId(state.value.categories[activeCategoryIndex].id)
                ?.randomOrNull()
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = emptyList()) }
    }

    fun toggleSelection(manga: LibraryManga) {
        mutableState.update { state ->
            val newSelection = state.selection.toMutableList().apply {
                if (fastAny { it.id == manga.id }) {
                    removeAll { it.id == manga.id }
                } else {
                    add(manga)
                }
            }
            state.copy(selection = newSelection)
        }
    }

    /**
     * Selects all mangas between and including the given manga and the last pressed manga from the
     * same category as the given manga
     */
    fun toggleRangeSelection(manga: LibraryManga) {
        mutableState.update { state ->
            val newSelection = state.selection.toMutableList().apply {
                val lastSelected = lastOrNull()
                if (lastSelected?.category != manga.category) {
                    add(manga)
                    return@apply
                }

                val items = state.getLibraryItemsByCategoryId(manga.category)
                    ?.fastMap { it.libraryManga }.orEmpty()
                val lastMangaIndex = items.indexOf(lastSelected)
                val curMangaIndex = items.indexOf(manga)

                val selectedIds = fastMap { it.id }
                val selectionRange = when {
                    lastMangaIndex < curMangaIndex -> IntRange(lastMangaIndex, curMangaIndex)
                    curMangaIndex < lastMangaIndex -> IntRange(curMangaIndex, lastMangaIndex)
                    // We shouldn't reach this point
                    else -> return@apply
                }
                val newSelections = selectionRange.mapNotNull { index ->
                    items[index].takeUnless { it.id in selectedIds }
                }
                addAll(newSelections)
            }
            state.copy(selection = newSelection)
        }
    }

    fun selectAll(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.toMutableList().apply {
                val categoryId = state.categories.getOrNull(index)?.id ?: -1
                val selectedIds = fastMap { it.id }
                state.getLibraryItemsByCategoryId(categoryId)
                    ?.fastMapNotNull { item ->
                        item.libraryManga.takeUnless { it.id in selectedIds }
                    }
                    ?.let { addAll(it) }
            }
            state.copy(selection = newSelection)
        }
    }

    fun invertSelection(index: Int) {
        mutableState.update { state ->
            val newSelection = state.selection.toMutableList().apply {
                val categoryId = state.categories[index].id
                val items = state.getLibraryItemsByCategoryId(categoryId)?.fastMap { it.libraryManga }.orEmpty()
                val selectedIds = fastMap { it.id }
                val (toRemove, toAdd) = items.fastPartition { it.id in selectedIds }
                val toRemoveIds = toRemove.fastMap { it.id }
                removeAll { it.id in toRemoveIds }
                addAll(toAdd)
            }
            state.copy(selection = newSelection)
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun openChangeCategoryDialog() {
        coroutineScope.launchIO {
            // Create a copy of selected manga
            val mangaList = state.value.selection.map { it.manga }

            // Hide the default category because it has a different behavior than the ones from db.
            val categories = state.value.categories.filter { it.id != 0L }

            // Get indexes of the common categories to preselect.
            val common = getCommonCategories(mangaList)
            // Get indexes of the mix categories to preselect.
            val mix = getMixCategories(mangaList)
            val preselected = categories.map {
                when (it) {
                    in common -> CheckboxState.State.Checked(it)
                    in mix -> CheckboxState.TriState.Exclude(it)
                    else -> CheckboxState.State.None(it)
                }
            }
            mutableState.update { it.copy(dialog = Dialog.ChangeCategory(mangaList, preselected)) }
        }
    }

    fun openDeleteMangaDialog() {
        val mangaList = state.value.selection.map { it.manga }
        mutableState.update { it.copy(dialog = Dialog.DeleteManga(mangaList)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed class Dialog {
        data class ChangeCategory(val manga: List<Manga>, val initialSelection: List<CheckboxState<Category>>) : Dialog()
        data class DeleteManga(val manga: List<Manga>) : Dialog()
        data class DownloadCustomAmount(val manga: List<Manga>, val max: Int) : Dialog()
    }

    @Immutable
    private data class ItemPreferences(
        val downloadBadge: Boolean,
        val unreadBadge: Boolean,
        val localBadge: Boolean,
        val languageBadge: Boolean,

        val globalFilterDownloaded: Boolean,
        val filterDownloaded: Int,
        val filterUnread: Int,
        val filterStarted: Int,
        val filterBookmarked: Int,
        val filterCompleted: Int,
    )

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val library: LibraryMap = emptyMap(),
        val searchQuery: String? = null,
        val selection: List<LibraryManga> = emptyList(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = false,
        val showMangaCount: Boolean = false,
        val showMangaContinueButton: Boolean = false,
        val dialog: Dialog? = null,
    ) {
        private val libraryCount by lazy {
            library.values
                .flatten()
                .fastDistinctBy { it.libraryManga.manga.id }
                .size
        }

        val isLibraryEmpty by lazy { libraryCount == 0 }

        val selectionMode = selection.isNotEmpty()

        val categories = library.keys.toList()

        fun getLibraryItemsByCategoryId(categoryId: Long): List<LibraryItem>? {
            return library.firstNotNullOfOrNull { (k, v) -> v.takeIf { k.id == categoryId } }
        }

        fun getLibraryItemsByPage(page: Int): List<LibraryItem> {
            return library.values.toTypedArray().getOrNull(page) ?: emptyList()
        }

        fun getMangaCountForCategory(category: Category): Int? {
            return if (showMangaCount || !searchQuery.isNullOrEmpty()) library[category]?.size else null
        }

        fun getToolbarTitle(
            defaultTitle: String,
            defaultCategoryTitle: String,
            page: Int,
        ): LibraryToolbarTitle {
            val category = categories.getOrNull(page) ?: return LibraryToolbarTitle(defaultTitle)
            val categoryName = category.let {
                if (it.isSystemCategory) defaultCategoryTitle else it.name
            }
            val title = if (showCategoryTabs) defaultTitle else categoryName
            val count = when {
                !showMangaCount -> null
                !showCategoryTabs -> getMangaCountForCategory(category)
                // Whole library count
                else -> libraryCount
            }

            return LibraryToolbarTitle(title, count)
        }
    }
}
