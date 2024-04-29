/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.metadata.eventtype;

import java.util.List;

import jdk.jfr.EventType;
import jdk.jfr.SettingDescriptor;
import jdk.test.lib.Asserts;

/**
 * @test
 * @summary Test getSettings()
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.api.metadata.eventtype.TestGetSettings
 */
public class TestGetSettings {

    public static void main(String[] args) throws Throwable {
        EventType eventType = EventType.getEventType(EventWithCustomSettings.class);
        List<SettingDescriptor> settings = eventType.getSettingDescriptors();
        Asserts.assertEquals(settings.size(), 5, "Wrong number of settings");

        try {
            settings.add(settings.getFirst());
            Asserts.fail("Should not be able to modify list returned by getSettings()");
        } catch (UnsupportedOperationException uoe) {
        }
    }
}
