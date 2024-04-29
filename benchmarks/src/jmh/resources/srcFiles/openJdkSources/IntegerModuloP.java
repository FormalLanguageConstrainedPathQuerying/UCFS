/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util.math;

import sun.security.util.math.intpoly.IntegerPolynomialP256;
import sun.security.util.math.intpoly.P256OrderField;

import java.math.BigInteger;

/**
 * The base interface for integers modulo a prime value. Objects of this
 * type may be either mutable or immutable, and subinterfaces can be used
 * to specify that an object is mutable or immutable. This type should never
 * be used to declare local/member variables, but it may be used for
 * formal parameters of a method. None of the methods in this interface
 * modify the value of arguments or this.
 *
 * The behavior of this interface depends on the particular implementation.
 * For example, some implementations only support a limited number of add
 * operations before each multiply operation. See the documentation of the
 * implementation for details.
 *
 * @see ImmutableIntegerModuloP
 * @see MutableIntegerModuloP
 */
public interface IntegerModuloP {

    /**
     * Get the field associated with this element.
     *
     * @return the field
     */
    IntegerFieldModuloP getField();

    /**
     * Get the canonical value of this element as a BigInteger. This value
     * will always be in the range [0, p), where p is the prime that defines
     * the field. This method performs reduction and other computation to
     * produce the result.
     *
     * @return the value as a BigInteger
     */
    BigInteger asBigInteger();

    /**
     * Return this value as a fixed (immutable) element. This method will
     * copy the underlying representation if the object is mutable.
     *
     * @return a fixed element with the same value
     */
    ImmutableIntegerModuloP fixed();

    /**
     * Return this value as a mutable element. This method will always copy
     * the underlying representation.
     *
     * @return a mutable element with the same value
     */
    MutableIntegerModuloP mutable();

    /**
     * Add this field element with the supplied element and return the result.
     *
     * @param b the sumand
     * @return this + b
     */
    ImmutableIntegerModuloP add(IntegerModuloP b);

    /**
     * Compute the additive inverse of the field element
     * @return the addditiveInverse (0 - this)
     */
    ImmutableIntegerModuloP additiveInverse();

    /**
     * Multiply this field element with the supplied element and return the
     * result.
     *
     * @param b the multiplicand
     * @return this * b
     */
    ImmutableIntegerModuloP multiply(IntegerModuloP b);

    /**
     * Perform an addition modulo a power of two and return the little-endian
     * encoding of the result. The value is (this' + b') % 2^(8 * len),
     * where this' and b' are the canonical integer values equivalent to
     * this and b.
     *
     * @param b the sumand
     * @param len the length of the desired array
     * @return a byte array of length len containing the result
     */
    default byte[] addModPowerTwo(IntegerModuloP b, int len) {
        byte[] result = new byte[len];
        addModPowerTwo(b, result);
        return result;
    }

    /**
     * Perform an addition modulo a power of two and store the little-endian
     * encoding of the result in the supplied array. The value is
     * (this' + b') % 2^(8 * result.length), where this' and b' are the
     * canonical integer values equivalent to this and b.
     *
     * @param b the sumand
     * @param result an array which stores the result upon return
     */
    void addModPowerTwo(IntegerModuloP b, byte[] result);

    /**
     * Returns the little-endian encoding of this' % 2^(8 * len), where this'
     * is the canonical integer value equivalent to this.
     *
     * @param len the length of the desired array
     * @return a byte array of length len containing the result
     */
    default byte[] asByteArray(int len) {
        byte[] result = new byte[len];
        asByteArray(result);
        return result;
    }

    /**
     * Places the little-endian encoding of this' % 2^(8 * result.length)
     * into the supplied array, where this' is the canonical integer value
     * equivalent to this.
     *
     * @param result an array which stores the result upon return
     */
    void asByteArray(byte[] result);

    /**
     * Break encapsulation, used for IntrinsicCandidate functions
     */
    long[] getLimbs();

    /**
     * Compute the multiplicative inverse of this field element.
     *
     * @return the multiplicative inverse (1 / this)
     */
    default ImmutableIntegerModuloP multiplicativeInverse() {
        return MultiplicativeInverser.of(getField().getSize()).inverse(this);
    }

    /**
     * Subtract the supplied element from this one and return the result.
     * @param b the subtrahend
     *
     * @return the difference (this - b)
     */
    default ImmutableIntegerModuloP subtract(IntegerModuloP b) {
        return add(b.additiveInverse());
    }

    /**
     * Calculate the square of this element and return the result. This method
     * should be used instead of a.multiply(a) because implementations may
     * include optimizations that only apply to squaring.
     *
     * @return the product (this * this)
     */
    default ImmutableIntegerModuloP square() {
        return multiply(this);
    }

