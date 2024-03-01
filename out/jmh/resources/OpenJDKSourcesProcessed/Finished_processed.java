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
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.spec.AlgorithmParameterSpec;
import java.text.MessageFormat;
import java.util.Locale;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLPeerUnverifiedException;

import jdk.internal.event.EventHelper;
import jdk.internal.event.TLSHandshakeEvent;
import sun.security.internal.spec.TlsPrfParameterSpec;
import sun.security.ssl.CipherSuite.HashAlg;
import static sun.security.ssl.CipherSuite.HashAlg.H_NONE;
import sun.security.ssl.SSLBasicKeyDerivation.SecretSizeSpec;
import sun.security.ssl.SSLCipher.SSLReadCipher;
import sun.security.ssl.SSLCipher.SSLWriteCipher;
import sun.security.ssl.SSLHandshake.HandshakeMessage;
import sun.security.util.HexDumpEncoder;

/**
 * Pack of the Finished handshake message.
 */
final class Finished {
    static final SSLConsumer t12HandshakeConsumer =
        new T12FinishedConsumer();
    static final HandshakeProducer t12HandshakeProducer =
        new T12FinishedProducer();

    static final SSLConsumer t13HandshakeConsumer =
        new T13FinishedConsumer();
    static final HandshakeProducer t13HandshakeProducer =
        new T13FinishedProducer();

    /**
     * The Finished handshake message.
     */
    private static final class FinishedMessage extends HandshakeMessage {
        private final byte[] verifyData;

        FinishedMessage(HandshakeContext context) throws IOException {
            super(context);

            VerifyDataScheme vds =
                    VerifyDataScheme.valueOf(context.negotiatedProtocol);

            byte[] vd;
            try {
                vd = vds.createVerifyData(context, false);
            } catch (IOException ioe) {
                throw context.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Failed to generate verify_data", ioe);
            }

            this.verifyData = vd;
        }

        FinishedMessage(HandshakeContext context,
                ByteBuffer m) throws IOException {
            super(context);
            int verifyDataLen = 12;
            if (context.negotiatedProtocol == ProtocolVersion.SSL30) {
                verifyDataLen = 36;
            } else if (context.negotiatedProtocol.useTLS13PlusSpec()) {
                verifyDataLen =
                        context.negotiatedCipherSuite.hashAlg.hashLength;
            }

            if (m.remaining() != verifyDataLen) {
                throw context.conContext.fatal(Alert.DECODE_ERROR,
                    "Inappropriate finished message: need " + verifyDataLen +
                    " but remaining " + m.remaining() + " bytes verify_data");
            }

            this.verifyData = new byte[verifyDataLen];
            m.get(verifyData);

            VerifyDataScheme vd =
                    VerifyDataScheme.valueOf(context.negotiatedProtocol);
            byte[] myVerifyData;
            try {
                myVerifyData = vd.createVerifyData(context, true);
            } catch (IOException ioe) {
                throw context.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Failed to generate verify_data", ioe);
            }
            if (!MessageDigest.isEqual(myVerifyData, verifyData)) {
                throw context.conContext.fatal(Alert.DECRYPT_ERROR,
                        "The Finished message cannot be verified.");
            }
        }

        @Override
        public SSLHandshake handshakeType() {
            return SSLHandshake.FINISHED;
        }

        @Override
        public int messageLength() {
            return verifyData.length;
        }

        @Override
        public void send(HandshakeOutStream hos) throws IOException {
            hos.write(verifyData);
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                    """
                            "Finished": '{'
                              "verify data": '{'
                            {0}
                              '}'
                            '}'""",
                    Locale.ENGLISH);

