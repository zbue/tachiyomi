package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.repository.ChapterRepository
import eu.kanade.domain.download.interactor.DeleteDownload
import eu.kanade.domain.download.service.DownloadPreferences
import eu.kanade.domain.manga.model.Manga
import eu.kanade.domain.manga.repository.MangaRepository
import eu.kanade.tachiyomi.util.lang.withNonCancellableContext
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority

class SetReadStatus(
    private val downloadPreferences: DownloadPreferences,
    private val deleteDownload: DeleteDownload,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
) {

    private val mapper = { chapter: Chapter, read: Boolean, lastPageRead: Long ->
        ChapterUpdate(
            read = read,
            lastPageRead = lastPageRead,
            id = chapter.id,
        )
    }

    suspend fun awaitAll(read: Boolean, lastPageRead: Long? = null, isFinalized: Boolean = true, vararg chapters: Chapter): Result = withNonCancellableContext {
        val initialChapters = chapters.map {
            it.copy(
                read = when (read) {
                    true -> it.read
                    false -> !it.read
                },
                lastPageRead = when (lastPageRead) {
                    null -> 0
                    else -> it.lastPageRead
                },
            )
        }

        val originalChapters = chapters.filter {
            when (read) {
                true -> !it.read
                false -> it.read || it.lastPageRead > 0
            }
        }

        val chaptersToUpdate = if (isFinalized) initialChapters else originalChapters

        if (chaptersToUpdate.isEmpty()) {
            return@withNonCancellableContext Result.NoChapters
        }

        try {
            chapterRepository.updateAll(
                chaptersToUpdate.map { mapper(it, read, lastPageRead ?: 0) },
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        if (read && isFinalized && downloadPreferences.removeAfterMarkedAsRead().get()) {
            chaptersToUpdate
                .groupBy { it.mangaId }
                .forEach { (mangaId, chapters) ->
                    deleteDownload.awaitAll(
                        manga = mangaRepository.getMangaById(mangaId),
                        chapters = chapters.toTypedArray(),
                    )
                }
        }

        Result.Success
    }

    suspend fun await(read: Boolean, lastPageRead: Long, showSnackbar: Boolean, vararg chapters: Chapter): Result = withNonCancellableContext {
        awaitAll(
            read = read,
            lastPageRead = lastPageRead,
            isFinalized = !showSnackbar,
            chapters = chapters,
        )
    }

    private suspend fun await(mangaId: Long, read: Boolean): Result = withNonCancellableContext {
        awaitAll(
            read = read,
            isFinalized = true,
            chapters = chapterRepository
                .getChapterByMangaId(mangaId)
                .toTypedArray(),
        )
    }

    suspend fun await(manga: Manga, read: Boolean) =
        await(manga.id, read)

    sealed class Result {
        object Success : Result()
        object NoChapters : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
