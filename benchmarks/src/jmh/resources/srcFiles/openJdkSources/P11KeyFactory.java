/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.pkcs11;

import java.security.*;
import java.security.spec.*;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import sun.security.pkcs11.wrapper.PKCS11Exception;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;


/**
 * KeyFactory base class. Provides common infrastructure for the RSA, DSA,
 * and DH implementations.
 *
 * The subclasses support conversion between keys and keyspecs
 * using X.509, PKCS#8, and their individual algorithm specific formats,
 * assuming keys are extractable.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
abstract class P11KeyFactory extends KeyFactorySpi {

    final Token token;

    final String algorithm;

    P11KeyFactory(Token token, String algorithm) {
        super();
        this.token = token;
        this.algorithm = algorithm;
    }

    private static final Map<String,Long> keyTypes;

    static {
        keyTypes = new HashMap<String,Long>();
        addKeyType("RSA", CKK_RSA);
        addKeyType("DSA", CKK_DSA);
        addKeyType("DH",  CKK_DH);
        addKeyType("EC",  CKK_EC);
    }

    private static void addKeyType(String name, long id) {
        Long l = Long.valueOf(id);
        keyTypes.put(name, l);
        keyTypes.put(name.toUpperCase(Locale.ENGLISH), l);
    }

    static long getPKCS11KeyType(String algorithm) {
        Long kt = keyTypes.get(algorithm);
        return (kt != null) ? kt.longValue() : -1;
    }

    /**
     * Convert an arbitrary key of algorithm into a P11Key of token.
     * Used by P11Signature.init() and RSACipher.init().
     */
    static P11Key convertKey(Token token, Key key, String algorithm)
            throws InvalidKeyException {
        return (P11Key)token.getKeyFactory(algorithm).engineTranslateKey(key);
    }

    protected final <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> keySpec)
            throws InvalidKeySpecException {
        token.ensureValid();
        if ((key == null) || (keySpec == null)) {
            throw new InvalidKeySpecException
                ("key and keySpec must not be null");
        }
        if (keySpec.isAssignableFrom(PKCS8EncodedKeySpec.class)
                || keySpec.isAssignableFrom(X509EncodedKeySpec.class)) {
            try {
                return implGetSoftwareFactory().getKeySpec(key, keySpec);
            } catch (GeneralSecurityException e) {
                throw new InvalidKeySpecException("Could not encode key", e);
            }
        }
        P11Key p11Key;
        try {
            p11Key = (P11Key)engineTranslateKey(key);
        } catch (InvalidKeyException e) {
            throw new InvalidKeySpecException("Could not convert key", e);
        }
        Session[] session = new Session[1];
        try {
            if (p11Key.isPublic()) {
                return implGetPublicKeySpec(p11Key, keySpec, session);
            } else {
                return implGetPrivateKeySpec(p11Key, keySpec, session);
            }
        } catch (PKCS11Exception e) {
            throw new InvalidKeySpecException("Could not generate KeySpec", e);
        } finally {
            session[0] = token.releaseSession(session[0]);
        }
    }

    protected final Key engineTranslateKey(Key key) throws InvalidKeyException {
        token.ensureValid();
        if (key == null) {
            throw new InvalidKeyException("Key must not be null");
        }
        if (!key.getAlgorithm().equals(this.algorithm)) {
            throw new InvalidKeyException
                ("Key algorithm must be " + algorithm);
        }
        if (key instanceof P11Key p11Key) {
            if (p11Key.token == token) {
                return key;
            }
        }
        P11Key p11Key = token.privateCache.get(key);
        if (p11Key != null) {
            return p11Key;
        }
        if (key instanceof PublicKey) {
            PublicKey publicKey = implTranslatePublicKey((PublicKey)key);
            token.privateCache.put(key, (P11Key)publicKey);
            return publicKey;
        } else if (key instanceof PrivateKey) {
            PrivateKey privateKey = implTranslatePrivateKey((PrivateKey)key);
            token.privateCache.put(key, (P11Key)privateKey);
            return privateKey;
        } else {
            throw new InvalidKeyException
                ("Key must be instance of PublicKey or PrivateKey");
        }
    }

    abstract <T extends KeySpec> T  implGetPublicKeySpec(P11Key key, Class<T> keySpec,
            Session[] session) throws PKCS11Exception, InvalidKeySpecException;

    abstract <T extends KeySpec> T  implGetPrivateKeySpec(P11Key key, Class<T> keySpec,
            Session[] session) throws PKCS11Exception, InvalidKeySpecException;

    abstract PublicKey implTranslatePublicKey(PublicKey key)
            throws InvalidKeyException;

    abstract PrivateKey implTranslatePrivateKey(PrivateKey key)
            throws InvalidKeyException;

    abstract KeyFactory implGetSoftwareFactory() throws GeneralSecurityException;

}