            HexDumpEncoder hexEncoder = new HexDumpEncoder();
            Object[] messageFields = {
                    Utilities.indent(hexEncoder.encode(verifyData), "    "),
                };
            return messageFormat.format(messageFields);
        }
    }

    interface VerifyDataGenerator {
        byte[] createVerifyData(HandshakeContext context,
                boolean isValidation) throws IOException;
    }

    enum VerifyDataScheme {
        SSL30       ("kdf_ssl30", new S30VerifyDataGenerator()),
        TLS10       ("kdf_tls10", new T10VerifyDataGenerator()),
        TLS12       ("kdf_tls12", new T12VerifyDataGenerator()),
        TLS13       ("kdf_tls13", new T13VerifyDataGenerator());

        final String name;
        final VerifyDataGenerator generator;

        VerifyDataScheme(String name, VerifyDataGenerator verifyDataGenerator) {
            this.name = name;
            this.generator = verifyDataGenerator;
        }

        static VerifyDataScheme valueOf(ProtocolVersion protocolVersion) {
            switch (protocolVersion) {
                case SSL30:
                    return VerifyDataScheme.SSL30;
                case TLS10:
                case TLS11:
                case DTLS10:
                    return VerifyDataScheme.TLS10;
                case TLS12:
                case DTLS12:
                    return VerifyDataScheme.TLS12;
                case TLS13:
                    return VerifyDataScheme.TLS13;
                default:
                    return null;
            }
        }

        public byte[] createVerifyData(HandshakeContext context,
                boolean isValidation) throws IOException {
            if (generator != null) {
                return generator.createVerifyData(context, isValidation);
            }

            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private static final
            class S30VerifyDataGenerator implements VerifyDataGenerator {
        @Override
        public byte[] createVerifyData(HandshakeContext context,
                boolean isValidation) throws IOException {
            HandshakeHash handshakeHash = context.handshakeHash;
            SecretKey masterSecretKey =
                    context.handshakeSession.getMasterSecret();
            boolean useClientLabel =
                    (context.sslConfig.isClientMode && !isValidation) ||
                    (!context.sslConfig.isClientMode && isValidation);
            return handshakeHash.digest(useClientLabel, masterSecretKey);
        }
    }

    private static final
            class T10VerifyDataGenerator implements VerifyDataGenerator {
        @Override
        public byte[] createVerifyData(HandshakeContext context,
                boolean isValidation) throws IOException {
            HandshakeHash handshakeHash = context.handshakeHash;
            SecretKey masterSecretKey =
                    context.handshakeSession.getMasterSecret();

            boolean useClientLabel =
                    (context.sslConfig.isClientMode && !isValidation) ||
                    (!context.sslConfig.isClientMode && isValidation);
            String tlsLabel;
            if (useClientLabel) {
                tlsLabel = "client finished";
            } else {
                tlsLabel = "server finished";
            }

            try {
                byte[] seed = handshakeHash.digest();
                String prfAlg = "SunTlsPrf";
                HashAlg hashAlg = H_NONE;

                /*
                 * RFC 5246/7.4.9 says that finished messages can
                 * be ciphersuite-specific in both length/PRF hash
                 * algorithm.  If we ever run across a different
                 * length, this call will need to be updated.
                 */
                @SuppressWarnings("deprecation")
                TlsPrfParameterSpec spec = new TlsPrfParameterSpec(
                    masterSecretKey, tlsLabel, seed, 12,
                    hashAlg.name, hashAlg.hashLength, hashAlg.blockSize);
                KeyGenerator kg = KeyGenerator.getInstance(prfAlg);
                kg.init(spec);
                SecretKey prfKey = kg.generateKey();
                if (!"RAW".equals(prfKey.getFormat())) {
                    throw new ProviderException(
                        "Invalid PRF output, format must be RAW. " +
                        "Format received: " + prfKey.getFormat());
                }
                return prfKey.getEncoded();
            } catch (GeneralSecurityException e) {
                throw new RuntimeException("PRF failed", e);
            }
        }
    }

    private static final
            class T12VerifyDataGenerator implements VerifyDataGenerator {
        @Override
        public byte[] createVerifyData(HandshakeContext context,
                boolean isValidation) throws IOException {
            CipherSuite cipherSuite = context.negotiatedCipherSuite;
            HandshakeHash handshakeHash = context.handshakeHash;
            SecretKey masterSecretKey =
                    context.handshakeSession.getMasterSecret();

            boolean useClientLabel =
                    (context.sslConfig.isClientMode && !isValidation) ||
                    (!context.sslConfig.isClientMode && isValidation);
            String tlsLabel;
            if (useClientLabel) {
                tlsLabel = "client finished";
            } else {
                tlsLabel = "server finished";
            }

            try {
                byte[] seed = handshakeHash.digest();
                String prfAlg = "SunTls12Prf";
                HashAlg hashAlg = cipherSuite.hashAlg;

                /*
                 * RFC 5246/7.4.9 says that finished messages can
                 * be ciphersuite-specific in both length/PRF hash
                 * algorithm.  If we ever run across a different
                 * length, this call will need to be updated.
                 */
                @SuppressWarnings("deprecation")
                TlsPrfParameterSpec spec = new TlsPrfParameterSpec(
                    masterSecretKey, tlsLabel, seed, 12,
                    hashAlg.name, hashAlg.hashLength, hashAlg.blockSize);
                KeyGenerator kg = KeyGenerator.getInstance(prfAlg);
                kg.init(spec);
                SecretKey prfKey = kg.generateKey();
                if (!"RAW".equals(prfKey.getFormat())) {
                    throw new ProviderException(
                        "Invalid PRF output, format must be RAW. " +
                        "Format received: " + prfKey.getFormat());
                }
                return prfKey.getEncoded();
            } catch (GeneralSecurityException e) {
                throw new RuntimeException("PRF failed", e);
            }
        }
    }

    private static final
            class T13VerifyDataGenerator implements VerifyDataGenerator {
        private static final byte[] hkdfLabel = "tls13 finished".getBytes();
        private static final byte[] hkdfContext = new byte[0];

        @Override
        public byte[] createVerifyData(HandshakeContext context,
                boolean isValidation) throws IOException {
            HashAlg hashAlg =
                    context.negotiatedCipherSuite.hashAlg;
            SecretKey secret = isValidation ?
                    context.baseReadSecret : context.baseWriteSecret;
            SSLBasicKeyDerivation kdf = new SSLBasicKeyDerivation(
                    secret, hashAlg.name,
                    hkdfLabel, hkdfContext, hashAlg.hashLength);
            AlgorithmParameterSpec keySpec =
                    new SecretSizeSpec(hashAlg.hashLength);
            SecretKey finishedSecret =
                    kdf.deriveKey("TlsFinishedSecret", keySpec);

            String hmacAlg =
                "Hmac" + hashAlg.name.replace("-", "");
            try {
                Mac hmac = Mac.getInstance(hmacAlg);
                hmac.init(finishedSecret);
                return hmac.doFinal(context.handshakeHash.digest());
            } catch (NoSuchAlgorithmException |InvalidKeyException ex) {
                throw new ProviderException(
                        "Failed to generate verify_data", ex);
            }
        }
    }

    /**
     * The "Finished" handshake message producer.
     */
    private static final
            class T12FinishedProducer implements HandshakeProducer {
        private T12FinishedProducer() {
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            HandshakeContext hc = (HandshakeContext)context;
            if (hc.sslConfig.isClientMode) {
                return onProduceFinished(
                        (ClientHandshakeContext)context, message);
            } else {
                return onProduceFinished(
                        (ServerHandshakeContext)context, message);
            }
        }

        private byte[] onProduceFinished(ClientHandshakeContext chc,
                HandshakeMessage message) throws IOException {
            chc.handshakeHash.update();

            FinishedMessage fm = new FinishedMessage(chc);

            ChangeCipherSpec.t10Producer.produce(chc, message);

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Produced client Finished handshake message", fm);
            }

            fm.write(chc.handshakeOutput);
            chc.handshakeOutput.flush();

            /*
             * save server verify data for secure renegotiation
             */
            if (chc.conContext.secureRenegotiation) {
                chc.conContext.clientVerifyData = fm.verifyData;
            }

            if (chc.statelessResumption) {
                chc.handshakeConsumers.put(
                        SSLHandshake.NEW_SESSION_TICKET.id, SSLHandshake.NEW_SESSION_TICKET);
            }
            if (!chc.isResumption) {
                chc.conContext.consumers.put(ContentType.CHANGE_CIPHER_SPEC.id,
                        ChangeCipherSpec.t10Consumer);
                chc.handshakeConsumers.put(
                        SSLHandshake.FINISHED.id, SSLHandshake.FINISHED);
                chc.conContext.inputRecord.expectingFinishFlight();
            } else {
                if (chc.handshakeSession.isRejoinable()) {
                    ((SSLSessionContextImpl)chc.sslContext.
                        engineGetClientSessionContext()).put(
                            chc.handshakeSession);
                }
                chc.conContext.conSession = chc.handshakeSession.finish();
                chc.conContext.protocolVersion = chc.negotiatedProtocol;

                chc.handshakeFinished = true;

                if (!chc.sslContext.isDTLS()) {
                    chc.conContext.finishHandshake();
                }
            }

            return null;
        }

        private byte[] onProduceFinished(ServerHandshakeContext shc,
                HandshakeMessage message) throws IOException {
            if (shc.statelessResumption) {
                NewSessionTicket.handshake12Producer.produce(shc, message);
            }

            shc.handshakeHash.update();

            FinishedMessage fm = new FinishedMessage(shc);

            ChangeCipherSpec.t10Producer.produce(shc, message);

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Produced server Finished handshake message", fm);
            }

            fm.write(shc.handshakeOutput);
            shc.handshakeOutput.flush();

            /*
             * save client verify data for secure renegotiation
             */
            if (shc.conContext.secureRenegotiation) {
                shc.conContext.serverVerifyData = fm.verifyData;
            }

            if (shc.isResumption) {
                shc.conContext.consumers.put(ContentType.CHANGE_CIPHER_SPEC.id,
                        ChangeCipherSpec.t10Consumer);
                shc.handshakeConsumers.put(
                        SSLHandshake.FINISHED.id, SSLHandshake.FINISHED);
                shc.conContext.inputRecord.expectingFinishFlight();
            } else {
                if (shc.statelessResumption &&
                        shc.handshakeSession.isStatelessable()) {
                    shc.handshakeSession.setContext((SSLSessionContextImpl)
                            shc.sslContext.engineGetServerSessionContext());
                } else {
                    if (shc.handshakeSession.isRejoinable()) {
                        ((SSLSessionContextImpl)shc.sslContext.
                                engineGetServerSessionContext()).put(
                                shc.handshakeSession);
                    }
                }
                shc.conContext.conSession = shc.handshakeSession.finish();
                shc.conContext.protocolVersion = shc.negotiatedProtocol;

                shc.handshakeFinished = true;

                if (!shc.sslContext.isDTLS()) {
                    shc.conContext.finishHandshake();
                }
            }

            return null;
        }
    }

    /**
     * The "Finished" handshake message consumer.
     */
    private static final class T12FinishedConsumer implements SSLConsumer {
        private T12FinishedConsumer() {
        }

        @Override
        public void consume(ConnectionContext context,
                ByteBuffer message) throws IOException {
            HandshakeContext hc = (HandshakeContext)context;

            hc.handshakeConsumers.remove(SSLHandshake.FINISHED.id);

            if (hc.conContext.consumers.containsKey(
                    ContentType.CHANGE_CIPHER_SPEC.id)) {
                throw hc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                        "Missing ChangeCipherSpec message");
            }

            if (hc.sslConfig.isClientMode) {
                onConsumeFinished((ClientHandshakeContext)context, message);
            } else {
                onConsumeFinished((ServerHandshakeContext)context, message);
            }
        }

        private void onConsumeFinished(ClientHandshakeContext chc,
                ByteBuffer message) throws IOException {
            FinishedMessage fm = new FinishedMessage(chc, message);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Consuming server Finished handshake message", fm);
            }

            if (chc.conContext.secureRenegotiation) {
                chc.conContext.serverVerifyData = fm.verifyData;
            }

            if (!chc.isResumption) {
                if (chc.handshakeSession.isRejoinable()) {
                    ((SSLSessionContextImpl)chc.sslContext.
                        engineGetClientSessionContext()).put(
                            chc.handshakeSession);
                }
                chc.conContext.conSession = chc.handshakeSession.finish();
                chc.conContext.protocolVersion = chc.negotiatedProtocol;

                chc.handshakeFinished = true;
                recordEvent(chc.conContext.conSession);

                if (!chc.sslContext.isDTLS()) {
                    chc.conContext.finishHandshake();
                }
            } else {
                chc.handshakeProducers.put(SSLHandshake.FINISHED.id,
                        SSLHandshake.FINISHED);
            }

            SSLHandshake[] probableHandshakeMessages = new SSLHandshake[] {
                SSLHandshake.FINISHED
            };

            for (SSLHandshake hs : probableHandshakeMessages) {
                HandshakeProducer handshakeProducer =
                        chc.handshakeProducers.remove(hs.id);
                if (handshakeProducer != null) {
                    handshakeProducer.produce(chc, fm);
                }
            }
        }

        private void onConsumeFinished(ServerHandshakeContext shc,
                ByteBuffer message) throws IOException {
            if (!shc.isResumption) {
                if (shc.handshakeConsumers.containsKey(
                        SSLHandshake.CERTIFICATE_VERIFY.id)) {
                    throw shc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                            "Unexpected Finished handshake message");
                }
            }

            FinishedMessage fm = new FinishedMessage(shc, message);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Consuming client Finished handshake message", fm);
            }

            if (shc.conContext.secureRenegotiation) {
                shc.conContext.clientVerifyData = fm.verifyData;
            }

            if (shc.isResumption) {
                if (shc.handshakeSession.isRejoinable() &&
                        !shc.statelessResumption) {
                    ((SSLSessionContextImpl)shc.sslContext.
                        engineGetServerSessionContext()).put(
                            shc.handshakeSession);
                }
                shc.conContext.conSession = shc.handshakeSession.finish();
                shc.conContext.protocolVersion = shc.negotiatedProtocol;

                shc.handshakeFinished = true;
                recordEvent(shc.conContext.conSession);

                if (!shc.sslContext.isDTLS()) {
                    shc.conContext.finishHandshake();
                }
            } else {
                shc.handshakeProducers.put(SSLHandshake.FINISHED.id,
                        SSLHandshake.FINISHED);
            }

            SSLHandshake[] probableHandshakeMessages = new SSLHandshake[] {
                SSLHandshake.FINISHED
            };

            for (SSLHandshake hs : probableHandshakeMessages) {
                HandshakeProducer handshakeProducer =
                        shc.handshakeProducers.remove(hs.id);
                if (handshakeProducer != null) {
                    handshakeProducer.produce(shc, fm);
                }
            }
        }
    }

    /**
     * The "Finished" handshake message producer.
     */
    private static final
            class T13FinishedProducer implements HandshakeProducer {
        private T13FinishedProducer() {
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            HandshakeContext hc = (HandshakeContext)context;
            if (hc.sslConfig.isClientMode) {
                return onProduceFinished(
                        (ClientHandshakeContext)context, message);
            } else {
                return onProduceFinished(
                        (ServerHandshakeContext)context, message);
            }
        }

        private byte[] onProduceFinished(ClientHandshakeContext chc,
                HandshakeMessage message) throws IOException {
            chc.handshakeHash.update();

            FinishedMessage fm = new FinishedMessage(chc);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Produced client Finished handshake message", fm);
            }

            fm.write(chc.handshakeOutput);
            chc.handshakeOutput.flush();

            if (chc.conContext.secureRenegotiation) {
                chc.conContext.clientVerifyData = fm.verifyData;
            }

            SSLKeyDerivation kd = chc.handshakeKeyDerivation;
            if (kd == null) {
                throw chc.conContext.fatal(Alert.INTERNAL_ERROR,
                    "no key derivation");
            }

            SSLTrafficKeyDerivation kdg =
                    SSLTrafficKeyDerivation.valueOf(chc.negotiatedProtocol);
            if (kdg == null) {
                throw chc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Not supported key derivation: " +
                        chc.negotiatedProtocol);
            }

            try {
                SecretKey writeSecret = kd.deriveKey(
                        "TlsClientAppTrafficSecret", null);

                SSLKeyDerivation writeKD =
                        kdg.createKeyDerivation(chc, writeSecret);
                SecretKey writeKey = writeKD.deriveKey(
                        "TlsKey", null);
                SecretKey writeIvSecret = writeKD.deriveKey(
                        "TlsIv", null);
                IvParameterSpec writeIv =
                        new IvParameterSpec(writeIvSecret.getEncoded());
                SSLWriteCipher writeCipher =
                        chc.negotiatedCipherSuite.bulkCipher.createWriteCipher(
                                Authenticator.valueOf(chc.negotiatedProtocol),
                                chc.negotiatedProtocol, writeKey, writeIv,
                                chc.sslContext.getSecureRandom());

                if (writeCipher == null) {
                    throw chc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Illegal cipher suite (" + chc.negotiatedCipherSuite +
                        ") and protocol version (" + chc.negotiatedProtocol +
                        ")");
                }

                chc.baseWriteSecret = writeSecret;
                chc.conContext.outputRecord.changeWriteCiphers(
                        writeCipher, false);

            } catch (GeneralSecurityException gse) {
                throw chc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Failure to derive application secrets", gse);
            }

            SSLSecretDerivation sd = ((SSLSecretDerivation) kd).forContext(chc);
            SecretKey resumptionMasterSecret = sd.deriveKey(
                    "TlsResumptionMasterSecret", null);
            chc.handshakeSession.setResumptionMasterSecret(
                    resumptionMasterSecret);

            chc.conContext.conSession = chc.handshakeSession.finish();
            chc.conContext.protocolVersion = chc.negotiatedProtocol;

            chc.handshakeFinished = true;
            chc.conContext.finishHandshake();
            recordEvent(chc.conContext.conSession);


            return null;
        }

        private byte[] onProduceFinished(ServerHandshakeContext shc,
                HandshakeMessage message) throws IOException {
            shc.handshakeHash.update();

            FinishedMessage fm = new FinishedMessage(shc);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Produced server Finished handshake message", fm);
            }

            fm.write(shc.handshakeOutput);
            shc.handshakeOutput.flush();

            SSLKeyDerivation kd = shc.handshakeKeyDerivation;
            if (kd == null) {
                throw shc.conContext.fatal(Alert.INTERNAL_ERROR,
                    "no key derivation");
            }

            SSLTrafficKeyDerivation kdg =
                    SSLTrafficKeyDerivation.valueOf(shc.negotiatedProtocol);
            if (kdg == null) {
                throw shc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Not supported key derivation: " +
                        shc.negotiatedProtocol);
            }

            try {
                SecretKey saltSecret = kd.deriveKey("TlsSaltSecret", null);

                HashAlg hashAlg = shc.negotiatedCipherSuite.hashAlg;
                HKDF hkdf = new HKDF(hashAlg.name);
                byte[] zeros = new byte[hashAlg.hashLength];
                SecretKeySpec sharedSecret =
                        new SecretKeySpec(zeros, "TlsZeroSecret");
                SecretKey masterSecret =
                    hkdf.extract(saltSecret, sharedSecret, "TlsMasterSecret");

                SSLKeyDerivation secretKD =
                        new SSLSecretDerivation(shc, masterSecret);

                SecretKey writeSecret = secretKD.deriveKey(
                        "TlsServerAppTrafficSecret", null);
                SSLKeyDerivation writeKD =
                        kdg.createKeyDerivation(shc, writeSecret);
                SecretKey writeKey = writeKD.deriveKey(
                        "TlsKey", null);
                SecretKey writeIvSecret = writeKD.deriveKey(
                        "TlsIv", null);
                IvParameterSpec writeIv =
                        new IvParameterSpec(writeIvSecret.getEncoded());
                SSLWriteCipher writeCipher =
                        shc.negotiatedCipherSuite.bulkCipher.createWriteCipher(
                                Authenticator.valueOf(shc.negotiatedProtocol),
                                shc.negotiatedProtocol, writeKey, writeIv,
                                shc.sslContext.getSecureRandom());

                if (writeCipher == null) {
                    throw shc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Illegal cipher suite (" + shc.negotiatedCipherSuite +
                        ") and protocol version (" + shc.negotiatedProtocol +
                        ")");
                }

                shc.baseWriteSecret = writeSecret;
                shc.conContext.outputRecord.changeWriteCiphers(
                        writeCipher, false);

                shc.handshakeKeyDerivation = secretKD;
            } catch (GeneralSecurityException gse) {
                throw shc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Failure to derive application secrets", gse);
            }

            /*
             * save client verify data for secure renegotiation
             */
            if (shc.conContext.secureRenegotiation) {
                shc.conContext.serverVerifyData = fm.verifyData;
            }

            shc.handshakeSession.setContext((SSLSessionContextImpl)
                    shc.sslContext.engineGetServerSessionContext());
            shc.conContext.conSession = shc.handshakeSession.finish();

            shc.handshakeConsumers.put(
                    SSLHandshake.FINISHED.id, SSLHandshake.FINISHED);

            return null;
        }
    }

    /**
     * The "Finished" handshake message consumer.
     */
    private static final class T13FinishedConsumer implements SSLConsumer {
        private T13FinishedConsumer() {
        }

        @Override
        public void consume(ConnectionContext context,
                ByteBuffer message) throws IOException {
            HandshakeContext hc = (HandshakeContext)context;
            if (hc.sslConfig.isClientMode) {
                onConsumeFinished(
                        (ClientHandshakeContext)context, message);
            } else {
                onConsumeFinished(
                        (ServerHandshakeContext)context, message);
            }
        }

        private void onConsumeFinished(ClientHandshakeContext chc,
                ByteBuffer message) throws IOException {
            if (!chc.isResumption) {
                if (chc.handshakeConsumers.containsKey(
                        SSLHandshake.CERTIFICATE.id) ||
                    chc.handshakeConsumers.containsKey(
                        SSLHandshake.CERTIFICATE_VERIFY.id)) {
                    throw chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                            "Unexpected Finished handshake message");
                }
            }

            FinishedMessage fm = new FinishedMessage(chc, message);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Consuming server Finished handshake message", fm);
            }

            if (chc.conContext.secureRenegotiation) {
                chc.conContext.serverVerifyData = fm.verifyData;
            }


            chc.conContext.consumers.remove(ContentType.CHANGE_CIPHER_SPEC.id);

            chc.handshakeHash.update();
            SSLKeyDerivation kd = chc.handshakeKeyDerivation;
            if (kd == null) {
                throw chc.conContext.fatal(Alert.INTERNAL_ERROR,
                    "no key derivation");
            }

            SSLTrafficKeyDerivation kdg =
                    SSLTrafficKeyDerivation.valueOf(chc.negotiatedProtocol);
            if (kdg == null) {
                throw chc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Not supported key derivation: " +
                        chc.negotiatedProtocol);
            }

            if (!chc.isResumption && chc.handshakeSession.isRejoinable()) {
                ((SSLSessionContextImpl)chc.sslContext.
                        engineGetClientSessionContext()).
                        put(chc.handshakeSession);
            }

            try {
                SecretKey saltSecret = kd.deriveKey("TlsSaltSecret", null);

                HashAlg hashAlg = chc.negotiatedCipherSuite.hashAlg;
                HKDF hkdf = new HKDF(hashAlg.name);
                byte[] zeros = new byte[hashAlg.hashLength];
                SecretKeySpec sharedSecret =
                        new SecretKeySpec(zeros, "TlsZeroSecret");
                SecretKey masterSecret =
                    hkdf.extract(saltSecret, sharedSecret, "TlsMasterSecret");

                SSLKeyDerivation secretKD =
                        new SSLSecretDerivation(chc, masterSecret);

                SecretKey readSecret = secretKD.deriveKey(
                        "TlsServerAppTrafficSecret", null);
                SSLKeyDerivation writeKD =
                        kdg.createKeyDerivation(chc, readSecret);
                SecretKey readKey = writeKD.deriveKey(
                        "TlsKey", null);
                SecretKey readIvSecret = writeKD.deriveKey(
                        "TlsIv", null);
                IvParameterSpec readIv =
                        new IvParameterSpec(readIvSecret.getEncoded());
                SSLReadCipher readCipher =
                        chc.negotiatedCipherSuite.bulkCipher.createReadCipher(
                                Authenticator.valueOf(chc.negotiatedProtocol),
                                chc.negotiatedProtocol, readKey, readIv,
                                chc.sslContext.getSecureRandom());

                if (readCipher == null) {
                    throw chc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Illegal cipher suite (" + chc.negotiatedCipherSuite +
                        ") and protocol version (" + chc.negotiatedProtocol +
                        ")");
                }

                chc.baseReadSecret = readSecret;
                chc.conContext.inputRecord.changeReadCiphers(readCipher);

                chc.handshakeKeyDerivation = secretKD;
            } catch (GeneralSecurityException gse) {
                throw chc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Failure to derive application secrets", gse);
            }

            chc.handshakeProducers.put(SSLHandshake.FINISHED.id,
                        SSLHandshake.FINISHED);
            SSLHandshake[] probableHandshakeMessages = new SSLHandshake[] {
                SSLHandshake.CERTIFICATE,
                SSLHandshake.CERTIFICATE_VERIFY,
                SSLHandshake.FINISHED
            };

            for (SSLHandshake hs : probableHandshakeMessages) {
                HandshakeProducer handshakeProducer =
                        chc.handshakeProducers.remove(hs.id);
                if (handshakeProducer != null) {
                    handshakeProducer.produce(chc, null);
                }
            }
        }

        private void onConsumeFinished(ServerHandshakeContext shc,
                ByteBuffer message) throws IOException {
            if (!shc.isResumption) {
                if (shc.handshakeConsumers.containsKey(
                        SSLHandshake.CERTIFICATE.id) ||
                    shc.handshakeConsumers.containsKey(
                        SSLHandshake.CERTIFICATE_VERIFY.id)) {
                    throw shc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                            "Unexpected Finished handshake message");
                }
            }

            FinishedMessage fm = new FinishedMessage(shc, message);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Consuming client Finished handshake message", fm);
            }

            if (shc.conContext.secureRenegotiation) {
                shc.conContext.clientVerifyData = fm.verifyData;
            }


            SSLKeyDerivation kd = shc.handshakeKeyDerivation;
            if (kd == null) {
                throw shc.conContext.fatal(Alert.INTERNAL_ERROR,
                    "no key derivation");
            }

            SSLTrafficKeyDerivation kdg =
                    SSLTrafficKeyDerivation.valueOf(shc.negotiatedProtocol);
            if (kdg == null) {
                throw shc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Not supported key derivation: " +
                        shc.negotiatedProtocol);
            }

            try {
                SecretKey readSecret = kd.deriveKey(
                        "TlsClientAppTrafficSecret", null);

                SSLKeyDerivation readKD =
                        kdg.createKeyDerivation(shc, readSecret);
                SecretKey readKey = readKD.deriveKey(
                        "TlsKey", null);
                SecretKey readIvSecret = readKD.deriveKey(
                        "TlsIv", null);
                IvParameterSpec readIv =
                        new IvParameterSpec(readIvSecret.getEncoded());
                SSLReadCipher readCipher =
                        shc.negotiatedCipherSuite.bulkCipher.createReadCipher(
                                Authenticator.valueOf(shc.negotiatedProtocol),
                                shc.negotiatedProtocol, readKey, readIv,
                                shc.sslContext.getSecureRandom());

                if (readCipher == null) {
                    throw shc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Illegal cipher suite (" + shc.negotiatedCipherSuite +
                        ") and protocol version (" + shc.negotiatedProtocol +
                        ")");
                }

                shc.baseReadSecret = readSecret;
                shc.conContext.inputRecord.changeReadCiphers(readCipher);

                shc.handshakeHash.update();
                SSLSecretDerivation sd =
                        ((SSLSecretDerivation)kd).forContext(shc);
                SecretKey resumptionMasterSecret = sd.deriveKey(
                "TlsResumptionMasterSecret", null);
                shc.handshakeSession.setResumptionMasterSecret(
                        resumptionMasterSecret);
            } catch (GeneralSecurityException gse) {
                throw shc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Failure to derive application secrets", gse);
            }

            shc.conContext.conSession = shc.handshakeSession.finish();
            shc.conContext.protocolVersion = shc.negotiatedProtocol;

            shc.handshakeFinished = true;

            if (!shc.sslContext.isDTLS()) {
                shc.conContext.finishHandshake();
            }
            recordEvent(shc.conContext.conSession);

            NewSessionTicket.t13PosthandshakeProducer.produce(shc);
        }
    }

    private static void recordEvent(SSLSessionImpl session) {
        TLSHandshakeEvent event = new TLSHandshakeEvent();
        if (event.shouldCommit() || EventHelper.isLoggingSecurity()) {
            int hash = 0;
            try {
                hash = session
                        .getCertificateChain()[0]
                        .hashCode();
            } catch (SSLPeerUnverifiedException e) {
            }
            long peerCertificateId = Integer.toUnsignedLong(hash);
            if (event.shouldCommit()) {
                event.peerHost = session.getPeerHost();
                event.peerPort = session.getPeerPort();
                event.cipherSuite = session.getCipherSuite();
                event.protocolVersion = session.getProtocol();
                event.certificateId = peerCertificateId;
                event.commit();
            }
            if (EventHelper.isLoggingSecurity()) {
                EventHelper.logTLSHandshakeEvent(null,
                                session.getPeerHost(),
                                session.getPeerPort(),
                                session.getCipherSuite(),
                                session.getProtocol(),
                                peerCertificateId);
            }
        }
    }
}
