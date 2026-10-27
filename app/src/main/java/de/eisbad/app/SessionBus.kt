package de.eisbad.app

import kotlinx.coroutines.flow.MutableStateFlow

enum class Phase { IDLE, PREP, BREATH, HOLD, RECOVERY, ENTRY, COLD, DONE }

data class UiState(
    val running: Boolean = false,
    val phase: Phase = Phase.IDLE,
    val title: String = "",
    val big: String = "",
    val sub: String = "",
    val progress: Float = 0f,
)

/** Einfacher Kanal zwischen Service (Sender) und UI (Empfänger). */
object SessionBus {
    val state = MutableStateFlow(UiState())

    /** Wird von der UI gesetzt ("Weiter"); der Runner liest und löscht das Flag. */
    @Volatile
    var skip: Boolean = false
}
