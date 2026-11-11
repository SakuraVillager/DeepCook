package com.deepcook.app

import com.deepcook.app.feature.cooking.NoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteCommandTest {
    @Test fun anExplicitNoteCommandIsExtracted() {
        assertEquals("少放盐", NoteCommand.extract("记一笔：少放盐"))
        assertEquals("下次多放辣", NoteCommand.extract("记一下 下次多放辣"))
        assertEquals("火小一点", NoteCommand.extract("备注一下：火小一点"))
        assertEquals("少放糖", NoteCommand.extract("帮我记住少放糖"))
        // Recognised speech arrives with a full stop; it belongs to the sentence, not the note.
        assertEquals("少放盐", NoteCommand.extract("记一笔：少放盐。"))
    }

    @Test fun complaintsAndQuestionsAreNotNotes() {
        assertNull(NoteCommand.extract("这个太咸了"))
        assertNull(NoteCommand.extract("这个熟了吗"))
        assertNull(NoteCommand.extract("记住"))
    }
}
