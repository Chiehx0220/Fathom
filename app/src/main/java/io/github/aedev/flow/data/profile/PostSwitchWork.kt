package io.github.aedev.flow.data.profile

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.widget.core.FlowWidgets
import io.github.aedev.flow.widget.core.refresh.WidgetContentSync
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the first process after a switch redoes: the home screen kept drawing the previous profile's widgets. It runs
 * from the Application, since the system may start this process for a service before any screen.
 */
@Singleton
class PostSwitchWork
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val widgetContentSync: WidgetContentSync,
    ) {
        suspend fun runIfSwitched() {
            if (!ActiveProfile.registry(context).finishPostSwitch()) return
            widgetContentSync.redrawAfterSwitch()
            FlowWidgets.updateAll(context, except = widgetContentSync.widgets)
        }
    }
