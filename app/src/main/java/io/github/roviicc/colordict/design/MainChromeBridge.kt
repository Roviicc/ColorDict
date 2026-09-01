package io.github.roviicc.colordict.design

import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.github.roviicc.colordict.design.components.PopupMainChrome

class MainChromeController internal constructor(
    private val queryState: androidx.compose.runtime.MutableState<String>,
    private val recentState: androidx.compose.runtime.MutableState<Boolean>,
    private val focusRequester: FocusRequester,
) {
    fun query(): String = queryState.value

    fun setQuery(query: String) {
        queryState.value = query
    }

    fun setRecentVisible(visible: Boolean) {
        recentState.value = visible
    }

    fun requestSearchFocus() {
        focusRequester.requestFocus()
    }
}

object MainChromeBridge {
    interface Callbacks {
        fun onQueryChanged(query: String)
        fun onSearch(query: String)
        fun onMenu(anchor: View)
    }

    @JvmStatic
    fun attach(
        view: ComposeView,
        initialQuery: String,
        callbacks: Callbacks,
    ): MainChromeController {
        val query = mutableStateOf(initialQuery)
        val showRecent = mutableStateOf(false)
        val focusRequester = FocusRequester()
        val controller = MainChromeController(query, showRecent, focusRequester)

        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            PopupDictionaryTheme {
                PopupMainChrome(
                    query = query.value,
                    showRecentLabel = showRecent.value,
                    focusRequester = focusRequester,
                    onQueryChange = {
                        query.value = it
                        callbacks.onQueryChanged(it.trim())
                    },
                    onSubmit = { callbacks.onSearch(query.value.trim()) },
                    onMenuClick = { callbacks.onMenu(view) },
                )
            }
        }
        return controller
    }
}
