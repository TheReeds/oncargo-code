package pe.logyx.core.network

/** Un mensaje SSE completo. */
data class SseFrame(val id: String?, val event: String?, val data: String)

/**
 * Parser incremental de `text/event-stream` (WHATWG): se le pasa línea por línea; devuelve un [SseFrame] al llegar la
 * línea vacía que cierra un mensaje con datos. Los comentarios (`: ping`, latido) se ignoran; `retry:` también.
 */
class SseParser {
    private var id: String? = null
    private var event: String? = null
    private val data = StringBuilder()
    private var hasData = false

    fun feed(line: String): SseFrame? {
        if (line.isEmpty()) {
            val frame = if (hasData) SseFrame(id, event, data.toString()) else null
            id = null; event = null; data.setLength(0); hasData = false
            return frame
        }
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "id" -> id = value
            "event" -> event = value
            "data" -> { if (hasData) data.append('\n'); data.append(value); hasData = true }
        }
        return null
    }
}
