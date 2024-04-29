/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * HexDumpReader provides utility methods to read a hex dump text file
 * and convert to an InputStream.  The format supported by the methods
 * can be generated by the following command.
 *
 * $ od -vw -t x1 foo | sed -r -e 's/^[0-9]+ ?
 */
public final class HexDumpReader {

    private HexDumpReader() {}

    /*
     * Converts a Hex dump file given by the String name into an InputStream
     * containing bytes. The expected format of the file should be given as lines
     * that are either:
     *  - Valid hexadecimal value(s) (two hexadecimal characters) combined with no
     *    spaces between. E.g. "ace95365" represents four hexadecimal values.
     *    There should not be an odd amount of hexadecimal characters. E.g. "ace"
     *  - Contain leading comments given by '#' (which are ignored). E.g. "#foo"
     *    Non-leading comments are not allowed. E.g. "ace953 #foo"
     *  - Empty (which are ignored).
     */
    public static InputStream getStreamFromHexDump(String fileName) throws IOException {
        return getStreamFromHexDump(new File(System.getProperty("test.src", "."),
                fileName));
    }

    public static InputStream getStreamFromHexDump(File hexFile) throws IOException {
        String hexString = Files.readAllLines(hexFile.toPath(), StandardCharsets.UTF_8)
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .collect(Collectors.joining());
        byte[] bArray = HexFormat.of().parseHex(hexString);
        return new ByteArrayInputStream(bArray);
    }
}
