/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.random;

import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import jdk.internal.util.random.RandomSupport;
import jdk.internal.util.random.RandomSupport.AbstractSplittableWithBrineGenerator;
import jdk.internal.util.random.RandomSupport.RandomGeneratorProperties;

/**
 * A "splittable" pseudorandom number generator (PRNG) whose period
 * is roughly 2<sup>1152</sup>.  Class {@link L128X1024MixRandom} implements
 * interfaces {@link RandomGenerator} and {@link SplittableGenerator},
 * and therefore supports methods for producing pseudorandomly chosen
 * values of type {@code int}, {@code long}, {@code float}, {@code double},
 * and {@code boolean} (and for producing streams of pseudorandomly chosen
 * numbers of type {@code int}, {@code long}, and {@code double}),
 * as well as methods for creating new split-off {@link L128X1024MixRandom}
 * objects or streams of such objects.
 *
 * <p>The {@link L128X1024MixRandom} algorithm is a specific member of
 * the LXM family of algorithms for pseudorandom number generators;
 * for more information, see the documentation for package
 * {@link jdk.random}.  Each instance of {@link L128X1024MixRandom}
 * has 1152 bits of state plus one 128-bit instance-specific parameter.
 *
 * <p>If two instances of {@link L128X1024MixRandom} are created with
 * the same seed within the same program execution, and the same
 * sequence of method calls is made for each, they will generate and
 * return identical sequences of values.
 *
 * <p>As with {@link java.util.SplittableRandom}, instances of
 * {@link L128X1024MixRandom} are <em>not</em> thread-safe.  They are
 * designed to be split, not shared, across threads (see the {@link #split}
 * method). For example, a {@link java.util.concurrent.ForkJoinTask}
 * fork/join-style computation using random numbers might include a
 * construction of the form
 * {@code new Subtask(someL128X1024MixRandom.split()).fork()}.
 *
 * <p>This class provides additional methods for generating random
 * streams, that employ the above techniques when used in
 * {@code stream.parallel()} mode.
 *
 * <p>Instances of {@link L128X1024MixRandom} are not cryptographically
 * secure.  Consider instead using {@link java.security.SecureRandom}
 * in security-sensitive applications. Additionally,
 * default-constructed instances do not use a cryptographically random
 * seed unless the {@linkplain System#getProperty system property}
 * {@code java.util.secureRandomSeed} is set to {@code true}.
 *
 * @since   17
 *
 */
@RandomGeneratorProperties(
        name = "L128X1024MixRandom",
        group = "LXM",
        i = 1024, j = 1, k = 128,
        equidistribution = 1
)
public final class L128X1024MixRandom extends AbstractSplittableWithBrineGenerator {

    /*
     * Implementation Overview.
     *
     * The 128-bit parameter `a` is represented as two long fields `ah` and `al`.
     * The 128-bit state variable `s` is represented as two long fields `sh` and `sl`.
     *
     * The split operation uses the current generator to choose 20
     * new 64-bit long values that are then used to initialize the
     * parameters `ah` and `al`, the state variables `sh`, `sl`,
     * and the array `x` for a newly constructed generator.
     *
     * With extremely high probability, no two generators so chosen
     * will have the same `a` parameter, and testing has indicated
     * that the values generated by two instances of {@link L128X1024MixRandom}
     * will be (approximately) independent if have different values for `a`.
     *
     * The default (no-argument) constructor, in essence, uses
     * "defaultGen" to generate 20 new 64-bit values for the same
     * purpose.  Multiple generators created in this way will certainly
     * differ in their `a` parameters.  The defaultGen state must be accessed
     * in a thread-safe manner, so we use an AtomicLong to represent
     * this state.  To bootstrap the defaultGen, we start off using a
     * seed based on current time unless the
     * java.util.secureRandomSeed property is set. This serves as a
     * slimmed-down (and insecure) variant of SecureRandom that also
     * avoids stalls that may occur when using /dev/random.
     *
     * File organization: First static fields, then instance
     * fields, then constructors, then instance methods.
     */

    /* ---------------- static fields ---------------- */

    /*
     * The length of the array x.
     */

    private static final int N = 16;

    /**
     * The seed generator for default constructors.
     */
    private static final AtomicLong defaultGen = new AtomicLong(RandomSupport.initialSeed());

    /*
     * Low half of multiplier used in the LCG portion of the algorithm;
     * the overall multiplier is (2**64 + ML).
     * Chosen based on research by Sebastiano Vigna and Guy Steele (2019).
     * The spectral scores for dimensions 2 through 8 for the multiplier 0x1d605bbb58c8abbfdLL
     * are [0.991889, 0.907938, 0.830964, 0.837980, 0.780378, 0.797464, 0.761493].
     */

    private static final long ML = 0xd605bbb58c8abbfdL;

    /* ---------------- instance fields ---------------- */

    /**
     * The parameter that is used as an additive constant for the LCG.
     * Must be odd (therefore al must be odd).
     */
    private final long ah, al;

    /**
     * The per-instance state: sh and sl for the LCG; the array x for the XBG;
     * p is the rotating pointer into the array x.
     * At least one of the 16 elements of the array x must be nonzero.
     */
    private long sh, sl;
    private final long[] x;
    private int p = N - 1;

    /* ---------------- constructors ---------------- */

