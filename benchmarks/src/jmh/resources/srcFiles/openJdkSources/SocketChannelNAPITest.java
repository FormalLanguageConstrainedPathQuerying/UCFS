/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8243099
 * @library /test/lib
 * @modules jdk.net
 * @summary Check ExtendedSocketOption NAPI_ID support for SocketChannel and
 *          ServerSocketChannel
 * @run testng SocketChannelNAPITest
 * @run testng/othervm -Djava.net.preferIPv4Stack=true SocketChannelNAPITest
 */

import jdk.test.lib.net.IPSupport;
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import static jdk.net.ExtendedSocketOptions.SO_INCOMING_NAPI_ID;

public class SocketChannelNAPITest {
    private InetAddress hostAddr;
    private static final Class<SocketException> SE = SocketException.class;
    private static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    private static final Class<UnsupportedOperationException> UOE = UnsupportedOperationException.class;

    @BeforeTest
    public void setup() throws IOException {
        IPSupport.throwSkippedExceptionIfNonOperational();
        try (var s = SocketChannel.open();
             var ssc = ServerSocketChannel.open()) {
            if (!s.supportedOptions().contains(SO_INCOMING_NAPI_ID)) {
                assertThrows(UOE, () -> s.getOption(SO_INCOMING_NAPI_ID));
                assertThrows(UOE, () -> s.setOption(SO_INCOMING_NAPI_ID, 42));
                assertThrows(UOE, () -> s.setOption(SO_INCOMING_NAPI_ID, null));
                assertThrows(UOE, () -> ssc.getOption(SO_INCOMING_NAPI_ID));
                assertThrows(UOE, () -> ssc.setOption(SO_INCOMING_NAPI_ID, 42));
                assertThrows(UOE, () -> ssc.setOption(SO_INCOMING_NAPI_ID, null));
                throw new SkipException("NAPI ID not supported on this system");
            }
        }
        hostAddr = InetAddress.getLocalHost();
    }

    @Test
    public void testSetGetOptionSocketChannel() throws IOException {
        try (var sc = SocketChannel.open()) {
            assertEquals((int) sc.getOption(SO_INCOMING_NAPI_ID), 0);
            assertThrows(SE, () -> sc.setOption(SO_INCOMING_NAPI_ID, 42));
            assertThrows(IAE, () -> sc.setOption(SO_INCOMING_NAPI_ID, null));
        }
    }

    @Test
    public void testSetGetOptionServerSocketChannel() throws IOException {
        try (var ssc = ServerSocketChannel.open()) {
            assertEquals((int) ssc.getOption(SO_INCOMING_NAPI_ID), 0);
            assertThrows(SE, () -> ssc.setOption(SO_INCOMING_NAPI_ID, 42));
            assertThrows(IAE, () -> ssc.setOption(SO_INCOMING_NAPI_ID, null));
        }
    }

    @Test
    public void testSocketChannel() throws Exception {
        int sID, cID, tempID = 0;
        boolean initialRun = true;
        try (var ss = ServerSocketChannel.open()) {
            ss.bind(new InetSocketAddress(hostAddr, 0));

            try (var c = SocketChannel.open()) {
                c.connect(ss.getLocalAddress());

                try (var s = ss.accept()) {
                    assertEquals((int) ss.getOption(SO_INCOMING_NAPI_ID), 0);

                    for (int i = 0; i < 10; i++) {
                        s.write(ByteBuffer.wrap("test".getBytes()));
                        sID = s.getOption(SO_INCOMING_NAPI_ID);
                        assertEquals(sID, 0, "SocketChannel: Sender");

                        c.read(ByteBuffer.allocate(128));
                        cID = c.getOption(SO_INCOMING_NAPI_ID);

                        if (initialRun) {
                            assertTrue(cID >= 0, "SocketChannel: Receiver");
                        } else {
                            assertEquals(cID, tempID);
                            initialRun = false;
                        }
                        tempID = cID;
                    }
                }
            }
        }
    }
}
