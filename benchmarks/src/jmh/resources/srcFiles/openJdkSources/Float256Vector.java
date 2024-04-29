/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.vector;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.vector.VectorSupport;

import static jdk.internal.vm.vector.VectorSupport.*;

import static jdk.incubator.vector.VectorOperators.*;


@SuppressWarnings("cast")  
final class Float256Vector extends FloatVector {
    static final FloatSpecies VSPECIES =
        (FloatSpecies) FloatVector.SPECIES_256;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<Float256Vector> VCLASS = Float256Vector.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); 

    static final Class<Float> ETYPE = float.class; 

    Float256Vector(float[] v) {
        super(v);
    }

    Float256Vector(Object v) {
        this((float[]) v);
    }

    static final Float256Vector ZERO = new Float256Vector(new float[VLENGTH]);
    static final Float256Vector IOTA = new Float256Vector(VSPECIES.iotaArray());

    static {
        VSPECIES.dummyVector();
        VSPECIES.withLanes(LaneType.BYTE);
    }


    @ForceInline
    final @Override
    public FloatSpecies vspecies() {
        return VSPECIES;
    }

    @ForceInline
    @Override
    public final Class<Float> elementType() { return float.class; }

    @ForceInline
    @Override
    public final int elementSize() { return Float.SIZE; }

    @ForceInline
    @Override
    public final VectorShape shape() { return VSHAPE; }

    @ForceInline
    @Override
    public final int length() { return VLENGTH; }

    @ForceInline
    @Override
    public final int bitSize() { return VSIZE; }

    @ForceInline
    @Override
    public final int byteSize() { return VSIZE / Byte.SIZE; }

    /*package-private*/
    @ForceInline
    final @Override
    float[] vec() {
        return (float[])getPayload();
    }


    @Override
    @ForceInline
    public final Float256Vector broadcast(float e) {
        return (Float256Vector) super.broadcastTemplate(e);  
    }

    @Override
    @ForceInline
    public final Float256Vector broadcast(long e) {
        return (Float256Vector) super.broadcastTemplate(e);  
    }

    @Override
    @ForceInline
    Float256Mask maskFromArray(boolean[] bits) {
        return new Float256Mask(bits);
    }

    @Override
    @ForceInline
    Float256Shuffle iotaShuffle() { return Float256Shuffle.IOTA; }

    @ForceInline
    Float256Shuffle iotaShuffle(int start, int step, boolean wrap) {
      if (wrap) {
        return (Float256Shuffle)VectorSupport.shuffleIota(ETYPE, Float256Shuffle.class, VSPECIES, VLENGTH, start, step, 1,
                (l, lstart, lstep, s) -> s.shuffleFromOp(i -> (VectorIntrinsics.wrapToRange(i*lstep + lstart, l))));
      } else {
        return (Float256Shuffle)VectorSupport.shuffleIota(ETYPE, Float256Shuffle.class, VSPECIES, VLENGTH, start, step, 0,
                (l, lstart, lstep, s) -> s.shuffleFromOp(i -> (i*lstep + lstart)));
      }
    }

    @Override
    @ForceInline
    Float256Shuffle shuffleFromBytes(byte[] reorder) { return new Float256Shuffle(reorder); }

    @Override
    @ForceInline
    Float256Shuffle shuffleFromArray(int[] indexes, int i) { return new Float256Shuffle(indexes, i); }

    @Override
    @ForceInline
    Float256Shuffle shuffleFromOp(IntUnaryOperator fn) { return new Float256Shuffle(fn); }

    @ForceInline
    final @Override
    Float256Vector vectorFactory(float[] vec) {
        return new Float256Vector(vec);
    }

    @ForceInline
    final @Override
    Byte256Vector asByteVectorRaw() {
        return (Byte256Vector) super.asByteVectorRawTemplate();  
    }

    @ForceInline
    final @Override
    AbstractVector<?> asVectorRaw(LaneType laneType) {
        return super.asVectorRawTemplate(laneType);  
    }


    @ForceInline
    final @Override
    Float256Vector uOp(FUnOp f) {
        return (Float256Vector) super.uOpTemplate(f);  
    }

    @ForceInline
    final @Override
    Float256Vector uOp(VectorMask<Float> m, FUnOp f) {
        return (Float256Vector)
            super.uOpTemplate((Float256Mask)m, f);  
    }


    @ForceInline
    final @Override
    Float256Vector bOp(Vector<Float> v, FBinOp f) {
        return (Float256Vector) super.bOpTemplate((Float256Vector)v, f);  
    }

    @ForceInline
    final @Override
    Float256Vector bOp(Vector<Float> v,
                     VectorMask<Float> m, FBinOp f) {
        return (Float256Vector)
            super.bOpTemplate((Float256Vector)v, (Float256Mask)m,
                              f);  
    }


    @ForceInline
    final @Override
    Float256Vector tOp(Vector<Float> v1, Vector<Float> v2, FTriOp f) {
        return (Float256Vector)
            super.tOpTemplate((Float256Vector)v1, (Float256Vector)v2,
                              f);  
    }

    @ForceInline
    final @Override
    Float256Vector tOp(Vector<Float> v1, Vector<Float> v2,
                     VectorMask<Float> m, FTriOp f) {
        return (Float256Vector)
            super.tOpTemplate((Float256Vector)v1, (Float256Vector)v2,
                              (Float256Mask)m, f);  
    }

    @ForceInline
    final @Override
    float rOp(float v, VectorMask<Float> m, FBinOp f) {
        return super.rOpTemplate(v, m, f);  
    }

    @Override
    @ForceInline
    public final <F>
    Vector<F> convertShape(VectorOperators.Conversion<Float,F> conv,
                           VectorSpecies<F> rsp, int part) {
        return super.convertShapeTemplate(conv, rsp, part);  
    }

    @Override
    @ForceInline
    public final <F>
    Vector<F> reinterpretShape(VectorSpecies<F> toSpecies, int part) {
        return super.reinterpretShapeTemplate(toSpecies, part);  
    }



    @Override
    @ForceInline
    public Float256Vector lanewise(Unary op) {
        return (Float256Vector) super.lanewiseTemplate(op);  
    }

    @Override
    @ForceInline
    public Float256Vector lanewise(Unary op, VectorMask<Float> m) {
        return (Float256Vector) super.lanewiseTemplate(op, Float256Mask.class, (Float256Mask) m);  
    }

    @Override
    @ForceInline
    public Float256Vector lanewise(Binary op, Vector<Float> v) {
        return (Float256Vector) super.lanewiseTemplate(op, v);  
    }

    @Override
    @ForceInline
    public Float256Vector lanewise(Binary op, Vector<Float> v, VectorMask<Float> m) {
        return (Float256Vector) super.lanewiseTemplate(op, Float256Mask.class, v, (Float256Mask) m);  
    }


    /*package-private*/
    @Override
    @ForceInline
    public final
    Float256Vector
    lanewise(Ternary op, Vector<Float> v1, Vector<Float> v2) {
        return (Float256Vector) super.lanewiseTemplate(op, v1, v2);  
    }

    @Override
    @ForceInline
    public final
    Float256Vector
    lanewise(Ternary op, Vector<Float> v1, Vector<Float> v2, VectorMask<Float> m) {
        return (Float256Vector) super.lanewiseTemplate(op, Float256Mask.class, v1, v2, (Float256Mask) m);  
    }

    @Override
    @ForceInline
    public final
    Float256Vector addIndex(int scale) {
        return (Float256Vector) super.addIndexTemplate(scale);  
    }


    @Override
    @ForceInline
    public final float reduceLanes(VectorOperators.Associative op) {
        return super.reduceLanesTemplate(op);  
    }

    @Override
    @ForceInline
    public final float reduceLanes(VectorOperators.Associative op,
                                    VectorMask<Float> m) {
        return super.reduceLanesTemplate(op, Float256Mask.class, (Float256Mask) m);  
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op) {
        return (long) super.reduceLanesTemplate(op);  
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op,
                                        VectorMask<Float> m) {
        return (long) super.reduceLanesTemplate(op, Float256Mask.class, (Float256Mask) m);  
    }

    @ForceInline
    public VectorShuffle<Float> toShuffle() {
        return super.toShuffleTemplate(Float256Shuffle.class); 
    }


    @Override
    @ForceInline
    public final Float256Mask test(Test op) {
        return super.testTemplate(Float256Mask.class, op);  
    }

    @Override
    @ForceInline
    public final Float256Mask test(Test op, VectorMask<Float> m) {
        return super.testTemplate(Float256Mask.class, op, (Float256Mask) m);  
    }


    @Override
    @ForceInline
    public final Float256Mask compare(Comparison op, Vector<Float> v) {
        return super.compareTemplate(Float256Mask.class, op, v);  
    }

    @Override
    @ForceInline
    public final Float256Mask compare(Comparison op, float s) {
        return super.compareTemplate(Float256Mask.class, op, s);  
    }

    @Override
    @ForceInline
    public final Float256Mask compare(Comparison op, long s) {
        return super.compareTemplate(Float256Mask.class, op, s);  
    }

    @Override
    @ForceInline
    public final Float256Mask compare(Comparison op, Vector<Float> v, VectorMask<Float> m) {
        return super.compareTemplate(Float256Mask.class, op, v, (Float256Mask) m);
    }


    @Override
    @ForceInline
    public Float256Vector blend(Vector<Float> v, VectorMask<Float> m) {
        return (Float256Vector)
            super.blendTemplate(Float256Mask.class,
                                (Float256Vector) v,
                                (Float256Mask) m);  
    }

    @Override
    @ForceInline
    public Float256Vector slice(int origin, Vector<Float> v) {
        return (Float256Vector) super.sliceTemplate(origin, v);  
    }

    @Override
    @ForceInline
    public Float256Vector slice(int origin) {
        return (Float256Vector) super.sliceTemplate(origin);  
    }

    @Override
    @ForceInline
    public Float256Vector unslice(int origin, Vector<Float> w, int part) {
        return (Float256Vector) super.unsliceTemplate(origin, w, part);  
    }

    @Override
    @ForceInline
    public Float256Vector unslice(int origin, Vector<Float> w, int part, VectorMask<Float> m) {
        return (Float256Vector)
            super.unsliceTemplate(Float256Mask.class,
                                  origin, w, part,
                                  (Float256Mask) m);  
    }

    @Override
    @ForceInline
    public Float256Vector unslice(int origin) {
        return (Float256Vector) super.unsliceTemplate(origin);  
    }

    @Override
    @ForceInline
    public Float256Vector rearrange(VectorShuffle<Float> s) {
        return (Float256Vector)
            super.rearrangeTemplate(Float256Shuffle.class,
                                    (Float256Shuffle) s);  
    }

    @Override
    @ForceInline
    public Float256Vector rearrange(VectorShuffle<Float> shuffle,
                                  VectorMask<Float> m) {
        return (Float256Vector)
            super.rearrangeTemplate(Float256Shuffle.class,
                                    Float256Mask.class,
                                    (Float256Shuffle) shuffle,
                                    (Float256Mask) m);  
    }

    @Override
    @ForceInline
    public Float256Vector rearrange(VectorShuffle<Float> s,
                                  Vector<Float> v) {
        return (Float256Vector)
            super.rearrangeTemplate(Float256Shuffle.class,
                                    (Float256Shuffle) s,
                                    (Float256Vector) v);  
    }

    @Override
    @ForceInline
    public Float256Vector compress(VectorMask<Float> m) {
        return (Float256Vector)
            super.compressTemplate(Float256Mask.class,
                                   (Float256Mask) m);  
    }

    @Override
    @ForceInline
    public Float256Vector expand(VectorMask<Float> m) {
        return (Float256Vector)
            super.expandTemplate(Float256Mask.class,
                                   (Float256Mask) m);  
    }

    @Override
    @ForceInline
    public Float256Vector selectFrom(Vector<Float> v) {
        return (Float256Vector)
            super.selectFromTemplate((Float256Vector) v);  
    }

    @Override
    @ForceInline
    public Float256Vector selectFrom(Vector<Float> v,
                                   VectorMask<Float> m) {
        return (Float256Vector)
            super.selectFromTemplate((Float256Vector) v,
                                     (Float256Mask) m);  
    }


    @ForceInline
    @Override
    public float lane(int i) {
        int bits;
        switch(i) {
            case 0: bits = laneHelper(0); break;
            case 1: bits = laneHelper(1); break;
            case 2: bits = laneHelper(2); break;
            case 3: bits = laneHelper(3); break;
            case 4: bits = laneHelper(4); break;
            case 5: bits = laneHelper(5); break;
            case 6: bits = laneHelper(6); break;
            case 7: bits = laneHelper(7); break;
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return Float.intBitsToFloat(bits);
    }

    public int laneHelper(int i) {
        return (int) VectorSupport.extract(
                     VCLASS, ETYPE, VLENGTH,
                     this, i,
                     (vec, ix) -> {
                     float[] vecarr = vec.vec();
                     return (long)Float.floatToIntBits(vecarr[ix]);
                     });
    }

    @ForceInline
    @Override
    public Float256Vector withLane(int i, float e) {
        switch(i) {
            case 0: return withLaneHelper(0, e);
            case 1: return withLaneHelper(1, e);
            case 2: return withLaneHelper(2, e);
            case 3: return withLaneHelper(3, e);
            case 4: return withLaneHelper(4, e);
            case 5: return withLaneHelper(5, e);
            case 6: return withLaneHelper(6, e);
            case 7: return withLaneHelper(7, e);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    public Float256Vector withLaneHelper(int i, float e) {
        return VectorSupport.insert(
                                VCLASS, ETYPE, VLENGTH,
                                this, i, (long)Float.floatToIntBits(e),
                                (v, ix, bits) -> {
                                    float[] res = v.vec().clone();
                                    res[ix] = Float.intBitsToFloat((int)bits);
                                    return v.vectorFactory(res);
                                });
    }


    static final class Float256Mask extends AbstractMask<Float> {
        static final int VLENGTH = VSPECIES.laneCount();    
        static final Class<Float> ETYPE = float.class; 

        Float256Mask(boolean[] bits) {
            this(bits, 0);
        }

        Float256Mask(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        Float256Mask(boolean val) {
            super(prepare(val));
        }

        private static boolean[] prepare(boolean[] bits, int offset) {
            boolean[] newBits = new boolean[VSPECIES.laneCount()];
            for (int i = 0; i < newBits.length; i++) {
                newBits[i] = bits[offset + i];
            }
            return newBits;
        }

        private static boolean[] prepare(boolean val) {
            boolean[] bits = new boolean[VSPECIES.laneCount()];
            Arrays.fill(bits, val);
            return bits;
        }

        @ForceInline
        final @Override
        public FloatSpecies vspecies() {
            return VSPECIES;
        }

        @ForceInline
        boolean[] getBits() {
            return (boolean[])getPayload();
        }

        @Override
        Float256Mask uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new Float256Mask(res);
        }

        @Override
        Float256Mask bOp(VectorMask<Float> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((Float256Mask)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new Float256Mask(res);
        }

        @ForceInline
        @Override
        public final
        Float256Vector toVector() {
            return (Float256Vector) super.toVectorTemplate();  
        }

        /**
         * Helper function for lane-wise mask conversions.
         * This function kicks in after intrinsic failure.
         */
        @ForceInline
        private final <E>
        VectorMask<E> defaultMaskCast(AbstractSpecies<E> dsp) {
            if (length() != dsp.laneCount())
                throw new IllegalArgumentException("VectorMask length and species length differ");
            boolean[] maskArray = toArray();
            return  dsp.maskFactory(maskArray).check(dsp);
        }

        @Override
        @ForceInline
        public <E> VectorMask<E> cast(VectorSpecies<E> dsp) {
            AbstractSpecies<E> species = (AbstractSpecies<E>) dsp;
            if (length() != species.laneCount())
                throw new IllegalArgumentException("VectorMask length and species length differ");

            return VectorSupport.convert(VectorSupport.VECTOR_OP_CAST,
                this.getClass(), ETYPE, VLENGTH,
                species.maskType(), species.elementType(), VLENGTH,
                this, species,
                (m, s) -> s.maskFactory(m.toArray()).check(s));
        }

        @Override
        @ForceInline
        /*package-private*/
        Float256Mask indexPartiallyInUpperRange(long offset, long limit) {
            return (Float256Mask) VectorSupport.indexPartiallyInUpperRange(
                Float256Mask.class, float.class, VLENGTH, offset, limit,
                (o, l) -> (Float256Mask) TRUE_MASK.indexPartiallyInRange(o, l));
        }


        @Override
        @ForceInline
        public Float256Mask not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public Float256Mask compress() {
            return (Float256Mask)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                Float256Vector.class, Float256Mask.class, ETYPE, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }



        @Override
        @ForceInline
        public Float256Mask and(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            Float256Mask m = (Float256Mask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, Float256Mask.class, null, int.class, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public Float256Mask or(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            Float256Mask m = (Float256Mask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, Float256Mask.class, null, int.class, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public Float256Mask xor(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            Float256Mask m = (Float256Mask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, Float256Mask.class, null, int.class, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }


        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, Float256Mask.class, int.class, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, Float256Mask.class, int.class, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, Float256Mask.class, int.class, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, Float256Mask.class, int.class, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }


        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(Float256Mask.class, float.class, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }


        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, Float256Mask.class, int.class, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((Float256Mask)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, Float256Mask.class, int.class, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((Float256Mask)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static Float256Mask maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(Float256Mask.class, int.class, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final Float256Mask  TRUE_MASK = new Float256Mask(true);
        private static final Float256Mask FALSE_MASK = new Float256Mask(false);

    }


    static final class Float256Shuffle extends AbstractShuffle<Float> {
        static final int VLENGTH = VSPECIES.laneCount();    
        static final Class<Float> ETYPE = float.class; 

        Float256Shuffle(byte[] reorder) {
            super(VLENGTH, reorder);
        }

        public Float256Shuffle(int[] reorder) {
            super(VLENGTH, reorder);
        }

        public Float256Shuffle(int[] reorder, int i) {
            super(VLENGTH, reorder, i);
        }

        public Float256Shuffle(IntUnaryOperator fn) {
            super(VLENGTH, fn);
        }

        @Override
        public FloatSpecies vspecies() {
            return VSPECIES;
        }

        static {
            assert(VLENGTH < Byte.MAX_VALUE);
            assert(Byte.MIN_VALUE <= -VLENGTH);
        }
        static final Float256Shuffle IOTA = new Float256Shuffle(IDENTITY);

        @Override
        @ForceInline
        public Float256Vector toVector() {
            return VectorSupport.shuffleToVector(VCLASS, ETYPE, Float256Shuffle.class, this, VLENGTH,
                                                    (s) -> ((Float256Vector)(((AbstractShuffle<Float>)(s)).toVectorTemplate())));
        }

        @Override
        @ForceInline
        public <F> VectorShuffle<F> cast(VectorSpecies<F> s) {
            AbstractSpecies<F> species = (AbstractSpecies<F>) s;
            if (length() != species.laneCount())
                throw new IllegalArgumentException("VectorShuffle length and species length differ");
            int[] shuffleArray = toArray();
            return s.shuffleFromArray(shuffleArray, 0).check(s);
        }

        @ForceInline
        @Override
        public Float256Shuffle rearrange(VectorShuffle<Float> shuffle) {
            Float256Shuffle s = (Float256Shuffle) shuffle;
            byte[] reorder1 = reorder();
            byte[] reorder2 = s.reorder();
            byte[] r = new byte[reorder1.length];
            for (int i = 0; i < reorder1.length; i++) {
                int ssi = reorder2[i];
                r[i] = reorder1[ssi];  
            }
            return new Float256Shuffle(r);
        }
    }



    @ForceInline
    @Override
    final
    FloatVector fromArray0(float[] a, int offset) {
        return super.fromArray0Template(a, offset);  
    }

    @ForceInline
    @Override
    final
    FloatVector fromArray0(float[] a, int offset, VectorMask<Float> m, int offsetInRange) {
        return super.fromArray0Template(Float256Mask.class, a, offset, (Float256Mask) m, offsetInRange);  
    }

    @ForceInline
    @Override
    final
    FloatVector fromArray0(float[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float> m) {
        return super.fromArray0Template(Float256Mask.class, a, offset, indexMap, mapOffset, (Float256Mask) m);
    }



    @ForceInline
    @Override
    final
    FloatVector fromMemorySegment0(MemorySegment ms, long offset) {
        return super.fromMemorySegment0Template(ms, offset);  
    }

    @ForceInline
    @Override
    final
    FloatVector fromMemorySegment0(MemorySegment ms, long offset, VectorMask<Float> m, int offsetInRange) {
        return super.fromMemorySegment0Template(Float256Mask.class, ms, offset, (Float256Mask) m, offsetInRange);  
    }

    @ForceInline
    @Override
    final
    void intoArray0(float[] a, int offset) {
        super.intoArray0Template(a, offset);  
    }

    @ForceInline
    @Override
    final
    void intoArray0(float[] a, int offset, VectorMask<Float> m) {
        super.intoArray0Template(Float256Mask.class, a, offset, (Float256Mask) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(float[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float> m) {
        super.intoArray0Template(Float256Mask.class, a, offset, indexMap, mapOffset, (Float256Mask) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Float> m) {
        super.intoMemorySegment0Template(Float256Mask.class, ms, offset, (Float256Mask) m);
    }




}

