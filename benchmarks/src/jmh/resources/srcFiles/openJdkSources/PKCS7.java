/*
 * Copyright (c) 1996, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.pkcs;

import java.io.*;
import java.math.BigInteger;
import java.net.URI;
import java.util.*;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.security.cert.CRLException;
import java.security.cert.CertificateFactory;
import java.security.*;
import java.util.function.Function;

import sun.security.jca.JCAUtil;
import sun.security.provider.SHAKE256;
import sun.security.timestamp.*;
import sun.security.util.*;
import sun.security.x509.*;

/**
 * PKCS7 as defined in RSA Laboratories PKCS7 Technical Note. Profile
 * Supports only {@code SignedData} ContentInfo
 * type, where to the type of data signed is plain Data.
 * For signedData, {@code crls}, {@code attributes} and
 * PKCS#6 Extended Certificates are not supported.
 *
 * @author Benjamin Renaud
 */
public class PKCS7 {

    private BigInteger version = null;
    private AlgorithmId[] digestAlgorithmIds = null;
    private ContentInfo contentInfo = null;
    private X509Certificate[] certificates = null;
    private X509CRL[] crls = null;
    private SignerInfo[] signerInfos = null;

    private boolean oldStyle = false; 

    private Principal[] certIssuerNames;

    /**
     * Unmarshals a PKCS7 block from its encoded form, parsing the
     * encoded bytes from the InputStream.
     *
     * @param in an input stream holding at least one PKCS7 block.
     * @exception ParsingException on parsing errors.
     * @exception IOException on other errors.
     */
    public PKCS7(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        byte[] data = new byte[dis.available()];
        dis.readFully(data);

        parse(new DerInputStream(data));
    }

    /**
     * Unmarshals a PKCS7 block from its encoded form, parsing the
     * encoded bytes from the DerInputStream.
     *
     * @param derin a DerInputStream holding at least one PKCS7 block.
     * @exception ParsingException on parsing errors.
     */
    public PKCS7(DerInputStream derin) throws ParsingException {
        parse(derin);
    }

    /**
     * Unmarshals a PKCS7 block from its encoded form, parsing the
     * encoded bytes.
     *
     * @param bytes the encoded bytes.
     * @exception ParsingException on parsing errors.
     */
    public PKCS7(byte[] bytes) throws ParsingException {
        try {
            DerInputStream derin = new DerInputStream(bytes);
            parse(derin);
        } catch (IOException ioe1) {
            ParsingException pe = new ParsingException(
                "Unable to parse the encoded bytes");
            pe.initCause(ioe1);
            throw pe;
        }
    }

    /*
     * Parses a PKCS#7 block.
     */
    private void parse(DerInputStream derin)
        throws ParsingException
    {
        try {
            derin.mark(derin.available());
            parse(derin, false);
        } catch (IOException ioe) {
            try {
                derin.reset();
                parse(derin, true);
                oldStyle = true;
            } catch (IOException ioe1) {
                ParsingException pe = new ParsingException(
                    ioe1.getMessage());
                pe.initCause(ioe);
                pe.addSuppressed(ioe1);
                throw pe;
            }
        }
    }

    /**
     * Parses a PKCS#7 block.
     *
     * @param derin the ASN.1 encoding of the PKCS#7 block.
     * @param oldStyle flag indicating whether the given PKCS#7 block
     * is encoded according to JDK1.1.x.
     */
    private void parse(DerInputStream derin, boolean oldStyle)
        throws IOException
    {
        ContentInfo block = new ContentInfo(derin, oldStyle);
        ObjectIdentifier contentType = block.contentType;
        DerValue content = block.getContent();

        if (content == null) {
            throw new ParsingException("content is null");
        }

        if (contentType.equals(ContentInfo.SIGNED_DATA_OID)) {
            parseSignedData(content);
        } else if (contentType.equals(ContentInfo.OLD_SIGNED_DATA_OID)) {
            parseOldSignedData(content);
        } else if (contentType.equals(ContentInfo.NETSCAPE_CERT_SEQUENCE_OID)){
            parseNetscapeCertChain(content);
            contentInfo = block; 
        } else {
            throw new ParsingException("content type " + contentType +
                                       " not supported.");
        }
    }