    /**
     * Calculate the power this^b and return the result.
     *
     * @param b the exponent
     * @return the value of this^b
     */
    default ImmutableIntegerModuloP pow(BigInteger b) {
        MutableIntegerModuloP y = getField().get1().mutable();
        MutableIntegerModuloP x = mutable();
        int bitLength = b.bitLength();
        for (int bit = 0; bit < bitLength; bit++) {
            if (b.testBit(bit)) {
                y.setProduct(x);
            }
            x.setSquare();
        }
        return y.fixed();
    }

    sealed interface MultiplicativeInverser {
        static MultiplicativeInverser of(BigInteger m) {
            if (m.equals(IntegerPolynomialP256.MODULUS)) {
                return Secp256R1.instance;
            } else if (m.equals(P256OrderField.MODULUS)) {
                return Secp256R1Field.instance;
            } else {
                return new Default(m);
            }
        }

        /**
         * Compute the multiplicative inverse of {@code imp}.
         *
         * @return the multiplicative inverse (1 / imp)
         */
        ImmutableIntegerModuloP inverse(IntegerModuloP imp);

        final class Default implements MultiplicativeInverser {
            private final BigInteger b;

            Default(BigInteger b) {
                this.b = b.subtract(BigInteger.TWO);
            }

            @Override
            public ImmutableIntegerModuloP inverse(IntegerModuloP imp) {
                MutableIntegerModuloP y = imp.getField().get1().mutable();
                MutableIntegerModuloP x = imp.mutable();
                int bitLength = b.bitLength();
                for (int bit = 0; bit < bitLength; bit++) {
                    if (b.testBit(bit)) {
                        y.setProduct(x);
                    }
                    x.setSquare();
                }

                return y.fixed();
            }
        }

        final class Secp256R1 implements MultiplicativeInverser {
            private static final Secp256R1 instance = new Secp256R1();

            @Override
            public ImmutableIntegerModuloP inverse(IntegerModuloP imp) {

                MutableIntegerModuloP t = imp.mutable();
                MutableIntegerModuloP v = null;
                MutableIntegerModuloP w = null;
                for (int i = 0; i < 31; i++) {
                    t.setSquare();
                    switch (i) {
                        case 0 -> {
                            t.setProduct(imp);
                            v = t.mutable();    
                        }
                        case 4 -> {
                            t.setProduct(v);
                            w = t.mutable();    
                        }
                        case 12, 28 -> {
                            t.setProduct(w);
                            w = t.mutable();    
                        }
                        case 2, 6, 14, 30 -> {
                            t.setProduct(v);
                        }
                    }
                }


                MutableIntegerModuloP d = t.mutable();
                for (int i = 32; i < 256; i++) {
                    d.setSquare();
                    switch (i) {
                        case 191, 223 -> {
                            d.setProduct(t);
                        }
                        case 253 -> {
                            d.setProduct(w);
                        }
                        case 63, 255 -> {
                            d.setProduct(imp);
                        }
                    }
                }

                return d.fixed();
            }
        }

        final class Secp256R1Field implements MultiplicativeInverser {
            private static final Secp256R1Field instance = new Secp256R1Field();
            private static final BigInteger b =
                    P256OrderField.MODULUS.subtract(BigInteger.TWO);
            @Override
            public ImmutableIntegerModuloP inverse(IntegerModuloP imp) {

                IntegerModuloP[] w = new IntegerModuloP[4];
                w[0] = imp.fixed();
                MutableIntegerModuloP t = imp.mutable();
                for (int i = 1; i < 4; i++) {
                    t.setSquare();
                    t.setProduct(imp);
                    w[i] = t.fixed();
                }

                MutableIntegerModuloP d = null;
                for (int i = 4; i < 32; i++) {
                    t.setSquare();
                    switch (i) {
                        case 7 -> {
                            t.setProduct(w[3]);
                            d = t.mutable();   
                        }
                        case 15 -> {
                            t.setProduct(d);
                            d = t.mutable();   
                        }
                        case 31 -> {
                            t.setProduct(d); 
                        }
                    }
                }

                d = t.mutable();
                for (int i = 32; i < 128; i++) {
                    d.setSquare();
                    if (i == 95 || i == 127) {
                        d.setProduct(t);
                    }
                }

                for (int k = -1, i = 127; i >= 0; i--) {
                    if (b.testBit(i)) {
                        if (k == w.length - 2) {
                            d.setSquare();
                            d.setProduct(w[w.length - 1]);
                            k = -1;
                        } else {
                            k++;
                            d.setSquare();
                        }
                    } else {    
                        if (k >= 0) {
                            d.setProduct(w[k]);
                            k = -1;
                        }
                        d.setSquare();
                    }
                }

                return d.fixed();
            }
        }
    }
}
