package com.termux.terminal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 过滤 Android linker 在旧系统上输出的已知无害噪音。
 *
 * <p>Android 9 的 linker 不认识新版 arm64 运行时库里的 BTI 动态标记
 * {@code DT_AARCH64_BTI_PLT(0x70000001)}，会打印 warning 后继续忽略。
 * 这里仅在终端显示层丢弃精确命中的噪音行，不改变进程行为和退出码。</p>
 */
final class KnownLinkerWarningFilter {

    private static final String LINKER_WARNING_PREFIX = "WARNING: linker: Warning: ";

    private boolean mAtLineStart = true;
    private boolean mCollectingPotentialWarningLine = false;
    private final ByteArrayOutputStream mPotentialWarningBuffer = new ByteArrayOutputStream(256);

    byte[] filter(byte[] input, int count) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(count);
        for (int i = 0; i < count; i++) {
            byte b = input[i];

            if (!mCollectingPotentialWarningLine && mAtLineStart && isPotentialWarningStartByte(b)) {
                mCollectingPotentialWarningLine = true;
                mPotentialWarningBuffer.reset();
            }

            if (!mCollectingPotentialWarningLine) {
                writeNormalByte(output, b);
                continue;
            }

            mPotentialWarningBuffer.write(b);

            if (b == '\n') {
                finalizePotentialWarningLine(output);
            } else if (!couldStillBeKnownLinkerWarning()) {
                flushPotentialWarningBufferAsNormalLine(output);
            }
        }
        return output.toByteArray();
    }

    void flushForProcessExit(ByteArrayOutputStream output) {
        if (!mCollectingPotentialWarningLine) return;

        byte[] lineBytes = mPotentialWarningBuffer.toByteArray();
        String line = new String(lineBytes, StandardCharsets.UTF_8);
        if (!isKnownNoisyLinkerWarningLine(line)) {
            output.write(lineBytes, 0, lineBytes.length);
        }

        mCollectingPotentialWarningLine = false;
        mPotentialWarningBuffer.reset();
    }

    private void writeNormalByte(ByteArrayOutputStream output, byte b) {
        output.write(b);
        if (b == '\n') {
            mAtLineStart = true;
        } else if (b != '\r') {
            mAtLineStart = false;
        }
    }

    private void flushPotentialWarningBufferAsNormalLine(ByteArrayOutputStream output) {
        byte[] bytes = mPotentialWarningBuffer.toByteArray();
        output.write(bytes, 0, bytes.length);
        mCollectingPotentialWarningLine = false;
        mPotentialWarningBuffer.reset();

        if (bytes.length == 0) return;
        byte last = bytes[bytes.length - 1];
        if (last == '\n') {
            mAtLineStart = true;
        } else if (last != '\r') {
            mAtLineStart = false;
        }
    }

    private void finalizePotentialWarningLine(ByteArrayOutputStream output) {
        byte[] lineBytes = mPotentialWarningBuffer.toByteArray();
        String line = new String(lineBytes, StandardCharsets.UTF_8);
        if (!isKnownNoisyLinkerWarningLine(line)) {
            output.write(lineBytes, 0, lineBytes.length);
        }

        mCollectingPotentialWarningLine = false;
        mPotentialWarningBuffer.reset();
        mAtLineStart = true;
    }

    private boolean couldStillBeKnownLinkerWarning() {
        String line = new String(mPotentialWarningBuffer.toByteArray(), StandardCharsets.UTF_8);
        String visiblePrefix = stripLeadingTerminalControls(line);
        return visiblePrefix.isEmpty()
            || LINKER_WARNING_PREFIX.startsWith(visiblePrefix)
            || visiblePrefix.startsWith(LINKER_WARNING_PREFIX);
    }

    private static boolean isKnownNoisyLinkerWarningLine(String line) {
        return line.contains(LINKER_WARNING_PREFIX)
            && line.contains("libc++_shared.so")
            && line.contains("unused DT entry")
            && line.contains("0x70000001");
    }

    private static boolean isPotentialWarningStartByte(byte b) {
        return b == 'W'
            || b == '\r'
            || b == '\t'
            || b == ' '
            || b == 0x1b
            || (b >= 0 && b < ' ');
    }

    private static String stripLeadingTerminalControls(String line) {
        int index = 0;
        while (index < line.length()) {
            char ch = line.charAt(index);
            if (ch == '\u001b') {
                int next = index + 1;
                if (next >= line.length()) return "";

                char type = line.charAt(next);
                if (type == '[') {
                    int end = next + 1;
                    while (end < line.length()) {
                        char endChar = line.charAt(end);
                        if (endChar >= 0x40 && endChar <= 0x7e) {
                            index = end + 1;
                            break;
                        }
                        end++;
                    }
                    if (end >= line.length()) return "";
                    continue;
                }

                if (type == ']') {
                    int end = next + 1;
                    while (end < line.length()) {
                        char endChar = line.charAt(end);
                        if (endChar == '\u0007') {
                            index = end + 1;
                            break;
                        }
                        if (endChar == '\u001b' && end + 1 < line.length() && line.charAt(end + 1) == '\\') {
                            index = end + 2;
                            break;
                        }
                        end++;
                    }
                    if (end >= line.length()) return "";
                    continue;
                }

                index = next + 1;
                continue;
            }

            if (ch == '\r' || ch == '\t' || ch == ' ' || (ch < ' ' && ch != '\n') || ch == 0x7f) {
                index++;
                continue;
            }

            break;
        }
        return line.substring(index);
    }
}