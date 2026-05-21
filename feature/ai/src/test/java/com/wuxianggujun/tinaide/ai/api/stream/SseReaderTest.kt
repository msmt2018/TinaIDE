package com.wuxianggujun.tinaide.ai.api.stream

import com.google.common.truth.Truth.assertThat
import okio.Buffer
import org.junit.Test

class SseReaderTest {

    private fun reader(raw: String): SseReader {
        val buffer = Buffer().writeUtf8(raw)
        return SseReader(buffer)
    }

    @Test
    fun `reads simple data frame`() {
        val r = reader("data: {\"a\":1}\n\n")
        val ev = r.readEvent()
        assertThat(ev).isEqualTo(SseReader.Event.Payload("{\"a\":1}"))
        assertThat(r.readEvent()).isNull()
    }

    @Test
    fun `aggregates multiline data frame`() {
        val raw = "data: {\n" +
            "data:   \"a\":1\n" +
            "data: }\n\n"
        val ev = r(raw).readEvent()
        assertThat(ev).isEqualTo(SseReader.Event.Payload("{\n\"a\":1\n}"))
    }

    @Test
    fun `emits Done on DONE token`() {
        val ev = r("data: [DONE]\n\n").readEvent()
        assertThat(ev).isEqualTo(SseReader.Event.Done)
    }

    @Test
    fun `skips comments id event retry lines`() {
        val raw = buildString {
            append(": heartbeat\n")
            append("id: 5\n")
            append("event: message\n")
            append("retry: 1000\n")
            append("data: ok\n\n")
        }
        val ev = r(raw).readEvent()
        assertThat(ev).isEqualTo(SseReader.Event.Payload("ok"))
    }

    @Test
    fun `falls back to JSON line without data prefix`() {
        val raw = """{"choices":[]}""" + "\n"
        val ev = r(raw).readEvent()
        assertThat(ev).isEqualTo(SseReader.Event.Payload("""{"choices":[]}"""))
    }

    @Test
    fun `flushes trailing payload without final blank line`() {
        val raw = "data: tail"
        val ev = r(raw).readEvent()
        assertThat(ev).isEqualTo(SseReader.Event.Payload("tail"))
    }

    @Test
    fun `reads two consecutive frames`() {
        val raw = "data: one\n\ndata: two\n\n"
        val reader = r(raw)
        assertThat(reader.readEvent()).isEqualTo(SseReader.Event.Payload("one"))
        assertThat(reader.readEvent()).isEqualTo(SseReader.Event.Payload("two"))
        assertThat(reader.readEvent()).isNull()
    }

    @Test
    fun `skips blank frames before next payload`() {
        val reader = r("\n\ndata: ok\n\n")

        assertThat(reader.readEvent()).isEqualTo(SseReader.Event.Payload("ok"))
        assertThat(reader.readEvent()).isNull()
    }

    @Test
    fun `keeps raw continuation lines after data line`() {
        val raw = "data: {\n" +
            "  \"a\": 1\n" +
            "}\n\n"

        val ev = r(raw).readEvent()

        assertThat(ev).isEqualTo(SseReader.Event.Payload("{\n  \"a\": 1\n}"))
    }

    @Test
    fun `falls back to raw DONE line without data prefix`() {
        val ev = r("  [DONE]\n").readEvent()

        assertThat(ev).isEqualTo(SseReader.Event.Done)
    }

    @Test
    fun `ignores unrelated lines before valid payload`() {
        val reader = r("garbage\ndata: ok\n\n")

        assertThat(reader.readEvent()).isEqualTo(SseReader.Event.Payload("ok"))
        assertThat(reader.readEvent()).isNull()
    }
    private fun r(raw: String) = reader(raw)
}
