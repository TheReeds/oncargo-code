package pe.logyx.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SseParserTest {
    private fun SseParser.feedAll(text: String): List<SseFrame> = text.split("\n").dropLast(1).mapNotNull { feed(it) }

    @Test fun `un mensaje con id y data se emite al llegar la linea vacia`() {
        val p = SseParser()
        assertNull(p.feed("id: 7"))
        assertNull(p.feed("data: {\"type\":\"bid.placed\"}"))
        assertEquals(SseFrame("7", null, "{\"type\":\"bid.placed\"}"), p.feed(""))
    }

    @Test fun `los comentarios y el retry se ignoran`() {
        val p = SseParser()
        val frames = p.feedAll(": ping\nretry: 3000\n: otro\n\n")
        assertEquals(emptyList(), frames)
    }

    @Test fun `data en varias lineas se une con salto de linea`() {
        val p = SseParser()
        val frames = p.feedAll("data: uno\ndata: dos\ndata:tres\n\n")
        assertEquals(listOf(SseFrame(null, null, "uno\ndos\ntres")), frames)
    }

    @Test fun `frames vacios no emiten y el id no se arrastra al siguiente`() {
        val p = SseParser()
        val frames = p.feedAll("\n\nid: 1\ndata: a\n\n\ndata: b\n\n")
        assertEquals(listOf(SseFrame("1", null, "a"), SseFrame(null, null, "b")), frames)
    }

    @Test fun `solo id sin data no emite mensaje`() {
        val p = SseParser()
        assertEquals(emptyList(), p.feedAll("id: 9\n\n"))
    }

    @Test fun `campo event y espacio inicial unico`() {
        val p = SseParser()
        assertEquals(listOf(SseFrame(null, "msg", " x")), p.feedAll("event: msg\ndata:  x\n\n"))
    }
}
