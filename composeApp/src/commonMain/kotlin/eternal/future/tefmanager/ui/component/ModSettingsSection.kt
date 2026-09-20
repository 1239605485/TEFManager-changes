package eternal.future.tefmanager.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Compact entry point on the mod card; details are edited in a second-level window. */
@Composable
fun ModSettingsSection(mod: ModItem, store: ModSettingsStore, enabled: Boolean = true) {
    var values by remember(mod.pkgId) { mutableStateOf(store.load(mod.settings)) }
    var settingsOpen by remember { mutableStateOf(false) }

    fun update(key: String, value: JsonElement) {
        values = values + (key to value)
        store.save(values)
    }

    OutlinedButton(
        onClick = { settingsOpen = true },
        modifier = Modifier
            .padding(top = 2.dp)
            .height(42.dp),
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("模组设置", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
    }

    if (settingsOpen) {
        Dialog(
            onDismissRequest = { settingsOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { settingsOpen = false }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                        }
                        Column(Modifier.weight(1f)) {
                            Text("轻松泰拉设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(mod.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                        ModSummaryCard(mod, enabled)

                        val grouped = mod.settings.groupBy { it.sectionName() }
                        listOf("配方倍率", "钓鱼", "其他").forEach { section ->
                            val sectionSettings = grouped[section].orEmpty()
                            if (sectionSettings.isNotEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Tune,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column {
                                                Text(section, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                Text(
                                                    when (section) {
                                                        "配方倍率" -> "调整各类合成配方的产出数量倍率"
                                                        "钓鱼" -> "调整钓鱼相关的游戏体验"
                                                        else -> "更多功能选项配置"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                values = mod.settings.associate { it.key to it.defaultValue }
                                store.save(values)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("恢复默认") }
                        Button(
                            onClick = {
                                store.save(values)
                                settingsOpen = false
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("保存设置") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModSettingEditor(
    setting: ModItem.ModSetting,
    current: JsonElement,
    onChange: (JsonElement) -> Unit,
) {
    when (setting.type) {
        ModItem.SettingType.SWITCH -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingLabel(setting, Modifier.weight(1f))
            Switch(
                checked = current.jsonPrimitive.booleanOrNull ?: false,
                onCheckedChange = { onChange(JsonPrimitive(it)) }
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(setting.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (setting.description.isNotBlank()) Text(setting.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                    ) {
                        TextButton(
                            onClick = {
                                onChange(JsonPrimitive((now - setting.step.coerceAtLeast(1)).coerceIn(min, max)))
                            },
                            modifier = Modifier.size(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }
                                inputText = digits
                                digits.toIntOrNull()?.let { value ->
                                    onChange(JsonPrimitive(value.coerceIn(min, max)))
                                }
                            },
                            modifier = Modifier.width(82.dp),
                            singleLine = true,
                            suffix = { Text(unit) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("数值") }
                        )
                        TextButton(
                            onClick = {
                                onChange(JsonPrimitive((now + setting.step.coerceAtLeast(1)).coerceIn(min, max)))
                            },
                            modifier = Modifier.size(34.dp),
                            contentPadding = PaddingValues(0.dp)
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(modGlyph(mod.name), fontSize = 30.sp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(mod.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (enabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (enabled) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            Text(
                                if (enabled) "已启用" else "已禁用",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (enabled) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text("v${mod.version}  |  作者：${mod.author}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (mod.brieflyDescribe.isNotBlank()) {
                    Text(mod.brieflyDescribe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun SettingLabel(setting: ModItem.ModSetting, modifier: Modifier = Modifier.fillMaxWidth()) {
    Column(modifier) {
        Text(setting.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        if (setting.description.isNotBlank()) Text(setting.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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