    /**
     * Construct an initialized PKCS7 block.
     *
     * @param digestAlgorithmIds the message digest algorithm identifiers.
     * @param contentInfo the content information.
     * @param certificates an array of X.509 certificates.
     * @param crls an array of CRLs
     * @param signerInfos an array of signer information.
     */
    public PKCS7(AlgorithmId[] digestAlgorithmIds,
                 ContentInfo contentInfo,
                 X509Certificate[] certificates,
                 X509CRL[] crls,
                 SignerInfo[] signerInfos) {

        version = BigInteger.ONE;
        this.digestAlgorithmIds = digestAlgorithmIds;
        this.contentInfo = contentInfo;
        this.certificates = certificates;
        this.crls = crls;
        this.signerInfos = signerInfos;
    }

    public PKCS7(AlgorithmId[] digestAlgorithmIds,
                 ContentInfo contentInfo,
                 X509Certificate[] certificates,
                 SignerInfo[] signerInfos) {
        this(digestAlgorithmIds, contentInfo, certificates, null, signerInfos);
    }

    private void parseNetscapeCertChain(DerValue val) throws IOException {
        DerInputStream dis = new DerInputStream(val.toByteArray());
        DerValue[] contents = dis.getSequence(2);
        certificates = new X509Certificate[contents.length];

        CertificateFactory certfac = null;
        try {
            certfac = CertificateFactory.getInstance("X.509");
        } catch (CertificateException ce) {
        }

        for (int i=0; i < contents.length; i++) {
            ByteArrayInputStream bais = null;
            try {
                if (certfac == null)
                    certificates[i] = new X509CertImpl(contents[i]);
                else {
                    byte[] encoded = contents[i].toByteArray();
                    bais = new ByteArrayInputStream(encoded);
                    certificates[i] =
                        (X509Certificate)certfac.generateCertificate(bais);
                    bais.close();
                    bais = null;
                }
            } catch (CertificateException | IOException ce) {
                ParsingException pe = new ParsingException(ce.getMessage());
                pe.initCause(ce);
                throw pe;
            } finally {
                if (bais != null)
                    bais.close();
            }
        }
    }

    private void parseSignedData(DerValue val) throws IOException {
        DerInputStream dis = val.toDerInputStream();

        version = dis.getBigInteger();

        DerValue[] digestAlgorithmIdVals = dis.getSet(1);
        int len = digestAlgorithmIdVals.length;
        digestAlgorithmIds = new AlgorithmId[len];
        try {
            for (int i = 0; i < len; i++) {
                DerValue oid = digestAlgorithmIdVals[i];
                digestAlgorithmIds[i] = AlgorithmId.parse(oid);
            }

        } catch (IOException e) {
            ParsingException pe =
                new ParsingException("Error parsing digest AlgorithmId IDs: " +
                                     e.getMessage());
            pe.initCause(e);
            throw pe;
        }
        contentInfo = new ContentInfo(dis);

        CertificateFactory certfac = null;
        try {
            certfac = CertificateFactory.getInstance("X.509");
        } catch (CertificateException ce) {
        }

        /*
         * check if certificates (implicit tag) are provided
         * (certificates are OPTIONAL)
         */
        var certDer = dis.getOptionalImplicitContextSpecific(0, DerValue.tag_SetOf);
        if (certDer.isPresent()) {
            DerValue[] certVals = certDer.get().subs(DerValue.tag_SetOf, 2);
            len = certVals.length;
            certificates = new X509Certificate[len];
            int count = 0;

            for (int i = 0; i < len; i++) {
                ByteArrayInputStream bais = null;
                try {
                    byte tag = certVals[i].getTag();
                    if (tag == DerValue.tag_Sequence) {
                        if (certfac == null) {
                            certificates[count] = new X509CertImpl(certVals[i]);
                        } else {
                            byte[] encoded = certVals[i].toByteArray();
                            bais = new ByteArrayInputStream(encoded);
                            certificates[count] =
                                (X509Certificate)certfac.generateCertificate(bais);
                            bais.close();
                            bais = null;
                        }
                        count++;
                    }
                } catch (CertificateException | IOException ce) {
                    ParsingException pe = new ParsingException(ce.getMessage());
                    pe.initCause(ce);
                    throw pe;
                } finally {
                    if (bais != null)
                        bais.close();
                }
            }
            if (count != len) {
                certificates = Arrays.copyOf(certificates, count);
            }
        }

        var crlsDer = dis.getOptionalImplicitContextSpecific(1, DerValue.tag_SetOf);
        if (crlsDer.isPresent()) {
            DerValue[] crlVals = crlsDer.get().subs(DerValue.tag_SetOf, 1);
            len = crlVals.length;
            crls = new X509CRL[len];

            for (int i = 0; i < len; i++) {
                ByteArrayInputStream bais = null;
                try {
                    if (certfac == null)
                        crls[i] = new X509CRLImpl(crlVals[i]);
                    else {
                        byte[] encoded = crlVals[i].toByteArray();
                        bais = new ByteArrayInputStream(encoded);
                        crls[i] = (X509CRL) certfac.generateCRL(bais);
                        bais.close();
                        bais = null;
                    }
                } catch (CRLException e) {
                    ParsingException pe =
                        new ParsingException(e.getMessage());
                    pe.initCause(e);
                    throw pe;
                } finally {
                    if (bais != null)
                        bais.close();
                }
            }
        }

        DerValue[] signerInfoVals = dis.getSet(1);

        len = signerInfoVals.length;
        signerInfos = new SignerInfo[len];

        for (int i = 0; i < len; i++) {
            DerInputStream in = signerInfoVals[i].toDerInputStream();
            signerInfos[i] = new SignerInfo(in);
        }
    }

