/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7068321
 * @summary Support TLS Server Name Indication (SNI) Extension in JSSE Server
 * @library ../SSLEngine ../templates
 * @build SSLEngineService SSLCapabilities SSLExplorer
 * @run main/othervm SSLEngineExplorerWithCli
 */

import javax.net.ssl.*;
import java.nio.*;
import java.net.*;
import java.util.*;
import java.nio.channels.*;

public class SSLEngineExplorerWithCli extends SSLEngineService {

    /*
     * =============================================================
     * Set the various variables needed for the tests, then
     * specify what tests to run on each side.
     */

    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    static boolean separateServerThread = true;

    volatile static boolean serverReady = false;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = false;

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {

        SSLEngine ssle = createSSLEngine(false);

        InetSocketAddress isa =
                new InetSocketAddress(InetAddress.getLocalHost(), serverPort);
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(isa);
        serverPort = ssc.socket().getLocalPort();

        serverReady = true;

        SocketChannel sc = ssc.accept();

        while (!sc.finishConnect()) {
            Thread.sleep(50);
        }

        ByteBuffer buffer = ByteBuffer.allocate(0xFF);
        int position = 0;
        SSLCapabilities capabilities = null;

        buffer.limit(SSLExplorer.RECORD_HEADER_SIZE);
        while (position < SSLExplorer.RECORD_HEADER_SIZE) {
            int n = sc.read(buffer);
            if (n < 0) {
                throw new Exception("unexpected end of stream!");
            }
            position += n;
        }
        buffer.flip();

        int recordLength = SSLExplorer.getRequiredSize(buffer);
        if (buffer.capacity() < recordLength) {
            ByteBuffer oldBuffer = buffer;
            buffer = ByteBuffer.allocate(recordLength);
            buffer.put(oldBuffer);
        }

        buffer.position(SSLExplorer.RECORD_HEADER_SIZE);
        buffer.limit(buffer.capacity());
        while (position < recordLength) {
            int n = sc.read(buffer);
            if (n < 0) {
                throw new Exception("unexpected end of stream!");
            }
            position += n;
        }
        buffer.flip();

        capabilities = SSLExplorer.explore(buffer);
        if (capabilities != null) {
            System.out.println("Record version: " +
                    capabilities.getRecordVersion());
            System.out.println("Hello version: " +
                    capabilities.getHelloVersion());
        }

        ByteBuffer peerNetData = handshaking(ssle, sc, buffer);

        receive(ssle, sc, peerNetData);

        deliver(ssle, sc);

        ExtendedSSLSession session = (ExtendedSSLSession)ssle.getSession();
        checkCapabilities(capabilities, session);

        sc.close();
        ssc.close();
    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doClientSide() throws Exception {
        SSLEngine ssle = createSSLEngine(true);

        /*
         * Wait for server to get started.
         */
        while (!serverReady) {
            Thread.sleep(50);
        }

        SocketChannel sc = SocketChannel.open();
        sc.configureBlocking(false);
        InetSocketAddress isa =
                new InetSocketAddress(InetAddress.getLocalHost(), serverPort);
        sc.connect(isa);

        while (!sc.finishConnect() ) {
            Thread.sleep(50);
        }

        SNIHostName serverName = new SNIHostName(clientRequestedHostname);
        List<SNIServerName> serverNames = new ArrayList<>(1);
        serverNames.add(serverName);
        SSLParameters params = ssle.getSSLParameters();
        params.setServerNames(serverNames);
        ssle.setSSLParameters(params);

        ByteBuffer peerNetData = handshaking(ssle, sc, null);

        deliver(ssle, sc);

        receive(ssle, sc, peerNetData);

        ExtendedSSLSession session = (ExtendedSSLSession)ssle.getSession();
        checkSNIInSession(session);

        sc.close();
    }

    private static String clientRequestedHostname = "www.example.com";
    private static String serverAcceptableHostname =
                                                "www\\.example\\.(com|org)";

    void checkCapabilities(SSLCapabilities capabilities,
            ExtendedSSLSession session) throws Exception {
        List<SNIServerName> sessionSNI = session.getRequestedServerNames();
        if (!sessionSNI.equals(capabilities.getServerNames())) {
            for (SNIServerName sni : sessionSNI) {
                System.out.println("SNI in session is " + sni);
            }

            List<SNIServerName> capaSNI = capabilities.getServerNames();
            for (SNIServerName sni : capaSNI) {
                System.out.println("SNI in session is " + sni);
            }

            throw new Exception(
                    "server name indication does not match capabilities");
        }

        checkSNIInSession(session);
    }

    void checkSNIInSession(ExtendedSSLSession session) throws Exception {
        List<SNIServerName> sessionSNI = session.getRequestedServerNames();
        if (sessionSNI.isEmpty()) {
            throw new Exception(
                    "unexpected empty request server name indication");
        }

        if (sessionSNI.size() != 1) {
            throw new Exception(
                    "unexpected request server name indication");
        }

        SNIServerName serverName = sessionSNI.get(0);
        if (!(serverName instanceof SNIHostName)) {
            throw new Exception(
                    "unexpected instance of request server name indication");
        }

        String hostname = ((SNIHostName)serverName).getAsciiName();
        if (!clientRequestedHostname.equalsIgnoreCase(hostname)) {
            throw new Exception(
                    "unexpected request server name indication value");
        }
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */
    volatile Exception serverException = null;
    volatile Exception clientException = null;

    volatile int serverPort = 0;

    public static void main(String args[]) throws Exception {
        if (debug)
            System.setProperty("javax.net.debug", "all");

        new SSLEngineExplorerWithCli();
    }

    Thread clientThread = null;
    Thread serverThread = null;

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    SSLEngineExplorerWithCli() throws Exception {
        super("../etc");

        if (separateServerThread) {
            startServer(true);
            startClient(false);
        } else {
            startClient(true);
            startServer(false);
        }

        /*
         * Wait for other side to close down.
         */
        if (separateServerThread) {
            serverThread.join();
        } else {
            clientThread.join();
        }

        /*
         * When we get here, the test is pretty much over.
         *
         * If the main thread excepted, that propagates back
         * immediately.  If the other thread threw an exception, we
         * should report back.
         */
        if (serverException != null) {
            System.out.print("Server Exception:");
            throw serverException;
        }
        if (clientException != null) {
            System.out.print("Client Exception:");
            throw clientException;
        }
    }

    void startServer(boolean newThread) throws Exception {
        if (newThread) {
            serverThread = new Thread() {
                public void run() {
                    try {
                        doServerSide();
                    } catch (Exception e) {
                        /*
                         * Our server thread just died.
                         *
                         * Release the client, if not active already...
                         */
                        System.err.println("Server died...");
                        System.err.println(e);
                        serverReady = true;
                        serverException = e;
                    }
                }
            };
            serverThread.start();
        } else {
            doServerSide();
        }
    }

    void startClient(boolean newThread) throws Exception {
        if (newThread) {
            clientThread = new Thread() {
                public void run() {
                    try {
                        doClientSide();
                    } catch (Exception e) {
                        /*
                         * Our client thread just died.
                         */
                        System.err.println("Client died...");
                        clientException = e;
                    }
                }
            };
            clientThread.start();
        } else {
            doClientSide();
        }
    }
}
