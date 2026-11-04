package com.example.pocketagent

/** Small deterministic parser for interruption/status commands spoken during automation. */
object VoiceCommandParser {
    enum class Kind { PAUSE, RESUME, STOP, STATUS, TASK }

    data class Parsed(val kind: Kind, val text: String = "")

    fun parse(raw: String, automationActive: Boolean): Parsed {
        val value = raw.trim().lowercase()
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
            .replace(Regex("\\s+"), " ")
        if (value.isBlank()) return Parsed(Kind.TASK)
        val words = value.split(" ").toSet()

        if (words.any { it in setOf("stop", "cancel", "abort") } ||
            value.contains("stop this") || value.contains("stop the task") ||
            value.contains("stop automation") || value.contains("cancel task")) {
            return Parsed(Kind.STOP)
        }
        if (words.any { it in setOf("pause", "wait", "hold") } ||
            value.contains("pause this") || value.contains("pause the task")) {
            return Parsed(Kind.PAUSE)
        }
        if (words.any { it in setOf("resume", "continue", "proceed") } ||
            value.contains("continue this") || value.contains("carry on")) {
            return Parsed(Kind.RESUME)
        }
        if (automationActive && (
                value.contains("how is it going") || value.contains("how is that going") || value.contains("how are you doing") ||
                value.contains("what are you doing") || value.contains("what problem") || value.contains("which problem") || value.contains("what issue") ||
                value.contains("what is the problem") || value.contains("status") ||
                value.contains("progress") || value.contains("where are you")
            )) return Parsed(Kind.STATUS)
        return Parsed(Kind.TASK, raw.trim())
    }
}
