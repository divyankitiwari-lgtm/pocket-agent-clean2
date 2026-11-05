package com.example.pocketagent.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

enum class AiProtocol { OPENAI_COMPATIBLE, GEMINI, ANTHROPIC, OLLAMA }
data class AiProviderSpec(val id:String,val label:String,val protocol:AiProtocol,val baseUrl:String,val models:List<String>,val requiresKey:Boolean=true)

object AiProviderCatalog {
 val providers = listOf(
  AiProviderSpec("gemini","Google Gemini",AiProtocol.GEMINI,"https://generativelanguage.googleapis.com",listOf("gemini-2.5-flash","gemini-2.5-pro","gemini-2.0-flash")),
  AiProviderSpec("openai","OpenAI",AiProtocol.OPENAI_COMPATIBLE,"https://api.openai.com/v1",listOf("gpt-4.1-mini","gpt-4.1","gpt-4o-mini","o4-mini")),
  AiProviderSpec("anthropic","Anthropic Claude",AiProtocol.ANTHROPIC,"https://api.anthropic.com",listOf("claude-sonnet-4-5","claude-haiku-4-5","claude-opus-4-1")),
  AiProviderSpec("openrouter","OpenRouter",AiProtocol.OPENAI_COMPATIBLE,"https://openrouter.ai/api/v1",listOf("openrouter/auto","openrouter/free","google/gemini-2.5-flash","meta-llama/llama-3.3-70b-instruct:free")),
  AiProviderSpec("nvidia","NVIDIA NIM",AiProtocol.OPENAI_COMPATIBLE,"https://integrate.api.nvidia.com/v1",listOf("meta/llama-3.3-70b-instruct","nvidia/llama-3.1-nemotron-70b-instruct")),
  AiProviderSpec("groq","Groq",AiProtocol.OPENAI_COMPATIBLE,"https://api.groq.com/openai/v1",listOf("llama-3.3-70b-versatile","openai/gpt-oss-20b","qwen/qwen3-32b")),
  AiProviderSpec("cerebras","Cerebras",AiProtocol.OPENAI_COMPATIBLE,"https://api.cerebras.ai/v1",listOf("llama-3.3-70b","qwen-3-32b")),
  AiProviderSpec("together","Together AI",AiProtocol.OPENAI_COMPATIBLE,"https://api.together.xyz/v1",listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo","Qwen/Qwen2.5-72B-Instruct-Turbo")),
  AiProviderSpec("mistral","Mistral",AiProtocol.OPENAI_COMPATIBLE,"https://api.mistral.ai/v1",listOf("mistral-small-latest","mistral-large-latest","codestral-latest")),
  AiProviderSpec("deepseek","DeepSeek",AiProtocol.OPENAI_COMPATIBLE,"https://api.deepseek.com/v1",listOf("deepseek-chat","deepseek-reasoner")),
  AiProviderSpec("ollama","Ollama local server",AiProtocol.OLLAMA,"http://127.0.0.1:11434",listOf("qwen2.5:3b","llama3.2:3b"),false)
 )
 fun byId(id:String)=providers.firstOrNull{it.id==id}?:providers.first()
}
data class AiSlot(val enabled:Boolean=false,val providerId:String="gemini",val model:String="gemini-2.5-flash",val apiKey:String="",val baseUrl:String="")

class AiProviderStore(context:Context) {
 private val prefs = try {
  val master=MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
  EncryptedSharedPreferences.create(context,"jarvis_ai_slots",master,EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
 } catch(e:Exception) { throw IllegalStateException("Encrypted storage unavailable; API keys were not saved in plaintext.",e) }
 fun load(index:Int):AiSlot {
  require(index in 1..4)
  return try { val j=JSONObject(prefs.getString("slot_$index",null)?:return AiSlot())
   AiSlot(j.optBoolean("enabled"),j.optString("providerId","gemini"),j.optString("model","gemini-2.5-flash"),j.optString("apiKey"),j.optString("baseUrl"))
  } catch(e:Exception){AiSlot()}
 }
 fun save(index:Int,slot:AiSlot) {
  require(index in 1..4)
  val j=JSONObject().put("enabled",slot.enabled).put("providerId",slot.providerId).put("model",slot.model).put("apiKey",slot.apiKey).put("baseUrl",slot.baseUrl)
  check(prefs.edit().putString("slot_$index",j.toString()).commit())
 }
 fun enabledSlots()=(1..4).map{it to load(it)}.filter{it.second.enabled}
}
