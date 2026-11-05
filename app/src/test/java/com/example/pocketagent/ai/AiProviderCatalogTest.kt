package com.example.pocketagent.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderCatalogTest {
 @Test fun exposesMultipleProviderFamilies() {
  val ids=AiProviderCatalog.providers.map{it.id}
  assertTrue(ids.containsAll(listOf("gemini","openai","anthropic","openrouter","nvidia","groq","deepseek","ollama")))
 }
 @Test fun ollamaDoesNotRequireAnApiKey() {
  val ollama=AiProviderCatalog.byId("ollama")
  assertFalse(ollama.requiresKey)
  assertEquals(AiProtocol.OLLAMA,ollama.protocol)
 }
 @Test fun cloudProvidersUseTheirExpectedProtocols() {
  assertEquals(AiProtocol.GEMINI,AiProviderCatalog.byId("gemini").protocol)
  assertEquals(AiProtocol.ANTHROPIC,AiProviderCatalog.byId("anthropic").protocol)
  assertEquals(AiProtocol.OPENAI_COMPATIBLE,AiProviderCatalog.byId("nvidia").protocol)
 }
}
