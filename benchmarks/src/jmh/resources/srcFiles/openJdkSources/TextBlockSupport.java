/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.tools.javac.parser;

import java.util.HashSet;
import java.util.Set;

/**
 * Methods used to support text blocks lint.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
class TextBlockSupport {
     enum WhitespaceChecks {
        INCONSISTENT,
        TRAILING
    };

    /** Check that the use of white space in content is not problematic.
     */
    static Set<WhitespaceChecks> checkWhitespace(String string) {
        Set<WhitespaceChecks> checks = new HashSet<>();
        if (string.isEmpty()) {
            return checks;
        }
        int outdent = 0;
        char lastChar = string.charAt(string.length() - 1);
        boolean optOut = lastChar == '\n' || lastChar == '\r';
        String[] lines = string.split("\\R");
        int length = lines.length;
        String lastLine = length == 0 ? "" : lines[length - 1];
         if (!optOut) {
            outdent = indexOfNonWhitespace(lastLine);
            for (String line : lines) {
                if (!line.isBlank()) {
                    outdent = Integer.min(outdent, indexOfNonWhitespace(line));
                    if (outdent == 0) {
                        break;
                    }
                }
            }
        }
        String start = lastLine.substring(0, outdent);
        for (String line : lines) {
            if (!line.isBlank() && !line.startsWith(start)) {
                checks.add(WhitespaceChecks.INCONSISTENT);
            }
            if (outdent < line.length()) {
                lastChar = line.charAt(line.length() - 1);
                if (Character.isWhitespace(lastChar)) {
                    checks.add(WhitespaceChecks.TRAILING);
                }
            }
        }
        return checks;
    }

   private static int indexOfNonWhitespace(String string) {
        return string.length() - string.stripLeading().length();
    }
}
