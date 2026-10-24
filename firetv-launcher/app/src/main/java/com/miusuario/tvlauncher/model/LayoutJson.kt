package com.miusuario.tvlauncher.model

import org.json.JSONArray
import org.json.JSONObject

/** Guarda [LayoutState] como JSON: orden de la rejilla, carpetas (id, nombre, paquetes en orden) y ocultas. */
object LayoutJson {
    fun encode(state: LayoutState): String {
        val grid = JSONArray()
        state.entries.forEach { entry ->
            when (entry) {
                is AppEntry -> grid.put(JSONObject().put("app", entry.pkg))
                is FolderEntry -> grid.put(
                    JSONObject()
                        .put("folder", entry.folderId)
                        .put("name", entry.name)
                        .put("apps", JSONArray(entry.apps)),
                )
            }
        }
        return JSONObject()
            .put("version", 1)
            .put("grid", grid)
            .put("hidden", JSONArray(state.hidden.sorted()))
            .put("nextFolderId", state.nextFolderId)
            .toString()
    }

    /** Lee el JSON guardado. Si está vacío o dañado devuelve un estado vacío (todas las apps vuelven en orden alfabético). */
    fun decode(json: String?): LayoutState {
        if (json.isNullOrBlank()) return LayoutState()
        return try {
            val root = JSONObject(json)
            val grid = root.optJSONArray("grid") ?: JSONArray()
            val seen = HashSet<String>()
            val entries = ArrayList<GridEntry>()
            for (i in 0 until grid.length()) {
                val o = grid.getJSONObject(i)
                if (o.has("app")) {
                    val pkg = o.getString("app")
                    if (seen.add(pkg)) entries += AppEntry(pkg)
                } else if (o.has("folder")) {
                    val arr = o.optJSONArray("apps") ?: JSONArray()
                    val apps = (0 until arr.length()).map { arr.getString(it) }.filter { seen.add(it) }
                    entries += FolderEntry(o.getString("folder"), o.optString("name", "Carpeta"), apps)
                }
            }
            val hiddenArr = root.optJSONArray("hidden") ?: JSONArray()
            val hidden = (0 until hiddenArr.length()).map { hiddenArr.getString(it) }.filter { it !in seen }.toSet()
            LayoutState(entries, hidden, root.optInt("nextFolderId", entries.size + 1))
        } catch (e: org.json.JSONException) {
            LayoutState()
        }
    }
}
