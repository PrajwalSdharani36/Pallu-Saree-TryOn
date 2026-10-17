package com.pallu.tryon.ui.kiosk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pallu.tryon.data.AppContainer
import com.pallu.tryon.data.FavoriteBody
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
    /** Kept after finishing so the thank-you screen can list them for the staff. */
    val shortlist: List<String> = emptyList(),
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

    fun touch() {
        lastTouch = System.currentTimeMillis()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** From the QR camera or the typed code. */
    fun lookup(code: String, onFound: () -> Unit) {
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
                            KioskState(session = s, drapeStyle = s.drapeStyle, mode = null)
                        }
                        onFound()
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

    /** "I'm done": server deletes the photo and every generated image; shortlist is kept for staff. */
    fun finish(onDone: () -> Unit) {
        val session = _state.value.session ?: return onDone()
        _state.update { it.copy(finishing = true) }
        pollJob?.cancel()
        val shortlist = session.items.filter { it.favorite }.map { it.saree.name }
        viewModelScope.launch {
            val repo = container.repository()
            runCatching { repo.api.finish(session.id) }
            _state.update {
                KioskState(shortlist = shortlist)
            }
            onDone()
        }
    }

    /** Walk-away / cancel: same privacy guarantee as finishing, then back to the start screen. */
    fun reset() {
        pollJob?.cancel()
        val session = _state.value.session
        _state.value = KioskState()
        lastCode = null
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
