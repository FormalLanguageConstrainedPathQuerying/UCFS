/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.mscapi;

import java.math.BigInteger;
import java.security.*;
import java.security.Key;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Arrays;

import javax.crypto.*;
import javax.crypto.spec.*;

import sun.security.rsa.RSAKeyFactory;
import sun.security.internal.spec.TlsRsaPremasterSecretParameterSpec;
import sun.security.util.KeyUtil;

/**
 * Cipher implementation using the Microsoft Crypto API.
 * Supports RSA en/decryption and signing/verifying using PKCS#1 v1.5 padding.
 *
 * Objects should be instantiated by calling Cipher.getInstance() using the
 * following algorithm name:
 *
 *  . "RSA/ECB/PKCS1Padding" (or "RSA") for PKCS#1 padding. The mode (blocktype)
 *    is selected based on the en/decryption mode and public/private key used.
 *
 * We only do one RSA operation per doFinal() call. If the application passes
 * more data via calls to update() or doFinal(), we throw an
 * IllegalBlockSizeException when doFinal() is called (see JCE API spec).
 * Bulk encryption using RSA does not make sense and is not standardized.
 *
 * Note: RSA keys should be at least 512 bits long
 *
 * @since   1.6
 * @author  Andreas Sterbenz
 * @author  Vincent Ryan
 */
public final class CRSACipher extends CipherSpi {

    private static final int ERROR_INVALID_PARAMETER = 0x57;
    private static final int NTE_INVALID_PARAMETER = 0x80090027;

    private static final byte[] B0 = new byte[0];

    private static final int MODE_ENCRYPT = 1;
    private static final int MODE_DECRYPT = 2;
    private static final int MODE_SIGN    = 3;
    private static final int MODE_VERIFY  = 4;

    private static final String PAD_PKCS1 = "PKCS1Padding";
    private static final int PAD_PKCS1_LENGTH = 11;

    private int mode;

    private String paddingType;
    private int paddingLength = 0;

    private byte[] buffer;
    private int bufOfs;

    private int outputSize;

    private CKey publicKey;

    private CKey privateKey;

    private AlgorithmParameterSpec spec = null;

    private boolean forTlsPremasterSecret = false;

    private SecureRandom random;

