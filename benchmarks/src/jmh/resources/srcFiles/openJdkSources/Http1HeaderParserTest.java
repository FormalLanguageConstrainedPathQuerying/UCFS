/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import java.io.ByteArrayInputStream;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import sun.net.www.MessageHeader;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import static java.lang.System.out;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.stream.Collectors.toList;
import static org.testng.Assert.*;


public class Http1HeaderParserTest {

    @DataProvider(name = "responses")
    public Object[][] responses() {
        List<String> responses = new ArrayList<>();

        String[] basic =
            { "HTTP/1.1 200 OK\r\n\r\n",

              "HTTP/1.1 200 OK\r\n" +
              "Date: Mon, 15 Jan 2001 12:18:21 GMT\r\n" +
              "Server: Apache/1.3.14 (Unix)\r\n" +
              "Connection: close\r\n" +
              "Content-Type: text/html; charset=iso-8859-1\r\n" +
              "Content-Length: 10\r\n\r\n" +
              "123456789",

              "HTTP/1.1 200 OK\r\n" +
              "Content-Length: 9\r\n" +
              "Content-Type: text/html; charset=UTF-8\r\n\r\n" +
              "XXXXX",

              "HTTP/1.1 200 OK\r\n" +
              "X-Header: U\u00ffU\r\n" + 
              "Content-Length: 9\r\n" +
              "Content-Type: text/html; charset=UTF-8\r\n\r\n" +
              "XXXXX",

              "HTTP/1.1 200 OK\r\n" +
              "Content-Length:   9\r\n" +
              "Content-Type:   text/html; charset=UTF-8\r\n\r\n" +   
              "XXXXX",

              "HTTP/1.1 200 OK\r\n" +
              "Content-Length:\t10\r\n" +
              "Content-Type:\ttext/html; charset=UTF-8\r\n\r\n" +   
              "XXXXX",

              "HTTP/1.1 200 OK\r\n" +
              "Content-Length:\t\t10\r\n" +
              "Content-Type:\t\ttext/html; charset=UTF-8\r\n\r\n" +   
              "XXXXX",

              "HTTP/1.1 407 Proxy Authorization Required\r\n" +
              "Proxy-Authenticate: Basic realm=\"a fake realm\"\r\n\r\n",

              "HTTP/1.1 401 Unauthorized\r\n" +
              "WWW-Authenticate: Digest realm=\"wally land\" domain=/ " +
              "nonce=\"2B7F3A2B\" qop=\"auth\"\r\n\r\n",

              "HTTP/1.1 200 OK\r\n" +
              "X-Foo:\r\n\r\n",      

              "HTTP/1.1 200 OK\r\n" +
              "X-Foo:\r\n\r\n" +     
              "Some Response Body",

              "HTTP/1.1 200 OK\r\n" +
              "X-Foo:\r\n" +    
              "Content-Length: 10\r\n\r\n" +
              "Some Response Body",

              "HTTP/1.1 200 OK\r\n" +
              "X-Foo:\r\n" +    
              "Content-Length: 10\r\n\r\n",

              "HTTP/1.1 200 OK\r\n" +
              "X-Foo: chegar\r\n" +
              "X-Foo: dfuchs\r\n" +  
              "Content-Length: 0\r\n" +
              "X-Foo: michaelm\r\n" +
              "X-Foo: prappo\r\n\r\n",

              "HTTP/1.1 200 OK\r\n" +
              "X-Foo:\r\n" +    
              "X-Foo: dfuchs\r\n" +
              "Content-Length: 0\r\n" +
              "X-Foo: michaelm\r\n" +
              "X-Foo: prappo\r\n\r\n",

              "HTTP/1.1 200 OK\r\n" +
              "Accept-Ranges: bytes\r\n" +
              "Cache-control: max-age=0, no-cache=\"set-cookie\"\r\n" +
              "Content-Length: 132868\r\n" +
              "Content-Type: text/html; charset=UTF-8\r\n" +
              "Date: Sun, 05 Nov 2017 22:24:03 GMT\r\n" +
              "Server: Apache/2.4.6 (Red Hat Enterprise Linux) OpenSSL/1.0.1e-fips Communique/4.2.2\r\n" +
              "Set-Cookie: AWSELB=AF7927F5100F4202119876ED2436B5005EE;PATH=/;MAX-AGE=900\r\n" +
              "Vary: Host,Accept-Encoding,User-Agent\r\n" +
              "X-Mod-Pagespeed: 1.12.34.2-0\r\n" +
              "Connection: keep-alive\r\n\r\n"
            };
        Arrays.stream(basic).forEach(responses::add);
        Arrays.stream(basic)
                .map(Http1HeaderParserTest::mixedCRLF)
                .forEach(responses::add);

        String[] foldingTemplate =
           {  "HTTP/1.1 200 OK\r\n" +
              "Content-Length: 9\r\n" +
              "Content-Type: text/html;$NEWLINE" +  
              " charset=UTF-8\r\n" +                
              "Connection: close\r\n\r\n" +
              "XXYYZZAABBCCDDEE",

              "HTTP/1.1 200 OK\r\n" +
              "Content-Length: 19\r\n" +
              "Content-Type: text/html;$NEWLINE" +  
              "   charset=UTF-8\r\n" +              
              "Connection: keep-alive\r\n\r\n" +
              "XXYYZZAABBCCDDEEFFGG",

              "HTTP/1.1 200 OK\r\n" +
              "Content-Length: 999\r\n" +
              "Content-Type: text/html;$NEWLINE" +  
              "\tcharset=UTF-8\r\n" +               
              "Connection: close\r\n\r\n" +
              "XXYYZZAABBCCDDEE",

              "HTTP/1.1 200 OK\r\n" +
              "Content-Length: 54\r\n" +
              "Content-Type: text/html;$NEWLINE" +  
              "\t\t\tcharset=UTF-8\r\n" +           
              "Connection: keep-alive\r\n\r\n" +
              "XXYYZZAABBCCDDEEFFGG",

              "HTTP/1.1 200 OK\r\n" +
              "Content-Length: -1\r\n" +
              "Content-Type: text/html;$NEWLINE" +  
              "\t \t \tcharset=UTF-8\r\n" +         
              "Connection: keep-alive\r\n\r\n" +
              "XXYYZZAABBCCDDEEFFGGHH",

              "HTTP/1.1 200 OK\r\n" +
              "Content-Length: 65\r\n" +
              "Content-Type: text/html;$NEWLINE" +  
              " \t \t charset=UTF-8\r\n" +          
              "Connection: keep-alive\r\n\r\n" +
              "XXYYZZAABBCCDDEEFFGGHHII",

              "HTTP/1.1 401 Unauthorized\r\n" +
              "WWW-Authenticate: Digest realm=\"wally land\","
                      +"$NEWLINE    domain=/,"
                      +"$NEWLINE nonce=\"2B7F3A2B\","
                      +"$NEWLINE\tqop=\"auth\"\r\n\r\n",

           };
        for (String newLineChar : new String[] { "\n", "\r", "\r\n" }) {
            for (String template : foldingTemplate)
                responses.add(template.replace("$NEWLINE", newLineChar));
        }
        for (String newLineChar : new String[] { "\n", "\r", "\r\n" }) {
            for (String template : foldingTemplate)
                responses.add(mixedCRLF(template).replace("$NEWLINE", newLineChar));
        }

        String[] bad = 
           { "HTTP/1.1 200 OK\r\n" +
             "Connection:\r\n\r\n",   

             "HTTP/1.1 200 OK\r\n" +
             "Connection:\r\n\r\n" +  
             "XXXXX",

             "HTTP/1.1 200 OK\r\n" +
             ": no header\r\n\r\n",  

             "HTTP/1.1 200 OK\r\n" +
             ": no; header\r\n" +  
             "Content-Length: 65\r\n\r\n",

             "HTTP/1.1 200 OK\r\n" +
             ": no header\r\n" +  
             "Content-Length: 65\r\n\r\n" +
             "XXXXX",

             "HTTP/1.1 200 OK\r\n" +
             "X-foo: bar\r\n" +
             " : no header\r\n" +  
             "Content-Length: 65\r\n\r\n" +
             "XXXXX",

             "HTTP/1.1 200 OK\r\n" +
             "X-foo: bar\r\n" +
             " \t : no header\r\n" +  
             "Content-Length: 65\r\n\r\n" +
             "XXXXX",

             "HTTP/1.1 200 OK\r\n" +
             ": no header\r\n\r\n" +  
             "XXXXX",

             "HTTP/1.1 200 OK\r\n" +
             "Conte\r" +
             "nt-Length: 9\r\n" +    
             "Content-Type: text/html; charset=UTF-8\r\n\r\n" +
             "XXXXXYYZZ",

             "HTTP/1.0 404 Not Found\r\n" +
             "header-without-colon\r\n\r\n",

             "HTTP/1.0 404 Not Found\r\n" +
             "header-without-colon\r\n\r\n" +
             "SOMEBODY",

           };
        Arrays.stream(bad).forEach(responses::add);

        return responses.stream().map(p -> new Object[] { p }).toArray(Object[][]::new);
    }

