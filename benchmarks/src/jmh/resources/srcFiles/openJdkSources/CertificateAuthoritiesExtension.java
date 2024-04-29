/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.*;
import javax.net.ssl.SSLProtocolException;
import javax.security.auth.x500.X500Principal;
import sun.security.ssl.SSLExtension.ExtensionConsumer;
import sun.security.ssl.SSLExtension.SSLExtensionSpec;
import sun.security.ssl.SSLHandshake.HandshakeMessage;

/**
 * Pack of the "certificate_authorities" extensions.
 */
final class CertificateAuthoritiesExtension {
    static final HandshakeProducer chNetworkProducer =
            new CHCertificateAuthoritiesProducer();
    static final ExtensionConsumer chOnLoadConsumer =
            new CHCertificateAuthoritiesConsumer();

    static final HandshakeProducer crNetworkProducer =
            new CRCertificateAuthoritiesProducer();
    static final ExtensionConsumer crOnLoadConsumer =
            new CRCertificateAuthoritiesConsumer();

    static final SSLStringizer ssStringizer =
            new CertificateAuthoritiesStringizer();

    /**
     * The "certificate_authorities" extension.
     */
    static final class CertificateAuthoritiesSpec implements SSLExtensionSpec {
        final List<byte[]> authorities;     

        private CertificateAuthoritiesSpec(List<byte[]> authorities) {
            this.authorities = authorities;
        }

        private CertificateAuthoritiesSpec(HandshakeContext hc,
                ByteBuffer m) throws IOException  {
            if (m.remaining() < 3) {        
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                    "Invalid certificate_authorities extension: " +
                    "insufficient data"));
            }

