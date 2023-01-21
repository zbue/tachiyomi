package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.padding
import eu.kanade.tachiyomi.R

@Composable
fun ChapterHeader(
    enabled: Boolean,
    hasActiveFilters: Boolean,
    chapterCount: Int,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    onClick = onClick,
                )
                .padding(horizontal = MaterialTheme.padding.medium, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val textRes = if (hasActiveFilters) R.string.error_no_match else R.string.no_chapters_error

            Text(
                text = if (chapterCount == 0) {
                    stringResource(textRes)
                } else {
                    pluralStringResource(id = R.plurals.manga_num_chapters, count = chapterCount, chapterCount)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
            )

            BadgedBox(badge = { if (hasActiveFilters) Badge() }) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = stringResource(R.string.action_filter),
                )
            }
        }
    }
}