    /*
     * Parses an old-style SignedData encoding (for backwards
     * compatibility with JDK1.1.x).
     */
    private void parseOldSignedData(DerValue val) throws IOException {
        DerInputStream dis = val.toDerInputStream();

        version = dis.getBigInteger();

        DerValue[] digestAlgorithmIdVals = dis.getSet(1);
        int len = digestAlgorithmIdVals.length;

        digestAlgorithmIds = new AlgorithmId[len];
        try {
            for (int i = 0; i < len; i++) {
                DerValue oid = digestAlgorithmIdVals[i];
                digestAlgorithmIds[i] = AlgorithmId.parse(oid);
            }
        } catch (IOException e) {
            throw new ParsingException("Error parsing digest AlgorithmId IDs");
        }

        contentInfo = new ContentInfo(dis, true);

        CertificateFactory certfac = null;
        try {
            certfac = CertificateFactory.getInstance("X.509");
        } catch (CertificateException ce) {
        }
        DerValue[] certVals = dis.getSet(2);
        len = certVals.length;
        certificates = new X509Certificate[len];

        for (int i = 0; i < len; i++) {
            ByteArrayInputStream bais = null;
            try {
                if (certfac == null)
                    certificates[i] = new X509CertImpl(certVals[i]);
                else {
                    byte[] encoded = certVals[i].toByteArray();
                    bais = new ByteArrayInputStream(encoded);
                    certificates[i] =
                        (X509Certificate)certfac.generateCertificate(bais);
                    bais.close();
                    bais = null;
                }
            } catch (CertificateException | IOException ce) {
                ParsingException pe = new ParsingException(ce.getMessage());
                pe.initCause(ce);
                throw pe;
            } finally {
                if (bais != null)
                    bais.close();
            }
        }

        dis.getSet(0);

        DerValue[] signerInfoVals = dis.getSet(1);
        len = signerInfoVals.length;
        signerInfos = new SignerInfo[len];
        for (int i = 0; i < len; i++) {
            DerInputStream in = signerInfoVals[i].toDerInputStream();
            signerInfos[i] = new SignerInfo(in, true);
        }
    }