    static final AtomicInteger index = new AtomicInteger();
    static final AtomicInteger limit = new AtomicInteger(1);
    static final AtomicBoolean useCRLF = new AtomicBoolean();
    static final String mixedCRLF(String headers) {
        int next;
        int start = 0;
        int last = headers.lastIndexOf("\r\n");
        String prev = "";
        StringBuilder res = new StringBuilder();
        while ((next = headers.indexOf("\r\n", start)) > 0) {
            res.append(headers.substring(start, next));
            if ("\n".equals(prev) && next == last) {
                res.append(prev);
            } else {
                prev = useCRLF.get() ? "\r\n" : "\n";
                res.append(prev);
            }
            start = next + 2;

            index.incrementAndGet();
            if (index.get() == limit.get()) {
                index.set(0);
                if (useCRLF.get()) limit.incrementAndGet();
                if (limit.get() > 3) limit.set(1);
                useCRLF.set(!useCRLF.get());
            }
        }
        res.append(headers.substring(start));
        return res.toString();
    }


    @Test(dataProvider = "responses")
    public void verifyHeaders(String respString) throws Exception {
        System.out.println("\ntesting:\n\t" + respString
                .replace("\r\n", "<CRLF>")
                .replace("\r", "<CR>")
                .replace("\n","<LF>")
                .replace("LF>", "LF>\n\t"));
        byte[] bytes = respString.getBytes(ISO_8859_1);
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        MessageHeader m = new MessageHeader(bais);
        Map<String,List<String>> messageHeaderMap = m.getHeaders();
        int availableBytes = bais.available();

        Http1HeaderParser decoder = new Http1HeaderParser();
        ByteBuffer b = ByteBuffer.wrap(bytes);
        decoder.parse(b);
        System.out.printf("Http1HeaderParser parsed %d bytes out of %d%n", b.position(), bytes.length);
        Map<String,List<String>> decoderMap1 = decoder.headers().map();

        String statusLine1 = messageHeaderMap.get(null).get(0);
        String statusLine2 = decoder.statusLine();
        if (statusLine1.startsWith("HTTP")) {
            assertEquals(statusLine2, statusLine1, "Status-line not equal");
        } else {
            assertTrue(statusLine2.startsWith("HTTP/1."), "Status-line not HTTP/1.");
        }

        Map<String,List<String>> map = new HashMap<>();
        for (Map.Entry<String,List<String>> e : messageHeaderMap.entrySet()) {
            if (e.getKey() != null) {
                map.put(e.getKey(), e.getValue());
            }
        }
        messageHeaderMap = map;

        assertHeadersEqual(messageHeaderMap, decoderMap1,
                          "messageHeaderMap not equal to decoderMap1");

        assertEquals(availableBytes, b.remaining(),
                String.format("stream available (%d) not equal to remaining (%d)",
                        availableBytes, b.remaining()));
        decoder = new Http1HeaderParser();
        List<ByteBuffer> buffers = IntStream.range(0, bytes.length)
                .mapToObj(i -> ByteBuffer.wrap(bytes, i, 1))
                .collect(toList());
        while (decoder.parse(buffers.remove(0)) != true);
        Map<String,List<String>> decoderMap2 = decoder.headers().map();
        assertEquals(availableBytes, buffers.size(),
                     "stream available not equals to remaining buffers");
        assertEquals(decoderMap1, decoderMap2, "decoder maps not equal");

    }

