package com.pallu.tryon.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pallu.tryon.data.AddItemBody
import com.pallu.tryon.data.AppContainer
import com.pallu.tryon.data.CreateSessionBody
import com.pallu.tryon.data.Saree
import com.pallu.tryon.data.TryOnSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class StaffState(
    val recent: List<TryOnSession> = emptyList(),
    val loadingRecent: Boolean = false,
    val scanned: List<Saree> = emptyList(),
    val customerLabel: String = "",
    /** When set, scans are added straight to this existing session. */
    val addingTo: String? = null,
    val looking: Set<String> = emptySet(),
    val busy: Boolean = false,
    val error: String? = null,
    val session: TryOnSession? = null,
    /** Shown on the staff home screen when looks can't be made for real (demo mode, key problem). */
    val engineNote: com.pallu.tryon.data.EngineStatus? = null,
)

sealed interface ScanEvent {
    data class Added(val saree: Saree) : ScanEvent
    data class Duplicate(val name: String) : ScanEvent
    data class NotFound(val code: String) : ScanEvent
}

class StaffViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(StaffState())
    val state: StateFlow<StaffState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ScanEvent> = _events

    private val recentlySeen = mutableMapOf<String, Long>()
    private var pollJob: Job? = null

    fun startNew() {
        recentlySeen.clear()
        _state.update { it.copy(scanned = emptyList(), customerLabel = "", addingTo = null, error = null, session = null) }
    }

    fun startAdding(sessionId: String) {
        recentlySeen.clear()
        _state.update { it.copy(scanned = emptyList(), addingTo = sessionId, error = null) }
    }

    fun setLabel(label: String) = _state.update { it.copy(customerLabel = label.take(60)) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun refreshRecent() = viewModelScope.launch {
        _state.update { it.copy(loadingRecent = true) }
        val repo = container.repository()
        runCatching { repo.api.recentSessions(20) }
            .onSuccess { list -> _state.update { it.copy(recent = list, loadingRecent = false, error = null) } }
            .onFailure { e -> _state.update { it.copy(loadingRecent = false, error = repo.describe(e)) } }
        runCatching { repo.api.health() }.onSuccess { h ->
            _state.update { it.copy(engineNote = h.engineStatus?.takeIf { s -> s.state != "ok" && s.message.isNotBlank() }) }
        }
    }

    /** Called for every camera detection and for typed codes. De-duplicates bursts from the camera. */
    fun onCode(raw: String) {
        val code = raw.trim()
        if (code.isEmpty()) return
        val now = System.currentTimeMillis()
        if ((recentlySeen[code] ?: 0L) > now - 2500) return
        recentlySeen[code] = now
        val st = _state.value
        if (code in st.looking) return
        st.scanned.firstOrNull { it.barcode == code }?.let {
            _events.tryEmit(ScanEvent.Duplicate(it.name)); return
        }
        _state.update { it.copy(looking = it.looking + code) }
        viewModelScope.launch {
            val repo = container.repository()
            try {
                val adding = _state.value.addingTo
                val saree = repo.api.sareeByBarcode(code)
                if (adding != null) {
                    val updated = repo.api.addItem(adding, AddItemBody(barcode = code))
                    _state.update { it.copy(session = updated) }
                }
                _state.update { it.copy(scanned = it.scanned + saree, error = null) }
                _events.tryEmit(ScanEvent.Added(saree))
            } catch (e: Exception) {
                val msg = repo.describe(e)
                if (msg.startsWith("No saree")) _events.tryEmit(ScanEvent.NotFound(code))
                else _state.update { it.copy(error = msg) }
            } finally {
                _state.update { it.copy(looking = it.looking - code) }
            }
        }
    }

    fun remove(saree: Saree) = _state.update { it.copy(scanned = it.scanned.filterNot { s -> s.id == saree.id }) }

    fun createSession(onReady: (String) -> Unit) = viewModelScope.launch {
        val st = _state.value
        if (st.scanned.isEmpty() || st.busy) return@launch
        _state.update { it.copy(busy = true, error = null) }
        val repo = container.repository()
        runCatching {
            repo.api.createSession(
                CreateSessionBody(sareeIds = st.scanned.map { it.id }, customerLabel = st.customerLabel.trim())
            )
        }.onSuccess { s ->
            _state.update { it.copy(busy = false, session = s, scanned = emptyList()) }
            onReady(s.id)
        }.onFailure { e -> _state.update { it.copy(busy = false, error = repo.describe(e)) } }
    }

    /** Live status for the QR screen: staff can see when the customer is at the mirror and what they liked. */
    fun watch(sessionId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val repo = container.repository()
            while (isActive) {
                runCatching { repo.api.session(sessionId) }
                    .onSuccess { s -> _state.update { it.copy(session = s, error = null) } }
                    .onFailure { e -> _state.update { it.copy(error = repo.describe(e)) } }
                delay(3000)
            }
        }
    }

    fun stopWatching() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Gift mode: the customer sent a photo of the person to the staff member, who adds it here. */
    fun uploadGiftPhoto(sessionId: String, jpeg: ByteArray, onDone: () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true, error = null) }
        val repo = container.repository()
        // empty drape style keeps whatever the customer chose at the mirror
        runCatching { repo.uploadPhoto(sessionId, jpeg, mode = "gift", drapeStyle = "") }
            .onSuccess { s -> _state.update { it.copy(busy = false, session = s) }; onDone() }
            .onFailure { e -> _state.update { it.copy(busy = false, error = repo.describe(e)) } }
    }

    fun removeItem(sessionId: String, itemId: String) = viewModelScope.launch {
        val repo = container.repository()
        runCatching { repo.api.removeItem(sessionId, itemId) }
            .onSuccess { s -> _state.update { it.copy(session = s) } }
            .onFailure { e -> _state.update { it.copy(error = repo.describe(e)) } }
    }
}

fun statusWords(status: String): String = when (status) {
    "open" -> "Waiting at the mirror"
    "generating" -> "Creating looks"
    "ready" -> "Viewing looks"
    "closed" -> "Finished"
    "expired" -> "Expired"
    else -> status
}
