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

package sun.security.ssl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import javax.net.ssl.SSLHandshakeException;
import java.security.cert.X509Certificate;
import sun.security.provider.certpath.OCSPResponse;
import sun.security.ssl.SSLHandshake.HandshakeMessage;
import static sun.security.ssl.CertStatusExtension.*;
import static sun.security.ssl.CertificateMessage.*;

/**
 * Consumers and producers for the CertificateStatus handshake message.
 * This message takes one of two related but slightly different forms,
 * depending on the type of stapling selected by the server.  The message
 * data will be of the form(s):
 *
 *  [status_request, RFC 6066]
 *
 *  struct {
 *      CertificateStatusType status_type;
 *      select (status_type) {
 *          case ocsp: OCSPResponse;
 *      } response;
 *  } CertificateStatus;
 *
 *  opaque OCSPResponse<1..2^24-1>;
 *
 *  [status_request_v2, RFC 6961]
 *
 *  struct {
 *      CertificateStatusType status_type;
 *      select (status_type) {
 *        case ocsp: OCSPResponse;
 *        case ocsp_multi: OCSPResponseList;
 *      } response;
 *  } CertificateStatus;
 *
 *  opaque OCSPResponse<0..2^24-1>;
 *
 *  struct {
 *      OCSPResponse ocsp_response_list<1..2^24-1>;
 *  } OCSPResponseList;
 */
final class CertificateStatus {
    static final SSLConsumer handshakeConsumer =
            new CertificateStatusConsumer();
    static final HandshakeProducer handshakeProducer =
            new CertificateStatusProducer();
    static final HandshakeAbsence handshakeAbsence =
            new CertificateStatusAbsence();

    /**
     * The CertificateStatus handshake message.
     */
    static final class CertificateStatusMessage extends HandshakeMessage {

        final CertStatusRequestType statusType;
        final int encodedResponsesLen;
        final int messageLength;
        final List<byte[]> encodedResponses = new ArrayList<>();

        CertificateStatusMessage(HandshakeContext handshakeContext) {
            super(handshakeContext);

            ServerHandshakeContext shc =
                    (ServerHandshakeContext)handshakeContext;

            StatusResponseManager.StaplingParameters stapleParams =
                    shc.stapleParams;
            if (stapleParams == null) {
                throw new IllegalArgumentException(
                        "Unexpected null stapling parameters");
            }

            X509Certificate[] certChain =
                (X509Certificate[])shc.handshakeSession.getLocalCertificates();
            if (certChain == null) {
                throw new IllegalArgumentException(
                        "Unexpected null certificate chain");
            }

            statusType = stapleParams.statReqType;
            int encodedLen = 0;
            if (statusType == CertStatusRequestType.OCSP) {
                byte[] resp = stapleParams.responseMap.get(certChain[0]);
                if (resp == null) {
                    resp = new byte[0];
                }
                encodedResponses.add(resp);
                encodedLen += resp.length + 3;
            } else if (statusType == CertStatusRequestType.OCSP_MULTI) {
                for (X509Certificate cert : certChain) {
                    byte[] resp = stapleParams.responseMap.get(cert);
                    if (resp == null) {
                        resp = new byte[0];
                    }
                    encodedResponses.add(resp);
                    encodedLen += resp.length + 3;
                }
            } else {
                throw new IllegalArgumentException(
                        "Unsupported StatusResponseType: " + statusType);
            }

            encodedResponsesLen = encodedLen;
            messageLength = messageLength(statusType, encodedResponsesLen);
        }

        CertificateStatusMessage(HandshakeContext handshakeContext,
                ByteBuffer m) throws IOException {
            super(handshakeContext);

            statusType = CertStatusRequestType.valueOf((byte)Record.getInt8(m));
            if (statusType == CertStatusRequestType.OCSP) {
                byte[] respDER = Record.getBytes24(m);
                if (respDER.length > 0) {
                    encodedResponses.add(respDER);
                    encodedResponsesLen = 3 + respDER.length;
                } else {
                    throw handshakeContext.conContext.fatal(
                            Alert.HANDSHAKE_FAILURE,
                            "Zero-length OCSP Response");
                }
            } else if (statusType == CertStatusRequestType.OCSP_MULTI) {
                int respListLen = Record.getInt24(m);
                encodedResponsesLen = respListLen;

                while (respListLen > 0) {
                    byte[] respDER = Record.getBytes24(m);
                    encodedResponses.add(respDER);
                    respListLen -= (respDER.length + 3);
                }

                if (respListLen != 0) {
                    throw handshakeContext.conContext.fatal(
                            Alert.INTERNAL_ERROR,
                            "Bad OCSP response list length");
                }
            } else {
                throw handshakeContext.conContext.fatal(
                        Alert.HANDSHAKE_FAILURE,
                        "Unsupported StatusResponseType: " + statusType);
            }
            messageLength = messageLength(statusType, encodedResponsesLen);
        }

