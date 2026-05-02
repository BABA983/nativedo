// PORT-MODE: Feature Workflow Port
// WORKFLOW-MAP: docs/telegram-port/browser.md
// UPSTREAM-SCOPE: org/telegram/ui/web/MHTML.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - Ported into NativeDo browser package unchanged in behavior.
// ADAPTED-BACKEND:
//   - None.
// OMITTED:
//   - None.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/web/MHTML.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.browser;

import android.util.Base64;
import android.util.Base64InputStream;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

final class MHTML {

    final File file;
    final HashMap<String, HeaderValue> headers = new HashMap<>();
    final String boundary;

    final ArrayList<Entry> entries = new ArrayList<>();
    final HashMap<String, Entry> entriesByLocation = new HashMap<>();
    private final long[] filePos = new long[1];

    MHTML(File file) throws IOException {
        this.file = file;

        FileInputStream fileInputStream = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream));

        headers.putAll(parseHeaders(reader));
        boundary = HeaderValue.getProp(headers.get("content-type"), "boundary");
        if (boundary != null) {
            parseEntries(reader, fileInputStream);
        }

        reader.close();
    }

    private void parseEntries(BufferedReader reader, FileInputStream stream) throws IOException {
        String line;
        Entry currentEntry = null;
        final int boundaryLength = 2 + boundary.length();
        while ((line = reader.readLine()) != null) {
            filePos[0] += line.getBytes().length + 2;
            if (line.length() == boundaryLength && line.substring(2).equals(boundary)) {
                if (currentEntry != null) {
                    currentEntry.end = filePos[0] - boundaryLength - 2;
                    entries.add(currentEntry);
                    entriesByLocation.put(currentEntry.getLocation(), currentEntry);
                }
                currentEntry = new Entry();
                currentEntry.file = file;
                currentEntry.headers.putAll(parseHeaders(reader));
                currentEntry.start = filePos[0];
            }
        }
        if (currentEntry != null && currentEntry.start != 0 && currentEntry.end != 0) {
            entries.add(currentEntry);
            entriesByLocation.put(currentEntry.getLocation(), currentEntry);
        }
    }

    private HashMap<String, HeaderValue> parseHeaders(BufferedReader reader) throws IOException {
        HashMap<String, HeaderValue> parsedHeaders = new HashMap<>();
        String line;
        String currentHeader = null;
        StringBuilder currentValue = null;
        while ((line = reader.readLine()) != null) {
            filePos[0] += line.getBytes().length + 2;
            line = line.trim();
            if (line.isEmpty()) {
                break;
            }

            if (currentHeader != null && currentValue != null) {
                currentValue.append(line);
                if (!line.endsWith(";")) {
                    appendHeader(currentHeader, currentValue.toString(), parsedHeaders);
                    currentHeader = null;
                    currentValue = null;
                }
            } else {
                int index = line.indexOf(':');
                if (index < 0) {
                    continue;
                }

                String name = line.substring(0, index).trim();
                String value = line.substring(index + 1).trim();
                if (value.endsWith(";")) {
                    currentHeader = name;
                    currentValue = new StringBuilder();
                    currentValue.append(value);
                } else {
                    appendHeader(name, value, parsedHeaders);
                }
            }
        }
        if (currentHeader != null && currentValue != null) {
            appendHeader(currentHeader, currentValue.toString(), parsedHeaders);
        }
        return parsedHeaders;
    }

    private static void appendHeader(String nameString, String valueString, HashMap<String, HeaderValue> headers) {
        HeaderValue headerValue = new HeaderValue();
        String[] parts = valueString.split(";(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (int i = 0; i < parts.length; ++i) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            int index = part.indexOf('=');
            if (i == 0 || index < 0) {
                headerValue.value = part;
                continue;
            }
            String name = part.substring(0, index).trim();
            String value = part.substring(index + 1).trim();
            if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                value = value.substring(1, value.length() - 1);
            }
            headerValue.props.put(name, value);
        }
        headers.put(nameString.trim().toLowerCase(), headerValue);
    }

    static final class Entry {
        final HashMap<String, HeaderValue> headers = new HashMap<>();

        File file;
        long start;
        long end;

        private Entry() {
        }

        String getType() {
            return HeaderValue.getValue(headers.get("content-type"));
        }

        String getLocation() {
            return HeaderValue.getValue(headers.get("content-location"));
        }

        String getTransferEncoding() {
            return HeaderValue.getValue(headers.get("content-transfer-encoding"));
        }

        String getId() {
            return HeaderValue.getValue(headers.get("content-id"));
        }

        InputStream getRawInputStream() throws IOException {
            return new BoundedInputStream(file, start, end);
        }

        InputStream getInputStream() throws IOException {
            InputStream stream = new BufferedInputStream(getRawInputStream());
            if ("base64".equals(getTransferEncoding())) {
                return new Base64InputStream(stream, Base64.DEFAULT);
            } else if ("quoted-printable".equalsIgnoreCase(getTransferEncoding())) {
                return new QuotedPrintableInputStream(stream);
            } else {
                return stream;
            }
        }
    }

    static final class HeaderValue {
        String value;
        final HashMap<String, String> props = new HashMap<>();

        private HeaderValue() {
        }

        static String getValue(HeaderValue headerValue) {
            return headerValue == null ? null : headerValue.value;
        }

        static String getProp(HeaderValue headerValue, String name) {
            return headerValue == null ? null : headerValue.props.get(name);
        }
    }

    static final class QuotedPrintableInputStream extends FilterInputStream {

        QuotedPrintableInputStream(InputStream stream) {
            super(stream);
        }

        @Override
        public int read() throws IOException {
            int c = in.read();
            if (c == '=') {
                int next1 = in.read();
                int next2 = in.read();
                if (next1 == -1 || next2 == -1) {
                    return -1;
                }
                if (next1 == '\r' && next2 == '\n') {
                    return read();
                } else if (next1 == '\n' || next2 == '\n') {
                    return next2;
                } else {
                    return hexToByte(next1, next2);
                }
            }
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int bytesRead = 0;
            for (int i = 0; i < len; i++) {
                int c = read();
                if (c == -1) {
                    return bytesRead == 0 ? -1 : bytesRead;
                }
                b[off + i] = (byte) c;
                bytesRead++;
            }
            return bytesRead;
        }

        private int hexToByte(int high, int low) {
            return (hexDigitToInt(high) << 4) | hexDigitToInt(low);
        }

        private int hexDigitToInt(int digit) {
            if (digit >= '0' && digit <= '9') {
                return digit - '0';
            } else if (digit >= 'A' && digit <= 'F') {
                return digit - 'A' + 10;
            } else if (digit >= 'a' && digit <= 'f') {
                return digit - 'a' + 10;
            } else {
                return 0;
            }
        }
    }

    static final class BoundedInputStream extends FileInputStream {
        private final long endOffset;

        BoundedInputStream(File file, long startOffset, long endOffset) throws IOException {
            super(file);
            this.endOffset = endOffset;
            if (startOffset > 0) {
                long skipped = skip(startOffset);
                if (skipped != startOffset) {
                    throw new RuntimeException("BoundedInputStream failed to skip");
                }
            }
        }

        @Override
        public int read() throws IOException {
            if (getChannel().position() >= endOffset) {
                return -1;
            }
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (getChannel().position() >= endOffset) {
                return -1;
            }
            long maxLen = endOffset - getChannel().position();
            if (len > maxLen) {
                len = (int) maxLen;
            }
            return super.read(b, off, len);
        }
    }
}
