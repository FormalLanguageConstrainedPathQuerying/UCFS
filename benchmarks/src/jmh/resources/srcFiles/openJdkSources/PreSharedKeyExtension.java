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
import java.security.*;
import java.text.MessageFormat;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLProtocolException;
import static sun.security.ssl.ClientAuthType.CLIENT_AUTH_REQUIRED;
import sun.security.ssl.ClientHello.ClientHelloMessage;
import sun.security.ssl.SSLExtension.ExtensionConsumer;
import sun.security.ssl.SSLExtension.SSLExtensionSpec;
import sun.security.ssl.SSLHandshake.HandshakeMessage;
import sun.security.ssl.SessionTicketExtension.SessionTicketSpec;
import sun.security.util.HexDumpEncoder;

import static sun.security.ssl.SSLExtension.*;

/**
 * Pack of the "pre_shared_key" extension.
 */
final class PreSharedKeyExtension {
    static final HandshakeProducer chNetworkProducer =
            new CHPreSharedKeyProducer();
    static final ExtensionConsumer chOnLoadConsumer =
            new CHPreSharedKeyConsumer();
    static final HandshakeAbsence chOnLoadAbsence =
            new CHPreSharedKeyOnLoadAbsence();
    static final HandshakeConsumer chOnTradeConsumer =
            new CHPreSharedKeyUpdate();
    static final HandshakeAbsence chOnTradAbsence =
            new CHPreSharedKeyOnTradeAbsence();
    static final SSLStringizer chStringizer =
            new CHPreSharedKeyStringizer();

    static final HandshakeProducer shNetworkProducer =
            new SHPreSharedKeyProducer();
    static final ExtensionConsumer shOnLoadConsumer =
            new SHPreSharedKeyConsumer();
    static final HandshakeAbsence shOnLoadAbsence =
            new SHPreSharedKeyAbsence();
    static final SSLStringizer shStringizer =
            new SHPreSharedKeyStringizer();

    private static final class PskIdentity {
        final byte[] identity;
        final int obfuscatedAge;

        PskIdentity(byte[] identity, int obfuscatedAge) {
            this.identity = identity;
            this.obfuscatedAge = obfuscatedAge;
        }

        int getEncodedLength() {
            return 2 + identity.length + 4;
        }

        void writeEncoded(ByteBuffer m) throws IOException {
            Record.putBytes16(m, identity);
            Record.putInt32(m, obfuscatedAge);
        }

        @Override
        public String toString() {
            return "{" + Utilities.toHexString(identity) + ", " +
                obfuscatedAge + "}";
        }
    }

    private static final
            class CHPreSharedKeySpec implements SSLExtensionSpec {
        final List<PskIdentity> identities;
        final List<byte[]> binders;

        CHPreSharedKeySpec(List<PskIdentity> identities, List<byte[]> binders) {
            this.identities = identities;
            this.binders = binders;
        }

        CHPreSharedKeySpec(HandshakeContext hc,
                ByteBuffer m) throws IOException {
            if (m.remaining() < 44) {
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                    "Invalid pre_shared_key extension: " +
                    "insufficient data (length=" + m.remaining() + ")"));
            }

