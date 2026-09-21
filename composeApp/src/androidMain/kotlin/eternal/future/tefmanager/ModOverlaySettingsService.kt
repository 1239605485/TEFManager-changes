package eternal.future.tefmanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationCompat
import eternal.future.tefmanager.model.ModItem
import eternal.future.tefmanager.utils.addon.AddonManager
import eternal.future.tefmanager.utils.addon.ModLoaderManager
import eternal.future.tefmanager.utils.addon.ModManager
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/**
 * Android system overlay used while Terraria is in front.  It intentionally
 * owns no configuration state: every control writes the same config.json used
 * by TEFManager, so TerraRelief's existing file hot-reload sees the change.
 */
class ModOverlaySettingsService : Service() {
    companion object {
        private const val CHANNEL_ID = "mod_overlay_service"
        private const val NOTIFICATION_ID = 1002
        private const val TERRARELIEF_ID = "com.celso.terrarelief"
    }

    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("游戏内模组设置已开启")
            .setContentText("点击悬浮按钮可实时调整已启用模组")
            .setPriority(NotificationManager.IMPORTANCE_LOW)
            .build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Settings.canDrawOverlays(this) && bubble == null && panel == null) showBubble()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeOverlay(bubble)
        removeOverlay(panel)
        bubble = null
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showBubble() {
        val view = TextView(this).apply {
            text = "⚙"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(0xFF1264E8.toInt(), 28)
            elevation = dp(8).toFloat()
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        val params = overlayParams(dp(56), dp(56), focusable = false).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(14)
            y = dp(180)
        }
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downX).roundToInt()
                    params.y = startY + (event.rawY - downY).roundToInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (kotlin.math.abs(event.rawX - downX) < dp(6) && kotlin.math.abs(event.rawY - downY) < dp(6)) {
                        showPanel()
                    }
                    true
                }
                else -> true
            }
        }
        bubble = view
        windowManager.addView(view, params)
    }

    private fun showPanel() {
        removeOverlay(bubble)
        bubble = null
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.WHITE, 22, 0xFFB9C7E2.toInt())
            elevation = dp(10).toFloat()
        }
        renderModList(shell)
        panel = shell
        windowManager.addView(shell, overlayParams(dp(332), WindowManager.LayoutParams.WRAP_CONTENT, focusable = true).apply {
            gravity = Gravity.CENTER
        })
    }

    private fun renderModList(shell: LinearLayout) {
        shell.removeAllViews()
        shell.addView(header("已启用模组", "关闭") { closePanel() })
        val mods = activeConfigurableMods()
        if (mods.isEmpty()) {
            shell.addView(text("没有已启用且可配置的模组", 14, 0xFF5E6A80.toInt()).apply {
                setPadding(dp(4), dp(22), dp(4), dp(22))
                gravity = Gravity.CENTER
            })
            return
        }
        mods.forEach { entry ->
            shell.addView(Button(this).apply {
                text = entry.mod.name
                isAllCaps = false
                setTextColor(0xFF17213A.toInt())
                background = rounded(0xFFEEF3FF.toInt(), 12, 0xFFC7D0E5.toInt())
                setOnClickListener { renderSettings(shell, entry) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(8)
            })
        }
    }

    private fun renderSettings(shell: LinearLayout, entry: OverlayMod) {
        shell.removeAllViews()
        shell.addView(header(entry.mod.name, "返回") { renderModList(shell) })
        shell.addView(text("每次改动都会立即写入配置并应用", 12, 0xFF5E6A80.toInt()).apply {
            setPadding(0, 0, 0, dp(8))
        })
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val values = entry.manager.getSettingsStore(entry.mod.pkgId).load(entry.mod.settings)
        entry.mod.settings.forEach { setting ->
            content.addView(settingRow(entry, setting, values[setting.key] ?: setting.defaultValue), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) })
        }
        scroll.addView(content)
        shell.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(400)))
    }

    private fun settingRow(entry: OverlayMod, setting: ModItem.ModSetting, current: kotlinx.serialization.json.JsonElement): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(8), dp(8))
            background = rounded(0xFFF5F7FF.toInt(), 12)
        }
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(setting.title, 14, 0xFF17213A.toInt()))
            if (setting.description.isNotBlank()) addView(text(setting.description, 11, 0xFF5E6A80.toInt(), 2))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        when (setting.type) {
            ModItem.SettingType.SWITCH -> row.addView(Switch(this).apply {
                isChecked = current.jsonPrimitive.booleanOrNull ?: false
                setOnCheckedChangeListener { _, checked -> save(entry, setting.key, JsonPrimitive(checked)) }
            })
            ModItem.SettingType.INTEGER -> addIntegerControl(row, entry, setting, current.jsonPrimitive.intOrNull ?: setting.min)
            ModItem.SettingType.CHOICE -> row.addView(Button(this).apply {
                var selectedValue = current.jsonPrimitive.content
                text = setting.options.firstOrNull { it.value == selectedValue }?.label ?: selectedValue
                textSize = 12f
                isAllCaps = false
                setOnClickListener {
                    if (setting.options.isEmpty()) return@setOnClickListener
                    val index = setting.options.indexOfFirst { option -> option.value == selectedValue }
                    val next = setting.options[if (index >= 0) (index + 1) % setting.options.size else 0]
                    text = next.label
                    selectedValue = next.value
                    save(entry, setting.key, JsonPrimitive(next.value))
                }
            })
        }
        return row
    }

    private fun addIntegerControl(row: LinearLayout, entry: OverlayMod, setting: ModItem.ModSetting, initial: Int) {
        val min = setting.min.takeIf { it > 0 } ?: 1
        val max = setting.max.takeIf { it > 0 } ?: 99
        val field = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(initial.coerceIn(min, max).toString())
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFF17213A.toInt())
            background = rounded(Color.WHITE, 8, 0xFFC7D0E5.toInt())
            setSelectAllOnFocus(true)
            setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveInteger(entry, setting, text.toString(), min, max, this) }
        }
        if (setting.unit == "×") {
            row.addView(stepButton("−") { changeInteger(entry, setting, field, -setting.step.coerceAtLeast(1), min, max) })
        }
        row.addView(field, LinearLayout.LayoutParams(dp(58), dp(42)))
        if (setting.unit == "×") {
            row.addView(stepButton("+") { changeInteger(entry, setting, field, setting.step.coerceAtLeast(1), min, max) })
        }
    }

    private fun stepButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; textSize = 18f; isAllCaps = false; setPadding(0, 0, 0, 0); setOnClickListener { onClick() }
    }

    private fun changeInteger(entry: OverlayMod, setting: ModItem.ModSetting, field: EditText, delta: Int, min: Int, max: Int) {
        val next = ((field.text.toString().toIntOrNull() ?: min) + delta).coerceIn(min, max)
        field.setText(next.toString())
        save(entry, setting.key, JsonPrimitive(next))
    }

    private fun saveInteger(entry: OverlayMod, setting: ModItem.ModSetting, raw: String, min: Int, max: Int, field: EditText) {
        val value = (raw.toIntOrNull() ?: min).coerceIn(min, max)
        field.setText(value.toString())
        save(entry, setting.key, JsonPrimitive(value))
    }

    private fun save(entry: OverlayMod, key: String, value: kotlinx.serialization.json.JsonElement) {
        val store = entry.manager.getSettingsStore(entry.mod.pkgId)
        val updated = store.load(entry.mod.settings).toMutableMap().apply { put(key, value) }
        store.save(updated)
    }

    private fun activeConfigurableMods(): List<OverlayMod> {
        AddonManager.refreshModManager(ModLoaderManager.packs)
        return AddonManager.modManagersList.values.flatMap { manager ->
            manager.packs.filter { mod ->
                manager.isEnabled(mod.pkgId) && mod.settings.isNotEmpty() && mod.pkgId == TERRARELIEF_ID
            }.map { OverlayMod(manager, it) }
        }
    }

    private fun header(title: String, action: String, onAction: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(text(title, 18, 0xFF17213A.toInt()), LinearLayout.LayoutParams(0, dp(40), 1f))
        addView(Button(this@ModOverlaySettingsService).apply { text = action; isAllCaps = false; setOnClickListener { onAction() } })
    }

    private fun closePanel() { removeOverlay(panel); panel = null; if (bubble == null) showBubble() }

    private fun overlayParams(width: Int, height: Int, focusable: Boolean): WindowManager.LayoutParams = WindowManager.LayoutParams(
        width, height,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        if (focusable) WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSLUCENT
    )

    private fun removeOverlay(view: View?) { if (view != null) runCatching { windowManager.removeView(view) } }

    private fun text(value: String, size: Int, color: Int, lines: Int = 1) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); maxLines = lines
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(1), it) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "游戏内模组设置", NotificationManager.IMPORTANCE_LOW
            ))
        }
    }

    private data class OverlayMod(val manager: ModManager, val mod: ModItem)
}