    /**
     * Encodes the signed data to a DerOutputStream.
     *
     * @param out the DerOutputStream to write the encoded data to.
     * @exception IOException on encoding errors.
     */
    public void encodeSignedData(DerOutputStream out)
        throws IOException
    {
        DerOutputStream signedData = new DerOutputStream();

        signedData.putInteger(version);

        signedData.putOrderedSetOf(DerValue.tag_Set, digestAlgorithmIds);

        contentInfo.encode(signedData);

        if (certificates != null && certificates.length != 0) {
            X509CertImpl[] implCerts = new X509CertImpl[certificates.length];
            for (int i = 0; i < certificates.length; i++) {
                if (certificates[i] instanceof X509CertImpl)
                    implCerts[i] = (X509CertImpl) certificates[i];
                else {
                    try {
                        byte[] encoded = certificates[i].getEncoded();
                        implCerts[i] = new X509CertImpl(encoded);
                    } catch (CertificateException ce) {
                        throw new IOException(ce);
                    }
                }
            }

            signedData.putOrderedSetOf((byte)0xA0, implCerts);
        }

        if (crls != null && crls.length != 0) {
            Set<X509CRLImpl> implCRLs = HashSet.newHashSet(crls.length);
            for (X509CRL crl: crls) {
                if (crl instanceof X509CRLImpl)
                    implCRLs.add((X509CRLImpl) crl);
                else {
                    try {
                        byte[] encoded = crl.getEncoded();
                        implCRLs.add(new X509CRLImpl(encoded));
                    } catch (CRLException ce) {
                        throw new IOException(ce);
                    }
                }
            }

            signedData.putOrderedSetOf((byte)0xA1,
                    implCRLs.toArray(new X509CRLImpl[0]));
        }

        signedData.putOrderedSetOf(DerValue.tag_Set, signerInfos);

        DerValue signedDataSeq = new DerValue(DerValue.tag_Sequence,
                                              signedData.toByteArray());

        ContentInfo block = new ContentInfo(ContentInfo.SIGNED_DATA_OID,
                                            signedDataSeq);

        block.encode(out);
    }

    /**
     * This verifies a given SignerInfo.
     *
     * @param info the signer information.
     * @param bytes the DER encoded content information.
     *
     * @exception NoSuchAlgorithmException on unrecognized algorithms.
     * @exception SignatureException on signature handling errors.
     */
    public SignerInfo verify(SignerInfo info, byte[] bytes)
    throws NoSuchAlgorithmException, SignatureException {
        return info.verify(this, bytes);
    }

    /**
     * Returns all signerInfos which self-verify.
     *
     * @param bytes the DER encoded content information.
     *
     * @exception NoSuchAlgorithmException on unrecognized algorithms.
     * @exception SignatureException on signature handling errors.
     */
    public SignerInfo[] verify(byte[] bytes)
    throws NoSuchAlgorithmException, SignatureException {

        ArrayList<SignerInfo> intResult = new ArrayList<>();
        for (int i = 0; i < signerInfos.length; i++) {

            SignerInfo signerInfo = verify(signerInfos[i], bytes);
            if (signerInfo != null) {
                intResult.add(signerInfo);
            }
        }
        if (!intResult.isEmpty()) {

            SignerInfo[] result = new SignerInfo[intResult.size()];
            return intResult.toArray(result);
        }
        return null;
    }

    /**
     * Returns the version number of this PKCS7 block.
     * @return the version or null if version is not specified
     *         for the content type.
     */
    public  BigInteger getVersion() {
        return version;
    }

    /**
     * Returns the message digest algorithms specified in this PKCS7 block.
     * @return the array of Digest Algorithms or null if none are specified
     *         for the content type.
     */
    public AlgorithmId[] getDigestAlgorithmIds() {
        return  digestAlgorithmIds;
    }

    /**
     * Returns the content information specified in this PKCS7 block.
     */
    public ContentInfo getContentInfo() {
        return contentInfo;
    }

    /**
     * Returns the X.509 certificates listed in this PKCS7 block.
     * @return a clone of the array of X.509 certificates or null if
     *         none are specified for the content type.
     */
    public X509Certificate[] getCertificates() {
        if (certificates != null)
            return certificates.clone();
        else
            return null;
    }

    /**
     * Returns the X.509 crls listed in this PKCS7 block.
     * @return a clone of the array of X.509 crls or null if none
     *         are specified for the content type.
     */
    public X509CRL[] getCRLs() {
        if (crls != null)
            return crls.clone();
        else
            return null;
    }

    /**
     * Returns the signer's information specified in this PKCS7 block.
     * @return the array of Signer Infos or null if none are specified
     *         for the content type.
     */
    public SignerInfo[] getSignerInfos() {
        return signerInfos;
    }

