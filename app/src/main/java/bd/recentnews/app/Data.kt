package bd.recentnews.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

data class Post(
    val id: String, val title: String, val url: String, val image: String?,
    val label: String?, val published: String, val html: String,
)

val CATEGORIES = listOf(
    "National", "International", "Business", "Sports", "Arts & Entertainment",
    "Science & Technology", "Education News", "Health News", "Law and Court",
    "District", "Special Report",
)

object Api {
    const val BASE = "https://www.recentnewsbd.com"
    const val PAGE = 15

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun fetch(label: String?, query: String?, start: Int): List<Post> {
        val path = if (!label.isNullOrEmpty()) "/feeds/posts/default/-/${enc(label)}" else "/feeds/posts/default"
        val q = if (!query.isNullOrBlank()) "&q=${enc(query)}" else ""
        val url = "$BASE$path?alt=json&max-results=$PAGE&start-index=$start$q"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000; conn.readTimeout = 20000
        val body = try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
        val entries = JSONObject(body).getJSONObject("feed").optJSONArray("entry") ?: return emptyList()
        val out = ArrayList<Post>()
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            val html = e.optJSONObject("content")?.optString("\$t")
                ?: e.optJSONObject("summary")?.optString("\$t") ?: ""
            var link = ""
            e.optJSONArray("link")?.let { ls ->
                for (j in 0 until ls.length()) {
                    val l = ls.getJSONObject(j)
                    if (l.optString("rel") == "alternate") link = l.optString("href")
                }
            }
            val thumb = e.optJSONObject("media\$thumbnail")?.optString("url")
                ?: Regex("<img[^>]+src=[\"']([^\"']+)").find(html)?.groupValues?.get(1)
            val label0 = e.optJSONArray("category")?.optJSONObject(0)?.optString("term")
            out.add(
                Post(
                    id = e.getJSONObject("id").getString("\$t"),
                    title = e.getJSONObject("title").getString("\$t"),
                    url = link, image = thumb?.let(::bigger), label = label0,
                    published = e.getJSONObject("published").getString("\$t"), html = html,
                )
            )
        }
        return out
    }

    private fun bigger(u: String): String =
        u.replace(Regex("/s\\d+(-c)?/"), "/w800/").replace(Regex("=s\\d+(-c)?$"), "=w800")

    fun prettyDate(iso: String): String = try {
        val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(iso.take(19))!!
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(d)
    } catch (e: Exception) { iso.take(10) }
}