            int idEncodedLength = Record.getInt16(m);
            if (idEncodedLength < 7) {
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                    "Invalid pre_shared_key extension: " +
                    "insufficient identities (length=" + idEncodedLength + ")"));
            }

            identities = new ArrayList<>();
            int idReadLength = 0;
            while (idReadLength < idEncodedLength) {
                byte[] id = Record.getBytes16(m);
                if (id.length < 1) {
                    throw hc.conContext.fatal(Alert.DECODE_ERROR,
                            new SSLProtocolException(
                        "Invalid pre_shared_key extension: " +
                        "insufficient identity (length=" + id.length + ")"));
                }
                int obfuscatedTicketAge = Record.getInt32(m);

                PskIdentity pskId = new PskIdentity(id, obfuscatedTicketAge);
                identities.add(pskId);
                idReadLength += pskId.getEncodedLength();
            }

            if (m.remaining() < 35) {
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                    "Invalid pre_shared_key extension: " +
                    "insufficient binders data (length=" +
                    m.remaining() + ")"));
            }

            int bindersEncodedLen = Record.getInt16(m);
            if (bindersEncodedLen < 33) {
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                    "Invalid pre_shared_key extension: " +
                    "insufficient binders (length=" +
                    bindersEncodedLen + ")"));
            }

            binders = new ArrayList<>();
            int bindersReadLength = 0;
            while (bindersReadLength < bindersEncodedLen) {
                byte[] binder = Record.getBytes8(m);
                if (binder.length < 32) {
                    throw hc.conContext.fatal(Alert.DECODE_ERROR,
                            new SSLProtocolException(
                        "Invalid pre_shared_key extension: " +
                        "insufficient binder entry (length=" +
                        binder.length + ")"));
                }
                binders.add(binder);
                bindersReadLength += 1 + binder.length;
            }
        }

        int getIdsEncodedLength() {
            int idEncodedLength = 0;
            for (PskIdentity curId : identities) {
                idEncodedLength += curId.getEncodedLength();
            }

            return idEncodedLength;
        }

        int getBindersEncodedLength() {
            int binderEncodedLength = 0;
            for (byte[] curBinder : binders) {
                binderEncodedLength += 1 + curBinder.length;
            }

            return binderEncodedLength;
        }

        byte[] getEncoded() throws IOException {
            int idsEncodedLength = getIdsEncodedLength();
            int bindersEncodedLength = getBindersEncodedLength();
            int encodedLength = 4 + idsEncodedLength + bindersEncodedLength;
            byte[] buffer = new byte[encodedLength];
            ByteBuffer m = ByteBuffer.wrap(buffer);
            Record.putInt16(m, idsEncodedLength);
            for (PskIdentity curId : identities) {
                curId.writeEncoded(m);
            }
            Record.putInt16(m, bindersEncodedLength);
            for (byte[] curBinder : binders) {
                Record.putBytes8(m, curBinder);
            }

            return buffer;
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                    """
                            "PreSharedKey": '{'
                              "identities": '{'
                            {0}
                              '}'  "binders": "{1}",
                            '}'""",
                Locale.ENGLISH);

            Object[] messageFields = {
                Utilities.indent(identitiesString()),
                Utilities.indent(bindersString())
            };

            return messageFormat.format(messageFields);
        }

        String identitiesString() {
            HexDumpEncoder hexEncoder = new HexDumpEncoder();

            StringBuilder result = new StringBuilder();
            for (PskIdentity curId : identities) {
                result.append("  {\n"+ Utilities.indent(
                        hexEncoder.encode(curId.identity), "    ") +
                        "\n  }\n");
            }

            return result.toString();
        }

        String bindersString() {
            StringBuilder result = new StringBuilder();
            for (byte[] curBinder : binders) {
                result.append("{" + Utilities.toHexString(curBinder) + "}\n");
            }

            return result.toString();
        }
    }

    private static final
            class CHPreSharedKeyStringizer implements SSLStringizer {
        @Override
        public String toString(HandshakeContext hc, ByteBuffer buffer) {
            try {
                return (new CHPreSharedKeySpec(hc, buffer)).toString();
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }
    }

    private static final
            class SHPreSharedKeySpec implements SSLExtensionSpec {
        final int selectedIdentity;

        SHPreSharedKeySpec(int selectedIdentity) {
            this.selectedIdentity = selectedIdentity;
        }

        SHPreSharedKeySpec(HandshakeContext hc,
                ByteBuffer m) throws IOException {
            if (m.remaining() < 2) {
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                    "Invalid pre_shared_key extension: " +
                    "insufficient selected_identity (length=" +
                    m.remaining() + ")"));
            }
            this.selectedIdentity = Record.getInt16(m);
        }

        byte[] getEncoded() {
            return new byte[] {
                (byte)((selectedIdentity >> 8) & 0xFF),
                (byte)(selectedIdentity & 0xFF)
            };
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                    """
                            "PreSharedKey": '{'
                              "selected_identity"      : "{0}",
                            '}'""",
                Locale.ENGLISH);

            Object[] messageFields = {
                Utilities.byte16HexString(selectedIdentity)
            };

            return messageFormat.format(messageFields);
        }
    }

    private static final
            class SHPreSharedKeyStringizer implements SSLStringizer {
        @Override
        public String toString(HandshakeContext hc, ByteBuffer buffer) {
            try {
                return (new SHPreSharedKeySpec(hc, buffer)).toString();
            } catch (Exception ex) {
                return ex.getMessage();
            }
        }
    }

    private static final
            class CHPreSharedKeyConsumer implements ExtensionConsumer {
        private CHPreSharedKeyConsumer() {
        }

        @Override
        public void consume(ConnectionContext context,
                            HandshakeMessage message,
                            ByteBuffer buffer) throws IOException {
            ClientHelloMessage clientHello = (ClientHelloMessage) message;
            ServerHandshakeContext shc = (ServerHandshakeContext)context;
            if (!shc.sslConfig.isAvailable(SSLExtension.CH_PRE_SHARED_KEY)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "Ignore unavailable pre_shared_key extension");
                }
                return;     
            }

            CHPreSharedKeySpec pskSpec = new CHPreSharedKeySpec(shc, buffer);

            if (!shc.handshakeExtensions.containsKey(
                    SSLExtension.PSK_KEY_EXCHANGE_MODES)) {
                throw shc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Client sent PSK but not PSK modes, or the PSK " +
                        "extension is not the last extension");
            }

            if (pskSpec.identities.size() != pskSpec.binders.size()) {
                throw shc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "PSK extension has incorrect number of binders");
            }

            if (shc.isResumption) {     
                SSLSessionContextImpl sessionCache = (SSLSessionContextImpl)
                        shc.sslContext.engineGetServerSessionContext();
                int idIndex = 0;
                SSLSessionImpl s = null;

                for (PskIdentity requestedId : pskSpec.identities) {
                    if (requestedId.identity.length == SessionId.MAX_LENGTH) {
                        s = sessionCache.pull(requestedId.identity);
                    }
                    if (s == null &&
                            requestedId.identity.length > SessionId.MAX_LENGTH &&
                            sessionCache.statelessEnabled()) {
                        ByteBuffer b =
                            new SessionTicketSpec(shc, requestedId.identity).
                                        decrypt(shc);
                        if (b != null) {
                            try {
                                s = new SSLSessionImpl(shc, b);
                            } catch (IOException | RuntimeException e) {
                                s = null;
                            }
                        }
                        if (b == null || s == null) {
                            if (SSLLogger.isOn &&
                                    SSLLogger.isOn("ssl,handshake")) {
                                SSLLogger.fine(
                                        "Stateless session ticket invalid");
                            }
                        }
                    }

                    if (s != null && canRejoin(clientHello, shc, s)) {
                        if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                            SSLLogger.fine("Resuming session: ", s);
                        }

                        shc.resumingSession = s;
                        shc.handshakeExtensions.put(SH_PRE_SHARED_KEY,
                            new SHPreSharedKeySpec(idIndex));   
                        break;
                    }

                    ++idIndex;
                }

                if (idIndex == pskSpec.identities.size()) {
                    shc.isResumption = false;
                    shc.resumingSession = null;
                }
            }
            shc.handshakeExtensions.put(
                SSLExtension.CH_PRE_SHARED_KEY, pskSpec);
        }
    }

    private static boolean canRejoin(ClientHelloMessage clientHello,
        ServerHandshakeContext shc, SSLSessionImpl s) {

        boolean result = s.isRejoinable() && (s.getPreSharedKey() != null);

        if (result && s.getProtocolVersion() != shc.negotiatedProtocol) {
            if (SSLLogger.isOn &&
                SSLLogger.isOn("ssl,handshake,verbose")) {

                SSLLogger.finest("Can't resume, incorrect protocol version");
            }
            result = false;
        }

        if (shc.localSupportedSignAlgs == null) {
            shc.localSupportedSignAlgs =
                    SignatureScheme.getSupportedAlgorithms(
                            shc.sslConfig,
                            shc.algorithmConstraints, shc.activeProtocols);
        }

        if (result &&
            (shc.sslConfig.clientAuthType == CLIENT_AUTH_REQUIRED)) {
            try {
                s.getPeerPrincipal();
            } catch (SSLPeerUnverifiedException e) {
                if (SSLLogger.isOn &&
                        SSLLogger.isOn("ssl,handshake,verbose")) {
                    SSLLogger.finest(
                        "Can't resume, " +
                        "client authentication is required");
                }
                result = false;
            }

            Collection<SignatureScheme> sessionSigAlgs =
                s.getLocalSupportedSignatureSchemes();
            if (result &&
                !shc.localSupportedSignAlgs.containsAll(sessionSigAlgs)) {

                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine("Can't resume. Session uses different " +
                        "signature algorithms");
                }
                result = false;
            }
        }

        String identityAlg = shc.sslConfig.identificationProtocol;
        if (result && identityAlg != null) {
            String sessionIdentityAlg = s.getIdentificationProtocol();
            if (!identityAlg.equalsIgnoreCase(sessionIdentityAlg)) {
                if (SSLLogger.isOn &&
                    SSLLogger.isOn("ssl,handshake,verbose")) {

                    SSLLogger.finest("Can't resume, endpoint id" +
                        " algorithm does not match, requested: " +
                        identityAlg + ", cached: " + sessionIdentityAlg);
                }
                result = false;
            }
        }

        if (result && (!shc.isNegotiable(s.getSuite()) ||
            !clientHello.cipherSuites.contains(s.getSuite()))) {
            if (SSLLogger.isOn &&
                    SSLLogger.isOn("ssl,handshake,verbose")) {
                SSLLogger.finest(
                    "Can't resume, unavailable session cipher suite");
            }
            result = false;
        }

        return result;
    }

    private static final
            class CHPreSharedKeyUpdate implements HandshakeConsumer {
        private CHPreSharedKeyUpdate() {
        }

        @Override
        public void consume(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            ServerHandshakeContext shc = (ServerHandshakeContext)context;
            if (!shc.isResumption || shc.resumingSession == null) {
                return;
            }

            CHPreSharedKeySpec chPsk = (CHPreSharedKeySpec)
                    shc.handshakeExtensions.get(SSLExtension.CH_PRE_SHARED_KEY);
            SHPreSharedKeySpec shPsk = (SHPreSharedKeySpec)
                    shc.handshakeExtensions.get(SSLExtension.SH_PRE_SHARED_KEY);
            if (chPsk == null || shPsk == null) {
                throw shc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Required extensions are unavailable");
            }

            byte[] binder = chPsk.binders.get(shPsk.selectedIdentity);

            HandshakeHash pskBinderHash = shc.handshakeHash.copy();
            byte[] lastMessage = pskBinderHash.removeLastReceived();
            ByteBuffer messageBuf = ByteBuffer.wrap(lastMessage);
            messageBuf.position(4);
            ClientHelloMessage.readPartial(shc.conContext, messageBuf);
            int length = messageBuf.position();
            messageBuf.position(0);
            pskBinderHash.receive(messageBuf, length);

            checkBinder(shc, shc.resumingSession, pskBinderHash, binder);
        }
    }

    private static void checkBinder(ServerHandshakeContext shc,
            SSLSessionImpl session,
            HandshakeHash pskBinderHash, byte[] binder) throws IOException {
        SecretKey psk = session.getPreSharedKey();
        if (psk == null) {
            throw shc.conContext.fatal(Alert.INTERNAL_ERROR,
                    "Session has no PSK");
        }

        SecretKey binderKey = deriveBinderKey(shc, psk, session);
        byte[] computedBinder =
                computeBinder(shc, binderKey, session, pskBinderHash);
        if (!MessageDigest.isEqual(binder, computedBinder)) {
            throw shc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Incorrect PSK binder value");
        }
    }

    static final class PartialClientHelloMessage extends HandshakeMessage {

        private final ClientHello.ClientHelloMessage msg;
        private final CHPreSharedKeySpec psk;

        PartialClientHelloMessage(HandshakeContext ctx,
                                  ClientHello.ClientHelloMessage msg,
                                  CHPreSharedKeySpec psk) {
            super(ctx);

            this.msg = msg;
            this.psk = psk;
        }

        @Override
        SSLHandshake handshakeType() {
            return msg.handshakeType();
        }

        private int pskTotalLength() {
            return psk.getIdsEncodedLength() +
                psk.getBindersEncodedLength() + 8;
        }

        @Override
        int messageLength() {

            if (msg.extensions.get(SSLExtension.CH_PRE_SHARED_KEY) != null) {
                return msg.messageLength();
            } else {
                return msg.messageLength() + pskTotalLength();
            }
        }

        @Override
        void send(HandshakeOutStream hos) throws IOException {
            msg.sendCore(hos);

            int extsLen = msg.extensions.length();
            if (msg.extensions.get(SSLExtension.CH_PRE_SHARED_KEY) == null) {
                extsLen += pskTotalLength();
            }
            hos.putInt16(extsLen - 2);
            for (SSLExtension ext : SSLExtension.values()) {
                byte[] extData = msg.extensions.get(ext);
                if (extData == null) {
                    continue;
                }
                if (ext == SSLExtension.CH_PRE_SHARED_KEY) {
                    continue;
                }
                int extID = ext.id;
                hos.putInt16(extID);
                hos.putBytes16(extData);
            }

            int extID = SSLExtension.CH_PRE_SHARED_KEY.id;
            hos.putInt16(extID);
            byte[] encodedPsk = psk.getEncoded();
            hos.putInt16(encodedPsk.length);
            hos.write(encodedPsk, 0, psk.getIdsEncodedLength() + 2);
        }
    }

    private static final
            class CHPreSharedKeyProducer implements HandshakeProducer {
        private CHPreSharedKeyProducer() {
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {

            ClientHandshakeContext chc = (ClientHandshakeContext)context;
            if (!chc.isResumption || chc.resumingSession == null) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine("No session to resume.");
                }
                return null;
            }

            Collection<SignatureScheme> sessionSigAlgs =
                chc.resumingSession.getLocalSupportedSignatureSchemes();
            if (!chc.localSupportedSignAlgs.containsAll(sessionSigAlgs)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine("Existing session uses different " +
                        "signature algorithms");
                }
                return null;
            }

            SecretKey psk = chc.resumingSession.getPreSharedKey();
            if (psk == null) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine("Existing session has no PSK.");
                }
                return null;
            }

            if (chc.pskIdentity == null) {
                chc.pskIdentity = chc.resumingSession.consumePskIdentity();
            }

            if (chc.pskIdentity == null) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                        "PSK has no identity, or identity was already used");
                }
                return null;
            }

            SSLSessionContextImpl sessionCache = (SSLSessionContextImpl)
                chc.sslContext.engineGetClientSessionContext();
            sessionCache.remove(chc.resumingSession.getSessionId());

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                    "Found resumable session. Preparing PSK message.");
            }

            List<PskIdentity> identities = new ArrayList<>();
            int ageMillis = (int)(System.currentTimeMillis() -
                    chc.resumingSession.getTicketCreationTime());
            int obfuscatedAge =
                    ageMillis + chc.resumingSession.getTicketAgeAdd();
            identities.add(new PskIdentity(chc.pskIdentity, obfuscatedAge));

            SecretKey binderKey =
                    deriveBinderKey(chc, psk, chc.resumingSession);
            ClientHelloMessage clientHello = (ClientHelloMessage)message;
            CHPreSharedKeySpec pskPrototype = createPskPrototype(
                chc.resumingSession.getSuite().hashAlg.hashLength, identities);
            HandshakeHash pskBinderHash = chc.handshakeHash.copy();

            byte[] binder = computeBinder(chc, binderKey, pskBinderHash,
                    chc.resumingSession, chc, clientHello, pskPrototype);

            List<byte[]> binders = new ArrayList<>();
            binders.add(binder);

            CHPreSharedKeySpec pskMessage =
                    new CHPreSharedKeySpec(identities, binders);
            chc.handshakeExtensions.put(CH_PRE_SHARED_KEY, pskMessage);
            return pskMessage.getEncoded();
        }

        private CHPreSharedKeySpec createPskPrototype(
                int hashLength, List<PskIdentity> identities) {
            List<byte[]> binders = new ArrayList<>();
            byte[] binderProto = new byte[hashLength];
            int i = identities.size();
            while (i-- > 0) {
                binders.add(binderProto);
            }

            return new CHPreSharedKeySpec(identities, binders);
        }
    }

    private static byte[] computeBinder(
            HandshakeContext context, SecretKey binderKey,
            SSLSessionImpl session,
            HandshakeHash pskBinderHash) throws IOException {

        pskBinderHash.determine(
                session.getProtocolVersion(), session.getSuite());
        pskBinderHash.update();
        byte[] digest = pskBinderHash.digest();

        return computeBinder(context, binderKey, session, digest);
    }

    private static byte[] computeBinder(
            HandshakeContext context, SecretKey binderKey,
            HandshakeHash hash, SSLSessionImpl session,
            HandshakeContext ctx, ClientHello.ClientHelloMessage hello,
            CHPreSharedKeySpec pskPrototype) throws IOException {

        PartialClientHelloMessage partialMsg =
                new PartialClientHelloMessage(ctx, hello, pskPrototype);

        SSLEngineOutputRecord record = new SSLEngineOutputRecord(hash);
        HandshakeOutStream hos = new HandshakeOutStream(record);
        partialMsg.write(hos);

        hash.determine(session.getProtocolVersion(), session.getSuite());
        hash.update();
        byte[] digest = hash.digest();

        return computeBinder(context, binderKey, session, digest);
    }

    private static byte[] computeBinder(HandshakeContext context,
            SecretKey binderKey,
            SSLSessionImpl session, byte[] digest) throws IOException {
        try {
            CipherSuite.HashAlg hashAlg = session.getSuite().hashAlg;
            HKDF hkdf = new HKDF(hashAlg.name);
            byte[] label = ("tls13 finished").getBytes();
            byte[] hkdfInfo = SSLSecretDerivation.createHkdfInfo(
                    label, new byte[0], hashAlg.hashLength);
            SecretKey finishedKey = hkdf.expand(
                    binderKey, hkdfInfo, hashAlg.hashLength, "TlsBinderKey");

            String hmacAlg =
                "Hmac" + hashAlg.name.replace("-", "");
            try {
                Mac hmac = Mac.getInstance(hmacAlg);
                hmac.init(finishedKey);
                return hmac.doFinal(digest);
            } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
                throw context.conContext.fatal(Alert.INTERNAL_ERROR, ex);
            }
        } catch (GeneralSecurityException ex) {
            throw context.conContext.fatal(Alert.INTERNAL_ERROR, ex);
        }
    }

    private static SecretKey deriveBinderKey(HandshakeContext context,
            SecretKey psk, SSLSessionImpl session) throws IOException {
        try {
            CipherSuite.HashAlg hashAlg = session.getSuite().hashAlg;
            HKDF hkdf = new HKDF(hashAlg.name);
            byte[] zeros = new byte[hashAlg.hashLength];
            SecretKey earlySecret = hkdf.extract(zeros, psk, "TlsEarlySecret");

            byte[] label = ("tls13 res binder").getBytes();
            MessageDigest md = MessageDigest.getInstance(hashAlg.name);
            byte[] hkdfInfo = SSLSecretDerivation.createHkdfInfo(
                    label, md.digest(new byte[0]), hashAlg.hashLength);
            return hkdf.expand(earlySecret,
                    hkdfInfo, hashAlg.hashLength, "TlsBinderKey");
        } catch (GeneralSecurityException ex) {
            throw context.conContext.fatal(Alert.INTERNAL_ERROR, ex);
        }
    }

    private static final
            class CHPreSharedKeyOnLoadAbsence implements HandshakeAbsence {
        @Override
        public void absent(ConnectionContext context,
                           HandshakeMessage message) throws IOException {

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                "Handling pre_shared_key absence.");
            }

            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            shc.resumingSession = null;
            shc.isResumption = false;
        }
    }

    /**
     * The absence processing if the extension is not present in
     * a ClientHello handshake message.
     */
    private static final class CHPreSharedKeyOnTradeAbsence
            implements HandshakeAbsence {
        @Override
        public void absent(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            if (shc.negotiatedProtocol.useTLS13PlusSpec() &&
                    (!shc.handshakeExtensions.containsKey(
                            SSLExtension.CH_SIGNATURE_ALGORITHMS) ||
                     !shc.handshakeExtensions.containsKey(
                            SSLExtension.CH_SUPPORTED_GROUPS))) {
                throw shc.conContext.fatal(Alert.MISSING_EXTENSION,
                    "No supported_groups or signature_algorithms extension " +
                    "when pre_shared_key extension is not present");
            }
        }
    }

    private static final
            class SHPreSharedKeyConsumer implements ExtensionConsumer {
        private SHPreSharedKeyConsumer() {
        }

        @Override
        public void consume(ConnectionContext context,
            HandshakeMessage message, ByteBuffer buffer) throws IOException {
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            if (!chc.handshakeExtensions.containsKey(
                    SSLExtension.CH_PRE_SHARED_KEY)) {
                throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                    "Server sent unexpected pre_shared_key extension");
            }

            SHPreSharedKeySpec shPsk = new SHPreSharedKeySpec(chc, buffer);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                    "Received pre_shared_key extension: ", shPsk);
            }

            if (shPsk.selectedIdentity != 0) {
                throw chc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Selected identity index is not in correct range.");
            }

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Resuming session: ", chc.resumingSession);
            }
        }
    }

    private static final
            class SHPreSharedKeyAbsence implements HandshakeAbsence {
        @Override
        public void absent(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine("Handling pre_shared_key absence.");
            }

            chc.resumingSession = null;
            chc.isResumption = false;
        }
    }

    private static final
            class SHPreSharedKeyProducer implements HandshakeProducer {
        private SHPreSharedKeyProducer() {
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            ServerHandshakeContext shc = (ServerHandshakeContext)context;
            SHPreSharedKeySpec psk = (SHPreSharedKeySpec)
                    shc.handshakeExtensions.get(SH_PRE_SHARED_KEY);
            if (psk == null) {
                return null;
            }

            return psk.getEncoded();
        }
    }
}
