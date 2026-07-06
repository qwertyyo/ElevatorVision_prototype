package com.example.elevatorvision

import android.content.Context
import org.json.JSONObject

data class StandardItem(
    val id: String,
    val year: Int?,
    val round: Int?,
    val title: String,
    val standardization: String,
    val pages: List<Int>
)

object StandardsRepository {

    private var itemsByClass: Map<String, List<StandardItem>> = emptyMap()
    private var isLoaded = false

    fun load(context: Context) {
        if (isLoaded) return

        val json = context.assets.open("standards.json")
            .bufferedReader()
            .use { it.readText() }

        val root = JSONObject(json)
        val result = mutableMapOf<String, List<StandardItem>>()

        root.keys().forEach { className ->
            val arr = root.getJSONArray(className)
            val list = mutableListOf<StandardItem>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pagesArr = obj.optJSONArray("pages")
                val pages = buildList {
                    if (pagesArr != null) {
                        for (p in 0 until pagesArr.length()) add(pagesArr.getInt(p))
                    }
                }

                list.add(
                    StandardItem(
                        id = obj.optString("id"),
                        year = if (obj.isNull("year")) null else obj.optInt("year"),
                        round = if (obj.isNull("round")) null else obj.optInt("round"),
                        title = obj.optString("title"),
                        standardization = obj.optString("표준화"),
                        pages = pages
                    )
                )
            }
            result[className] = list
        }

        itemsByClass = result
        isLoaded = true
    }

    fun getByClassName(className: String): List<StandardItem> {
        return itemsByClass[className] ?: emptyList()
    }
}