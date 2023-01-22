package eu.kanade.presentation.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.util.padding

@Composable
fun TabIndicator(currentTabPosition: TabPosition) {
    TabRowDefaults.Indicator(
        Modifier
            .tabIndicatorOffset(currentTabPosition)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
    )
}

@Composable
fun TabCustomIndicator(currentTabPosition: TabPosition) {
    TabRowDefaults.Indicator(
        modifier = Modifier
            .tabIndicatorOffset(currentTabPosition)
            .fillMaxHeight()
            .padding(MaterialTheme.padding.small)
            .clip(CircleShape),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    )
}

@Composable
fun TabText(
    text: String,
    badgeCount: Int? = null,
    selected: Boolean = false,
) {
    val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f

    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text)
        if (badgeCount != null && badgeCount != 0) {
            Pill(
                text = "$badgeCount",
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = pillAlpha),
                contentColor = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                fontSize = 12.sp,
            )
        }
    }
}