    /**
     * Returns the X.509 certificate listed in this PKCS7 block
     * which has a matching serial number and Issuer name, or
     * null if one is not found.
     *
     * @param serial the serial number of the certificate to retrieve.
     * @param issuerName the Distinguished Name of the Issuer.
     */
    public X509Certificate getCertificate(BigInteger serial, X500Name issuerName) {
        if (certificates != null) {
            if (certIssuerNames == null)
                populateCertIssuerNames();
            for (int i = 0; i < certificates.length; i++) {
                X509Certificate cert = certificates[i];
                BigInteger thisSerial = cert.getSerialNumber();
                if (serial.equals(thisSerial)
                    && issuerName.equals(certIssuerNames[i]))
                {
                    return cert;
                }
            }
        }
        return null;
    }

    /**
     * Populate array of Issuer DNs from certificates and convert
     * each Principal to type X500Name if necessary.
     */
    @SuppressWarnings("deprecation")
    private void populateCertIssuerNames() {
        if (certificates == null)
            return;

        certIssuerNames = new Principal[certificates.length];
        for (int i = 0; i < certificates.length; i++) {
            X509Certificate cert = certificates[i];
            Principal certIssuerName = cert.getIssuerDN();
            if (!(certIssuerName instanceof X500Name)) {
                try {
                    X509CertInfo tbsCert =
                        new X509CertInfo(cert.getTBSCertificate());
                    certIssuerName = tbsCert.getIssuer();
                } catch (Exception e) {
                }
            }
            certIssuerNames[i] = certIssuerName;
        }
    }

    /**
     * Returns the PKCS7 block in a printable string form.
     */
    public String toString() {
        String out = "";

        out += contentInfo + "\n";
        if (version != null)
            out += "PKCS7 :: version: " + Debug.toHexString(version) + "\n";
        if (digestAlgorithmIds != null) {
            out += "PKCS7 :: digest AlgorithmIds: \n";
            for (int i = 0; i < digestAlgorithmIds.length; i++)
                out += "\t" + digestAlgorithmIds[i] + "\n";
        }
        if (certificates != null) {
            out += "PKCS7 :: certificates: \n";
            for (int i = 0; i < certificates.length; i++)
                out += "\t" + i + ".   " + certificates[i] + "\n";
        }
        if (crls != null) {
            out += "PKCS7 :: crls: \n";
            for (int i = 0; i < crls.length; i++)
                out += "\t" + i + ".   " + crls[i] + "\n";
        }
        if (signerInfos != null) {
            out += "PKCS7 :: signer infos: \n";
            for (int i = 0; i < signerInfos.length; i++)
                out += ("\t" + i + ".  " + signerInfos[i] + "\n");
        }
        return out;
    }

    /**
     * Returns true if this is a JDK1.1.x-style PKCS#7 block, and false
     * otherwise.
     */
    public boolean isOldStyle() {
        return this.oldStyle;
    }

    /**
     * Generate a PKCS7 data block.
     *
     * @param sigalg signature algorithm to be used
     * @param sigProvider (optional) provider
     * @param privateKey signer's private key
     * @param signerChain signer's certificate chain
     * @param content the content to sign
     * @param internalsf whether the content should be included in output
     * @param directsign if the content is signed directly or through authattrs
     * @param ts (optional) timestamper
     * @return the pkcs7 output in an array
     * @throws SignatureException if signing failed
     * @throws InvalidKeyException if key cannot be used
     * @throws IOException should not happen here, all byte array
     * @throws NoSuchAlgorithmException if siglag is bad
     */
    public static byte[] generateSignedData(
            String sigalg, Provider sigProvider,
            PrivateKey privateKey, X509Certificate[] signerChain,
            byte[] content, boolean internalsf, boolean directsign,
            Function<byte[], PKCS9Attributes> ts)
                throws SignatureException, InvalidKeyException, IOException,
                    NoSuchAlgorithmException {

        Signature signer = SignatureUtil.fromKey(sigalg, privateKey, sigProvider);

        AlgorithmId digAlgID = SignatureUtil.getDigestAlgInPkcs7SignerInfo(
                signer, sigalg, privateKey, signerChain[0].getPublicKey(), directsign);
        AlgorithmId sigAlgID = SignatureUtil.fromSignature(signer, privateKey);

        PKCS9Attributes authAttrs = null;
        if (!directsign) {
            byte[] md;
            String digAlgName = digAlgID.getName();
            if (digAlgName.equals("SHAKE256") || digAlgName.equals("SHAKE256-LEN")) {
                var shaker = new SHAKE256(64);
                shaker.update(content, 0, content.length);
                md = shaker.digest();
            } else {
                md = MessageDigest.getInstance(digAlgName)
                        .digest(content);
            }
            DerOutputStream derAp = new DerOutputStream();
            DerOutputStream derAlgs = new DerOutputStream();
            digAlgID.encode(derAlgs);
            DerOutputStream derSigAlg = new DerOutputStream();
            sigAlgID.encode(derSigAlg);
            derAlgs.writeImplicit((byte)0xA1, derSigAlg);
            derAp.write(DerValue.tag_Sequence, derAlgs);
            authAttrs = new PKCS9Attributes(new PKCS9Attribute[]{
                    new PKCS9Attribute(PKCS9Attribute.CONTENT_TYPE_OID,
                            ContentInfo.DATA_OID),
                    new PKCS9Attribute(PKCS9Attribute.SIGNING_TIME_OID,
                            new Date()),
                    new PKCS9Attribute(PKCS9Attribute.CMS_ALGORITHM_PROTECTION_OID,
                            derAp.toByteArray()),
                    new PKCS9Attribute(PKCS9Attribute.MESSAGE_DIGEST_OID,
                            md)
            });
            signer.update(authAttrs.getDerEncoding());
        } else {
            signer.update(content);
        }

        byte[] signature = signer.sign();

        return constructToken(signature, signerChain,
                internalsf ? content : null,
                authAttrs,
                ts == null ? null : ts.apply(signature),
                digAlgID,
                sigAlgID);
    }

