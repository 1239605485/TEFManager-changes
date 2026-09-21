package eternal.future.tefmanager

import android.app.*
import android.app.usage.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.text.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import eternal.future.tefmanager.model.ModItem
import eternal.future.tefmanager.utils.addon.*
import kotlinx.serialization.json.*
import kotlin.math.*

/** Small, game-only overlay. Every change writes the mod's ordinary config.json. */
class ModOverlaySettingsService : Service() {
    companion object {
        const val EXTRA_GAME_PACKAGE = "overlay_game_package"
        private const val CHANNEL = "mod_overlay_service"; private const val NOTIFICATION = 1002
        private const val TERRARELIEF = "com.celso.terrarelief"; private const val CHECK_MS = 750L
        private const val CREAM = 0xFFFFFBF2.toInt(); private const val LAVENDER = 0xFFEAE6FF.toInt()
        private const val PURPLE = 0xFF8C85D8.toInt(); private const val INK = 0xFF273052.toInt(); private const val BLUE = 0xFF2376EE.toInt()
    }
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var bubble: View? = null; private var hotspot: View? = null; private var panel: View? = null
    private var bubbleX = 0; private var bubbleY = 0; private var gamePackage = ""; private var seenGame = false
    private val checkForeground = object : Runnable { override fun run() { if (leftGame()) stopSelf() else handler.postDelayed(this, CHECK_MS) } }

