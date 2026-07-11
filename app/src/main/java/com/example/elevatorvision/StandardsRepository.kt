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

data class LawItem(
    val id: String,
    val effectiveDate: String?,
    val content: String
)

object StandardsRepository {

    private var itemsByClass: Map<String, List<StandardItem>> = emptyMap()
    private var lawByClass: Map<String, List<LawItem>> = emptyMap()
    private var isLoaded = false

    fun load(context: Context) {
        if (isLoaded) return

        itemsByClass = loadStandards(context)
        lawByClass = loadLaw(context)
        isLoaded = true
    }

    private fun loadStandards(context: Context): Map<String, List<StandardItem>> {
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
        return result
    }

    private fun loadLaw(context: Context): Map<String, List<LawItem>> {
        val json = context.assets.open("law_criteria.json")
            .bufferedReader()
            .use { it.readText() }

        val root = JSONObject(json)
        val result = mutableMapOf<String, List<LawItem>>()

        root.keys().forEach { className ->
            val arr = root.getJSONArray(className)
            val list = mutableListOf<LawItem>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val effectiveDate = if (obj.isNull("effective_date")) null else obj.optString("effective_date")
                list.add(
                    LawItem(
                        id = "$className-$i",
                        effectiveDate = effectiveDate,
                        content = obj.optString("content")
                    )
                )
            }
            result[className] = list
        }
        return result
    }

    fun getByClassName(className: String): List<StandardItem> {
        return itemsByClass[className] ?: emptyList()
    }

    fun getLawByClassName(className: String): List<LawItem> {
        return lawByClass[className] ?: emptyList()
    }
}