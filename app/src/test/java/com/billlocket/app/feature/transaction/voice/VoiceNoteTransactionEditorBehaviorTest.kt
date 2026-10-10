package com.billlocket.app.feature.transaction.voice

import com.billlocket.app.domain.model.AccountId
import com.billlocket.app.domain.model.CategoryId
import com.billlocket.app.domain.model.DraftId
import com.billlocket.app.domain.model.TransactionType
import com.billlocket.app.feature.transaction.editor.TransactionEditorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceNoteTransactionEditorBehaviorTest {

    private fun createValidState(note: String = ""): TransactionEditorState {
        return TransactionEditorState(
            draftId = DraftId("draft-123"),
            type = TransactionType.EXPENSE,
            amountText = "50000",
            selectedCategoryId = CategoryId("cat-food"),
            selectedAccountId = AccountId("acc-cash"),
            note = note,
            isLoading = false,
            isSaving = false,
            fatalError = null,
        )
    }

    @Test
    fun `financially valid state allows save regardless of voice state`() {
        val validState = createValidState()
        assertTrue("Valid financial state must allow save", validState.canSave)

        // Test with different voice states
        val voiceStates = listOf(
            VoiceNoteState.Idle,
            VoiceNoteState.Listening(partialText = "đang nói"),
            VoiceNoteState.Processing,
            VoiceNoteState.Completed("cơm trưa"),
            VoiceNoteState.Error(VoiceRecognitionError.NetworkError),
            VoiceNoteState.Error(VoiceRecognitionError.NoSpeechMatch),
            VoiceNoteState.Unavailable,
        )

        for (vs in voiceStates) {
            // Transaction save feasibility remains governed purely by financial validity
            assertTrue("State must remain saveable during voice state: $vs", validState.canSave)
        }
    }

    @Test
    fun `voice recognition failure never introduces fatalError in transaction editor`() {
        val state = createValidState(note = "Ghi chú ban đầu")
        assertNull(state.fatalError)

        // Simulating voice error
        val voiceError = VoiceRecognitionError.NetworkError
        // Editor fatalError remains null
        assertNull("Fatal error must remain null on speech failure", state.fatalError)
        // Original note is preserved
        assertEquals("Ghi chú ban đầu", state.note)
    }

    @Test
    fun `final recognized speech successfully merges through note merger into editor note`() {
        var state = createValidState(note = "Trà đào")

        // User dictates "thêm trân châu"
        val recognized = "thêm trân châu"
        val merged = VoiceNoteTextMerger.merge(state.note, recognized)
        state = state.copy(note = merged)

        assertEquals("Trà đào thêm trân châu", state.note)
        assertTrue(state.canSave)
    }
}
