/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 *
 * This Java port DoubleArray Trie Structure, precompiled charmap parsing and sentence piece normalizer was derived from
 * Huggingface's spm-precompiled.
 * project at https:
 */

package org.elasticsearch.xpack.ml.inference.nlp.tokenizers;

import com.ibm.icu.text.BreakIterator;

import org.apache.lucene.analysis.charfilter.BaseCharFilter;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;
import org.apache.lucene.util.UnicodeUtil;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * This is custom normalizer logic purpose built to replicate the logic in DoubleArray Trie System (darts)
 * object and the sentence piece normalizer.
 *
 * Links with further explanation of various parts of the algorithm:
 *  - <a href="https:
 *      huggingface lib
 *      </a>
 *  - <a href="https:
 *      DARTS
 *      </a>
 *  - <a href="https:
 *
 *  We implement this as a char filter to take advantage of the underlying offset correction and because normalization needs to occur before
 *  tokenization (just like a charfilter)
 */
public class PrecompiledCharMapNormalizer extends BaseCharFilter {

    record Config(int[] offsets, String utf8str) {}

    static Config fromBase64EncodedResource(String resourcePath) throws IOException {
        byte[] bytes = Base64.getDecoder().wrap(PrecompiledCharMapNormalizer.class.getResourceAsStream(resourcePath)).readAllBytes();
        int offset = 0;
        int trieSize = ByteBuffer.wrap(bytes, offset, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
        offset += 4;
        int size = trieSize / 4;
        int[] offsets = new int[size];
        for (int i = 0; i < size; i++) {
            offsets[i] = ByteBuffer.wrap(bytes, offset, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
            offset += 4;
        }
        String utf8Str = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
        return new Config(offsets, utf8Str);
    }

    private final int[] offsets;
    private final byte[] normalizedStrUtf8Bytes;
    private final char[] reusableCharDecodeBuffer = new char[64];
    private Reader transformedInput;

    public PrecompiledCharMapNormalizer(int[] offsets, String normalizedStr, Reader in) {
        super(in);
        this.offsets = offsets;
        this.normalizedStrUtf8Bytes = normalizedStr.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean hasLeaf(int v) {
        return ((v >>> 8) & 1) == 1;
    }

    private static int label(int v) {
        return (v & ((1 << 31) | 0xFF));
    }

    private static int value(int v) {
        return (v & ((1 << 31) - 1));
    }

    private static int offset(int v) {
        return (v >>> 10) << ((v & (1 << 9)) >>> 6);
    }

    OptionalInt commonPrefix(byte[] inputBytes) {
        return commonPrefix(inputBytes, 0, inputBytes.length);
    }

    /**
     * This finds a common prefix position within the normalization byte string.
     *
     * Since the normalization string is NULL delimited, start at the returned index and continue until you hit the NULL byte. That is
     * then the normalized string.
     *
     * The prefix search is done according to DoubleArray Trie System (DARTS).
     *
     * See:
     * <a href="https:
     *     DARTS
     *     </a>
     * @param inputBytes utf8 bytes to normalize
     * @param offset offset position to start given the input
     * @param len the length of bytes to consider
     * @return The starting position in the normalization string of the normalized bytes, if found.
     */
    private OptionalInt commonPrefix(byte[] inputBytes, int offset, int len) {
        int pos = 0;
        OptionalInt vs = OptionalInt.empty();
        int v = offsets[pos];
        pos ^= offset(v);
        for (int i = offset; i < offset + len; i++) {
            int k = inputBytes[i];
            if (k < 0) {
                k += 256;
            }
            if (k == 0) {
                break;
            }
            pos ^= k;
            v = offsets[pos];
            if (label(v) != k) {
                return vs;
            }
            pos ^= offset(v);
            if (hasLeaf(v)) {
                vs = OptionalInt.of(value(offsets[pos]));
                return vs;
            }
        }
        return vs;
    }

    private Optional<BytesRef> normalizePart(byte[] strBytes, int offset, int len) {
        OptionalInt index = commonPrefix(strBytes, offset, len);
        if (index.isEmpty()) {
            return Optional.empty();
        }
        int firstIndex = index.getAsInt();
        int secondIndex = firstIndex;
        while (secondIndex < normalizedStrUtf8Bytes.length && normalizedStrUtf8Bytes[secondIndex] != 0) {
            secondIndex++;
        }
        if (secondIndex == firstIndex) {
            return Optional.of(new BytesRef(BytesRef.EMPTY_BYTES));
        }
        return Optional.of(new BytesRef(normalizedStrUtf8Bytes, firstIndex, secondIndex - firstIndex));
    }

    Reader normalize(CharSequence str) {
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(str));
        byte[] strBytes = new byte[byteBuffer.limit()];
        byteBuffer.get(strBytes);
        BreakIterator b = BreakIterator.getCharacterInstance(Locale.ROOT);
        b.setText(str);
        int startIter = b.first();
        CharsRefBuilder strBuilder = new CharsRefBuilder();
        strBuilder.grow(strBytes.length);
        int bytePos = 0;
        int normalizedCharPos = 0;
        for (int end = b.next(); end != BreakIterator.DONE; startIter = end, end = b.next()) {
            int byteLen = UnicodeUtil.calcUTF16toUTF8Length(str, startIter, end - startIter);

            if (byteLen < 6) {
                Optional<BytesRef> maybeSubStr = normalizePart(strBytes, bytePos, byteLen);
                if (maybeSubStr.isPresent()) {
                    BytesRef subStr = maybeSubStr.get();
                    int numChars = UnicodeUtil.UTF8toUTF16(subStr.bytes, subStr.offset, subStr.length, reusableCharDecodeBuffer);
                    normalizedCharPos += numChars;
                    if (numChars != end - startIter) {
                        addOffCorrectMap(normalizedCharPos, getLastCumulativeDiff() + end - startIter - numChars);
                    }
                    strBuilder.append(reusableCharDecodeBuffer, 0, numChars);
                    bytePos += byteLen;
                    continue;
                }
            }
            int charByteIndex = 0;
            int i = startIter;
            while (i < end) {
                boolean isSurrogatePair = (i + 1 < end && Character.isSurrogatePair(str.charAt(i), str.charAt(i + 1)));
                int numUtf16Chars = isSurrogatePair ? 2 : 1;

                int utf8CharBytes = UnicodeUtil.calcUTF16toUTF8Length(str, i, numUtf16Chars);
                Optional<BytesRef> maybeSubStr = normalizePart(strBytes, charByteIndex + bytePos, utf8CharBytes);
                if (maybeSubStr.isPresent()) {
                    BytesRef subStr = maybeSubStr.get();
                    int numChars = UnicodeUtil.UTF8toUTF16(subStr.bytes, subStr.offset, subStr.length, reusableCharDecodeBuffer);
                    normalizedCharPos += numChars;
                    if (numChars < 1) {
                        addOffCorrectMap(normalizedCharPos, getLastCumulativeDiff() + 1);
                    } else if (numChars > 1) {
                        addOffCorrectMap(normalizedCharPos, getLastCumulativeDiff() - 1);
                    }
                    strBuilder.append(reusableCharDecodeBuffer, 0, numChars);
                } else {
                    normalizedCharPos += 1;
                    strBuilder.append(str.charAt(i));
                    if (isSurrogatePair) {
                        strBuilder.append(str.charAt(i + 1));
                    }
                }
                charByteIndex += utf8CharBytes;

                i = i + numUtf16Chars;
            }
            bytePos += byteLen;
        }
        return new CharArrayReader(strBuilder.chars(), 0, strBuilder.length());
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        if (transformedInput == null) {
            fill();
        }

        return transformedInput.read(cbuf, off, len);
    }

    @Override
    public int read() throws IOException {
        if (transformedInput == null) {
            fill();
        }

        return transformedInput.read();
    }

    private void fill() throws IOException {
        List<CharSequence> charArrays = new ArrayList<>();
        char[] temp = new char[1024];
        for (int cnt = input.read(temp); cnt > 0; cnt = input.read(temp)) {
            charArrays.add(new CharsRef(Arrays.copyOfRange(temp, 0, cnt), 0, cnt));
        }
        transformedInput = normalize(new MultiCharSequence(charArrays));
    }
}
