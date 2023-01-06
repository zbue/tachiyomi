package eu.kanade.tachiyomi.ui.category

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import eu.kanade.domain.category.interactor.CreateCategoryWithName
import eu.kanade.domain.category.interactor.DeleteCategory
import eu.kanade.domain.category.interactor.GetCategories
import eu.kanade.domain.category.interactor.RenameCategory
import eu.kanade.domain.category.interactor.ReorderCategory
import eu.kanade.domain.category.model.Category
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoryScreenModel(
    private val getCategories: GetCategories = Injekt.get(),
    private val createCategoryWithName: CreateCategoryWithName = Injekt.get(),
    private val deleteCategory: DeleteCategory = Injekt.get(),
    private val reorderCategory: ReorderCategory = Injekt.get(),
    private val renameCategory: RenameCategory = Injekt.get(),
) : StateScreenModel<CategoryScreenState>(CategoryScreenState.Loading) {

    private val _events: Channel<CategoryEvent> = Channel()
    val events = _events.consumeAsFlow()

    init {
        coroutineScope.launch {
            getCategories.subscribe()
                .collectLatest { categories ->
                    mutableState.update {
                        CategoryScreenState.Success(
                            categories = categories.filterNot(Category::isSystemCategory),
                        )
                    }
                }
        }
    }

    fun createCategory(name: String) {
        coroutineScope.launch {
            when (createCategoryWithName.await(name)) {
                is CreateCategoryWithName.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        coroutineScope.launch {
            when (deleteCategory.await(categoryId = categoryId)) {
                is DeleteCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun moveUp(category: Category) {
        coroutineScope.launch {
            when (reorderCategory.moveUp(category)) {
                is ReorderCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun moveDown(category: Category) {
        coroutineScope.launch {
            when (reorderCategory.moveDown(category)) {
                is ReorderCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        coroutineScope.launch {
            when (renameCategory.await(category, name)) {
                is RenameCategory.Result.InternalError -> _events.send(CategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                CategoryScreenState.Loading -> it
                is CategoryScreenState.Success -> it.copy(dialog = null)
            }
        }
    }

    fun showCreateCategoryDialog() {
        mutableState.update { state ->
            when (state) {
                CategoryScreenState.Loading -> state
                is CategoryScreenState.Success -> state.copy(dialog = Dialog.CreateCategory)
            }
        }
    }

    fun showRenameCategoryDialog(categoryToRename: Category) {
        mutableState.update { state ->
            when (state) {
                CategoryScreenState.Loading -> state
                is CategoryScreenState.Success -> state.copy(dialog = Dialog.RenameCategory(categoryToRename))
            }
        }
    }

    fun showDeleteCategoryDialog(categoryToDelete: Category) {
        mutableState.update { state ->
            when (state) {
                CategoryScreenState.Loading -> state
                is CategoryScreenState.Success -> state.copy(dialog = Dialog.DeleteCategory(categoryToDelete))
            }
        }
    }

    sealed class Dialog {
        object CreateCategory : Dialog()
        data class RenameCategory(val categoryToRename: Category) : Dialog()
        data class DeleteCategory(val categoryToDelete: Category) : Dialog()
    }

    sealed class CategoryEvent {
        sealed class LocalizedMessage(@StringRes val stringRes: Int) : CategoryEvent()
        object InternalError : LocalizedMessage(R.string.internal_error)
    }
}

sealed class CategoryScreenState {

    @Immutable
    object Loading : CategoryScreenState()

    @Immutable
    data class Success(
        val categories: List<Category>,
        val dialog: CategoryScreenModel.Dialog? = null,
    ) : CategoryScreenState() {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