    /**
     * Assemble a PKCS7 token from its components
     * @param signature the signature
     * @param signerChain the signer's certificate chain
     * @param content (optional) encapsulated content
     * @param authAttrs (optional) authenticated attributes
     * @param unauthAttrs (optional) unauthenticated attributes
     * @param digAlgID digest algorithm identifier
     * @param encAlgID encryption algorithm identifier
     * @return the token in a byte array
     * @throws IOException should not happen here, all byte array
     */
    private static byte[] constructToken(byte[] signature,
                                         X509Certificate[] signerChain,
                                         byte[] content,
                                         PKCS9Attributes authAttrs,
                                         PKCS9Attributes unauthAttrs,
                                         AlgorithmId digAlgID,
                                         AlgorithmId encAlgID)
            throws IOException {
        X500Name issuerName =
                X500Name.asX500Name(signerChain[0].getIssuerX500Principal());
        BigInteger serialNumber = signerChain[0].getSerialNumber();
        SignerInfo signerInfo = new SignerInfo(issuerName, serialNumber,
                digAlgID, authAttrs,
                encAlgID,
                signature, unauthAttrs);

        SignerInfo[] signerInfos = {signerInfo};
        AlgorithmId[] algorithms = {signerInfo.getDigestAlgorithmId()};
        ContentInfo contentInfo = (content == null)
                ? new ContentInfo(ContentInfo.DATA_OID, null)
                : new ContentInfo(content);
        PKCS7 pkcs7 = new PKCS7(algorithms, contentInfo,
                signerChain, signerInfos);
        DerOutputStream p7out = new DerOutputStream();
        pkcs7.encodeSignedData(p7out);

        return p7out.toByteArray();
    }

    /**
     * Examine the certificate for a Subject Information Access extension
     * (<a href="https:
     * The extension's {@code accessMethod} field should contain the object
     * identifier defined for timestamping: 1.3.6.1.5.5.7.48.3 and its
     * {@code accessLocation} field should contain an HTTP or HTTPS URL.
     *
     * @param tsaCertificate (optional) X.509 certificate for the TSA.
     * @return An HTTP or HTTPS URI or null if none was found.
     */
    public static URI getTimestampingURI(X509Certificate tsaCertificate) {

        if (tsaCertificate == null) {
            return null;
        }
        try {
            byte[] extensionValue = tsaCertificate.getExtensionValue
                    (KnownOIDs.SubjectInfoAccess.value());
            if (extensionValue == null) {
                return null;
            }
            DerInputStream der = new DerInputStream(extensionValue);
            der = new DerInputStream(der.getOctetString());
            DerValue[] derValue = der.getSequence(5);
            AccessDescription description;
            GeneralName location;
            URIName uri;
            for (int i = 0; i < derValue.length; i++) {
                description = new AccessDescription(derValue[i]);
                if (description.getAccessMethod()
                        .equals(ObjectIdentifier.of(KnownOIDs.AD_TimeStamping))) {
                    location = description.getAccessLocation();
                    if (location.getType() == GeneralNameInterface.NAME_URI) {
                        uri = (URIName) location.getName();
                        if (uri.getScheme().equalsIgnoreCase("http") ||
                                uri.getScheme().equalsIgnoreCase("https")) {
                            return uri.getURI();
                        }
                    }
                }
            }
        } catch (IOException ioe) {
        }
        return null;
    }