    /**
     * Basic constructor that initializes all fields from parameters.
     * It then adjusts the field values if necessary to ensure that
     * all constraints on the values of fields are met.
     *
     * @param ah high half of the additive parameter for the LCG
     * @param al low half of the additive parameter for the LCG
     * @param sh high half of the initial state for the LCG
     * @param sl low half of the initial state for the LCG
     * @param x0 first word of the initial state for the XBG
     * @param x1 second word of the initial state for the XBG
     * @param x2 third word of the initial state for the XBG
     * @param x3 fourth word of the initial state for the XBG
     * @param x4 fifth word of the initial state for the XBG
     * @param x5 sixth word of the initial state for the XBG
     * @param x6 seventh word of the initial state for the XBG
     * @param x7 eight word of the initial state for the XBG
     * @param x8 ninth word of the initial state for the XBG
     * @param x9 tenth word of the initial state for the XBG
     * @param x10 eleventh word of the initial state for the XBG
     * @param x11 twelfth word of the initial state for the XBG
     * @param x12 thirteenth word of the initial state for the XBG
     * @param x13 fourteenth word of the initial state for the XBG
     * @param x14 fifteenth word of the initial state for the XBG
     * @param x15 sixteenth word of the initial state for the XBG
     */
    public L128X1024MixRandom(long ah, long al, long sh, long sl,
                 long x0, long x1, long x2, long x3,
                 long x4, long x5, long x6, long x7,
                 long x8, long x9, long x10, long x11,
                 long x12, long x13, long x14, long x15) {
   this.ah = ah;
        this.al = al | 1;
        this.sh = sh;
        this.sl = sl;
        this.x = new long[N];
        this.x[0] = x0;
        this.x[1] = x1;
        this.x[2] = x2;
        this.x[3] = x3;
        this.x[4] = x4;
        this.x[5] = x5;
        this.x[6] = x6;
        this.x[7] = x7;
        this.x[8] = x8;
        this.x[9] = x9;
        this.x[10] = x10;
        this.x[11] = x11;
        this.x[12] = x12;
        this.x[13] = x13;
        this.x[14] = x14;
        this.x[15] = x15;
        if ((x0 | x1 | x2 | x3 | x4 | x5 | x6 | x7 | x8 | x9 | x10 | x11 | x12 | x13 | x14 | x15) == 0) {
       long v = sh;
            for (int j = 0; j < N; j++) {
                this.x[j] = RandomSupport.mixStafford13(v += RandomSupport.GOLDEN_RATIO_64);
            }
        }
    }

    /**
     * Creates a new instance of {@link L128X1024MixRandom} using the
     * specified {@code long} value as the initial seed. Instances of
     * {@link L128X1024MixRandom} created with the same seed in the same
     * program execution generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public L128X1024MixRandom(long seed) {
        this(RandomSupport.mixMurmur64(seed ^= RandomSupport.SILVER_RATIO_64),
             RandomSupport.mixMurmur64(seed += RandomSupport.GOLDEN_RATIO_64),
             0,
             1,
             RandomSupport.mixStafford13(seed),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed + RandomSupport.GOLDEN_RATIO_64));
    }

    /**
     * Creates a new instance of {@link L128X1024MixRandom} that is likely to
     * generate sequences of values that are statistically independent
     * of those of any other instances in the current program execution,
     * but may, and typically does, vary across program invocations.
     */
    public L128X1024MixRandom() {
        this(defaultGen.getAndAdd(RandomSupport.GOLDEN_RATIO_64));
    }

    /**
     * Creates a new instance of {@link L128X1024MixRandom} using the specified array of
     * initial seed bytes. Instances of {@link L128X1024MixRandom} created with the same
     * seed array in the same program execution generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public L128X1024MixRandom(byte[] seed) {
        long[] data = RandomSupport.convertSeedBytesToLongs(seed, 20, 16);
   long ah = data[0], al = data[1], sh = data[2], sl = data[3];
        this.ah = ah;
        this.al = al | 1;
        this.sh = sh;
        this.sl = sl;
        this.x = new long[N];
        for (int j = 0; j < N; j++) {
            this.x[j] = data[4+j];
        }
    }

    /* ---------------- public methods ---------------- */

    @Override
    public SplittableGenerator split(SplittableGenerator source, long brine) {
        return new L128X1024MixRandom(source.nextLong(), brine << 1,
                     source.nextLong(), source.nextLong(),
                     source.nextLong(), source.nextLong(),
                     source.nextLong(), source.nextLong(),
                     source.nextLong(), source.nextLong(),
                     source.nextLong(), source.nextLong(),
                     source.nextLong(), source.nextLong(),
                     source.nextLong(), source.nextLong(),
                     source.nextLong(), source.nextLong(),
                     source.nextLong(), source.nextLong());
    }

    @Override
    public long nextLong() {
        final int q = p;
        final long s0 = x[p = (p + 1) & (N - 1)];
        long s15 = x[q];

        final long result = RandomSupport.mixLea64(sh + s0);

        final long u = ML * sl;
        sh = (ML * sh) + Math.unsignedMultiplyHigh(ML, sl) + sl + ah;
        sl = u + al;
        if (Long.compareUnsigned(sl, u) < 0) ++sh;  

        s15 ^= s0;
        x[q] = Long.rotateLeft(s0, 25) ^ s15 ^ (s15 << 27);
        x[p] = Long.rotateLeft(s15, 36);

        return result;
    }

}
