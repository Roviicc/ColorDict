package io.github.roviicc.colordict.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Preview
import io.github.roviicc.colordict.design.components.DictionaryColorLegend
import io.github.roviicc.colordict.design.components.PopupBrowseItem
import io.github.roviicc.colordict.design.components.PopupBrowseRow
import io.github.roviicc.colordict.design.components.PopupIconButton
import io.github.roviicc.colordict.design.components.PopupMainChrome
import io.github.roviicc.colordict.design.components.PopupSectionHeader
import io.github.roviicc.colordict.design.components.PopupSearchField

@Preview(name = "Main search", group = "Screens", widthDp = 390, heightDp = 844)
@Composable
fun MainSearchPreview() {
    PopupDictionaryTheme {
        PopupMainChrome(
            query = "",
            showRecentLabel = true,
            focusRequester = FocusRequester(),
            onQueryChange = {},
            onSubmit = {},
            onMenuClick = {},
        )
    }
}

@Preview(name = "Search empty", group = "Inputs", widthDp = 390)
@Composable
fun SearchEmptyPreview() {
    PopupDictionaryTheme {
        Column(Modifier.padding(PopupSpacing.md)) {
            PopupSearchField("", {}, {}, FocusRequester())
        }
    }
}

@Preview(name = "Search populated", group = "Inputs", widthDp = 390)
@Composable
fun SearchPopulatedPreview() {
    PopupDictionaryTheme {
        Column(Modifier.padding(PopupSpacing.md)) {
            PopupSearchField("humble", {}, {}, FocusRequester())
        }
    }
}

@Preview(name = "Recent searches", group = "Labels", widthDp = 390)
@Composable
fun SectionHeaderPreview() {
    PopupDictionaryTheme { PopupSectionHeader("Recent searches") }
}

@Preview(name = "Dictionary colors", group = "Indicators")
@Composable
fun DictionaryColorsPreview() {
    PopupDictionaryTheme { DictionaryColorLegend(Modifier.padding(PopupSpacing.md)) }
}

@Preview(name = "History row", group = "Rows", widthDp = 390)
@Composable
fun HistoryRowPreview() {
    PopupDictionaryTheme {
        PopupBrowseRow(PopupBrowseItem("humble", isHistory = true), onClick = {})
    }
}
