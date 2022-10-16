package eu.kanade.presentation.manga.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R

@Composable
fun DeleteChaptersDialog(
    checked: Boolean,
    showCheckbox: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    var allow by remember { mutableStateOf(checked) }

    AlertDialog(
        title = {
            Text(text = stringResource(R.string.are_you_sure))
        },
        text = {
            Column {
                Text(text = stringResource(R.string.confirm_delete_chapters))
                if (showCheckbox) {
                    Row(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .toggleable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                value = allow,
                                onValueChange = { allow = it },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = allow,
                            onCheckedChange = null,
                        )
                        Text(
                            modifier = Modifier.padding(start = 4.dp),
                            text = stringResource(R.string.include_bookmarked_chapters),
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm(allow)
                },
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}
