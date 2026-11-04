package com.example.pocketagent

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.Closeable
import java.io.File

/**
 * On-device Gemma runtime used by both the JARVIS planner and local assistant.
 * The model is imported into app-private storage by MainActivity before use.
 */
class GemmaEngine(
    context: Context,
    modelFile: File
) : Closeable {
    private val inference: LlmInference

    init {
        require(modelFile.isFile && modelFile.length() > 1_000_000L) {
            "Gemma .task model is missing or incomplete. Import it again."
        }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(512)
            .build()
        inference = LlmInference.createFromOptions(context.applicationContext, options)
    }

    @Synchronized
    fun generate(prompt: String): String {
        require(prompt.isNotBlank()) { "Prompt cannot be empty." }
        return inference.generateResponse(prompt).also {
            check(it.isNotBlank()) { "Gemma returned an empty response." }
        }
    }

    @Synchronized
    override fun close() {
        inference.close()
    }
}
