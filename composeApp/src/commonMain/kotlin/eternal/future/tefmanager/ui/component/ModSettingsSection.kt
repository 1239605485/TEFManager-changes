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
import androidx.compose.material.icons.rounded.CheckCircle
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
fun ModSettingsSection(mod: ModItem, store: ModSettingsStore, enabled: Boolean = true) {
    var values by remember(mod.pkgId) { mutableStateOf(store.load(mod.settings)) }
    var settingsOpen by remember { mutableStateOf(false) }
    var advancedMode by remember { mutableStateOf(false) }
    var rawJson by remember(mod.pkgId) { mutableStateOf(store.loadRaw()) }
    var jsonError by remember { mutableStateOf<String?>(null) }

    fun update(key: String, value: JsonElement) {
        values = values + (key to value)
        store.save(values)
        rawJson = store.loadRaw()
    }

    OutlinedButton(
        onClick = {
            rawJson = store.loadRaw()
            advancedMode = false
            jsonError = null
            settingsOpen = true
        },
        modifier = Modifier.padding(top = 2.dp).height(42.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, PixelLine),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = PixelPanelRaised, contentColor = PixelCyan),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("模组设置", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
    }

    if (!settingsOpen) return

    Dialog(
        onDismissRequest = { settingsOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f),
            shape = RoundedCornerShape(26.dp),
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
                        Text("MOD CONFIG // ${mod.name}", style = MaterialTheme.typography.labelSmall, color = PixelMuted)
                    }
                    Text("v${mod.version}", style = MaterialTheme.typography.labelSmall, color = PixelAmber, fontWeight = FontWeight.Bold)
                }
                PixelRule()

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ModSummaryCard(mod, enabled)

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
                        val grouped = mod.settings.groupBy { it.sectionName() }
                        listOf("配方倍率", "钓鱼", "其他").forEachIndexed { index, section ->
                            val sectionSettings = grouped[section].orEmpty()
                            if (sectionSettings.isNotEmpty()) {
                                PixelSettingsGroup(
                                    index = index + 1,
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
                                rawJson = store.defaultsRaw(mod.settings)
                                jsonError = null
                            } else {
                                values = mod.settings.associate { it.key to it.defaultValue }
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
                                        values = store.load(mod.settings)
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
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }
                                inputText = digits
                                digits.toIntOrNull()?.let { onChange(JsonPrimitive(it.coerceIn(min, max))) }
                            },
                            modifier = Modifier.width(82.dp),
                            singleLine = true,
                            suffix = { Text(unit, color = PixelAmber) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("数值") },
                            colors = pixelFieldColors()
                        )
                        TextButton(
                            onClick = { onChange(JsonPrimitive((now + setting.step.coerceAtLeast(1)).coerceIn(min, max))) },
                            modifier = Modifier.size(34.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = PixelCyan)
                        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                    }
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
private fun ModSummaryCard(mod: ModItem, enabled: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = PixelPanel), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, PixelLine)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(modifier = Modifier.size(58.dp), shape = RoundedCornerShape(10.dp), color = PixelBackground, border = BorderStroke(1.dp, PixelCyanDark)) {
                Box(contentAlignment = Alignment.Center) { Text(modGlyph(mod.name), fontSize = 30.sp) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(mod.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PixelText)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (enabled) PixelCyanDark else PixelBackground,
                        border = BorderStroke(1.dp, if (enabled) PixelCyan else PixelLine)
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (enabled) Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp), tint = PixelCyan)
                            Text(if (enabled) "已启用" else "已禁用", style = MaterialTheme.typography.labelSmall, color = if (enabled) PixelCyan else PixelMuted)
                        }
                    }
                }
                Text("v${mod.version}  |  作者：${mod.author}", style = MaterialTheme.typography.labelSmall, color = PixelMuted)
                if (mod.brieflyDescribe.isNotBlank()) Text(mod.brieflyDescribe, style = MaterialTheme.typography.bodySmall, color = PixelMuted, maxLines = 2)
            }
        }
    }
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
private fun PixelSettingsGroup(index: Int, title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = PixelPanel), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, PixelLine)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = PixelAmber, shape = RoundedCornerShape(5.dp), modifier = Modifier.size(30.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(index.toString().padStart(2, '0'), color = PixelBackground, fontSize = 11.sp, fontWeight = FontWeight.Black) }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PixelText)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = PixelMuted)
                }
                Text("//", color = PixelCyan, fontWeight = FontWeight.Black)
            }
            PixelRule()
            content()
        }
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

private val PixelBackground = Color(0xFF08121C)
private val PixelPanel = Color(0xFF101E2A)
private val PixelPanelRaised = Color(0xFF142736)
private val PixelLine = Color(0xFF27485B)
private val PixelCyan = Color(0xFF3DE2D1)
private val PixelCyanDark = Color(0xFF126D72)
private val PixelAmber = Color(0xFFF5B84B)
private val PixelText = Color(0xFFE8F5F4)
private val PixelMuted = Color(0xFF8AA7B2)
private val PixelDanger = Color(0xFFFF817C)
