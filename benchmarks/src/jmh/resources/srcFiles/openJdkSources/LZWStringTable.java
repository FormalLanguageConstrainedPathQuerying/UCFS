/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.imageio.plugins.common;

import java.io.PrintStream;

/**
 * General purpose LZW String Table.
 * Extracted from GIFEncoder by Adam Doppelt
 * Comments added by Robin Luiten
 * {@code expandCode} added by Robin Luiten
 * The strLen table to give quick access to the length of an expanded
 * code for use by the {@code expandCode} method added by Robin.
 **/
public class LZWStringTable {
    /** codesize + Reserved Codes */
    private static final int RES_CODES = 2;

    private static final short HASH_FREE = (short)0xFFFF;
    private static final short NEXT_FIRST = (short)0xFFFF;

    private static final int MAXBITS = 12;
    private static final int MAXSTR = (1 << MAXBITS);

    private static final short HASHSIZE = 9973;
    private static final short HASHSTEP = 2039;

    byte[]  strChr;  
    short[] strNxt;  
    short[] strHsh;  
    short numStrings;  

    /*
     * each entry corresponds to a code and contains the length of data
     * that the code expands to when decoded.
     */
    int[] strLen;

    /*
     * Constructor allocate memory for string store data
     */
    public LZWStringTable() {
        strChr = new byte[MAXSTR];
        strNxt = new short[MAXSTR];
        strLen = new int[MAXSTR];
        strHsh = new short[HASHSIZE];
    }

    /*
     * @param index value of -1 indicates no predecessor [used in initialisation]
     * @param b the byte [character] to add to the string store which follows
     * the predecessor string specified the index.
     * @return 0xFFFF if no space in table left for addition of predecessor
     * index and byte b. Else return the code allocated for combination index + b.
     */
    public int addCharString(short index, byte b) {
        int hshidx;

        if (numStrings >= MAXSTR) { 
            return 0xFFFF;
        }

        hshidx = hash(index, b);
        while (strHsh[hshidx] != HASH_FREE) {
            hshidx = (hshidx + HASHSTEP) % HASHSIZE;
        }

        strHsh[hshidx] = numStrings;
        strChr[numStrings] = b;
        if (index == HASH_FREE) {
            strNxt[numStrings] = NEXT_FIRST;
            strLen[numStrings] = 1;
        } else {
            strNxt[numStrings] = index;
            strLen[numStrings] = strLen[index] + 1;
        }

        return numStrings++; 
    }

    /*
     * @param index index to prefix string
     * @param b the character that follows the index prefix
     * @return b if param index is HASH_FREE. Else return the code
     * for this prefix and byte successor
     */
    public short findCharString(short index, byte b) {
        int hshidx, nxtidx;

        if (index == HASH_FREE) {
            return (short)(b & 0xFF);    
        }

        hshidx = hash(index, b);
        while ((nxtidx = strHsh[hshidx]) != HASH_FREE) { 
            if (strNxt[nxtidx] == index && strChr[nxtidx] == b) {
                return (short)nxtidx;
            }
            hshidx = (hshidx + HASHSTEP) % HASHSIZE;
        }

        return (short)0xFFFF;
    }

    /*
     * @param codesize the size of code to be preallocated for the
     * string store.
     */
    public void clearTable(int codesize) {
        numStrings = 0;

        for (int q = 0; q < HASHSIZE; q++) {
            strHsh[q] = HASH_FREE;
        }

        int w = (1 << codesize) + RES_CODES;
        for (int q = 0; q < w; q++) {
            addCharString((short)0xFFFF, (byte)q); 
        }
    }

    public static int hash(short index, byte lastbyte) {
        return (((short)(lastbyte << 8) ^ index) & 0xFFFF) % HASHSIZE;
    }

    /*
     * If expanded data doesn't fit into array only what will fit is written
     * to buf and the return value indicates how much of the expanded code has
     * been written to the buf. The next call to expandCode() should be with
     * the same code and have the skip parameter set the negated value of the
     * previous return. Successive negative return values should be negated and
     * added together for next skip parameter value with same code.
     *
     * @param buf buffer to place expanded data into
     * @param offset offset to place expanded data
     * @param code the code to expand to the byte array it represents.
     * PRECONDITION This code must already be in the LZSS
     * @param skipHead is the number of bytes at the start of the expanded code to
     * be skipped before data is written to buf. It is possible that skipHead is
     * equal to codeLen.
     * @return the length of data expanded into buf. If the expanded code is longer
     * than space left in buf then the value returned is a negative number which when
     * negated is equal to the number of bytes that were used of the code being expanded.
     * This negative value also indicates the buffer is full.
     */
    public int expandCode(byte[] buf, int offset, short code, int skipHead) {
        if (offset == -2) {
            if (skipHead == 1) {
                skipHead = 0;
            }
        }
        if (code == (short)0xFFFF ||    
            skipHead == strLen[code])  
        {
            return 0;
        }

        int expandLen;  
        int codeLen = strLen[code] - skipHead; 
        int bufSpace = buf.length - offset;  
        if (bufSpace > codeLen) {
            expandLen = codeLen; 
        } else {
            expandLen = bufSpace;
        }

        int skipTail = codeLen - expandLen;  

        int idx = offset + expandLen;   

        while ((idx > offset) && (code != (short)0xFFFF)) {
            if (--skipTail < 0) { 
                buf[--idx] = strChr[code];
            }
            code = strNxt[code];    
        }

        if (codeLen > expandLen) {
            return -expandLen; 
        } else {
            return expandLen;     
        }
    }

    public void dump(PrintStream out) {
        int i;
        for (i = 258; i < numStrings; ++i) {
            out.println(" strNxt[" + i + "] = " + strNxt[i]
                        + " strChr " + Integer.toHexString(strChr[i] & 0xFF)
                        + " strLen " + Integer.toHexString(strLen[i]));
        }
    }
}