            int listLen = Record.getInt16(m);
            if (listLen == 0) {
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                    "Invalid certificate_authorities extension: " +
                    "no certificate authorities");
            }

            if (listLen > m.remaining()) {
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                    "Invalid certificate_authorities extension: " +
                    "insufficient data");
            }

            this.authorities = new LinkedList<>();
            while (listLen > 0) {
                byte[] encoded = Record.getBytes16(m);
                listLen -= (2 + encoded.length);
                authorities.add(encoded);
            }
        }

        private static List<byte[]> getEncodedAuthorities(
                X509Certificate[] trustedCerts) {
            List<byte[]> authorities = new ArrayList<>(trustedCerts.length);
            int sizeAccount = 0;
            for (X509Certificate cert : trustedCerts) {
                X500Principal x500Principal = cert.getSubjectX500Principal();
                byte[] encodedPrincipal = x500Principal.getEncoded();
                sizeAccount += encodedPrincipal.length;
                if (sizeAccount > 0xFFFF) {  
                    return Collections.emptyList();
                }

                if (encodedPrincipal.length != 0) {
                    authorities.add(encodedPrincipal);
                }
            }

            return authorities;
        }

        X500Principal[] getAuthorities() {
            X500Principal[] principals = new X500Principal[authorities.size()];

            int i = 0;
            for (byte[] encoded : authorities) {
                principals[i++] = new X500Principal(encoded);
            }

            return principals;
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                "\"certificate authorities\": '['\n{0}']'", Locale.ENGLISH);
            StringBuilder builder = new StringBuilder(512);
            for (byte[] encoded : authorities) {
                try {
                    X500Principal principal = new X500Principal(encoded);
                    builder.append(principal.toString());
                } catch (IllegalArgumentException iae) {
                    builder.append("unparseable distinguished name: " + iae);
                }
                builder.append("\n");
            }
            Object[] messageFields = {
                Utilities.indent(builder.toString())
            };

            return messageFormat.format(messageFields);
        }
    }

    private static final
            class CertificateAuthoritiesStringizer implements SSLStringizer {
        @Override
        public String toString(HandshakeContext hc, ByteBuffer buffer) {
            try {
                return (new CertificateAuthoritiesSpec(hc, buffer))
                        .toString();
            } catch (IOException ioe) {
                return ioe.getMessage();
            }
        }
    }

    /**
     * Network data producer of a "certificate_authorities" extension in
     * the ClientHello handshake message.
     */
    private static final
        class CHCertificateAuthoritiesProducer implements HandshakeProducer {

        private CHCertificateAuthoritiesProducer() {
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            if (!chc.sslConfig.isAvailable(
                    SSLExtension.CH_CERTIFICATE_AUTHORITIES)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "Ignore unavailable " +
                            "certificate_authorities extension");
                }

                return null;    
            }

            X509Certificate[] caCerts =
                    chc.sslContext.getX509TrustManager().getAcceptedIssuers();
            if (caCerts.length == 0) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "No available certificate authorities");
                }

                return null;    
            }

            List<byte[]> encodedCAs =
                    CertificateAuthoritiesSpec.getEncodedAuthorities(caCerts);
            if (encodedCAs.isEmpty()) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning(
                            "The number of CAs exceeds the maximum size " +
                            "of the certificate_authorities extension");
                }

                return null;    
            }

            CertificateAuthoritiesSpec spec =
                    new CertificateAuthoritiesSpec(encodedCAs);

            int vectorLen = 0;
            for (byte[] encoded : spec.authorities) {
                vectorLen += encoded.length + 2;
            }

            byte[] extData = new byte[vectorLen + 2];
            ByteBuffer m = ByteBuffer.wrap(extData);
            Record.putInt16(m, vectorLen);
            for (byte[] encoded : spec.authorities) {
                Record.putBytes16(m, encoded);
            }

            chc.handshakeExtensions.put(
                    SSLExtension.CH_CERTIFICATE_AUTHORITIES, spec);

            return extData;
        }
    }

    /**
     * Network data consumer of a "certificate_authorities" extension in
     * the ClientHello handshake message.
     */
    private static final
        class CHCertificateAuthoritiesConsumer implements ExtensionConsumer {

        private CHCertificateAuthoritiesConsumer() {
        }

        @Override
        public void consume(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {

            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            if (!shc.sslConfig.isAvailable(
                    SSLExtension.CH_CERTIFICATE_AUTHORITIES)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "Ignore unavailable " +
                            "certificate_authorities extension");
                }

                return;     
            }

            CertificateAuthoritiesSpec spec =
                    new CertificateAuthoritiesSpec(shc, buffer);

            try {
                shc.peerSupportedAuthorities = spec.getAuthorities();
            } catch (IllegalArgumentException iae) {
                shc.conContext.fatal(Alert.DECODE_ERROR, "The distinguished " +
                        "names of the peer's certificate authorities could " +
                        "not be parsed", iae);
            }
            shc.handshakeExtensions.put(
                    SSLExtension.CH_CERTIFICATE_AUTHORITIES, spec);

        }
    }

    /**
     * Network data producer of a "certificate_authorities" extension in
     * the CertificateRequest handshake message.
     */
    private static final
        class CRCertificateAuthoritiesProducer implements HandshakeProducer {

        private CRCertificateAuthoritiesProducer() {
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            if (!shc.sslConfig.isAvailable(
                    SSLExtension.CR_CERTIFICATE_AUTHORITIES)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "Ignore unavailable " +
                            "certificate_authorities extension");
                }

                return null;    
            }

            X509Certificate[] caCerts =
                    shc.sslContext.getX509TrustManager().getAcceptedIssuers();
            if (caCerts.length == 0) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "No available certificate authorities");
                }

                return null;    
            }

            List<byte[]> encodedCAs =
                    CertificateAuthoritiesSpec.getEncodedAuthorities(caCerts);
            if (encodedCAs.isEmpty()) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.warning(
                        "Too many certificate authorities to use " +
                            "the certificate_authorities extension");
                }

                return null;    
            }

            CertificateAuthoritiesSpec spec =
                    new CertificateAuthoritiesSpec(encodedCAs);

            int vectorLen = 0;
            for (byte[] encoded : spec.authorities) {
                vectorLen += encoded.length + 2;
            }

            byte[] extData = new byte[vectorLen + 2];
            ByteBuffer m = ByteBuffer.wrap(extData);
            Record.putInt16(m, vectorLen);
            for (byte[] encoded : spec.authorities) {
                Record.putBytes16(m, encoded);
            }

            shc.handshakeExtensions.put(
                    SSLExtension.CR_CERTIFICATE_AUTHORITIES, spec);

            return extData;
        }
    }

    /**
     * Network data consumer of a "certificate_authorities" extension in
     * the CertificateRequest handshake message.
     */
    private static final
        class CRCertificateAuthoritiesConsumer implements ExtensionConsumer {

        private CRCertificateAuthoritiesConsumer() {
        }

        @Override
        public void consume(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {

            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            if (!chc.sslConfig.isAvailable(
                    SSLExtension.CR_CERTIFICATE_AUTHORITIES)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "Ignore unavailable " +
                            "certificate_authorities extension");
                }

                return;     
            }

            CertificateAuthoritiesSpec spec =
                    new CertificateAuthoritiesSpec(chc, buffer);

            try {
                chc.peerSupportedAuthorities = spec.getAuthorities();
            } catch (IllegalArgumentException iae) {
                chc.conContext.fatal(Alert.DECODE_ERROR, "The distinguished " +
                        "names of the peer's certificate authorities could " +
                        "not be parsed", iae);
            }
            chc.handshakeExtensions.put(
                    SSLExtension.CR_CERTIFICATE_AUTHORITIES, spec);

        }
    }
}
