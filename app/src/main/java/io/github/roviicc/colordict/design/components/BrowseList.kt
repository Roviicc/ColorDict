package io.github.roviicc.colordict.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.github.roviicc.colordict.design.PopupAzure500
import io.github.roviicc.colordict.design.PopupDivider
import io.github.roviicc.colordict.design.PopupNavy950
import io.github.roviicc.colordict.design.PopupSpacing

data class PopupBrowseItem(
    val word: String,
    val isHistory: Boolean,
    val dictionaryColors: List<Color> = emptyList(),
)

@Composable
fun PopupBrowseList(
    rows: List<PopupBrowseItem>,
    onRowClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        items(rows, key = { "${it.isHistory}:${it.word}" }) { row ->
            PopupBrowseRow(row = row, onClick = { onRowClick(row.word) })
        }
    }
}

@Composable
fun PopupBrowseRow(
    row: PopupBrowseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PopupSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.isHistory) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = PopupNavy950,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = row.word,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(start = if (row.isHistory) PopupSpacing.md else 0.dp)
                    .weight(1f),
            )
            if (row.isHistory) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = PopupNavy950,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(PopupSpacing.xxs)) {
                    row.dictionaryColors.forEach { color ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(color, androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(PopupDivider))
    }
}
