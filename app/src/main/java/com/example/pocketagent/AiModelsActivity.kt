package com.example.pocketagent

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.example.pocketagent.ai.AiProviderCatalog
import com.example.pocketagent.ai.AiProviderStore
import com.example.pocketagent.ai.AiSlot
import com.example.pocketagent.ai.MultiProviderAiClient
import java.io.File

class AiModelsActivity : Activity() {
 private lateinit var store:AiProviderStore
 private lateinit var slotSpinner:Spinner
 private lateinit var providerSpinner:Spinner
 private lateinit var modelSpinner:Spinner
 private lateinit var keyField:EditText
 private lateinit var baseUrlField:EditText
 private lateinit var enabledSwitch:Switch
 private lateinit var localSwitch:Switch
 private lateinit var status:TextView
 private var currentSlot=1
 private val ink=Color.rgb(235,243,255)
 private val muted=Color.rgb(153,174,196)

 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  store=try{AiProviderStore(this)}catch(e:Exception){
   AlertDialog.Builder(this).setTitle("Secure storage unavailable").setMessage(e.message?:"Cannot safely store API keys on this device.").setPositiveButton("Close"){_,_->finish()}.show();return
  }
  window.statusBarColor=Color.rgb(5,9,18);window.navigationBarColor=Color.rgb(5,9,18)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(14),dp(18),dp(18));background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(4,8,16),Color.rgb(12,20,35),Color.rgb(5,9,18)))}
  val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  header.addView(text("AI PROVIDERS & MODELS",19,ink,true),LinearLayout.LayoutParams(0,dp(48),1f))
  header.addView(button("Done"){finish()})
  root.addView(header)
  root.addView(text("Four independent API slots. JARVIS tries enabled slots in order, then the optional local model.",12,muted),margins(14))
  val card=glassCard()
  card.addView(label("FALLBACK SLOT"))
  slotSpinner=spinner((1..4).map{"Slot $it"});card.addView(slotSpinner,margins(12))
  card.addView(label("PROVIDER"))
  providerSpinner=spinner(AiProviderCatalog.providers.map{it.label});card.addView(providerSpinner,margins(12))
  card.addView(label("MODEL"))
  modelSpinner=spinner(AiProviderCatalog.providers.first().models);card.addView(modelSpinner,margins(12))
  card.addView(label("API KEY"))
  keyField=edit("Paste the key for this provider",true);card.addView(keyField,margins(12))
  card.addView(label("CUSTOM BASE URL (OPTIONAL)"))
  baseUrlField=edit("Provider default URL",false);card.addView(baseUrlField,margins(12))
  enabledSwitch=Switch(this).apply{text="Enable this fallback slot";setTextColor(ink)}
  card.addView(enabledSwitch,margins(10));root.addView(card,margins(14))
  val local=glassCard()
  local.addView(label("OPTIONAL LOCAL MODEL"))
  localSwitch=Switch(this).apply{text="Try imported Gemma after API slots fail";setTextColor(ink);isChecked=getPreferences(MODE_PRIVATE).getBoolean("local_fallback",true)}
  local.addView(localSwitch,margins(8))
  local.addView(text("Import a compatible MediaPipe .task model. Local inference is independent of API keys and may require substantial RAM and storage.",11,muted),margins(8))
  local.addView(button("Import local .task model"){chooseModelFile()},margins(8))
  root.addView(local,margins(14))
  status=text("Keys are stored with Android encrypted preferences. Never share screenshots containing keys.",11,muted)
  root.addView(status,margins(10))
  root.addView(button("Save current slot"){saveCurrent()},margins(8))
  root.addView(button("Test fallback chain"){testChain()},margins(8))
  root.addView(text("Providers: Gemini, OpenAI, Claude, OpenRouter, NVIDIA NIM, Groq, Cerebras, Together AI, Mistral, DeepSeek and Ollama. Model availability, limits and pricing are controlled by each provider.",11,muted),margins(12))
  slotSpinner.onItemSelectedListener=listener{p->if(::enabledSwitch.isInitialized)saveCurrent(false);currentSlot=p+1;loadCurrent()}
  providerSpinner.onItemSelectedListener=listener{p->if(::modelSpinner.isInitialized)updateModels(p,null)}
  updateModels(0,null);loadCurrent()
  setContentView(ScrollView(this).apply{addView(root)})
 }

 private fun listener(block:(Int)->Unit)=object:AdapterView.OnItemSelectedListener{
  override fun onNothingSelected(parent:AdapterView<*>?)=Unit
  override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long)=block(position)
 }
 private fun updateModels(index:Int,selected:String?){
  val spec=AiProviderCatalog.providers.getOrElse(index){AiProviderCatalog.providers.first()}
  modelSpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,spec.models)
  val selectedIndex=selected?.let{spec.models.indexOf(it)}?:-1
  if(selectedIndex>=0)modelSpinner.setSelection(selectedIndex)
  if(baseUrlField.text.isNullOrBlank())baseUrlField.setText(spec.baseUrl)
  keyField.isEnabled=spec.requiresKey
  if(!spec.requiresKey)keyField.setText("")
 }
 private fun loadCurrent(){
  val slot=store.load(currentSlot)
  val pi=AiProviderCatalog.providers.indexOfFirst{it.id==slot.providerId}.coerceAtLeast(0)
  providerSpinner.setSelection(pi);updateModels(pi,slot.model)
  keyField.setText(slot.apiKey);baseUrlField.setText(slot.baseUrl.ifBlank{AiProviderCatalog.providers[pi].baseUrl})
  enabledSwitch.isChecked=slot.enabled;status.text="Editing slot $currentSlot"
 }
 private fun saveCurrent(show:Boolean=true){
  val spec=AiProviderCatalog.providers[providerSpinner.selectedItemPosition.coerceIn(0,AiProviderCatalog.providers.lastIndex)]
  val slot=AiSlot(enabledSwitch.isChecked,spec.id,modelSpinner.selectedItem?.toString()?:spec.models.first(),keyField.text.toString().trim(),baseUrlField.text.toString().trim())
  if(slot.enabled&&spec.requiresKey&&slot.apiKey.isBlank()){status.text="Add an API key before enabling this slot.";return}
  try{store.save(currentSlot,slot);if(show)status.text="Slot $currentSlot saved securely."}catch(e:Exception){status.text=e.message?:"Secure save failed."}
 }
 private fun testChain(){
  saveCurrent(false);getPreferences(MODE_PRIVATE).edit().putBoolean("local_fallback",localSwitch.isChecked).apply()
  status.text="Testing providers in fallback order…"
  Thread{try{
   val result=MultiProviderAiClient(applicationContext).generate("Reply with exactly: JARVIS connection test successful.",localSwitch.isChecked)
   runOnUiThread{status.text="Success: ${result.providerLabel} · ${result.model} · slot ${result.slot}"}
  }catch(e:Exception){runOnUiThread{status.text=e.message?:"All configured routes failed."}}}.start()
 }
 private fun chooseModelFile(){
  val i=android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply{type="*/*";addCategory(android.content.Intent.CATEGORY_OPENABLE)}
  startActivityForResult(i,7412)
 }
 @Deprecated("Document picker callback")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:android.content.Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode!=7412||resultCode!=RESULT_OK)return
  val uri=data?.data?:return;status.text="Importing model…"
  Thread{try{
   contentResolver.openInputStream(uri).use{input->
    requireNotNull(input){"Cannot read selected file."}
    val temp=File(filesDir,"pocket-agent-local-model.task.copying")
    temp.outputStream().buffered().use{output->input.copyTo(output)}
    require(temp.length()>100_000_000L){"File is too small to be the expected Gemma model."}
    val target=File(filesDir,"pocket-agent-local-model.task");if(target.exists())target.delete()
    check(temp.renameTo(target)){"Could not move model into private storage."}
   }
   runOnUiThread{status.text="Model imported. It will be tried after API slots."}
  }catch(e:Exception){runOnUiThread{status.text=e.message?:"Import failed."}}}.start()
 }
 private fun glassCard()=LinearLayout(this).apply{
  orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(16))
  background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(185,35,52,76),Color.argb(110,14,24,41))).apply{cornerRadius=dp(24).toFloat();setStroke(dp(1),Color.argb(115,190,220,255))}
  elevation=dp(5).toFloat()
 }
 private fun label(v:String)=text(v,10,Color.rgb(130,210,222),true)
 private fun text(v:String,size:Int,color:Int,bold:Boolean=false)=TextView(this).apply{text=v;textSize=size.toFloat();setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD}
 private fun spinner(values:List<String>)=Spinner(this).apply{adapter=ArrayAdapter(this@AiModelsActivity,android.R.layout.simple_spinner_dropdown_item,values)}
 private fun edit(h:String,secret:Boolean)=EditText(this).apply{
  hint=h;textSize=13f;setTextColor(ink);setHintTextColor(muted);setPadding(dp(12),dp(10),dp(12),dp(10))
  background=GradientDrawable().apply{setColor(Color.argb(65,255,255,255));cornerRadius=dp(14).toFloat();setStroke(dp(1),Color.argb(100,200,220,240))}
  if(secret)inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
 }
 private fun button(v:String,action:()->Unit)=Button(this).apply{
  text=v;isAllCaps=false;setTextColor(Color.WHITE)
  background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(126,107,245),Color.rgb(78,94,215))).apply{cornerRadius=dp(18).toFloat()}
  setOnClickListener{action()}
 }
 private fun margins(bottom:Int)=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(bottom)}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}