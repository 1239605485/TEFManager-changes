package eternal.future.tefmanager.utils.addon

import eternal.future.tefmanager.model.ModItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okio.FileSystem
import okio.Path
import okio.buffer

/** Settings live beside the installed mod, so they are available to its private_dir. */
class ModSettingsStore(private val privateDir: Path) {
    private val fileSystem = FileSystem.SYSTEM
    private val json = Json { prettyPrint = true }
    private val configPath = privateDir / "config.json"

    /** Load the complete document so unknown/custom JSON fields are preserved. */
    fun loadDocument(): JsonObject? = try {
        if (!fileSystem.exists(configPath)) null
        else json.parseToJsonElement(fileSystem.source(configPath).buffer().use { it.readUtf8() }) as? JsonObject
    } catch (_: Exception) {
        null
    }

    fun load(schema: List<ModItem.ModSetting>): Map<String, JsonElement> {
        // Do not use Map.orEmpty() here: it widens JsonObject to Map<String, JsonElement>,
        // while the dotted-path helpers intentionally require a JsonObject.
        val saved = (loadDocument()?.get("values") as? JsonObject) ?: JsonObject(emptyMap())
        return schema.associate { setting ->
            setting.key to (getPath(saved, setting.key) ?: setting.defaultValue)
        }
    }

    fun save(values: Map<String, JsonElement>) {
        val oldValues = (loadDocument()?.get("values") as? JsonObject) ?: JsonObject(emptyMap())
        val mergedValues = values.entries.fold(oldValues) { document, (key, value) ->
            setPath(document, key, value)
        }
        val payload = buildJsonObject {
            loadDocument()?.forEach { (key, value) ->
                if (key != "values" && key != "schemaVersion") put(key, value)
            }
            put("schemaVersion", JsonPrimitive(1))
            put("values", JsonObject(mergedValues))
        }
        writeDocument(payload)
    }

    /** Pretty JSON used by the advanced editor. */
    fun loadRaw(): String {
        val document = loadDocument() ?: buildJsonObject {
            put("schemaVersion", JsonPrimitive(1))
            put("values", JsonObject(emptyMap()))
        }
        return json.encodeToString(JsonObject.serializer(), document)
    }

    /** Default document for the reset action in the advanced editor. */
    fun defaultsRaw(schema: List<ModItem.ModSetting>): String {
        val defaults = schema.fold(JsonObject(emptyMap())) { document, setting ->
            setPath(document, setting.key, setting.defaultValue)
        }
        val document = buildJsonObject {
            put("schemaVersion", JsonPrimitive(1))
            put("values", defaults)
        }
        return json.encodeToString(JsonObject.serializer(), document)
    }

    /** Format JSON without writing it, returning a human-readable error on failure. */
    fun formatRaw(raw: String): Result<String> = runCatching {
        val element = json.parseToJsonElement(raw)
        val objectValue = element as? JsonObject
            ?: error("根节点必须是 JSON 对象")
        validateDocument(objectValue)
        json.encodeToString(JsonObject.serializer(), objectValue)
    }

    /** Validate and atomically save a complete advanced JSON document. */
    fun saveRaw(raw: String): Result<Unit> = runCatching {
        val element = json.parseToJsonElement(raw)
        val objectValue = element as? JsonObject
            ?: error("根节点必须是 JSON 对象")
        validateDocument(objectValue)
        writeDocument(normalizeDocument(objectValue))
    }

    private fun validateDocument(document: JsonObject) {
        val values = document["values"]
        if (values != null && values !is JsonObject) {
            error("values 必须是 JSON 对象")
        }
    }

    private fun normalizeDocument(document: JsonObject): JsonObject {
        var normalized = document
        if (normalized["schemaVersion"] == null) {
            normalized = JsonObject(normalized + ("schemaVersion" to JsonPrimitive(1)))
        }
        if (normalized["values"] == null) {
            normalized = JsonObject(normalized + ("values" to JsonObject(emptyMap())))
        }
        return normalized
    }

    private fun getPath(document: JsonObject, path: String): JsonElement? {
        var current: JsonElement = document
        for (part in path.split('.').filter { it.isNotBlank() }) {
            current = (current as? JsonObject)?.get(part) ?: return null
        }
        return current
    }

    private fun setPath(document: JsonObject, path: String, value: JsonElement): JsonObject {
        val parts = path.split('.').filter { it.isNotBlank() }
        if (parts.isEmpty()) return document
        if (parts.size == 1) return JsonObject(document + (parts.first() to value))
        val head = parts.first()
        val child = document[head] as? JsonObject ?: JsonObject(emptyMap())
        val updatedChild = setPath(child, parts.drop(1).joinToString("."), value)
        return JsonObject(document + (head to updatedChild))
    }

    private fun writeDocument(document: JsonObject) {
        fileSystem.createDirectories(privateDir)
        val tempPath = privateDir / "config.json.tmp"
        fileSystem.sink(tempPath).buffer().use {
            it.writeUtf8(json.encodeToString(JsonObject.serializer(), document))
        }
        if (fileSystem.exists(configPath)) fileSystem.delete(configPath)
        fileSystem.atomicMove(tempPath, configPath)
    }
}