    /**
     * Requests, processes and validates a timestamp token from a TSA using
     * common defaults. Uses the following defaults in the timestamp request:
     * SHA-1 for the hash algorithm, a 64-bit nonce, and request certificate
     * set to true.
     *
     * @param tsa the timestamping authority to use
     * @param tSAPolicyID the TSAPolicyID of the Timestamping Authority as a
     *         numerical object identifier; or null if we leave the TSA server
     *         to choose one
     * @param toBeTimestamped the token that is to be timestamped
     * @return the encoded timestamp token
     * @throws IOException The exception is thrown if an error occurs while
     *                     communicating with the TSA, or a non-null
     *                     TSAPolicyID is specified in the request but it
     *                     does not match the one in the reply
     * @throws CertificateException The exception is thrown if the TSA's
     *                     certificate is not permitted for timestamping.
     */
    public static byte[] generateTimestampToken(Timestamper tsa,
                                                 String tSAPolicyID,
                                                 String tSADigestAlg,
                                                 byte[] toBeTimestamped)
        throws IOException, CertificateException
    {
        MessageDigest messageDigest;
        TSRequest tsQuery;
        try {
            messageDigest = MessageDigest.getInstance(tSADigestAlg);
            tsQuery = new TSRequest(tSAPolicyID, toBeTimestamped, messageDigest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }

        BigInteger nonce = new BigInteger(64, JCAUtil.getDefSecureRandom());
        tsQuery.setNonce(nonce);

        tsQuery.requestCertificate(true);

        TSResponse tsReply = tsa.generateTimestamp(tsQuery);
        int status = tsReply.getStatusCode();
        if (status != 0 && status != 1) {
            throw new IOException("Error generating timestamp: " +
                tsReply.getStatusCodeAsText() + " " +
                tsReply.getFailureCodeAsText());
        }

        if (tSAPolicyID != null &&
                !tSAPolicyID.equals(tsReply.getTimestampToken().getPolicyID())) {
            throw new IOException("TSAPolicyID changed in "
                    + "timestamp token");
        }
        PKCS7 tsToken = tsReply.getToken();

        TimestampToken tst = tsReply.getTimestampToken();
        try {
            if (!tst.getHashAlgorithm().equals(AlgorithmId.get(tSADigestAlg))) {
                throw new IOException("Digest algorithm not " + tSADigestAlg + " in "
                                      + "timestamp token");
            }
        } catch (NoSuchAlgorithmException nase) {
            throw new IllegalArgumentException();   
        }
        if (!MessageDigest.isEqual(tst.getHashedMessage(),
                                   tsQuery.getHashedMessage())) {
            throw new IOException("Digest octets changed in timestamp token");
        }

        BigInteger replyNonce = tst.getNonce();
        if (replyNonce == null && nonce != null) {
            throw new IOException("Nonce missing in timestamp token");
        }
        if (replyNonce != null && !replyNonce.equals(nonce)) {
            throw new IOException("Nonce changed in timestamp token");
        }

        for (SignerInfo si: tsToken.getSignerInfos()) {
            X509Certificate cert = si.getCertificate(tsToken);
            if (cert == null) {
                throw new CertificateException(
                "Certificate not included in timestamp token");
            } else {
                if (!cert.getCriticalExtensionOIDs().contains(
                        KnownOIDs.extendedKeyUsage.value())) {
                    throw new CertificateException(
                    "Certificate is not valid for timestamping");
                }
                List<String> keyPurposes = cert.getExtendedKeyUsage();
                if (keyPurposes == null ||
                        !keyPurposes.contains(KnownOIDs.KP_TimeStamping.value())) {
                    throw new CertificateException(
                    "Certificate is not valid for timestamping");
                }
            }
        }
        return tsReply.getEncodedToken();
    }
}
