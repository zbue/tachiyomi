package eu.kanade.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.kanade.tachiyomi.R

@Composable
fun ConfirmActionDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    dialogText: String,
    confirmText: String,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = confirmText)
            }
        },
        title = {
            Text(text = stringResource(R.string.are_you_sure))
        },
        text = {
            Text(text = dialogText)
        },
    )
}
