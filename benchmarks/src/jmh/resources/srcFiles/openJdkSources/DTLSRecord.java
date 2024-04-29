/*
 * Copyright (c) 1996, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * DTLS record
 */
interface DTLSRecord extends Record {

    int    headerSize = 13;            

    int    handshakeHeaderSize = 12;   

    /*
     * The size of the header plus the max IV length
     */
    int    headerPlusMaxIVSize =      headerSize        
                                    + maxIVLength;      

    /*
     * The maximum size that may be increased when translating plaintext to
     * ciphertext fragment.
     */
    int    maxPlaintextPlusSize =     headerSize        
                                    + maxIVLength       
                                    + maxMacSize        
                                    + maxPadding;       

    /*
     * the maximum record size
     */
    int    maxRecordSize =            headerPlusMaxIVSize   
                                    + maxDataSize           
                                    + maxPadding            
                                    + maxMacSize;           

    /*
     * Minimum record size of Certificate handshake message.
     * Client sends a certificate message containing no certificates if no
     * suitable certificate is available.  That is, the certificate_list
     * structure has a length of zero.
     *
     *   struct {
     *       ASN.1Cert certificate_list<0..2^24-1>;
     *   } Certificate;
     */
    int    minCertPlaintextSize =     headerSize            
                                    + handshakeHeaderSize   
                                    + 3;                    
}
