package io.github.roviicc.colordict.design

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.github.roviicc.colordict.data.DictRepository
import io.github.roviicc.colordict.design.components.PopupBrowseItem
import io.github.roviicc.colordict.design.components.PopupBrowseList

class BrowseListController internal constructor(
    private val rows: androidx.compose.runtime.MutableState<List<PopupBrowseItem>>,
    private val visible: androidx.compose.runtime.MutableState<Boolean>,
) {
    private var historyMode = false

    fun setHistory(words: List<String>) {
        historyMode = true
        rows.value = words.map { PopupBrowseItem(word = it, isHistory = true) }
    }

    fun setSuggestions(suggestions: List<DictRepository.Suggestion>) {
        historyMode = false
        rows.value = suggestions.map { suggestion ->
            PopupBrowseItem(
                word = suggestion.word,
                isHistory = false,
                dictionaryColors = suggestion.colors.map(::Color),
            )
        }
    }

    fun count(): Int = rows.value.size
    fun isHistoryMode(): Boolean = historyMode
    fun setVisible(isVisible: Boolean) { visible.value = isVisible }
}

object BrowseListBridge {
    fun interface Callbacks {
        fun onWordClick(word: String)
    }

    @JvmStatic
    fun attach(view: ComposeView, callbacks: Callbacks): BrowseListController {
        val rows = mutableStateOf<List<PopupBrowseItem>>(emptyList())
        val visible = mutableStateOf(true)
        val controller = BrowseListController(rows, visible)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            PopupDictionaryTheme {
                if (visible.value) {
                    PopupBrowseList(rows.value, onRowClick = callbacks::onWordClick)
                }
            }
        }
        return controller
    }
}