    public CRSACipher() {
        paddingType = PAD_PKCS1;
    }

    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        if (mode.equalsIgnoreCase("ECB") == false) {
            throw new NoSuchAlgorithmException("Unsupported mode " + mode);
        }
    }

    protected void engineSetPadding(String paddingName)
            throws NoSuchPaddingException {
        if (paddingName.equalsIgnoreCase(PAD_PKCS1)) {
            paddingType = PAD_PKCS1;
        } else {
            throw new NoSuchPaddingException
                ("Padding " + paddingName + " not supported");
        }
    }

    protected int engineGetBlockSize() {
        return 0;
    }

    protected int engineGetOutputSize(int inputLen) {
        return outputSize;
    }

    protected byte[] engineGetIV() {
        return null;
    }

    protected AlgorithmParameters engineGetParameters() {
        return null;
    }

    protected void engineInit(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException {
        init(opmode, key);
    }

    @SuppressWarnings("deprecation")
    protected void engineInit(int opmode, Key key,
            AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {

        if (params != null) {
            if (!(params instanceof TlsRsaPremasterSecretParameterSpec)) {
                throw new InvalidAlgorithmParameterException(
                        "Parameters not supported");
            }
            spec = params;
            this.random = random;   
            this.forTlsPremasterSecret = true;
        } else {
            this.forTlsPremasterSecret = false;
        }
        init(opmode, key);
    }

    protected void engineInit(int opmode, Key key,
            AlgorithmParameters params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {

        if (params != null) {
            throw new InvalidAlgorithmParameterException
                ("Parameters not supported");
        }
        init(opmode, key);
    }

    private void init(int opmode, Key key) throws InvalidKeyException {

        boolean encrypt;

        switch (opmode) {
        case Cipher.ENCRYPT_MODE:
        case Cipher.WRAP_MODE:
            paddingLength = PAD_PKCS1_LENGTH;
            encrypt = true;
            break;
        case Cipher.DECRYPT_MODE:
        case Cipher.UNWRAP_MODE:
            paddingLength = 0; 
            encrypt = false;
            break;
        default:
            throw new AssertionError("Unknown mode: " + opmode);
        }

        if (!(key instanceof CKey)) {
            if (key instanceof java.security.interfaces.RSAPublicKey) {
                java.security.interfaces.RSAPublicKey rsaKey =
                    (java.security.interfaces.RSAPublicKey) key;


                BigInteger modulus = rsaKey.getModulus();
                BigInteger exponent =  rsaKey.getPublicExponent();

                RSAKeyFactory.checkKeyLengths(((modulus.bitLength() + 7) & ~7),
                    exponent, -1, CKeyPairGenerator.RSA.KEY_SIZE_MAX);

                byte[] modulusBytes = modulus.toByteArray();
                byte[] exponentBytes = exponent.toByteArray();

                int keyBitLength = (modulusBytes[0] == 0)
                    ? (modulusBytes.length - 1) * 8
                    : modulusBytes.length * 8;

                byte[] keyBlob = CSignature.RSA.generatePublicKeyBlob(
                    keyBitLength, modulusBytes, exponentBytes);

                try {
                    key = CSignature.importPublicKey("RSA", keyBlob, keyBitLength);

                } catch (KeyStoreException e) {
                    throw new InvalidKeyException(e);
                }

            } else {
                throw new InvalidKeyException("Unsupported key type: " + key);
            }
        }

        if (key instanceof PublicKey) {
            mode = encrypt ? MODE_ENCRYPT : MODE_VERIFY;
            publicKey = (CKey)key;
            privateKey = null;
            outputSize = publicKey.length() / 8;
        } else if (key instanceof PrivateKey) {
            mode = encrypt ? MODE_SIGN : MODE_DECRYPT;
            privateKey = (CKey)key;
            publicKey = null;
            outputSize = privateKey.length() / 8;
        } else {
            throw new InvalidKeyException("Unknown key type: " + key);
        }

        bufOfs = 0;
        buffer = new byte[outputSize];
    }

    private void update(byte[] in, int inOfs, int inLen) {
        if ((inLen == 0) || (in == null)) {
            return;
        }
        if (bufOfs + inLen > (buffer.length - paddingLength)) {
            bufOfs = buffer.length + 1;
            return;
        }
        System.arraycopy(in, inOfs, buffer, bufOfs, inLen);
        bufOfs += inLen;
    }

    private byte[] doFinal() throws IllegalBlockSizeException {
        if (bufOfs > buffer.length) {
            throw new IllegalBlockSizeException("Data must not be longer "
                + "than " + (buffer.length - paddingLength)  + " bytes");
        }

        try {
            byte[] data = buffer;
            switch (mode) {
            case MODE_SIGN:
                return encryptDecrypt(data, bufOfs,
                    privateKey, true);

            case MODE_VERIFY:
                return encryptDecrypt(data, bufOfs,
                    publicKey, false);

            case MODE_ENCRYPT:
                return encryptDecrypt(data, bufOfs,
                    publicKey, true);

            case MODE_DECRYPT:
                return encryptDecrypt(data, bufOfs,
                    privateKey, false);

            default:
                throw new AssertionError("Internal error");
            }

        } catch (KeyException | BadPaddingException e) {
            throw new ProviderException(e);

        } finally {
            bufOfs = 0;
        }
    }

    protected byte[] engineUpdate(byte[] in, int inOfs, int inLen) {
        update(in, inOfs, inLen);
        return B0;
    }

    protected int engineUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) {
        update(in, inOfs, inLen);
        return 0;
    }

    protected byte[] engineDoFinal(byte[] in, int inOfs, int inLen)
            throws IllegalBlockSizeException {
        update(in, inOfs, inLen);
        return doFinal();
    }

    protected int engineDoFinal(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException,
            IllegalBlockSizeException {
        if (outputSize > out.length - outOfs) {
            throw new ShortBufferException
                ("Need " + outputSize + " bytes for output");
        }
        update(in, inOfs, inLen);
        byte[] result = doFinal();
        int n = result.length;
        System.arraycopy(result, 0, out, outOfs, n);
        return n;
    }

    protected byte[] engineWrap(Key key) throws InvalidKeyException,
            IllegalBlockSizeException {

        byte[] encoded = key.getEncoded(); 
        if ((encoded == null) || (encoded.length == 0)) {
            throw new InvalidKeyException("Could not obtain encoded key");
        }
        if (encoded.length > buffer.length) {
            throw new InvalidKeyException("Key is too long for wrapping");
        }
        update(encoded, 0, encoded.length);
        return doFinal();
    }

    @SuppressWarnings("deprecation")
    protected java.security.Key engineUnwrap(byte[] wrappedKey,
            String algorithm,
            int type) throws InvalidKeyException, NoSuchAlgorithmException {

        if (wrappedKey.length > buffer.length) {
            throw new InvalidKeyException("Key is too long for unwrapping");
        }

        boolean isTlsRsaPremasterSecret =
                algorithm.equals("TlsRsaPremasterSecret");
        Exception failover = null;
        byte[] encoded = null;

        update(wrappedKey, 0, wrappedKey.length);
        try {
            encoded = doFinal();
        } catch (IllegalBlockSizeException e) {
            throw new InvalidKeyException("Unwrapping failed", e);
        }

        try {
            if (isTlsRsaPremasterSecret) {
                if (!forTlsPremasterSecret) {
                    throw new IllegalStateException(
                            "No TlsRsaPremasterSecretParameterSpec specified");
                }

                encoded = KeyUtil.checkTlsPreMasterSecretKey(
                        ((TlsRsaPremasterSecretParameterSpec) spec).getClientVersion(),
                        ((TlsRsaPremasterSecretParameterSpec) spec).getServerVersion(),
                        random, encoded, encoded == null);
            }

            return constructKey(encoded, algorithm, type);
        } finally {
            if (encoded != null) {
                Arrays.fill(encoded, (byte) 0);
            }
        }
    }

    protected int engineGetKeySize(Key key) throws InvalidKeyException {

        if (key instanceof CKey) {
            return ((CKey) key).length();

        } else if (key instanceof RSAKey) {
            return ((RSAKey) key).getModulus().bitLength();

        } else {
            throw new InvalidKeyException("Unsupported key type: " + key);
        }
    }

    private static PublicKey constructPublicKey(byte[] encodedKey,
        String encodedKeyAlgorithm)
            throws InvalidKeyException, NoSuchAlgorithmException {

        try {
            KeyFactory keyFactory = KeyFactory.getInstance(encodedKeyAlgorithm);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);

            return keyFactory.generatePublic(keySpec);

        } catch (NoSuchAlgorithmException nsae) {
            throw new NoSuchAlgorithmException("No installed provider " +
                "supports the " + encodedKeyAlgorithm + " algorithm", nsae);

        } catch (InvalidKeySpecException ike) {
            throw new InvalidKeyException("Cannot construct public key", ike);
        }
    }

    private static PrivateKey constructPrivateKey(byte[] encodedKey,
        String encodedKeyAlgorithm)
            throws InvalidKeyException, NoSuchAlgorithmException {

        try {
            KeyFactory keyFactory = KeyFactory.getInstance(encodedKeyAlgorithm);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encodedKey);

            return keyFactory.generatePrivate(keySpec);

        } catch (NoSuchAlgorithmException nsae) {
            throw new NoSuchAlgorithmException("No installed provider " +
                "supports the " + encodedKeyAlgorithm + " algorithm", nsae);

        } catch (InvalidKeySpecException ike) {
            throw new InvalidKeyException("Cannot construct private key", ike);
        }
    }

    private static SecretKey constructSecretKey(byte[] encodedKey,
        String encodedKeyAlgorithm) {

        return new SecretKeySpec(encodedKey, encodedKeyAlgorithm);
    }

    private static Key constructKey(byte[] encodedKey,
            String encodedKeyAlgorithm,
            int keyType) throws InvalidKeyException, NoSuchAlgorithmException {

        switch (keyType) {
            case Cipher.PUBLIC_KEY:
                return constructPublicKey(encodedKey, encodedKeyAlgorithm);
            case Cipher.PRIVATE_KEY:
                return constructPrivateKey(encodedKey, encodedKeyAlgorithm);
            case Cipher.SECRET_KEY:
                return constructSecretKey(encodedKey, encodedKeyAlgorithm);
            default:
                throw new InvalidKeyException("Unknown key type " + keyType);
        }
    }

    /*
     * Encrypt/decrypt a data buffer using Microsoft Crypto API or CNG.
     * It expects and returns ciphertext data in big-endian form.
     */
    private byte[] encryptDecrypt(byte[] data, int dataSize,
            CKey key, boolean doEncrypt) throws KeyException, BadPaddingException {
        int[] returnStatus = new int[1];
        byte[] result;
        if (key.getHCryptKey() != 0) {
            result = encryptDecrypt(returnStatus, data, dataSize, key.getHCryptKey(), doEncrypt);
        } else {
            result = cngEncryptDecrypt(returnStatus, data, dataSize, key.getHCryptProvider(), doEncrypt);
        }
        if ((returnStatus[0] == ERROR_INVALID_PARAMETER) || (returnStatus[0] == NTE_INVALID_PARAMETER)) {
            if (forTlsPremasterSecret) {
                result = null;
            } else {
                throw new BadPaddingException("Error " + returnStatus[0] + " returned by MSCAPI");
            }
        } else if (returnStatus[0] != 0) {
            throw new KeyException("Error " + returnStatus[0] + " returned by MSCAPI");
        }

        return result;
    }

    private static native byte[] encryptDecrypt(int[] returnStatus, byte[] data, int dataSize,
            long key, boolean doEncrypt) throws KeyException;
    private static native byte[] cngEncryptDecrypt(int[] returnStatus, byte[] data, int dataSize,
            long key, boolean doEncrypt) throws KeyException;
}
