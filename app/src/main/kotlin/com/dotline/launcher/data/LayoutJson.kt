package com.dotline.launcher.data

import com.dotline.launcher.data.model.AppItem
import com.dotline.launcher.data.model.AppKey
import com.dotline.launcher.data.model.BuiltinWidget
import com.dotline.launcher.data.model.FolderItem
import com.dotline.launcher.data.model.HomeItem
import com.dotline.launcher.data.model.HomeLayout
import com.dotline.launcher.data.model.HostedWidgetItem
import com.dotline.launcher.data.model.Placement
import com.dotline.launcher.data.model.WidgetItem
import org.json.JSONArray
import org.json.JSONObject

/** JSON form of a [HomeLayout], used for the on-disk layout file and for backup/restore. */
object LayoutJson {
    private const val VERSION = 1

    fun encode(layout: HomeLayout): JSONObject = JSONObject().apply {
        put("v", VERSION)
        put("pages", JSONArray().also { pages -> layout.pages.forEach { page -> pages.put(encodeItems(page)) } })
        put("dock", encodeItems(layout.dock))
    }

    fun decode(o: JSONObject): HomeLayout {
        val pagesJson = o.optJSONArray("pages")
        val pages = ArrayList<List<HomeItem>>()
        if (pagesJson != null) {
            for (i in 0 until pagesJson.length()) {
                pages += decodeItems(pagesJson.optJSONArray(i))
            }
        }
        if (pages.isEmpty()) pages += emptyList<HomeItem>()
        return HomeLayout(pages = pages.take(HomeLayout.MAX_PAGES), dock = decodeItems(o.optJSONArray("dock")))
    }

    fun decode(text: String): HomeLayout = decode(JSONObject(text))

    private fun encodeItems(items: List<HomeItem>): JSONArray = JSONArray().also { arr ->
        items.forEach { item -> arr.put(encodeItem(item)) }
    }

    private fun encodeItem(item: HomeItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("c", item.placement.col)
        put("r", item.placement.row)
        put("w", item.placement.spanX)
        put("h", item.placement.spanY)
        when (item) {
            is AppItem -> {
                put("t", "app")
                put("app", item.app.flat)
            }
            is FolderItem -> {
                put("t", "folder")
                put("name", item.name)
                put("apps", JSONArray().also { a -> item.apps.forEach { a.put(it.flat) } })
            }
            is WidgetItem -> {
                put("t", "widget")
                put("kind", item.kind.name)
                put("config", item.config)
            }
            is HostedWidgetItem -> {
                put("t", "hosted")
                put("awid", item.appWidgetId)
                put("provider", item.provider)
            }
        }
    }

    private fun decodeItems(arr: JSONArray?): List<HomeItem> {
        if (arr == null) return emptyList()
        val out = ArrayList<HomeItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val item = runCatching { decodeItem(o) }.getOrNull()
            if (item != null) out += item
        }
        return out
    }

    private fun decodeItem(o: JSONObject): HomeItem? {
        val id = o.optString("id", "")
        if (id.isEmpty()) return null
        val p = Placement(
            col = o.optInt("c", 0),
            row = o.optInt("r", 0),
            spanX = o.optInt("w", 1).coerceAtLeast(1),
            spanY = o.optInt("h", 1).coerceAtLeast(1),
        )
        return when (o.optString("t")) {
            "app" -> AppKey.parse(o.optString("app", ""))?.let { AppItem(id, it, p) }
            "folder" -> {
                val apps = o.optJSONArray("apps")
                val keys = ArrayList<AppKey>()
                if (apps != null) for (i in 0 until apps.length()) AppKey.parse(apps.optString(i, ""))?.let { keys += it }
                FolderItem(id, o.optString("name", "Folder"), keys, p.copy(spanX = 1, spanY = 1))
            }
            "widget" -> {
                val kind = BuiltinWidget.entries.firstOrNull { it.name == o.optString("kind") } ?: return null
                WidgetItem(id, kind, p, o.optString("config", ""))
            }
            "hosted" -> HostedWidgetItem(id, o.optInt("awid", -1), o.optString("provider", ""), p)
            else -> null
        }
    }
}
