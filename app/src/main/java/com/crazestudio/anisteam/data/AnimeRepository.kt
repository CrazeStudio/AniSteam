package com.crazestudio.anisteam.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class Anime(val id: Int, val title: String, val imageUrl: String, val episodes: Int?, val score: Double?)

class AnimeRepository {
    private val client = OkHttpClient()
    private val endpoint = "https://graphql.anilist.co"

    suspend fun trending(): List<Anime> = request("""
        query {
          Page(perPage: 24) {
            media(type: ANIME, sort: TRENDING_DESC) {
              id title { userPreferred } coverImage { large } episodes averageScore
            }
          }
        }
    """.trimIndent())

    suspend fun search(term: String): List<Anime> = request("""
        query Search(${' {
          Page(perPage: 24) {
            media(search: ${' {
              id title { userPreferred } coverImage { large } episodes averageScore
            }
          }
        }
    """.trimIndent(), JSONObject().put("search", term))

    private suspend fun request(query: String, variables: JSONObject = JSONObject()): List<Anime> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("query", query).put("variables", variables)
                .toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(endpoint).post(body)
                .header("Accept", "application/json").build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) error("AniList request failed: " + response.code)
                val media = JSONObject(response.body?.string().orEmpty())
                    .getJSONObject("data").getJSONObject("Page").getJSONArray("media")
                buildList {
                    for (i in 0 until media.length()) {
                        val item = media.getJSONObject(i)
                        val title = item.getJSONObject("title").optString("userPreferred")
                        val image = item.getJSONObject("coverImage").optString("large")
                        if (title.isNotBlank() && image.isNotBlank()) {
                            add(Anime(
                                item.optInt("id"), title, image,
                                if (item.isNull("episodes")) null else item.optInt("episodes"),
                                if (item.isNull("averageScore")) null else item.optDouble("averageScore") / 10.0
                            ))
                        }
                    }
                }
            }
        }
}
 }search: String) {
          Page(perPage: 24) {
            media(search: $search, type: ANIME, sort: POPULARITY_DESC) {
              id title { userPreferred } coverImage { large } episodes averageScore
            }
          }
        }
    """.trimIndent(), JSONObject().put("search", term))

    private suspend fun request(query: String, variables: JSONObject = JSONObject()): List<Anime> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("query", query).put("variables", variables)
                .toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(endpoint).post(body)
                .header("Accept", "application/json").build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) error("AniList request failed: " + response.code)
                val media = JSONObject(response.body?.string().orEmpty())
                    .getJSONObject("data").getJSONObject("Page").getJSONArray("media")
                buildList {
                    for (i in 0 until media.length()) {
                        val item = media.getJSONObject(i)
                        val title = item.getJSONObject("title").optString("userPreferred")
                        val image = item.getJSONObject("coverImage").optString("large")
                        if (title.isNotBlank() && image.isNotBlank()) {
                            add(Anime(
                                item.optInt("id"), title, image,
                                if (item.isNull("episodes")) null else item.optInt("episodes"),
                                if (item.isNull("averageScore")) null else item.optDouble("averageScore") / 10.0
                            ))
                        }
                    }
                }
            }
        }
}
 }search, type: ANIME, sort: POPULARITY_DESC) {
              id title { userPreferred } coverImage { large } episodes averageScore
            }
          }
        }
    """.trimIndent(), JSONObject().put("search", term))

    private suspend fun request(query: String, variables: JSONObject = JSONObject()): List<Anime> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("query", query).put("variables", variables)
                .toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(endpoint).post(body)
                .header("Accept", "application/json").build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) error("AniList request failed: " + response.code)
                val media = JSONObject(response.body?.string().orEmpty())
                    .getJSONObject("data").getJSONObject("Page").getJSONArray("media")
                buildList {
                    for (i in 0 until media.length()) {
                        val item = media.getJSONObject(i)
                        val title = item.getJSONObject("title").optString("userPreferred")
                        val image = item.getJSONObject("coverImage").optString("large")
                        if (title.isNotBlank() && image.isNotBlank()) {
                            add(Anime(
                                item.optInt("id"), title, image,
                                if (item.isNull("episodes")) null else item.optInt("episodes"),
                                if (item.isNull("averageScore")) null else item.optDouble("averageScore") / 10.0
                            ))
                        }
                    }
                }
            }
        }
}
 }search: String) {
          Page(perPage: 24) {
            media(search: $search, type: ANIME, sort: POPULARITY_DESC) {
              id title { userPreferred } coverImage { large } episodes averageScore
            }
          }
        }
    """.trimIndent(), JSONObject().put("search", term))

    private suspend fun request(query: String, variables: JSONObject = JSONObject()): List<Anime> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("query", query).put("variables", variables)
                .toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(endpoint).post(body)
                .header("Accept", "application/json").build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) error("AniList request failed: " + response.code)
                val media = JSONObject(response.body?.string().orEmpty())
                    .getJSONObject("data").getJSONObject("Page").getJSONArray("media")
                buildList {
                    for (i in 0 until media.length()) {
                        val item = media.getJSONObject(i)
                        val title = item.getJSONObject("title").optString("userPreferred")
                        val image = item.getJSONObject("coverImage").optString("large")
                        if (title.isNotBlank() && image.isNotBlank()) {
                            add(Anime(
                                item.optInt("id"), title, image,
                                if (item.isNull("episodes")) null else item.optInt("episodes"),
                                if (item.isNull("averageScore")) null else item.optDouble("averageScore") / 10.0
                            ))
                        }
                    }
                }
            }
        }
}
