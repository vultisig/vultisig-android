package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns peer-discovery polling and the keysign committee selection.
 *
 * Extracted verbatim from `KeysignFlowViewModel` — the `participants`/`selection` state, the
 * `DiscoverParticipantsUseCase` collection job, and the add/remove/handle-participant mutations
 * used to live inline in the ViewModel. The ViewModel now delegates here and re-exposes the
 * read-only flows, keeping the mutable state private to this controller.
 *
 * One instance per `KeysignFlowViewModel` (unscoped constructor injection): it holds the discovery
 * [Job] and the selection state for that ViewModel's lifetime.
 */
internal class KeysignParticipantDiscovery
@Inject
constructor(private val discoverParticipantsUseCase: DiscoverParticipantsUseCase) {
    private val _participants = MutableStateFlow<List<String>>(emptyList())
    val participants: StateFlow<List<String>> = _participants

    private val _selection = MutableStateFlow<List<String>>(emptyList())
    val selection: StateFlow<List<String>> = _selection

    private var discoverParticipantsJob: Job? = null

    /** Replaces the current selection (e.g. seeding it with the local party id). */
    fun setSelection(selection: List<String>) {
        _selection.value = selection
    }

    fun addParticipant(participant: String) {
        val currentList = _selection.value
        if (currentList.contains(participant)) return
        _selection.value = currentList + participant
    }

    private fun removeParticipant(participant: String) {
        _selection.value -= participant
    }

    fun handleParticipant(participant: String) {
        if (participant in _selection.value) {
            removeParticipant(participant)
        } else {
            addParticipant(participant)
        }
    }

    /**
     * (Re)starts discovery polling on [scope]. Newly discovered participants are auto-added to the
     * selection, mirroring the original ViewModel behavior.
     */
    fun start(scope: CoroutineScope, serverUrl: String, sessionId: String, localPartyId: String) {
        discoverParticipantsJob?.cancel()
        discoverParticipantsJob =
            scope.launch {
                discoverParticipantsUseCase(serverUrl, sessionId, localPartyId).collect { discovered
                    ->
                    val existingParticipants = _participants.value.toSet()
                    val newParticipants = discovered - existingParticipants
                    _participants.update { discovered }
                    newParticipants.forEach(::addParticipant)
                }
            }
    }

    /** Cancels discovery polling. */
    fun stop() {
        discoverParticipantsJob?.cancel()
    }
}
