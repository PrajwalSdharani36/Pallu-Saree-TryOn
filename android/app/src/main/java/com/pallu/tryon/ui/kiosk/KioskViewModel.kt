package com.pallu.tryon.ui.kiosk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pallu.tryon.data.AppContainer
import com.pallu.tryon.data.AddItemBody
import com.pallu.tryon.data.FavoriteBody
import com.pallu.tryon.data.RedrapeBody
import com.pallu.tryon.data.Saree
import com.pallu.tryon.data.SessionUpdateBody
import com.pallu.tryon.data.SessionItem
import com.pallu.tryon.data.TryOnSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TryOnMode(val api: String) { SELF("self"), GIFT("gift") }

data class KioskState(
    val session: TryOnSession? = null,
    val lookingUp: Boolean = false,
    val mode: TryOnMode? = null,
    val drapeStyle: String = "nivi",
    val consent: Boolean = false,
    val photo: ByteArray? = null,
    val photoPreview: Bitmap? = null,
    val uploading: Boolean = false,
    val finishing: Boolean = false,
    val error: String? = null,
    /** Kept after finishing so the thank-you screen can show them for the staff. */
    val shortlist: List<Saree> = emptyList(),
    /** A few catalogue sarees for the welcome screen. */
    val showcase: List<Saree> = emptyList(),
    /** The store's whole collection, for "Try more sarees". Loaded when that sheet opens. */
    val catalog: List<Saree> = emptyList(),
    val catalogLoading: Boolean = false,
    /** Adding sarees or making a new drape: the sheet shows it's working. */
    val busy: Boolean = false,
) {
    val anyWorking get() = session?.items?.any { it.isWorking } == true
}

class KioskViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(KioskState())
    val state: StateFlow<KioskState> = _state.asStateFlow()

    @Volatile
    var lastTouch: Long = System.currentTimeMillis()
        private set

    private var pollJob: Job? = null
    private var lastCode: Pair<String, Long>? = null

    init {
        loadShowcase()
    }

    /** Catalogue photos for the welcome screen's fan of sarees. Refreshed each time the mirror resets. */
    fun loadShowcase() {
        viewModelScope.launch {
            runCatching { container.repository().api.sarees(limit = 5) }
                .onSuccess { list -> _state.update { it.copy(showcase = list.take(5)) } }
        }
    }

    fun touch() {
        lastTouch = System.currentTimeMillis()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /**
     * From the QR camera or the typed code. [onFound] gets true when the session already has a photo
     * (a staff member uploaded a gift photo), so the mirror can go straight to the looks.
     */
    fun lookup(code: String, onFound: (alreadyHasPhoto: Boolean) -> Unit) {
        val c = code.trim()
        if (c.isEmpty() || _state.value.lookingUp) return
        val now = System.currentTimeMillis()
        lastCode?.let { (prev, at) -> if (prev == c && now - at < 3000) return }
        lastCode = c to now
        touch()
        _state.update { it.copy(lookingUp = true, error = null) }
        viewModelScope.launch {
            val repo = container.repository()
            runCatching { repo.api.lookup(c) }
                .onSuccess { s ->
                    if (s.isClosed) {
                        _state.update { it.copy(lookingUp = false, error = "This QR code has already been used. Ask our staff for a new one.") }
                    } else {
                        _state.update {
                            KioskState(
                                session = s, drapeStyle = s.drapeStyle, showcase = it.showcase,
                                mode = if (s.hasPhoto && s.mode == "gift") TryOnMode.GIFT else null,
                            )
                        }
                        if (s.hasPhoto) startPolling()
                        onFound(s.hasPhoto)
                    }
                }
                .onFailure { e ->
                    val msg = repo.describe(e).let { m ->
                        if (m.startsWith("Session not found")) "We couldn't find that code. Check it with our staff." else m
                    }
                    _state.update { it.copy(lookingUp = false, error = msg) }
                }
        }
    }

    fun setMode(m: TryOnMode) = _state.update { it.copy(mode = m, consent = false) }
    fun setDrape(id: String) = _state.update { it.copy(drapeStyle = id) }
    fun setConsent(v: Boolean) = _state.update { it.copy(consent = v) }

    fun setPhoto(jpeg: ByteArray?) {
        val preview = jpeg?.let { runCatching { decodePreview(it) }.getOrNull() }
        _state.update { it.copy(photo = jpeg, photoPreview = preview, error = null) }
    }

    fun upload(onDone: () -> Unit) {
        val st = _state.value
        val session = st.session ?: return
        val photo = st.photo ?: return
        if (st.uploading) return
        _state.update { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            val repo = container.repository()
            runCatching { repo.uploadPhoto(session.id, photo, (st.mode ?: TryOnMode.SELF).api, st.drapeStyle) }
                .onSuccess { s ->
                    _state.update { it.copy(session = s, uploading = false) }
                    startPolling()
                    onDone()
                }
                .onFailure { e -> _state.update { it.copy(uploading = false, error = repo.describe(e)) } }
        }
    }

    /**
     * Gift mode: the customer sends the photo to a staff member, who uploads it from the staff phone.
     * Save the customer's choices now, then wait here until the photo arrives.
     */
    fun waitForGiftPhoto(onArrived: () -> Unit) {
        val session = _state.value.session ?: return
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val repo = container.repository()
            runCatching {
                repo.api.updateSession(session.id, SessionUpdateBody(drapeStyle = _state.value.drapeStyle, mode = TryOnMode.GIFT.api))
            }.onSuccess { s -> _state.update { it.copy(session = s) } }
                .onFailure { e -> _state.update { it.copy(error = repo.describe(e)) } }
            while (isActive) {
                delay(2500)
                val s = runCatching { repo.api.session(session.id) }.getOrNull() ?: continue
                _state.update { it.copy(session = s) }
                if (s.isClosed) return@launch
                if (s.hasPhoto) {
                    touch()
                    startPolling()
                    onArrived()
                    return@launch
                }
            }
        }
    }

    fun stopWaiting() {
        pollJob?.cancel()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val repo = container.repository()
            while (isActive) {
                val id = _state.value.session?.id ?: break
                runCatching { repo.api.session(id) }.onSuccess { s ->
                    _state.update { it.copy(session = s) }
                    if (s.isClosed) return@launch
                }
                // fast while looks are being made; slower afterwards so staff-added sarees still show up
                delay(if (_state.value.anyWorking) 2000 else 5000)
            }
        }
    }

    fun toggleFavorite(item: SessionItem) {
        val session = _state.value.session ?: return
        val want = !item.favorite
        _state.update { st ->
            st.copy(session = st.session?.let { s ->
                s.copy(items = s.items.map { if (it.id == item.id) it.copy(favorite = want) else it })
            })
        }
        viewModelScope.launch {
            val repo = container.repository()
            runCatching { repo.api.favorite(session.id, item.id, FavoriteBody(want)) }
                .onFailure { e -> _state.update { it.copy(error = repo.describe(e)) } }
        }
    }

    fun retry(item: SessionItem) {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            val repo = container.repository()
            runCatching { repo.api.retry(session.id, item.id) }
                .onSuccess { s -> _state.update { it.copy(session = s) }; startPolling() }
                .onFailure { e -> _state.update { it.copy(error = repo.describe(e)) } }
        }
    }

    /** For "Try more sarees": every saree in the store, newest first. */
    fun loadCatalog() {
        if (_state.value.catalogLoading) return
        _state.update { it.copy(catalogLoading = true) }
        viewModelScope.launch {
            val repo = container.repository()
            runCatching { repo.api.sarees(limit = 200) }
                .onSuccess { list -> _state.update { it.copy(catalog = list.filter { s -> s.active }, catalogLoading = false) } }
                .onFailure { e -> _state.update { it.copy(catalogLoading = false, error = repo.describe(e)) } }
        }
    }

    private var lastTag: Pair<String, Long>? = null

    /**
     * She scanned a saree's tag at the mirror. [onResult] gets the new look's id when it was added,
     * or a short message for her (unknown tag, already trying it, her own QR code, ...).
     */
    fun addByBarcode(code: String, onResult: (newItemId: String?, message: String?) -> Unit) {
        val session = _state.value.session ?: return
        val c = code.trim()
        if (c.isEmpty() || _state.value.busy) return
        val now = System.currentTimeMillis()
        lastTag?.let { (prev, at) -> if (prev == c && now - at < 4000) return } // the camera sees a tag many times a second
        lastTag = c to now
        touch()
        if (c.startsWith("pallu://")) {
            onResult(null, "That's your try-on code. Hold up the tag on a saree instead.")
            return
        }
        val before = session.items.map { it.id }.toSet()
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val repo = container.repository()
            runCatching { repo.api.addItem(session.id, AddItemBody(barcode = c)) }
                .onSuccess { s ->
                    _state.update { it.copy(session = s, busy = false) }
                    startPolling()
                    val added = s.items.firstOrNull { it.id !in before }
                    if (added != null) {
                        onResult(added.id, null)
                    } else {
                        val same = s.items.firstOrNull { it.saree.barcode == c }
                        onResult(null, if (same != null) "You're already trying ${same.saree.name}." else "Couldn't add that saree.")
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(busy = false) }
                    val notFound = (e as? retrofit2.HttpException)?.code() == 404
                    onResult(null, if (notFound) "We couldn't find that tag. Please ask our staff." else repo.describe(e))
                }
        }
    }

    /** Add sarees to this customer's try-on; they're draped with the photo she already took. */
    fun addSarees(ids: List<String>, onAdded: (firstNewItemId: String?) -> Unit) {
        val session = _state.value.session ?: return
        if (ids.isEmpty() || _state.value.busy) return
        touch()
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val repo = container.repository()
            var latest: TryOnSession? = null
            var error: String? = null
            for (id in ids) {
                runCatching { repo.api.addItem(session.id, AddItemBody(sareeId = id)) }
                    .onSuccess { latest = it }
                    .onFailure { e -> error = repo.describe(e) }
                if (error != null) break
            }
            val before = session.items.map { it.id }.toSet()
            _state.update { st -> st.copy(session = latest ?: st.session, busy = false, error = error) }
            startPolling()
            onAdded(latest?.items?.firstOrNull { it.id !in before }?.id)
        }
    }

    /** Make a new look of [item]'s saree in another drape, next to the original. */
    fun redrape(item: SessionItem, drape: String, onMade: (newItemId: String?) -> Unit) {
        val session = _state.value.session ?: return
        if (_state.value.busy) return
        touch()
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val repo = container.repository()
            runCatching { repo.api.redrape(session.id, item.id, RedrapeBody(drape)) }
                .onSuccess { s ->
                    _state.update { it.copy(session = s, busy = false) }
                    startPolling()
                    onMade(s.items.firstOrNull { it.saree.id == item.saree.id && it.drapeStyle == drape }?.id)
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = repo.describe(e)) }; onMade(null) }
        }
    }

    /** "I'm done": server deletes the photo and every generated image; shortlist is kept for staff. */
    fun finish(onDone: () -> Unit) {
        val session = _state.value.session ?: return onDone()
        _state.update { it.copy(finishing = true) }
        pollJob?.cancel()
        // one entry per saree, even if she hearted it in two drapes
        val shortlist = session.items.filter { it.favorite }.map { it.saree }.distinctBy { it.id }
        viewModelScope.launch {
            val repo = container.repository()
            runCatching { repo.api.finish(session.id) }
            _state.update {
                KioskState(shortlist = shortlist, showcase = it.showcase)
            }
            onDone()
        }
    }

    /** Walk-away / cancel: same privacy guarantee as finishing, then back to the start screen. */
    fun reset() {
        pollJob?.cancel()
        val session = _state.value.session
        _state.value = KioskState(showcase = _state.value.showcase)
        lastCode = null
        loadShowcase()
        if (session != null && !session.isClosed && session.hasPhoto) {
            viewModelScope.launch { runCatching { container.repository().api.finish(session.id) } }
        }
    }

    private fun decodePreview(jpeg: ByteArray): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > 1400) sample *= 2
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
