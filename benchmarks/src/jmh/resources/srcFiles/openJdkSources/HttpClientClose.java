/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8267140
 * @summary Test for HttpClient::close. Any running operation should
 *          succeed and the client should eventually exit.
 *          This test tests close, awaitTermination, and
 *          isTerminated.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.http2.Http2TestServer jdk.test.lib.net.SimpleSSLContext
 *        ReferenceTracker
 * @run testng/othervm
 *       -Djdk.internal.httpclient.debug=true
 *       -Djdk.httpclient.HttpClient.log=trace,headers,requests
 *       HttpClientClose
 */

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import javax.net.ssl.SSLContext;

import jdk.test.lib.RandomFactory;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.lang.System.err;
import static java.lang.System.in;
import static java.lang.System.out;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class HttpClientClose implements HttpServerAdapters {

    static {
        HttpServerAdapters.enableServerLogging();
    }
    static final Random RANDOM = RandomFactory.getRandom();

    ExecutorService readerService;
    SSLContext sslContext;
    HttpTestServer httpTestServer;        
    HttpTestServer httpsTestServer;       
    HttpTestServer http2TestServer;       
    HttpTestServer https2TestServer;      
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;

    static final String MESSAGE = "HttpClientClose message body";
    static final int ITERATIONS = 3;

    @DataProvider(name = "positive")
    public Object[][] positive() {
        return new Object[][] {
                { httpURI,    },
                { httpsURI,   },
                { http2URI,   },
                { https2URI,  },
        };
    }

    static final AtomicLong requestCounter = new AtomicLong();
    final ReferenceTracker TRACKER = ReferenceTracker.INSTANCE;

    static String readBody(InputStream body) {
        try (InputStream in = body) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    private static record CancellingSubscriber<U>(ExchangeResult<?> result)
            implements Subscriber<U> {
        @Override
        public void onSubscribe(Subscription subscription) {
            out.printf("%s:  cancelling subscription", result.step());
            subscription.cancel();
        }
        @Override
        public void onNext(U item) {}
        @Override
        public void onError(Throwable throwable) {}
        @Override
        public void onComplete() {}
    }

    private static <U> void ensureClosed(ExchangeResult<U> result) {
        var response = result.response;
        if (response == null) return;
        var body = response.body();
        try {
            if (body instanceof Closeable cl) {
                cl.close();
            } else if (body instanceof Publisher<?> pub) {
                pub.subscribe(new CancellingSubscriber<Object>(result));
            }
        } catch (IOException io) {
            out.printf("%s:  Failed to close body: %s", result.step(), io);
            io.printStackTrace(out);
        }
    }

    private record ExchangeResult<T>(int step, HttpResponse<T> response) {
        public static <U> ExchangeResult<U> ofStep(int step) {
            return new ExchangeResult<U>(step, null);
        }
        ExchangeResult<T> withResponse(HttpResponse<T> response) {
            return new ExchangeResult(step, response);
        }
        ExchangeResult<T> assertResponseState() {
            try {
                out.println(step + ":  Got response: " + response);
                assertEquals(response.statusCode(), 200);
            } catch (AssertionError error) {
                out.printf("%s:  Closing body due to assertion - %s", error);
                ensureClosed(this);
                throw error;
            }
            return this;
        }
    }

    @Test(dataProvider = "positive")
    void testConcurrent(String uriString) throws Exception {
        out.printf("%n---- starting concurrent (%s) ----%n%n", uriString);
        Throwable failed = null;
        HttpClient toCheck = null;
        List<CompletableFuture<String>> bodies = new ArrayList<>();
        try (HttpClient client = toCheck = HttpClient.newBuilder()
                .proxy(NO_PROXY)
                .followRedirects(Redirect.ALWAYS)
                .sslContext(sslContext)
                .build()) {
            TRACKER.track(client);

            for (int i = 0; i < ITERATIONS; i++) {
                URI uri = URI.create(uriString + "/concurrent/iteration-" + i);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .header("X-uuid", "uuid-" + requestCounter.incrementAndGet())
                        .build();
                out.printf("Iteration %d request: %s%n", i, request.uri());
                CompletableFuture<HttpResponse<InputStream>> responseCF;
                CompletableFuture<String> bodyCF;
                final int si = i;
                ExchangeResult<InputStream> result = ExchangeResult.ofStep(si);
                responseCF = client.sendAsync(request, BodyHandlers.ofInputStream())
                        .thenApply(result::withResponse)
                        .thenApplyAsync(ExchangeResult::assertResponseState, readerService)
                        .thenApply(ExchangeResult::response);
                bodyCF = responseCF.thenApplyAsync(HttpResponse::body, readerService)
                        .thenApply(HttpClientClose::readBody)
                        .thenApply((s) -> {
                            assertEquals(s, MESSAGE);
                            return s;
                        });
                long sleep = RANDOM.nextLong(5);
                if (sleep > 0) {
                    out.printf("%d: sleeping %d ms%n", i, sleep);
                    Thread.sleep(sleep);
                }
                var cf = bodyCF;
                bodies.add(cf);
            }
        }
        assertTrue(toCheck.isTerminated());
        CompletableFuture.allOf(bodies.toArray(new CompletableFuture<?>[0])).get();
    }

    @Test(dataProvider = "positive")
    void testSequential(String uriString) throws Exception {
        out.printf("%n---- starting sequential (%s) ----%n%n", uriString);
        Throwable failed = null;
        HttpClient toCheck = null;
        try (HttpClient client = toCheck = HttpClient.newBuilder()
                .proxy(NO_PROXY)
                .followRedirects(Redirect.ALWAYS)
                .sslContext(sslContext)
                .build()) {
            TRACKER.track(client);

            for (int i = 0; i < ITERATIONS; i++) {
                URI uri = URI.create(uriString + "/sequential/iteration-" + i);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .header("X-uuid", "uuid-" + requestCounter.incrementAndGet())
                        .build();
                out.printf("Iteration %d request: %s%n", i, request.uri());
                final int si = i;
                ExchangeResult<InputStream> result = ExchangeResult.ofStep(si);
                CompletableFuture<HttpResponse<InputStream>> responseCF;
                CompletableFuture<String> bodyCF;
                responseCF = client.sendAsync(request, BodyHandlers.ofInputStream())
                        .thenApply(result::withResponse)
                        .thenApplyAsync(ExchangeResult::assertResponseState, readerService)
                        .thenApply(ExchangeResult::response);
                bodyCF = responseCF.thenApplyAsync(HttpResponse::body, readerService)
                        .thenApply(HttpClientClose::readBody)
                        .thenApply((s) -> {
                            assertEquals(s, MESSAGE);
                            return s;
                        })
                        .thenApply((s) -> {
                            out.println(si + ":  Got body: " + s);
                            return s;
                        });
                long sleep = RANDOM.nextLong(5);
                if (sleep > 0) {
                    out.printf("%d: sleeping %d ms%n", i, sleep);
                    Thread.sleep(sleep);
                }
                bodyCF.get();
            }
        }
        assertTrue(toCheck.isTerminated());
    }


    @BeforeTest
    public void setup() throws Exception {
        out.println("\n**** Setup ****\n");
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");
        readerService = Executors.newCachedThreadPool();
        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(new ServerRequestHandler(), "/http1/exec/");
        httpURI = "http:
        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(new ServerRequestHandler(),"/https1/exec/");
        httpsURI = "https:

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(new ServerRequestHandler(), "/http2/exec/");
        http2URI = "http:
        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(new ServerRequestHandler(), "/https2/exec/");
        https2URI = "https:

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        Thread.sleep(100);
        AssertionError fail = TRACKER.checkShutdown(5000);
        try {
            shutdown(readerService);
            httpTestServer.stop();
            httpsTestServer.stop();
            http2TestServer.stop();
            https2TestServer.stop();
        } finally {
            if (fail != null) throw fail;
        }
    }

    static void shutdown(ExecutorService executorService) {
        try {
            executorService.shutdown();
            executorService.awaitTermination(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
        }
    }

    static class ServerRequestHandler implements HttpTestHandler {
        ConcurrentHashMap<String,String> closedRequests = new ConcurrentHashMap<>();

        @java.lang.Override
        public void handle(HttpTestExchange t) throws IOException {
            out.println("ServerRequestHandler for: " + t.getRequestURI());

            List<String> uuids = t.getRequestHeaders().get("X-uuid");
            if (uuids == null || uuids.size() != 1) {
                readAllRequestData(t);
                try (OutputStream os = t.getResponseBody()) {
                    String msg = "Incorrect uuid header values:[" + uuids + "]";
                    (new RuntimeException(msg)).printStackTrace();
                    t.sendResponseHeaders(500, -1);
                    os.write(msg.getBytes(UTF_8));
                }
                return;
            }

            String uuid = uuids.get(0);
            if (closedRequests.putIfAbsent(uuid, t.getRequestURI().toString()) == null) {
                if (t.getExchangeVersion() == HTTP_1_1) {
                    out.println("Server will close connection, client will retry: "
                            + t.getRequestURI().toString());
                    throw new IOException("Closing on first request");
                }
            }

            readAllRequestData(t);
            try (OutputStream os = t.getResponseBody()) {
                byte[] bytes = MESSAGE.getBytes(UTF_8);
                t.sendResponseHeaders(200, bytes.length);
                for (int i=0; i<bytes.length; i++) {
                    os.write(bytes, i, 1);
                    os.flush();
                    try {
                        Thread.sleep(RANDOM.nextInt(5));
                    } catch (InterruptedException x) { }
                }
            }

            closedRequests.remove(uuid);
        }
    }

    static void readAllRequestData(HttpTestExchange t) throws IOException {
        try (InputStream is = t.getRequestBody()) {
            is.readAllBytes();
        }
    }
}