    @DataProvider(name = "errors")
    public Object[][] errors() {
        List<String> responses = new ArrayList<>();

        String[] bad =
           {

             "HTTP/1.1 402 Payment Required\r\n" +
             "Content-Length: 65\r\n\rT\r\n\r\nGGGGGG",

             "HTTP/1.1 200OK\r\n\rT",

             "HTTP/1.1 200OK\rT",

             "HTTP/1.0 FOO\r\n",

             "HTTP/1.1 BAR\r\n",

             "HTTP/1.1 +99\r\n",

             "HTTP/1.1 -22\r\n",

             "HTTP/1.1 -20 \r\n",

             "HTTP/1.1 200 OK\r\n" +
             "X-fo\u00ffo: foo\r\n" +     
             "Content-Length: 5\r\n" +
             "Content-Type: text/html; charset=UTF-8\r\n\r\n" +
             "XXXXX",

             "HTTP/1.1 200 OK\r\n" +
             "HTTP/1.1 200 OK\r\n" +
             "X-foo : bar\r\n" +          
             "Content-Length: 5\r\n" +
             "Content-Type: text/html; charset=UTF-8\r\n\r\n" +
             "XXXXX",

             "HTTP/1.1 200 OK\r\n" +
             " X-foo: bar\r\n" +          
             "Content-Length: 5\r\n" +
             "Content-Type: text/html; charset=UTF-8\r\n\r\n" +
             "XXXXX",

             "HTTP/1.1 200 OK\r\n" +
             "X foo: bar\r\n" +           
             "Content-Length: 5\r\n" +
             "Content-Type: text/html; charset=UTF-8\r\n\r\n" +
             "XXXXX",

             "HTTP/1.1 200 OK\r\n" +
             "Content-Length: 5\r\n" +
             "Content Type: text/html; charset=UTF-8\r\n\r\n" + 
             "XXXXX",

             "HTTP/1.1 200 OK\r\n" +
             "Conte\r" +
             " nt-Length: 9\r\n" +    
             "Content-Type: text/html; charset=UTF-8\r\n\r\n" +
             "XXXXX",

             "HTTP/1.1 200 OK\r\n" +
             " : no header\r\n" +  
             "Content-Length: 65\r\n\r\n" +
             "XXXXX",

             "HTTP/1.1 200 OK\r\n" +
             " \t : no header\r\n" +  
             "Content-Length: 65\r\n\r\n" +
             "XXXXX",

           };
        Arrays.stream(bad).forEach(responses::add);

        return responses.stream().map(p -> new Object[] { p }).toArray(Object[][]::new);
    }