        private static int messageLength(
                CertStatusRequestType statusType, int encodedResponsesLen) {
            if (statusType == CertStatusRequestType.OCSP) {
                return 1 + encodedResponsesLen;
            } else if (statusType == CertStatusRequestType.OCSP_MULTI) {
                return 4 + encodedResponsesLen;
            }

            return -1;
        }

        @Override
        public SSLHandshake handshakeType() {
            return SSLHandshake.CERTIFICATE_STATUS;
        }

        @Override
        public int messageLength() {
            return messageLength;
        }

        @Override
        public void send(HandshakeOutStream s) throws IOException {
            s.putInt8(statusType.id);
            if (statusType == CertStatusRequestType.OCSP) {
                s.putBytes24(encodedResponses.get(0));
            } else if (statusType == CertStatusRequestType.OCSP_MULTI) {
                s.putInt24(encodedResponsesLen);
                for (byte[] respBytes : encodedResponses) {
                    s.putBytes24(respBytes);
                }
            } else {
                throw new SSLHandshakeException("Unsupported status_type: " +
                        statusType.id);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            for (byte[] respDER : encodedResponses) {
                if (respDER.length > 0) {
                    try {
                        OCSPResponse oResp = new OCSPResponse(respDER);
                        sb.append(oResp.toString()).append("\n");
                    } catch (IOException ioe) {
                        sb.append("OCSP Response Exception: ").append(ioe)
                                .append("\n");
                    }
                } else {
                    sb.append("<Zero-length entry>\n");
                }
            }

            MessageFormat messageFormat = new MessageFormat(
                    """
                            "CertificateStatus": '{'
                              "type"                : "{0}",
                              "responses "          : [
                            {1}
                              ]
                            '}'""",
                Locale.ENGLISH);
            Object[] messageFields = {
                statusType.name,
                Utilities.indent(Utilities.indent(sb.toString()))
            };

            return messageFormat.format(messageFields);
        }
    }

    /**
     * The CertificateStatus handshake message consumer.
     */
    private static final class CertificateStatusConsumer
            implements SSLConsumer {
        private CertificateStatusConsumer() {
        }

        @Override
        public void consume(ConnectionContext context,
                ByteBuffer message) throws IOException {
            ClientHandshakeContext chc = (ClientHandshakeContext)context;
            CertificateStatusMessage cst =
                    new CertificateStatusMessage(chc, message);

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Consuming server CertificateStatus handshake message",
                        cst);
            }

            chc.handshakeSession.setStatusResponses(cst.encodedResponses);

            T12CertificateConsumer.checkServerCerts(chc, chc.deferredCerts);

            chc.handshakeConsumers.remove(SSLHandshake.CERTIFICATE_STATUS.id);
        }
    }

    /**
     * The CertificateStatus handshake message consumer.
     */
    private static final class CertificateStatusProducer
            implements HandshakeProducer {
        private CertificateStatusProducer() {
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            if (!shc.staplingActive) {
                return null;
            }

            CertificateStatusMessage csm = new CertificateStatusMessage(shc);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                    "Produced server CertificateStatus handshake message", csm);
            }

            csm.write(shc.handshakeOutput);
            shc.handshakeOutput.flush();

            return null;
        }
    }

    private static final class CertificateStatusAbsence
            implements HandshakeAbsence {
        private CertificateStatusAbsence() {
        }

        @Override
        public void absent(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            if (chc.staplingActive) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine("Server did not send CertificateStatus, " +
                            "checking cert chain without status info.");
                }
                T12CertificateConsumer.checkServerCerts(chc, chc.deferredCerts);
            }
        }
    }
}

