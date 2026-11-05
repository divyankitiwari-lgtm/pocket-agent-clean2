package com.example.pocketagent

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandParserTest {
    @Test fun progressQuestionsAreStatusDuringAutomation() {
        val phrases = listOf("How is that going?", "What problem are you facing?",
            "Which problem are you facing?", "Tell me your progress")
        phrases.forEach { assertEquals(VoiceCommandParser.Kind.STATUS,
            VoiceCommandParser.parse(it, true).kind) }
    }

    @Test fun pauseResumeAndStopRemainAvailableDuringAutomation() {
        assertEquals(VoiceCommandParser.Kind.PAUSE, VoiceCommandParser.parse("pause", true).kind)
        assertEquals(VoiceCommandParser.Kind.RESUME, VoiceCommandParser.parse("continue", true).kind)
        assertEquals(VoiceCommandParser.Kind.STOP, VoiceCommandParser.parse("stop automation", true).kind)
    }

    @Test fun normalRequestStartsANewTaskWhenIdle() {
        val parsed = VoiceCommandParser.parse("Open the calculator", false)
        assertEquals(VoiceCommandParser.Kind.TASK, parsed.kind)
        assertEquals("Open the calculator", parsed.text)
    }
}
