/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.security.sasl.gsskerb;

import javax.security.sasl.*;
import java.io.*;
import java.util.Map;
import java.util.logging.Level;

import static java.nio.charset.StandardCharsets.UTF_8;

import javax.security.auth.callback.*;

import org.ietf.jgss.*;

/**
 * Implements the GSSAPI SASL server mechanism for Kerberos V5.
 * (<A HREF="http:
 * <a HREF="http:
 *
 * Expects thread's Subject to contain server's Kerberos credentials
 * - If not, underlying KRB5 mech will attempt to acquire Kerberos creds
 *   by logging into Kerberos (via default TextCallbackHandler).
 * - These creds will be used for exchange with client.
 *
 * Required callbacks:
 * - AuthorizeCallback
 *      handler must verify that authid/authzids are allowed and set
 *      authorized ID to be the canonicalized authzid (if applicable).
 *
 * Environment properties that affect behavior of implementation:
 *
 * javax.security.sasl.qop
 * - quality of protection; list of auth, auth-int, auth-conf; default is "auth"
 * javax.security.sasl.maxbuf
 * - max receive buffer size; default is 65536
 * javax.security.sasl.sendmaxbuffer
 * - max send buffer size; default is 65536; (min with client max recv size)
 *
 * @author Rosanna Lee
 */
final class GssKrb5Server extends GssKrb5Base implements SaslServer {
    private static final String MY_CLASS_NAME = GssKrb5Server.class.getName();

    private int handshakeStage = 0;
    private String peer;
    private String me;
    private String authzid;
    private CallbackHandler cbh;

    private final String protocolSaved;
    /**
     * Creates a SASL mechanism with server credentials that it needs
     * to participate in GSS-API/Kerberos v5 authentication exchange
     * with the client.
     */
    GssKrb5Server(String protocol, String serverName,
        Map<String, ?> props, CallbackHandler cbh) throws SaslException {

        super(props, MY_CLASS_NAME);

        this.cbh = cbh;

        String service;
        if (serverName == null) {
            protocolSaved = protocol;
            service = null;
        } else {
            protocolSaved = null;
            service = protocol + "@" + serverName;
        }

        logger.log(Level.FINE, "KRB5SRV01:Using service name: {0}", service);

        try {
            GSSManager mgr = GSSManager.getInstance();

            GSSName serviceName = service == null ? null:
                    mgr.createName(service, GSSName.NT_HOSTBASED_SERVICE, KRB5_OID);

            GSSCredential cred = mgr.createCredential(serviceName,
                GSSCredential.INDEFINITE_LIFETIME,
                KRB5_OID, GSSCredential.ACCEPT_ONLY);

            secCtx = mgr.createContext(cred);

            if ((allQop&INTEGRITY_ONLY_PROTECTION) != 0) {
                secCtx.requestInteg(true);
            }

            if ((allQop&PRIVACY_PROTECTION) != 0) {
                secCtx.requestConf(true);
            }
        } catch (GSSException e) {
            throw new SaslException("Failure to initialize security context", e);
        }
        logger.log(Level.FINE, "KRB5SRV02:Initialization complete");
    }


    /**
     * Processes the response data.
     *
     * The client sends response data to which the server must
     * process using GSS_accept_sec_context.
     * As per RFC 2222, the GSS authentication completes (GSS_S_COMPLETE)
     * we do an extra hand shake to determine the negotiated security protection
     * and buffer sizes.
     *
     * @param responseData A non-null but possible empty byte array containing the
     * response data from the client.
     * @return A non-null byte array containing the challenge to be
     * sent to the client, or null when no more data is to be sent.
     */
    public byte[] evaluateResponse(byte[] responseData) throws SaslException {
        if (completed) {
            throw new SaslException(
                "SASL authentication already complete");
        }

        if (logger.isLoggable(Level.FINER)) {
            traceOutput(MY_CLASS_NAME, "evaluateResponse",
                "KRB5SRV03:Response [raw]:", responseData);
        }

        switch (handshakeStage) {
        case 1:
            return doHandshake1(responseData);

        case 2:
            return doHandshake2(responseData);

        default:

            try {
                byte[] gssOutToken = secCtx.acceptSecContext(responseData,
                    0, responseData.length);

                if (logger.isLoggable(Level.FINER)) {
                    traceOutput(MY_CLASS_NAME, "evaluateResponse",
                        "KRB5SRV04:Challenge: [after acceptSecCtx]", gssOutToken);
                }

                if (secCtx.isEstablished()) {
                    handshakeStage = 1;

                    peer = secCtx.getSrcName().toString();
                    me = secCtx.getTargName().toString();

                    logger.log(Level.FINE,
                            "KRB5SRV05:Peer name is : {0}, my name is : {1}",
                            new Object[]{peer, me});

                    if (protocolSaved != null &&
                            !protocolSaved.equalsIgnoreCase(me.split("[/@]")[0])) {
                        throw new SaslException(
                                "GSS context targ name protocol error: " + me);
                    }

                    if (gssOutToken == null) {
                        return doHandshake1(EMPTY);
                    }
                }

                return gssOutToken;
            } catch (GSSException e) {
                throw new SaslException("GSS initiate failed", e);
            }
        }
    }

