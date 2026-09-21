package eternal.future.tefmanager.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import eternal.future.tefmanager.model.ModItem
import eternal.future.tefmanager.utils.addon.ModSettingsStore
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The second-level editor. Visuals live here; the JSON store and native mod API stay unchanged. */
@Composable
fun ModSettingsSection(mod: ModItem, store: ModSettingsStore) {
    val schema = remember(mod.pkgId, mod.settings) {
        mod.settings.ifEmpty { terraReliefFallbackSettings(mod) }
    }
    var values by remember(mod.pkgId, schema) { mutableStateOf(store.load(schema)) }
    var settingsOpen by remember { mutableStateOf(false) }
    var advancedMode by remember { mutableStateOf(false) }
    var rawJson by remember(mod.pkgId) { mutableStateOf(store.loadRaw()) }
    var jsonError by remember { mutableStateOf<String?>(null) }
    // Keep the scroll position above the dialog content so option updates do not
    // recreate the state and jump back to the top.
    val settingsScrollState = rememberScrollState()

    fun update(key: String, value: JsonElement) {
        values = values + (key to value)
        store.save(values)
        rawJson = store.loadRaw()
    }

    IconButton(
        onClick = {
            rawJson = store.loadRaw()
            advancedMode = false
            jsonError = null
            settingsOpen = true
        },
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = PixelPanelRaised,
            contentColor = PixelCyan
        )
    ) {
        Icon(Icons.Rounded.Settings, contentDescription = "模组设置", modifier = Modifier.size(20.dp))
    }

    if (!settingsOpen) return

    Dialog(
        onDismissRequest = { settingsOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            color = PixelBackground,
            border = BorderStroke(1.dp, PixelLine)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(PixelPanel).padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { settingsOpen = false },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = PixelCyan)
                    ) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
                    Column(Modifier.weight(1f)) {
                        Text("轻松泰拉设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PixelText)
                    }
                    Text("v${mod.version}", style = MaterialTheme.typography.labelSmall, color = PixelMuted)
                }
                PixelRule()

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(settingsScrollState).padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(PixelPanel, RoundedCornerShape(12.dp)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PixelTab("图形化设置", !advancedMode, Modifier.weight(1f)) {
                            advancedMode = false
                            jsonError = null
                        }
                        PixelTab("高级 JSON", advancedMode, Modifier.weight(1f)) {
                            rawJson = store.loadRaw()
                            advancedMode = true
                            jsonError = null
                        }
                    }

                    if (advancedMode) {
                        AdvancedJsonEditor(
                            rawJson = rawJson,
                            error = jsonError,
                            onChange = { rawJson = it; jsonError = null },
                            onFormat = {
                                store.formatRaw(rawJson).fold(
                                    onSuccess = { rawJson = it; jsonError = null },
                                    onFailure = { jsonError = it.message ?: "JSON 格式错误" }
                                )
                            }
                        )
                    } else {
                                        val grouped = schema.groupBy { it.sectionName() }
                        listOf("配方倍率", "钓鱼", "其他").forEach { section ->
                            val sectionSettings = grouped[section].orEmpty()
                            if (sectionSettings.isNotEmpty()) {
                                PixelSettingsGroup(
                                    title = section,
                                    description = when (section) {
                                        "配方倍率" -> "调整各类合成配方的产出数量倍率"
                                        "钓鱼" -> "调整钓鱼相关的游戏体验"
                                        else -> "更多功能选项配置"
                                    }
                                ) {
                                    sectionSettings.forEach { setting ->
                                        ModSettingEditor(
                                             setting = setting,
                                            current = values[setting.key] ?: setting.defaultValue,
                                            onChange = { update(setting.key, it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                PixelRule()
                Row(
                    modifier = Modifier.fillMaxWidth().background(PixelPanel).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (advancedMode) {
                                rawJson = store.defaultsRaw(schema)
                                jsonError = null
                            } else {
                                values = schema.associate { it.key to it.defaultValue }
                                store.save(values)
                                rawJson = store.loadRaw()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PixelLine),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PixelMuted)
                    ) { Text("恢复默认") }
                    Button(
                        onClick = {
                            if (advancedMode) {
                                store.saveRaw(rawJson).fold(
                                    onSuccess = {
                                        values = store.load(schema)
                                        jsonError = null
                                        advancedMode = false
                                        settingsOpen = false
                                    },
                                    onFailure = { jsonError = it.message ?: "JSON 格式错误" }
                                )
                            } else {
                                store.save(values)
                                settingsOpen = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PixelCyan, contentColor = PixelBackground)
                    ) { Text("保存设置", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun AdvancedJsonEditor(rawJson: String, error: String?, onChange: (String) -> Unit, onFormat: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = PixelPanel), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, PixelLine)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("// 高级 JSON 配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PixelCyan)
            Text(
                "可编辑 values 下的数字、开关、字符串以及多级对象。保存前会检查 JSON 格式；错误内容不会覆盖原配置。",
                style = MaterialTheme.typography.bodySmall,
                color = PixelMuted
            )
            OutlinedTextField(
                value = rawJson,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth().height(360.dp),
                label = { Text("config.json") },
                placeholder = { Text("请输入 JSON 配置") },
                singleLine = false,
                minLines = 14,
                maxLines = 24,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                colors = pixelFieldColors()
            )
            if (error != null) Text("JSON 错误：$error", style = MaterialTheme.typography.bodySmall, color = PixelDanger)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFormat, colors = ButtonDefaults.textButtonColors(contentColor = PixelCyan)) { Text("格式化 JSON") }
            }
        }
    }
}

@Composable
private fun ModSettingEditor(setting: ModItem.ModSetting, current: JsonElement, onChange: (JsonElement) -> Unit) {
    when (setting.type) {
        ModItem.SettingType.SWITCH -> Row(
            modifier = Modifier.fillMaxWidth().background(PixelPanelRaised, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingLabel(setting, Modifier.weight(1f))
            Switch(
                checked = current.jsonPrimitive.booleanOrNull ?: false,
                onCheckedChange = { onChange(JsonPrimitive(it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PixelBackground,
                    checkedTrackColor = PixelCyan,
                    checkedBorderColor = PixelCyan,
                    uncheckedThumbColor = PixelMuted,
                    uncheckedTrackColor = PixelBackground,
                    uncheckedBorderColor = PixelLine
                )
            )
        }

        ModItem.SettingType.INTEGER -> {
            val min = setting.min.takeIf { it > 0 } ?: 1
            val max = setting.max.takeIf { it > 0 } ?: 10
            val now = (current.jsonPrimitive.intOrNull ?: min).coerceIn(min, max)
            val unit = setting.unit.ifBlank { "×" }
            var inputText by remember(setting.key, now) { mutableStateOf(now.toString()) }
            LaunchedEffect(now) { inputText = now.toString() }
            Row(
                modifier = Modifier.fillMaxWidth().background(PixelPanelRaised, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(setting.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = PixelText)
                    if (setting.description.isNotBlank()) Text(setting.description, style = MaterialTheme.typography.bodySmall, color = PixelMuted)
                }
                if (unit == "×") {
                    // Multipliers are the only values with quick +/- controls.
                    // The field stays editable for precise values.
                    Surface(shape = RoundedCornerShape(10.dp), color = PixelBackground, border = BorderStroke(1.dp, PixelLine)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                        ) {
                            TextButton(
                                onClick = { onChange(JsonPrimitive((now - setting.step.coerceAtLeast(1)).coerceIn(min, max))) },
                                modifier = Modifier.size(34.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = PixelCyan)
                            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                            NumberField(inputText, unit, onInput = { raw ->
                                val digits = raw.filter { it.isDigit() }
                                inputText = digits
                                digits.toIntOrNull()?.let { onChange(JsonPrimitive(it.coerceIn(min, max))) }
                            })
                            TextButton(
                                onClick = { onChange(JsonPrimitive((now + setting.step.coerceAtLeast(1)).coerceIn(min, max))) },
                                modifier = Modifier.size(34.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = PixelCyan)
                            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                        }
                    }
                } else {
                    // Percentages and quantities deliberately remain clean manual fields.
                    NumberField(inputText, unit, onInput = { raw ->
                        val digits = raw.filter { it.isDigit() }
                        inputText = digits
                        digits.toIntOrNull()?.let { onChange(JsonPrimitive(it.coerceIn(min, max))) }
                    })
                }
            }
        }

        ModItem.SettingType.CHOICE -> {
            SettingLabel(setting)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                setting.options.forEach { option ->
                    AssistChip(
                        onClick = { onChange(JsonPrimitive(option.value)) },
                        label = {
                            val selected = current.jsonPrimitive.contentOrNull == option.value
                            Text(if (selected) "✓ ${option.label}" else option.label)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    unit: String,
    onInput: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onInput,
        modifier = Modifier.width(82.dp),
        singleLine = true,
        suffix = { Text(unit, color = PixelAmber) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        label = { Text("数值") },
        supportingText = null,
        colors = pixelFieldColors()
    )
}

@Composable
private fun SettingLabel(setting: ModItem.ModSetting, modifier: Modifier = Modifier.fillMaxWidth()) {
    Column(modifier) {
        Text(setting.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = PixelText)
        if (setting.description.isNotBlank()) Text(setting.description, style = MaterialTheme.typography.bodySmall, color = PixelMuted)
    }
}

@Composable
private fun PixelTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(38.dp).background(if (selected) PixelCyanDark else Color.Transparent, RoundedCornerShape(9.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (selected) PixelText else PixelMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
private fun PixelRule() {
    Row(Modifier.fillMaxWidth().height(1.dp).background(PixelLine)) {
        Box(Modifier.width(48.dp).fillMaxHeight().background(PixelCyan))
    }
}

@Composable
private fun PixelSettingsGroup(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PixelText)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(1.dp).background(PixelLine))
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = PixelMuted)
        content()
    }
}

@Composable
private fun pixelFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PixelCyan,
    unfocusedBorderColor = PixelLine,
    focusedLabelColor = PixelCyan,
    unfocusedLabelColor = PixelMuted,
    focusedTextColor = PixelText,
    unfocusedTextColor = PixelText,
    cursorColor = PixelCyan,
    focusedContainerColor = PixelBackground,
    unfocusedContainerColor = PixelBackground
)

/**
 * Older EasyCraft/TerraRelief packages did not declare their settings in
 * Info.json, so the settings entry was never rendered at all. Keep a small
 * compatibility schema for that package; newer packages still use their own
 * declared schema unchanged.
 */
private fun terraReliefFallbackSettings(mod: ModItem): List<ModItem.ModSetting> =
    if (mod.pkgId != "com.celso.terrarelief") emptyList() else listOf(
        ModItem.ModSetting(
            key = "building_material_multiplier",
            title = "建筑材料合成产出",
            description = "调整建筑材料的合成产出数量",
            type = ModItem.SettingType.INTEGER,
            defaultValue = JsonPrimitive(3), min = 1, max = 99, step = 1, unit = "×"
        ),
        ModItem.ModSetting(
            key = "torch_multiplier",
            title = "火把合成产出",
            description = "调整火把的合成产出数量",
            type = ModItem.SettingType.INTEGER,
            defaultValue = JsonPrimitive(2), min = 1, max = 99, step = 1, unit = "×"
        ),
        ModItem.ModSetting(
            key = "potion_multiplier",
            title = "常用药水产出",
            description = "调整常用药水的合成产出数量",
            type = ModItem.SettingType.INTEGER,
            defaultValue = JsonPrimitive(5), min = 1, max = 99, step = 1, unit = "×"
        ),
        ModItem.ModSetting(
            key = "boss_multiplier",
            title = "Boss 合成产出",
            description = "调整 Boss 合成材料的产出数量",
            type = ModItem.SettingType.INTEGER,
            defaultValue = JsonPrimitive(50), min = 0, max = 100, step = 5, unit = "%"
        ),
        ModItem.ModSetting(
            key = "fishing_enabled",
            title = "启用钓鱼增强",
            description = "增加钓鱼相关的收益",
            type = ModItem.SettingType.SWITCH,
            defaultValue = JsonPrimitive(true)
        ),
        ModItem.ModSetting(
            key = "fish_multiplier",
            title = "普通鱼类数量",
            description = "调整普通鱼类的获取数量",
            type = ModItem.SettingType.INTEGER,
            defaultValue = JsonPrimitive(2), min = 1, max = 99, step = 1, unit = "×"
        )
    )

private fun ModItem.ModSetting.sectionName(): String = when {
    key.startsWith("fishing_") || key == "bait_save_enabled" -> "钓鱼"
    key.contains("building") || key.contains("potion") || key.contains("boss") || key.contains("torch") -> "配方倍率"
    else -> "其他"
}

private fun modGlyph(name: String): String {
    val lower = name.lowercase()
    return when {
        name.contains("火把") || lower.contains("torch") -> "🔥"
        name.contains("树") || lower.contains("tree") -> "🌳"
        name.contains("箱") || lower.contains("chest") -> "📦"
        else -> "🧩"
    }
}

// Light palette: keep the pixel-console layout, but match TEFManager's original
// white/lavender appearance instead of forcing a separate dark theme.
private val PixelBackground = Color(0xFFF5F7FF)
private val PixelPanel = Color(0xFFFFFFFF)
private val PixelPanelRaised = Color(0xFFEEF3FF)
private val PixelLine = Color(0xFFC7D0E5)
private val PixelCyan = Color(0xFF1264E8)
private val PixelCyanDark = Color(0xFFDCE8FF)
private val PixelAmber = Color(0xFFE79A00)
private val PixelText = Color(0xFF17213A)
private val PixelMuted = Color(0xFF5E6A80)
private val PixelDanger = Color(0xFFD13636)
