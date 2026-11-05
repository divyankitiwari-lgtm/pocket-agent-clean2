package com.example.pocketagent.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AiGenerationResult(val text:String,val providerLabel:String,val model:String,val slot:Int,val usedLocalModel:Boolean)

class MultiProviderAiClient(private val context:Context) {
 private val store=AiProviderStore(context.applicationContext)
 fun generate(prompt:String):AiGenerationResult {
  require(prompt.isNotBlank())
  val failures=mutableListOf<String>()
  for((index,slot) in store.enabledSlots()) {
   val spec=AiProviderCatalog.byId(slot.providerId)
   if(spec.requiresKey && slot.apiKey.isBlank()){failures+="Slot $index (${spec.label}): API key missing";continue}
   try {
    val answer=request(spec,slot,prompt).trim()
    if(answer.isNotBlank()) return AiGenerationResult(answer,spec.label,slot.model,index,false)
    failures+="Slot $index (${spec.label}): empty response"
   } catch(e:Exception) { failures+="Slot $index (${spec.label}): ${safeMessage(e)}" }
  }
  throw IllegalStateException(if(failures.isEmpty()) "No provider enabled. Configure an API provider and key." else "All AI routes failed: "+failures.joinToString(" | "))
 }
 private fun safeMessage(e:Exception)=(e.message?:e.javaClass.simpleName).replace(Regex("(?i)(key|token|authorization)[=: ]+[^ ,;]+"),"\$1=[redacted]").take(180)
 private fun request(spec:AiProviderSpec,slot:AiSlot,prompt:String):String {
  val base=slot.baseUrl.trim().ifBlank{spec.baseUrl}.trimEnd('/')
  val url:String
  val payload:JSONObject
  when(spec.protocol) {
   AiProtocol.GEMINI -> {
    url="$base/v1beta/models/${slot.model}:generateContent?key="+URLEncoder.encode(slot.apiKey,"UTF-8")
    payload=JSONObject().put("contents",JSONArray().put(JSONObject().put("role","user").put("parts",JSONArray().put(JSONObject().put("text",prompt))))).put("generationConfig",JSONObject().put("temperature",0.4).put("maxOutputTokens",2048))
   }
   AiProtocol.ANTHROPIC -> { url="$base/v1/messages"; payload=JSONObject().put("model",slot.model).put("max_tokens",2048).put("messages",JSONArray().put(JSONObject().put("role","user").put("content",prompt))) }
   AiProtocol.OLLAMA -> { url="$base/api/chat"; payload=JSONObject().put("model",slot.model).put("stream",false).put("messages",JSONArray().put(JSONObject().put("role","user").put("content",prompt))) }
   AiProtocol.OPENAI_COMPATIBLE -> { url="$base/chat/completions"; payload=JSONObject().put("model",slot.model).put("temperature",0.4).put("messages",JSONArray().put(JSONObject().put("role","user").put("content",prompt))) }
  }
  val c=URL(url).openConnection() as HttpURLConnection
  try {
   c.requestMethod="POST"; c.connectTimeout=15000;c.readTimeout=60000;c.doOutput=true
   c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json")
   when(spec.protocol) {
    AiProtocol.GEMINI,AiProtocol.OLLAMA -> Unit
    AiProtocol.ANTHROPIC -> {c.setRequestProperty("x-api-key",slot.apiKey);c.setRequestProperty("anthropic-version","2023-06-01")}
    AiProtocol.OPENAI_COMPATIBLE -> if(slot.apiKey.isNotBlank()){c.setRequestProperty("Authorization","Bearer ${slot.apiKey}");if(spec.id=="openrouter"){c.setRequestProperty("HTTP-Referer","https://github.com/divyankitiwari-lgtm/pocket-agent-clean2");c.setRequestProperty("X-Title","JARVIS Android")}}
   }
   c.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))}
   val code=c.responseCode
   val body=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
   if(code !in 200..299){val msg=try{JSONObject(body).optJSONObject("error")?.optString("message")?:body}catch(e:Exception){body};throw IllegalStateException("HTTP $code: ${msg.take(200)}")}
   val j=JSONObject(body)
   return when(spec.protocol) {
    AiProtocol.GEMINI -> j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
    AiProtocol.ANTHROPIC -> {val a=j.optJSONArray("content")?:JSONArray();(0 until a.length()).mapNotNull{a.optJSONObject(it)?.optString("text")}.filter{it.isNotBlank()}.joinToString("\n")}
    AiProtocol.OLLAMA -> j.optJSONObject("message")?.optString("content").orEmpty()
    AiProtocol.OPENAI_COMPATIBLE -> j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
   }
  } finally {c.disconnect()}
 }
}
