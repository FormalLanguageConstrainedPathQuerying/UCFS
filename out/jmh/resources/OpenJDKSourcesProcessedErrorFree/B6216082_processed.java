/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6216082
 * @summary  Redirect problem with HttpsURLConnection using a proxy
 * @modules java.base/sun.net.www
 * @library .. /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 *        jdk.test.lib.Platform
 *        ClosedChannelList
 *        TunnelProxy
 * @key intermittent
 * @run main/othervm B6216082
 */

import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ProxySelector;
import java.net.URL;
import java.security.KeyStore;
import java.util.Optional;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jdk.test.lib.NetworkConfiguration;

public class B6216082 {
    static SimpleHttpTransaction httpTrans;
    static HttpsServer server;
    static TunnelProxy proxy;

    static InetAddress firstNonLoAddress = null;

    public static void main(String[] args) throws Exception {
        HostnameVerifier reservedHV =
            HttpsURLConnection.getDefaultHostnameVerifier();
        try {
            Class.forName("java.nio.channels.ClosedByInterruptException");
            if (!setupEnv()) {
                return;
            }
            startHttpServer();
            ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress(firstNonLoAddress, proxy.getLocalPort())));
            makeHttpCall();
        } finally {
            if (proxy != null) {
                proxy.terminate();
            }
            if (server != null) {
               server.stop(1);
            }
            HttpsURLConnection.setDefaultHostnameVerifier(reservedHV);
        }
    }

    /*
     * Where do we find the keystores for ssl?
     */
    static String pathToStores = "../../../../../../javax/net/ssl/etc";
    static String keyStoreFile = "keystore";
    static String trustStoreFile = "truststore";
    static String passwd = "passphrase";
    public static boolean setupEnv() throws Exception {
        firstNonLoAddress = getNonLoAddress();
        if (firstNonLoAddress == null) {
            System.err.println("The test needs at least one non-loopback address to run. Quit now.");
            return false;
        }
        System.out.println(firstNonLoAddress.getHostAddress());
        System.setProperty( "https.proxyHost", firstNonLoAddress.getHostAddress());

        String keyFilename = System.getProperty("test.src", "./") + "/" +
                             pathToStores + "/" + keyStoreFile;
        String trustFilename = System.getProperty("test.src", "./") + "/" +
                               pathToStores + "/" + trustStoreFile;

        System.setProperty("javax.net.ssl.keyStore", keyFilename);
        System.setProperty("javax.net.ssl.keyStorePassword", passwd);
        System.setProperty("javax.net.ssl.trustStore", trustFilename);
        System.setProperty("javax.net.ssl.trustStorePassword", passwd);
        HttpsURLConnection.setDefaultHostnameVerifier(new NameVerifier());
        return true;
    }

    public static InetAddress getNonLoAddress() throws Exception {
        InetAddress lh = InetAddress.getByName("localhost");
        NetworkInterface loNIC = NetworkInterface.getByInetAddress(lh);

        NetworkConfiguration nc = NetworkConfiguration.probe();
        Optional<InetAddress> oaddr = nc.interfaces()
                .filter(nif -> !nif.getName().equalsIgnoreCase(loNIC.getName()))
                .flatMap(nif -> nc.addresses(nif))
                .filter(a -> !a.isLoopbackAddress())
                .findFirst();

        return oaddr.orElseGet(() -> null);
    }

    public static void startHttpServer() throws  Exception {
        httpTrans = new SimpleHttpTransaction();
        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");
        char[] passphrase = "passphrase".toCharArray();

        ks.load(new FileInputStream(System.getProperty("javax.net.ssl.keyStore")), passphrase);
        ts.load(new FileInputStream(System.getProperty("javax.net.ssl.trustStore")), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext sslCtx = SSLContext.getInstance("TLS");

        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        server = HttpsServer.create(new InetSocketAddress(firstNonLoAddress, 0), 10);
        server.setHttpsConfigurator(new HttpsConfigurator(sslCtx));
        server.createContext("/", httpTrans);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        proxy = new TunnelProxy(1, 10, firstNonLoAddress, 0);
    }

    public static void makeHttpCall() throws Exception {
        System.out.println("https server listen on: " + server.getAddress().getPort());
        System.out.println("https proxy listen on: " + proxy.getLocalPort());
        URL url = new URL("https" , firstNonLoAddress.getHostAddress(),
                            server.getAddress().getPort(), "/");
        HttpURLConnection uc = (HttpURLConnection)url.openConnection();
        System.out.println(uc.getResponseCode());
        if(uc.getResponseCode() != 200) {
            uc.disconnect();
            throw new RuntimeException("Test failed : bad http request with response code : "+ uc.getResponseCode());
        }
        uc.disconnect();
    }

    static class NameVerifier implements HostnameVerifier {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }
}

class SimpleHttpTransaction implements HttpHandler {

    /*
     * Our http server which simply redirect first call
     */
    public void handle(HttpExchange trans) {
        try {
            String path = trans.getRequestURI().getPath();
            if (path.equals("/")) {
                String location = "/redirect";
                trans.getResponseHeaders().set("Location", location);
                trans.sendResponseHeaders(302, -1);
            } else {
                trans.sendResponseHeaders(200, -1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
