package io.github.roviicc.colordict.design

import android.content.Context
import com.airbnb.android.showkase.models.Showkase

object ComponentCatalog {
    @JvmStatic fun isAvailable(): Boolean = true

    @JvmStatic fun open(context: Context) {
        context.startActivity(Showkase.getBrowserIntent(context))
    }
}