    @Test(dataProvider = "errors", expectedExceptions = ProtocolException.class)
    public void errors(String respString) throws ProtocolException {
        byte[] bytes = respString.getBytes(US_ASCII);
        Http1HeaderParser decoder = new Http1HeaderParser();
        ByteBuffer b = ByteBuffer.wrap(bytes);
        decoder.parse(b);
    }

    void assertHeadersEqual(Map<String,List<String>> expected,
                            Map<String,List<String>> actual,
                            String msg) {

        if (expected.equals(actual))
            return;

        assertEquals(expected.size(), actual.size(),
                     format("%s. Expected size %d, actual size %s. %nexpected= %s,%n actual=%s.",
                            msg, expected.size(), actual.size(), mapToString(expected), mapToString(actual)));

        for (Map.Entry<String,List<String>> e : expected.entrySet()) {
            String key = e.getKey();
            List<String> values = e.getValue();

            boolean found = false;
            for (Map.Entry<String,List<String>> other: actual.entrySet()) {
                if (key.equalsIgnoreCase(other.getKey())) {
                    found = true;
                    List<String> otherValues = other.getValue();
                    assertEquals(values.size(), otherValues.size(),
                                 format("%s. Expected list size %d, actual size %s",
                                        msg, values.size(), otherValues.size()));
                    if (!(values.containsAll(otherValues) && otherValues.containsAll(values)))
                        assertTrue(false, format("Lists are unequal [%s] [%s]", values, otherValues));
                    break;
                }
            }
            assertTrue(found, format("header name, %s, not found in %s", key, actual));
        }
    }

    static String mapToString(Map<String,List<String>> map) {
        StringBuilder sb = new StringBuilder();
        List<String> sortedKeys = new ArrayList(map.keySet());
        Collections.sort(sortedKeys);
        for (String key : sortedKeys) {
            List<String> values = map.get(key);
            sb.append("\n\t" + key + " | " + values);
        }
        return sb.toString();
    }


    /* Main entry point for standalone testing of the main functional test. */
    public static void main(String... args) throws Exception  {
        Http1HeaderParserTest test = new Http1HeaderParserTest();
        int count = 0;
        for (Object[] objs : test.responses()) {
            out.println("Testing " + count++ + ", " + objs[0]);
            test.verifyHeaders((String) objs[0]);
        }
        for (Object[] objs : test.errors()) {
            out.println("Testing " + count++ + ", " + objs[0]);
            try {
                test.errors((String) objs[0]);
                throw new RuntimeException("Expected ProtocolException for " + objs[0]);
            } catch (ProtocolException expected) { /* Ok */ }
        }
    }
}
