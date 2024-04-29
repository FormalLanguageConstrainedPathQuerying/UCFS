/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.Authenticator;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.time.Duration;
import javax.net.ssl.HttpsURLConnection;

/**
 * A simple Http client that connects to the HTTPTestServer.
 * @author danielfuchs
 */
public class HTTPTestClient extends HTTPTest {

    public static final long DELAY_BEFORE_RETRY = 2500; 

    public static void connect(HttpProtocolType protocol,
                               HTTPTestServer server,
                               HttpAuthType authType,
                               Authenticator auth)
            throws IOException {
        try {
            doConnect(protocol, server, authType, auth);
        } catch (BindException ex) {
            System.out.println("WARNING: Unexpected BindException: " + ex);
            System.out.println("\tSleeping a bit and try again...");
            long start = System.nanoTime();
            System.gc();
            try {
                Thread.sleep(DELAY_BEFORE_RETRY);
            } catch (InterruptedException iex) {
            }
            System.gc();
            System.out.println("\tRetrying after "
                    + Duration.ofNanos(System.nanoTime() - start).toMillis()
                    + " milliseconds");
            doConnect(protocol, server, authType, auth);
        }
    }

    public static void doConnect(HttpProtocolType protocol,
                               HTTPTestServer server,
                               HttpAuthType authType,
                               Authenticator auth)
            throws IOException {

        InetSocketAddress address = server.getAddress();
        final URL url = url(protocol,  address, "/");
        final Proxy proxy = proxy(server, authType);

        System.out.println("Client: FIRST request: " + url + " GET");
        HttpURLConnection conn = openConnection(url, authType, proxy);
        configure(conn, auth);
        System.out.println("Response code: " + conn.getResponseCode());
        String result = new String(conn.getInputStream().readAllBytes(), "UTF-8");
        System.out.println("Response body: " + result);
        if (!result.isEmpty()) {
            throw new RuntimeException("Unexpected response to GET: " + result);
        }
        System.out.println("\nClient: NEXT request: " + url + " POST");
        conn = openConnection(url, authType, proxy);
        configure(conn, auth);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.getOutputStream().write("Hello World!".getBytes("UTF-8"));
        System.out.println("Response code: " + conn.getResponseCode());
        result = new String(conn.getInputStream().readAllBytes(), "UTF-8");
        System.out.println("Response body: " + result);
        if ("Hello World!".equals(result)) {
            System.out.println("Test passed!");
        } else {
            throw new RuntimeException("Unexpected response to POST: " + result);
        }
    }

    private static void configure(HttpURLConnection conn, Authenticator auth)
        throws IOException {
        if (auth != null) {
            conn.setAuthenticator(auth);
        }
        if (conn instanceof HttpsURLConnection) {
            System.out.println("Client: configuring SSL connection");
        }
    }

}
