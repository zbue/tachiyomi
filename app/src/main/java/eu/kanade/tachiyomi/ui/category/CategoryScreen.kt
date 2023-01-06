package eu.kanade.tachiyomi.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.CategoryScreen
import eu.kanade.presentation.category.components.CategoryCreateDialog
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.category.components.CategoryRenameDialog
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest

class CategoryScreen : Screen {

    override val key = uniqueScreenKey

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { CategoryScreenModel() }

        val state by screenModel.state.collectAsState()

        if (state is CategoryScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as CategoryScreenState.Success

        CategoryScreen(
            state = successState,
            onClickCreate = screenModel::showCreateCategoryDialog,
            onClickRename = screenModel::showRenameCategoryDialog,
            onClickDelete = screenModel::showDeleteCategoryDialog,
            onClickMoveUp = screenModel::moveUp,
            onClickMoveDown = screenModel::moveDown,
            navigateUp = navigator::pop,
        )

        val onDismissRequest = { screenModel.dismissDialog() }
        when (val dialog = successState.dialog) {
            null -> {}
            CategoryScreenModel.Dialog.CreateCategory -> {
                CategoryCreateDialog(
                    onDismissRequest = onDismissRequest,
                    onCreate = screenModel::createCategory,
                    categories = successState.categories,
                )
            }
            is CategoryScreenModel.Dialog.RenameCategory -> {
                CategoryRenameDialog(
                    onDismissRequest = onDismissRequest,
                    onRename = { screenModel.renameCategory(dialog.categoryToRename, it) },
                    category = dialog.categoryToRename,
                )
            }
            is CategoryScreenModel.Dialog.DeleteCategory -> {
                CategoryDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = { screenModel.deleteCategory(dialog.categoryToDelete.id) },
                    category = dialog.categoryToDelete,
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is CategoryScreenModel.CategoryEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
