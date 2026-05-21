package com.termux.terminal;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class KnownLinkerWarningFilterTest {

    private static final String NOISY_WARNING = "WARNING: linker: Warning: \"/data/data/com.wuxianggujun.tinaide/files/android-sysroot/usr/lib/aarch64-linux-android/libc++_shared.so\" unused DT entry: unknown processor-specific (type 0x70000001 arg 0x0) (ignoring)\n";

    @Test
    public void filterDropsKnownLibcxxBtiWarning() {
        KnownLinkerWarningFilter filter = new KnownLinkerWarningFilter();

        String output = apply(filter, NOISY_WARNING + "Hello, 1!\n");

        assertEquals("Hello, 1!\n", output);
    }

    @Test
    public void filterDropsWarningAfterLeadingTerminalControls() {
        KnownLinkerWarningFilter filter = new KnownLinkerWarningFilter();

        String output = apply(filter, "\u001b[?2004l\r" + NOISY_WARNING + "Hello, 1!\n");

        assertEquals("Hello, 1!\n", output);
    }

    @Test
    public void filterPreservesNormalWarningOutput() {
        KnownLinkerWarningFilter filter = new KnownLinkerWarningFilter();

        String input = "WARNING: user warning\nHello, 1!\n";
        String output = apply(filter, input);

        assertEquals(input, output);
    }

    @Test
    public void filterHandlesWarningSplitAcrossReads() {
        KnownLinkerWarningFilter filter = new KnownLinkerWarningFilter();
        int split = NOISY_WARNING.indexOf("unused DT entry");

        String first = apply(filter, NOISY_WARNING.substring(0, split));
        String second = apply(filter, NOISY_WARNING.substring(split) + "Hello, 1!\n");

        assertEquals("", first);
        assertEquals("Hello, 1!\n", second);
    }

    @Test
    public void filterPreservesIncompleteNormalWarningLine() {
        KnownLinkerWarningFilter filter = new KnownLinkerWarningFilter();
        ByteArrayOutputStream tail = new ByteArrayOutputStream();

        String first = apply(filter, "WARNING: user warning without newline");
        filter.flushForProcessExit(tail);

        assertEquals("WARNING: user warning without newline", first);
        assertEquals("", new String(tail.toByteArray(), StandardCharsets.UTF_8));
    }

    private static String apply(KnownLinkerWarningFilter filter, String input) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        return new String(filter.filter(bytes, bytes.length), StandardCharsets.UTF_8);
    }
}