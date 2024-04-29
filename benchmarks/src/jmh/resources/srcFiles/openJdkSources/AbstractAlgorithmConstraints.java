/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.security.AccessController;
import java.security.AlgorithmConstraints;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * The class contains common functionality for algorithm constraints classes.
 */
public abstract class AbstractAlgorithmConstraints
        implements AlgorithmConstraints {

    protected final AlgorithmDecomposer decomposer;

    protected AbstractAlgorithmConstraints(AlgorithmDecomposer decomposer) {
        this.decomposer = decomposer;
    }

    static Set<String> getAlgorithms(String propertyName) {
        @SuppressWarnings("removal")
        String property = AccessController.doPrivileged(
                new PrivilegedAction<String>() {
                    @Override
                    public String run() {
                        return Security.getProperty(propertyName);
                    }
                });

        String[] algorithmsInProperty = null;
        if (property != null && !property.isEmpty()) {
            if (property.length() >= 2 && property.charAt(0) == '"' &&
                    property.charAt(property.length() - 1) == '"') {
                property = property.substring(1, property.length() - 1);
            }
            algorithmsInProperty = property.split(",");
            for (int i = 0; i < algorithmsInProperty.length; i++) {
                algorithmsInProperty[i] = algorithmsInProperty[i].trim();
            }
        }

        if (algorithmsInProperty == null) {
            return Collections.emptySet();
        }
        Set<String> algorithmsInPropertySet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        algorithmsInPropertySet.addAll(Arrays.asList(algorithmsInProperty));
        return algorithmsInPropertySet;
    }

    static boolean checkAlgorithm(Set<String> algorithms, String algorithm,
            AlgorithmDecomposer decomposer) {
        if (algorithm == null || algorithm.isEmpty()) {
            throw new IllegalArgumentException("No algorithm name specified");
        }

        if (algorithms.contains(algorithm)) {
            return false;
        }

        Set<String> elements = decomposer.decompose(algorithm);

        for (String element : elements) {
            if (algorithms.contains(element)) {
                return false;
            }
        }

        return true;
    }

}
