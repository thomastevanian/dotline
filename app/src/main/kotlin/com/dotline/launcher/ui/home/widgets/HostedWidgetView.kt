package com.dotline.launcher.ui.home.widgets

import android.appwidget.AppWidgetHostView
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dotline.launcher.core.CrashLog
import com.dotline.launcher.data.model.HostedWidgetItem
import com.dotline.launcher.ui.LocalAppGraph
import com.dotline.launcher.ui.theme.DotlineTheme

private val HostedCorner = RoundedCornerShape(20.dp)

/**
 * A normal Android app widget on the home screen, drawn by the system's [AppWidgetHostView]. The
 * view is told its size in dp so the widget can pick the right layout. When the provider has been
 * uninstalled a flat placeholder is shown instead (the item can still be removed in edit mode).
 */
@Composable
fun HostedWidgetView(item: HostedWidgetItem, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val density = LocalDensity.current
    val info = remember(item.appWidgetId) { graph.widgetHost.info(item.appWidgetId) }
    if (info == null) {
        MissingWidget(modifier)
        return
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    AndroidView(
        modifier = modifier
            .clip(HostedCorner)
            .onSizeChanged { size = it },
        factory = { context ->
            val view: AppWidgetHostView = graph.widgetHost.createView(context, item.appWidgetId, info)
            view.setPadding(0, 0, 0, 0)
            view
        },
        update = { view ->
            if (size.width > 0 && size.height > 0) {
                val widthDp = (size.width / density.density).toInt()
                val heightDp = (size.height / density.density).toInt()
                try {
                    view.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
                } catch (e: Exception) {
                    CrashLog.record("hosted widget: size", e)
                }
            }
        },
    )
}

@Composable
private fun MissingWidget(modifier: Modifier) {
    val colors = DotlineTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(HostedCorner)
            .background(colors.widget),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Widget not available",
            modifier = Modifier.padding(12.dp),
            style = DotlineTheme.type.caption.copy(color = colors.secondary, textAlign = TextAlign.Center),
        )
    }
}