    private byte[] doHandshake1(byte[] responseData) throws SaslException {
        try {
            if (responseData != null && responseData.length > 0) {
                throw new SaslException(
                    "Handshake expecting no response data from server");
            }


            byte[] gssInToken = new byte[4];
            gssInToken[0] = allQop;
            intToNetworkByteOrder(recvMaxBufSize, gssInToken, 1, 3);

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                    "KRB5SRV06:Supported protections: {0}; recv max buf size: {1}",
                    new Object[]{allQop,
                                 recvMaxBufSize});
            }

            handshakeStage = 2;  

            if (logger.isLoggable(Level.FINER)) {
                traceOutput(MY_CLASS_NAME, "doHandshake1",
                    "KRB5SRV07:Challenge [raw]", gssInToken);
            }

            byte[] gssOutToken = secCtx.wrap(gssInToken, 0, gssInToken.length,
                new MessageProp(0 /* gop */, false /* privacy */));

            if (logger.isLoggable(Level.FINER)) {
                traceOutput(MY_CLASS_NAME, "doHandshake1",
                    "KRB5SRV08:Challenge [after wrap]", gssOutToken);
            }
            return gssOutToken;

        } catch (GSSException e) {
            throw new SaslException("Problem wrapping handshake1", e);
        }
    }

    private byte[] doHandshake2(byte[] responseData) throws SaslException {
        try {
            MessageProp msgProp = new MessageProp(false);
            byte[] gssOutToken = secCtx.unwrap(responseData, 0,
                responseData.length, msgProp);
            checkMessageProp("Handshake failure: ", msgProp);

            if (logger.isLoggable(Level.FINER)) {
                traceOutput(MY_CLASS_NAME, "doHandshake2",
                    "KRB5SRV09:Response [after unwrap]", gssOutToken);
            }

            byte selectedQop = gssOutToken[0];
            if ((selectedQop&allQop) == 0) {
                throw new SaslException("Client selected unsupported protection: "
                    + selectedQop);
            }
            if ((selectedQop&PRIVACY_PROTECTION) != 0) {
                privacy = true;
                integrity = true;
            } else if ((selectedQop&INTEGRITY_ONLY_PROTECTION) != 0) {
                integrity = true;
            }

            int clntMaxBufSize = networkByteOrderToInt(gssOutToken, 1, 3);

            sendMaxBufSize = (sendMaxBufSize == 0) ? clntMaxBufSize :
                Math.min(sendMaxBufSize, clntMaxBufSize);

            rawSendSize = secCtx.getWrapSizeLimit(JGSS_QOP, privacy,
                sendMaxBufSize);

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
            "KRB5SRV10:Selected protection: {0}; privacy: {1}; integrity: {2}",
                    new Object[]{selectedQop,
                                 Boolean.valueOf(privacy),
                                 Boolean.valueOf(integrity)});
                logger.log(Level.FINE,
"KRB5SRV11:Client max recv size: {0}; server max send size: {1}; rawSendSize: {2}",
                    new Object[] {clntMaxBufSize,
                                  sendMaxBufSize,
                                  rawSendSize});
            }

            if (gssOutToken.length > 4) {
                authzid = new String(gssOutToken, 4,
                        gssOutToken.length - 4, UTF_8);
            } else {
                authzid = peer;
            }
            logger.log(Level.FINE, "KRB5SRV12:Authzid: {0}", authzid);

            AuthorizeCallback acb = new AuthorizeCallback(peer, authzid);

            cbh.handle(new Callback[] {acb});
            if (acb.isAuthorized()) {
                authzid = acb.getAuthorizedID();
                completed = true;
            } else {
                throw new SaslException(peer +
                    " is not authorized to connect as " + authzid);
            }

            return null;
        } catch (GSSException e) {
            throw new SaslException("Final handshake step failed", e);
        } catch (IOException | UnsupportedCallbackException e) {
            throw new SaslException("Problem with callback handler", e);
        }
    }

    public String getAuthorizationID() {
        if (completed) {
            return authzid;
        } else {
            throw new IllegalStateException("Authentication incomplete");
        }
    }

    public Object getNegotiatedProperty(String propName) {
        if (!completed) {
            throw new IllegalStateException("Authentication incomplete");
        }

        Object result;
        switch (propName) {
            case Sasl.BOUND_SERVER_NAME:
                try {
                    result = me.split("[/@]")[1];
                } catch (Exception e) {
                    result = null;
                }
                break;
            default:
                result = super.getNegotiatedProperty(propName);
        }
        return result;
    }
}
