package bd.recentnews.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewsVM : ViewModel() {
    var posts by mutableStateOf<List<Post>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var endReached by mutableStateOf(false); private set
    var label by mutableStateOf<String?>(null); private set
    var query by mutableStateOf(""); private set
    var openPost by mutableStateOf<Post?>(null)
    private var job: Job? = null
    private var lastLoad = 0L

    init { reload() }

    fun select(l: String?) { label = l; query = ""; posts = emptyList(); reload() }
    fun search(q: String) { query = q; label = null; posts = emptyList(); reload() }
    fun clearSearch() { if (query.isNotEmpty()) { query = ""; posts = emptyList(); reload() } }
    fun refreshIfStale() {
        if (openPost == null && posts.isNotEmpty() && System.currentTimeMillis() - lastLoad > 10 * 60_000) reload(true)
    }

    fun reload(pull: Boolean = false) {
        job?.cancel()
        refreshing = pull; loading = !pull; loadingMore = false; endReached = false; error = null
        job = viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { Api.fetch(label, query, 1) }
                posts = r; endReached = r.size < Api.PAGE; lastLoad = System.currentTimeMillis()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = "Could not load news. Check your internet connection." }
            loading = false; refreshing = false
        }
    }

    fun more() {
        if (loading || loadingMore || refreshing || endReached || posts.isEmpty()) return
        loadingMore = true
        job = viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { Api.fetch(label, query, posts.size + 1) }
                posts = (posts + r).distinctBy { it.id }; endReached = r.size < Api.PAGE
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { /* keep list; retry on next scroll */ }
            loadingMore = false
        }
    }
}