    override fun onCreate() { super.onCreate(); wm = getSystemService(WINDOW_SERVICE) as WindowManager; bubbleX = dp(12); bubbleY = dp(164); channel()
        startForeground(NOTIFICATION, NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.mipmap.ic_launcher_round).setContentTitle("游戏内模组设置已开启").setContentText("离开泰拉瑞亚时会自动关闭").setPriority(NotificationManager.IMPORTANCE_LOW).build()) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { gamePackage = intent?.getStringExtra(EXTRA_GAME_PACKAGE).orEmpty()
        if (Settings.canDrawOverlays(this) && bubble == null && hotspot == null && panel == null) showBubble(); handler.removeCallbacks(checkForeground); handler.postDelayed(checkForeground, CHECK_MS); return START_NOT_STICKY }
    override fun onDestroy() { handler.removeCallbacks(checkForeground); remove(bubble); remove(hotspot); remove(panel); bubble = null; hotspot = null; panel = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun leftGame(): Boolean {
        if (gamePackage.isBlank()) return false
        val events = (getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager).queryEvents(System.currentTimeMillis() - 4000, System.currentTimeMillis())
        val event = UsageEvents.Event(); var last: String? = null; var lastTime = 0L
        while (events.hasNextEvent()) { events.getNextEvent(event); val foreground = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || (Build.VERSION.SDK_INT >= 29 && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (foreground && event.timeStamp >= lastTime) { last = event.packageName; lastTime = event.timeStamp } }
        return when (last) { gamePackage -> { seenGame = true; false }; null -> false; else -> seenGame }
    }

    private fun showBubble() {
        if (bubble != null) return
        val image = resources.getIdentifier("overlay_character", "drawable", packageName)
        val v = ImageView(this).apply {
            if (image != 0) setImageResource(image)
            else setImageResource(android.R.drawable.ic_menu_manage)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            contentDescription = "打开模组设置"
            elevation = dp(3).toFloat()
        }
        val p = params(dp(56), dp(56), false).apply { gravity = Gravity.TOP or Gravity.START; x = bubbleX; y = bubbleY }
        var dx = 0f; var dy = 0f; var sx = 0; var sy = 0
        v.setOnTouchListener { _, e -> when (e.action) {
            MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = p.x; sy = p.y; true }
            MotionEvent.ACTION_MOVE -> { bubbleX = sx + (e.rawX - dx).roundToInt(); bubbleY = sy + (e.rawY - dy).roundToInt(); p.x = bubbleX; p.y = bubbleY; wm.updateViewLayout(v,p); true }
            MotionEvent.ACTION_UP -> { if (abs(e.rawX-dx) < dp(5) && abs(e.rawY-dy) < dp(5)) showPanel(); true }; else -> true } }
        bubble = v; wm.addView(v,p)
    }
    private fun hideBubble() { remove(bubble); bubble = null; if (hotspot != null) return
        val v = View(this).apply { alpha = .01f; setOnClickListener { remove(hotspot); hotspot = null; showBubble() } }; hotspot = v
        wm.addView(v,params(dp(18),dp(18),false).apply { gravity = Gravity.TOP or Gravity.START; x = bubbleX+dp(11); y = bubbleY+dp(11) }) }

    private fun showPanel() { remove(bubble); bubble = null
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8),dp(8),dp(8),dp(8)); background = shape(CREAM,16,PURPLE); elevation = dp(8).toFloat() }
        list(shell); panel = shell; wm.addView(shell,params(dp(272),-2,true).apply { gravity = Gravity.CENTER }) }
    private fun list(shell: LinearLayout) { shell.removeAllViews(); shell.addView(header("已启用模组",null))
        val mods = active(); if (mods.isEmpty()) shell.addView(label("没有已启用且可配置的模组",12,INK).apply { gravity=Gravity.CENTER; setPadding(0,dp(40),0,dp(40)) })
        else mods.forEach { e -> shell.addView(Button(this).apply { text="⚙  ${e.mod.name}"; textSize=13f; isAllCaps=false; gravity=Gravity.CENTER_VERTICAL; setTextColor(INK); background=shape(LAVENDER,10,PURPLE); setPadding(dp(10),0,dp(8),0); setOnClickListener { settings(shell,e) } }, LinearLayout.LayoutParams(-1,dp(42)).apply { topMargin=dp(7) }) } }
    private fun settings(shell: LinearLayout, entry: OverlayMod) { shell.removeAllViews(); shell.addView(header(entry.mod.name) { list(shell) }); shell.addView(label("修改后立即保存并应用",10,0xFF6B6B83.toInt()).apply { setPadding(dp(4),0,0,dp(5)) })
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; val values=entry.manager.getSettingsStore(entry.mod.pkgId).load(entry.mod.settings)
        entry.mod.settings.forEach { s -> content.addView(row(entry,s,values[s.key]?:s.defaultValue),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(5) }) }
        shell.addView(ScrollView(this).apply { isFillViewport = false; addView(content) },LinearLayout.LayoutParams(-1,dp(238))) }
    private fun header(title:String,back:(()->Unit)?): View = LinearLayout(this).apply {
        gravity=Gravity.CENTER_VERTICAL
        val drag = label(title,14,INK).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(3),0,0,0) }
        if(back!=null)addView(button("‹"){back()},LinearLayout.LayoutParams(dp(30),dp(32)))
        drag.setOnTouchListener { _, event -> dragPanel(event); true }
        addView(drag,LinearLayout.LayoutParams(0,dp(32),1f))
        addView(button("隐藏"){ close(false); hideBubble() },LinearLayout.LayoutParams(dp(52),dp(32)))
        addView(button("关闭"){ close(true) },LinearLayout.LayoutParams(dp(52),dp(32)))
        setOnTouchListener { _, event -> dragPanel(event); true }
    }
    private fun row(e:OverlayMod,s:ModItem.ModSetting,current:JsonElement):View { val r=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(7),dp(5),dp(5),dp(5)); background=shape(LAVENDER,9) }
        r.addView(LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; addView(label(s.title,11,INK)); if(s.description.isNotBlank())addView(label(s.description,9,0xFF68677D.toInt(),2)) },LinearLayout.LayoutParams(0,-2,1f))
        when(s.type) { ModItem.SettingType.SWITCH -> r.addView(Switch(this).apply { scaleX=.72f;scaleY=.72f;isChecked=current.jsonPrimitive.booleanOrNull?:false;setOnCheckedChangeListener{_,v->save(e,s.key,JsonPrimitive(v))} },LinearLayout.LayoutParams(dp(46),dp(30)))
            ModItem.SettingType.INTEGER -> integer(r,e,s,current.jsonPrimitive.intOrNull?:s.min)
            ModItem.SettingType.CHOICE -> r.addView(button(s.options.firstOrNull { it.value==current.jsonPrimitive.content }?.label?:current.jsonPrimitive.content){ val next=s.options.firstOrNull { it.value!=current.jsonPrimitive.content }?:return@button;save(e,s.key,JsonPrimitive(next.value)); settings(panel as? LinearLayout?:return@button,e) }) }; return r }
    private fun integer(r:LinearLayout,e:OverlayMod,s:ModItem.ModSetting,initial:Int) { val min=s.min.takeIf{it>0}?:1;val max=s.max.takeIf{it>0}?:99
        val f=EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER; setText(initial.coerceIn(min,max).toString()); textSize=12f;gravity=Gravity.CENTER;setTextColor(INK);setPadding(0,0,0,0);background=shape(CREAM,6,PURPLE);setSelectAllOnFocus(true) }
        f.addTextChangedListener(object:TextWatcher { override fun beforeTextChanged(a:CharSequence?,b:Int,c:Int,d:Int)=Unit;override fun afterTextChanged(a:Editable?)=Unit;override fun onTextChanged(a:CharSequence?,b:Int,c:Int,d:Int){ a?.toString()?.toIntOrNull()?.let { save(e,s.key,JsonPrimitive(it.coerceIn(min,max))) } } });f.setOnFocusChangeListener{_,focused->if(!focused)clamp(f,e,s,min,max)}
        if(s.unit=="×")r.addView(button("−"){change(f,-s.step.coerceAtLeast(1),min,max)},LinearLayout.LayoutParams(dp(25),dp(30)));r.addView(f,LinearLayout.LayoutParams(dp(42),dp(30)));if(s.unit=="×")r.addView(button("+"){change(f,s.step.coerceAtLeast(1),min,max)},LinearLayout.LayoutParams(dp(25),dp(30))) }
    private fun change(f:EditText,d:Int,min:Int,max:Int){f.setText(((f.text.toString().toIntOrNull()?:min)+d).coerceIn(min,max).toString())}
    private fun clamp(f:EditText,e:OverlayMod,s:ModItem.ModSetting,min:Int,max:Int){val v=(f.text.toString().toIntOrNull()?:min).coerceIn(min,max);if(f.text.toString()!=v.toString())f.setText(v.toString());save(e,s.key,JsonPrimitive(v))}
    private var panelDownX = 0f; private var panelDownY = 0f; private var panelStartX = 0; private var panelStartY = 0
    private fun dragPanel(event: MotionEvent): Boolean {
        val v = panel ?: return true
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { panelDownX = event.rawX; panelDownY = event.rawY; panelStartX = lp.x; panelStartY = lp.y }
            MotionEvent.ACTION_MOVE -> { lp.x = panelStartX + (event.rawX - panelDownX).roundToInt(); lp.y = panelStartY + (event.rawY - panelDownY).roundToInt(); wm.updateViewLayout(v, lp) }
        }
        return true
    }
    private fun button(t:String,click:()->Unit)=Button(this).apply { text=t;textSize=10f;isAllCaps=false;setPadding(dp(2),0,dp(2),0);minWidth=0;minHeight=0;minimumWidth=0;minimumHeight=0;setTextColor(if(t=="关闭")0xFFB53C3C.toInt()else BLUE);background=shape(CREAM,6,PURPLE);setOnClickListener{click()} }
    private fun save(e:OverlayMod,k:String,v:JsonElement){val store=e.manager.getSettingsStore(e.mod.pkgId);store.save(store.load(e.mod.settings).toMutableMap().apply{put(k,v)})}
    private fun active():List<OverlayMod>{AddonManager.refreshModManager(ModLoaderManager.packs);return AddonManager.modManagersList.values.flatMap{m->m.packs.filter{m.isEnabled(it.pkgId)&&it.settings.isNotEmpty()&&it.pkgId==TERRARELIEF}.map{OverlayMod(m,it)}}}
    private fun close(show:Boolean=true){remove(panel);panel=null;if(show&&bubble==null&&hotspot==null)showBubble()};private fun params(w:Int,h:Int,focus:Boolean)=WindowManager.LayoutParams(w,h,if(Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,if(focus)WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT)
    private fun remove(v:View?){if(v!=null)runCatching{wm.removeView(v)}};private fun label(t:String,s:Int,c:Int,lines:Int=1)=TextView(this).apply{text=t;textSize=s.toFloat();setTextColor(c);maxLines=lines};private fun shape(fill:Int,r:Int,stroke:Int?=null)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(r).toFloat();stroke?.let{setStroke(dp(1),it)}};private fun dp(v:Int)=(v*resources.displayMetrics.density).roundToInt();private fun channel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"游戏内模组设置",NotificationManager.IMPORTANCE_LOW))};private data class OverlayMod(val manager:ModManager,val mod:ModItem)
}
