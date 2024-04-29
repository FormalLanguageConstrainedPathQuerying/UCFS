/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run main PrimeTest
 * @bug 8026236 8074460 8078672 8294593
 * @summary test primality verification methods in BigInteger (use -Dseed=X to set PRNG seed)
 * @author bpb
 * @key randomness
 */
import java.math.BigInteger;
import java.util.BitSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;
import jdk.test.lib.RandomFactory;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

public class PrimeTest {

    private static final int DEFAULT_UPPER_BOUND = 1299709; 
    private static final int DEFAULT_CERTAINTY = 100;
    private static final int NUM_NON_PRIMES = 10000;

    /**
     * Run the test.
     *
     * @param args The parameters.
     * @throws Exception on failure
     */
    public static void main(String[] args) throws Exception {
        int upperBound = args.length > 0 ? Integer.valueOf(args[0]) : DEFAULT_UPPER_BOUND;
        int certainty = args.length > 1 ? Integer.valueOf(args[1]) : DEFAULT_CERTAINTY;
        boolean parallel = args.length > 2 ? Boolean.valueOf(args[2]) : true;

        System.out.println("Upper bound = " + upperBound
                + "\nCertainty = " + certainty
                + "\nParallel = " + parallel);

        NavigableSet<BigInteger> primes = getPrimes(upperBound);

        boolean primeTest = checkPrime(primes, certainty, parallel);
        System.out.println("Prime test result: " + (primeTest ? "SUCCESS" : "FAILURE"));
        if (!primeTest) {
            System.err.println("Prime test failed");
        }

        boolean nonPrimeTest = checkNonPrime(primes, certainty);
        System.out.println("Non-prime test result: " + (nonPrimeTest ? "SUCCESS" : "FAILURE"));

        boolean mersennePrimeTest = checkMersennePrimes(certainty);
        System.out.println("Mersenne test result: " + (mersennePrimeTest ? "SUCCESS" : "FAILURE"));

        if (!primeTest || !nonPrimeTest || !mersennePrimeTest) {
            throw new Exception("PrimeTest FAILED!");
        }

        if (!checkHugeFails()) {
            throw new Exception("Primality test on huge integer should fail but succeeded");
        }

        System.out.println("PrimeTest succeeded!");
    }

    /**
     * Create a {@code BitSet} wherein a set bit indicates the corresponding
     * index plus 2 is prime. That is, if bit N is set, then the integer N + 2
     * is prime. The values 0 and 1 are intentionally excluded. See the
     * <a
     * href="http:
     * Sieve of Eratosthenes</a> algorithm description for more information.
     *
     * @param upperBound The maximum prime to allow
     * @return bits indicating which indexes represent primes
     */
    private static BitSet createPrimes(int upperBound) {
        int nbits = upperBound - 1;
        BitSet bs = new BitSet(nbits);
        for (int p = 2; p * p < upperBound;) {
            for (int i = p * p; i < nbits + 2; i += p) {
                bs.set(i - 2, true);
            }
            do {
                ++p;
            } while (p > 1 && bs.get(p - 2));
        }
        bs.flip(0, nbits);
        return bs;
    }

    /**
     * Load the primes up to the specified bound (inclusive) into a
     * {@code NavigableSet}, appending the prime {@code Integer.MAX_VALUE}.
     *
     * @param upperBound The maximum prime to allow
     * @return a set of primes
     */
    private static NavigableSet<BigInteger> getPrimes(int upperBound) {
        BitSet bs = createPrimes(upperBound);
        NavigableSet<BigInteger> primes = bs.stream()
                .mapToObj(p -> BigInteger.valueOf(p + 2))
                .collect(toCollection(TreeSet::new));
        primes.add(BigInteger.valueOf(Integer.MAX_VALUE));
        System.out.println(String.format("Created %d primes", primes.size()));
        return primes;
    }

    /**
     * Verifies whether the fraction of probable primes detected is at least 1 -
     * 1/2^certainty.
     *
     * @return true if and only if the test succeeds
     */
    private static boolean checkPrime(Set<BigInteger> primes,
            int certainty,
            boolean parallel) {
        long probablePrimes = (parallel ? primes.parallelStream() : primes.stream())
                .filter(bi -> bi.isProbablePrime(certainty))
                .count();

        BigInteger p = BigInteger.valueOf(probablePrimes);
        BigInteger t = BigInteger.valueOf(primes.size());
        BigInteger fourToTheC = BigInteger.valueOf(4).pow(certainty / 2);
        BigInteger fourToTheCMinusOne = fourToTheC.subtract(BigInteger.ONE);
        BigInteger left = p.multiply(fourToTheC);
        BigInteger right = t.multiply(fourToTheCMinusOne);

        if (left.compareTo(right) < 0) {
            System.err.println("Probable prime certainty test failed");
        }

        return left.compareTo(right) >= 0;
    }

    /**
     * Verifies whether all {@code BigInteger}s in the tested range for which
     * {@code isProbablePrime()} returns {@code false} are <i>not</i>
     * prime numbers.
     *
     * @return true if and only if the test succeeds
     */
    private static boolean checkNonPrime(NavigableSet<BigInteger> primes,
            int certainty) {
        int maxPrime = DEFAULT_UPPER_BOUND;
        try {
            maxPrime = primes.last().intValueExact();
        } catch (ArithmeticException e) {
        }

        SplittableRandom splitRandom = RandomFactory.getSplittableRandom();
        List<BigInteger> nonPrimeBigInts = (splitRandom)
                .ints(NUM_NON_PRIMES, 2, maxPrime).mapToObj(BigInteger::valueOf)
                .filter(b -> !b.isProbablePrime(certainty)).collect(toList());

        boolean failed = nonPrimeBigInts.stream().anyMatch(primes::contains);

        if (failed) {
            for (BigInteger bigInt : nonPrimeBigInts) {
                if (primes.contains(bigInt)) {
                    System.err.println("Prime value thought to be non-prime: " + bigInt);
                }
            }
        }

        return !failed;
    }

    /**
     * Verifies whether a specified subset of Mersenne primes are correctly
     * identified as being prime. See
     * <a href="https:
     * for more information.
     *
     * @return true if and only if the test succeeds
     */
    private static boolean checkMersennePrimes(int certainty) {
        int[] MERSENNE_EXPONENTS = {
            2, 3, 5, 7, 13, 17, 19, 31, 61, 89, 107, 127, 521, 607, 1279, 2203,
            2281, 3217, 4253, 
            /* 4423, 9689, 9941, 11213, 19937, 21701, 23209, 44497,
            86243, 110503, 132049, 216091, 756839, 859433, 1257787, 1398269,
            2976221, 3021377, 6972593, 13466917, 20996011, 24036583, 25964951,
            30402457, 32582657, 37156667, 42643801, 43112609, 57885161 */
        };
        System.out.println("Checking first "+MERSENNE_EXPONENTS.length+" Mersenne primes");

        boolean result = true;
        for (int n : MERSENNE_EXPONENTS) {
            BigInteger mp = BigInteger.ONE.shiftLeft(n).subtract(BigInteger.ONE);
            if (!mp.isProbablePrime(certainty)) {
                System.err.println("Mp with p = "+n+" not classified as prime");
                result = false;
            }
        }

        return result;
    }

    private static boolean checkHugeFails() {
        try {
            BigInteger a = BigInteger.ONE.shiftLeft(500_000_000 + 1)
                    .setBit(0);
            a.isProbablePrime(1);
            return false;
        } catch (ArithmeticException e) {
            return true;
        }
    }

}
