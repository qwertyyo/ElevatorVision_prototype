package com.example.elevatorvision.kijun

import android.content.Context
import org.json.JSONObject

// 근거 조항 하나 (별표22, 연혁집 현행 조문 번호 + 제목)
data class KijunClause(
    val num: String,
    val title: String
)

// YOLO 클래스 하나를 검사기준 통합검색에 연결하기 위한 정보.
// assets/kijun_class_map.json 에서 읽는다 (yolo/make_kijun_map.py 가 yolo/ 엑셀로 만든다).
data class KijunClassEntry(
    val id: Int,
    val key: String,
    val nameKo: String,
    val location: String,
    val query: String,          // 통합검색에 넣을 대표 검색어
    val aliases: List<String>,
    val clauses: List<KijunClause>,
    val deleted: Boolean = false   // 번호만 남긴 삭제·통합 클래스 (인식·검색 목록에서 뺀다)
)

object KijunClassMap {

    private var byKey: Map<String, KijunClassEntry> = emptyMap()
    // 예전 모델(5개 클래스)·저장된 검사 기록의 클래스 이름 → 새 클래스 키
    private var legacy: Map<String, String> = emptyMap()
    private var isLoaded = false

    @Synchronized
    fun load(context: Context) {
        if (isLoaded) return
        val root = JSONObject(
            context.assets.open("kijun_class_map.json").bufferedReader().use { it.readText() }
        )

        val classes = root.getJSONArray("classes")
        val map = mutableMapOf<String, KijunClassEntry>()
        for (i in 0 until classes.length()) {
            val o = classes.getJSONObject(i)
            val aliasArr = o.optJSONArray("aliases")
            val clauseArr = o.optJSONArray("clauses")
            val entry = KijunClassEntry(
                id = o.getInt("id"),
                key = o.getString("key"),
                nameKo = o.getString("name_ko"),
                location = o.optString("location"),
                query = o.getString("query"),
                aliases = buildList {
                    if (aliasArr != null) for (a in 0 until aliasArr.length()) add(aliasArr.getString(a))
                },
                clauses = buildList {
                    if (clauseArr != null) for (c in 0 until clauseArr.length()) {
                        val co = clauseArr.getJSONObject(c)
                        add(KijunClause(co.getString("num"), co.optString("title")))
                    }
                },
                deleted = o.optBoolean("deleted")
            )
            map[entry.key] = entry
        }
        byKey = map

        val legacyObj = root.optJSONObject("legacy")
        legacy = buildMap {
            legacyObj?.keys()?.forEach { k -> put(k, legacyObj.getString(k)) }
        }
        isLoaded = true
    }

    /** 모델이 돌려준 클래스 이름(새 키 또는 예전 이름)으로 연결 정보를 찾는다. */
    // 통합된 클래스(예: 45 현수 벨트 → 3 주로프)는 legacy 쪽 대상을 먼저 본다.
    fun find(className: String): KijunClassEntry? =
        legacy[className]?.let { byKey[it] } ?: byKey[className]

    /** 검색 화면에 보여 줄 클래스 전부 (번호만 남긴 삭제·통합 클래스는 뺀다). */
    fun all(): List<KijunClassEntry> = byKey.values.filter { !it.deleted }.sortedBy { it.id }

    /** 인식 결과에서 버릴 클래스 번호 (번호만 남긴 삭제·통합 클래스). */
    fun deletedIds(): Set<Int> = byKey.values.filter { it.deleted }.map { it.id }.toSet()

    /** 인식 박스·칩에 쓸 짧은 이름: "권상기(구동기)" → "권상기". 모르는 클래스면 그대로. */
    fun shortName(className: String): String =
        find(className)?.nameKo?.substringBefore('(')?.trim()?.ifEmpty { null } ?: className

    /** 목록·시트 제목에 쓸 전체 이름. */
    fun fullName(className: String): String = find(className)?.nameKo ?: className
}
