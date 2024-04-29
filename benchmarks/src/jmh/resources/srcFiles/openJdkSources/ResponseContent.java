/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Implements chunked/fixed transfer encodings of HTTP/1.1 responses.
 *
 * Call pushBody() to read the body (blocking). Data and errors are provided
 * to given Consumers. After final buffer delivered, empty optional delivered
 */
class ResponseContent {

    final HttpResponse.BodySubscriber<?> pusher;
    final long contentLength;
    final HttpHeaders headers;
    private final Runnable onFinished;
    private final String dbgTag;

    ResponseContent(HttpConnection connection,
                    long contentLength,
                    HttpHeaders h,
                    HttpResponse.BodySubscriber<?> userSubscriber,
                    Runnable onFinished)
    {
        this.pusher = userSubscriber;
        this.contentLength = contentLength;
        this.headers = h;
        this.onFinished = onFinished;
        this.dbgTag = connection.dbgString() + "/ResponseContent";
    }

    static final int LF = 10;
    static final int CR = 13;

    private boolean chunkedContent, chunkedContentInitialized;

    boolean contentChunked() throws IOException {
        if (chunkedContentInitialized) {
            return chunkedContent;
        }
        if (contentLength == -2) {
            chunkedContentInitialized = true;
            chunkedContent = false;
            return chunkedContent;
        }
        if (contentLength == -1) {
            String tc = headers.firstValue("Transfer-Encoding")
                               .orElse("");
            if (!tc.isEmpty()) {
                if (tc.equalsIgnoreCase("chunked")) {
                    chunkedContent = true;
                } else {
                    throw new IOException("invalid content");
                }
            } else {
                chunkedContent = false;
            }
        }
        chunkedContentInitialized = true;
        return chunkedContent;
    }

    interface BodyParser extends Consumer<ByteBuffer> {
        void onSubscribe(AbstractSubscription sub);
        String currentStateMessage();
        void onError(Throwable t);
    }

    BodyParser getBodyParser(Consumer<Throwable> onComplete)
        throws IOException {
        if (contentChunked()) {
            return new ChunkedBodyParser(onComplete);
        } else {
            return contentLength == -2
                ? new UnknownLengthBodyParser(onComplete)
                : new FixedLengthBodyParser(contentLength, onComplete);
        }
    }


    static enum ChunkState {READING_LENGTH, READING_DATA, DONE}
    static final int MAX_CHUNK_HEADER_SIZE = 2050;
    class ChunkedBodyParser implements BodyParser {
        final ByteBuffer READMORE = Utils.EMPTY_BYTEBUFFER;
        final Consumer<Throwable> onComplete;
        final Logger debug = Utils.getDebugLogger(this::dbgString, Utils.DEBUG);
        final String dbgTag = ResponseContent.this.dbgTag + "/ChunkedBodyParser";

        volatile Throwable closedExceptionally;
        volatile int partialChunklen = 0; 
        volatile int chunklen = -1;  
        volatile int bytesremaining;  
        volatile boolean cr = false;  
        volatile int chunkext = 0;    
        volatile int digits = 0;      
        volatile int bytesToConsume;  
        volatile ChunkState state = ChunkState.READING_LENGTH; 
        volatile AbstractSubscription sub;
        ChunkedBodyParser(Consumer<Throwable> onComplete) {
            this.onComplete = onComplete;
        }

        String dbgString() {
            return dbgTag;
        }

        private void debugBuffer(ByteBuffer b) {
            if (!debug.on()) return;
            ByteBuffer printable = b.asReadOnlyBuffer();
            byte[] bytes = new byte[printable.limit() - printable.position()];
            printable.get(bytes, 0, bytes.length);
            String msg = "============== accepted ==================\n";
            try {
                var str = new String(bytes, UTF_8);
                msg += str;
            } catch (Exception x) {
                msg += x;
                x.printStackTrace();
            }
            msg += "\n==========================================\n";
            debug.log(msg);

        }

        @Override
        public void onSubscribe(AbstractSubscription sub) {
            if (debug.on())
                debug.log("onSubscribe: "  + pusher.getClass().getName());
            pusher.onSubscribe(this.sub = sub);
        }

        @Override
        public void onError(Throwable t) {
            closedExceptionally = t;
            onComplete.accept(t);
        }

        @Override
        public String currentStateMessage() {
            return format("chunked transfer encoding, state: %s", state);
        }
        @Override
        public void accept(ByteBuffer b) {
            if (closedExceptionally != null) {
                if (debug.on())
                    debug.log("already closed: " + closedExceptionally);
                return;
            }
            boolean completed = false;
            try {
                List<ByteBuffer> out = new ArrayList<>();
                do {
                    if (tryPushOneHunk(b, out))  {
                        if (!out.isEmpty()) {
                            boolean hasDemand = sub.demand().tryDecrement();
                            assert hasDemand;
                            pusher.onNext(Collections.unmodifiableList(out));
                            if (debug.on()) debug.log("Chunks sent");
                        }
                        if (debug.on()) debug.log("done!");
                        assert closedExceptionally == null;
                        assert state == ChunkState.DONE;
                        onFinished.run();
                        pusher.onComplete();
                        if (debug.on()) debug.log("subscriber completed");
                        completed = true;
                        onComplete.accept(closedExceptionally); 
                        break;
                    }
                } while (b.hasRemaining());

                if (!completed && !out.isEmpty()) {
                    boolean hasDemand = sub.demand().tryDecrement();
                    assert hasDemand;
                    pusher.onNext(Collections.unmodifiableList(out));
                    if (debug.on()) debug.log("Chunk sent");
                }
                assert state == ChunkState.DONE || !b.hasRemaining();
            } catch(Throwable t) {
                if (debug.on())
                    debug.log("Error while processing buffer: %s", (Object)t );
                closedExceptionally = t;
                if (!completed) onComplete.accept(t);
            }
        }

        private int tryReadChunkLen(ByteBuffer chunkbuf) throws IOException {
            assert state == ChunkState.READING_LENGTH;
            while (chunkbuf.hasRemaining()) {
                if (chunkext + digits >= MAX_CHUNK_HEADER_SIZE) {
                    throw new IOException("Chunk header size too long: " + (chunkext + digits));
                }
                int c = chunkbuf.get();
                if (cr) {
                    if (c == LF) {
                        return partialChunklen;
                    } else {
                        throw new IOException("invalid chunk header");
                    }
                }
                if (c == CR) {
                    cr = true;
                    if (digits == 0 && debug.on()) {
                        debug.log("tryReadChunkLen: invalid chunk header? No digits in chunkLen?");
                    }
                } else if (cr == false && chunkext > 0) {
                    chunkext++;
                    if (debug.on()) {
                        debug.log("tryReadChunkLen: More extraneous character after chunk length: " + c);
                    }
                } else {
                    int digit = toDigit(c);
                    if (digit < 0) {
                        if (digits > 0) {
                            chunkext++;
                            if (debug.on()) {
                                debug.log("tryReadChunkLen: Extraneous character after chunk length: " + c);
                            }
                        } else {
                            throw new IOException("Illegal character in chunk size: " + c);
                        }
                    } else {
                        digits++;
                        partialChunklen = partialChunklen * 16 + digit;
                    }
                }
            }
            return -1;
        }


        private int tryConsumeBytes(ByteBuffer chunkbuf) throws IOException {
            int n = bytesToConsume;
            if (n > 0) {
                int e = Math.min(chunkbuf.remaining(), n);

                if (Utils.ASSERTIONSENABLED) {
                    assert n <= 2 && e <= 2;
                    ByteBuffer tmp = chunkbuf.slice();
                    assert (n == 2 && e > 0) ? tmp.get() == CR : true;
                    assert (n == 1 || e == 2) ? tmp.get() == LF : true;
                }

                chunkbuf.position(chunkbuf.position() + e);
                n -= e;
                bytesToConsume = n;
            }
            assert n >= 0;
            return n;
        }

        /**
         * Returns a ByteBuffer containing chunk of data or a "hunk" of data
         * (a chunk of a chunk if the chunk size is larger than our ByteBuffers).
         * If the given chunk does not have enough data this method return
         * an empty ByteBuffer (READMORE).
         * If we encounter the final chunk (an empty chunk) this method
         * returns null.
         */
        ByteBuffer tryReadOneHunk(ByteBuffer chunk) throws IOException {
            int unfulfilled = bytesremaining;
            int toconsume = bytesToConsume;
            ChunkState st = state;
            if (st == ChunkState.READING_LENGTH && chunklen == -1) {
                if (debug.on()) debug.log(() ->  "Trying to read chunk len"
                        + " (remaining in buffer:"+chunk.remaining()+")");
                int clen = chunklen = tryReadChunkLen(chunk);
                if (clen == -1) return READMORE;
                digits = chunkext = 0;
                if (debug.on()) debug.log("Got chunk len %d", clen);
                cr = false; partialChunklen = 0;
                unfulfilled = bytesremaining =  clen;
                if (clen == 0) toconsume = bytesToConsume = 2; 
                else st = state = ChunkState.READING_DATA; 
            }

            if (toconsume > 0) {
                if (debug.on())
                    debug.log("Trying to consume bytes: %d (remaining in buffer: %s)",
                              toconsume, chunk.remaining());
                if (tryConsumeBytes(chunk) > 0) {
                    return READMORE;
                }
            }

            toconsume = bytesToConsume;
            assert toconsume == 0;


            if (st == ChunkState.READING_LENGTH) {
                int clen = chunklen;
                assert clen == 0;
                if (debug.on()) debug.log("No more chunks: %d", clen);
                state = ChunkState.DONE;
                return null;
            }

            int clen = chunklen;
            assert clen > 0;
            assert st == ChunkState.READING_DATA;

            ByteBuffer returnBuffer = READMORE; 
            if (unfulfilled > 0) {
                int bytesread = chunk.remaining();
                if (debug.on())
                    debug.log("Reading chunk: available %d, needed %d",
                              bytesread, unfulfilled);

                int bytes2return = Math.min(bytesread, unfulfilled);
                if (debug.on())
                    debug.log( "Returning chunk bytes: %d", bytes2return);
                returnBuffer = Utils.sliceWithLimitedCapacity(chunk, bytes2return).asReadOnlyBuffer();
                unfulfilled = bytesremaining -= bytes2return;
                if (unfulfilled == 0) bytesToConsume = 2;
            }

            assert unfulfilled >= 0;

            if (unfulfilled == 0) {
                if (debug.on())
                    debug.log("No more bytes to read - %d yet to consume.",
                              unfulfilled);
                if (tryConsumeBytes(chunk) == 0) {
                    chunklen = -1;
                    partialChunklen = 0;
                    cr = false;
                    digits = chunkext = 0;
                    state = ChunkState.READING_LENGTH;
                    if (debug.on()) debug.log("Ready to read next chunk");
                }
            }
            if (returnBuffer == READMORE) {
                if (debug.on()) debug.log("Need more data");
            }
            return returnBuffer;
        }


        private boolean tryPushOneHunk(ByteBuffer b, List<ByteBuffer> out)
                throws IOException {
            assert state != ChunkState.DONE;
            ByteBuffer b1 = tryReadOneHunk(b);
            if (b1 != null) {
                if (b1.hasRemaining()) {
                    if (debug.on())
                        debug.log("Sending chunk to consumer (%d)", b1.remaining());
                    out.add(b1);
                }
                return false; 
            } else {
                return true; 
            }
        }

        private int toDigit(int b) throws IOException {
            if (b >= 0x30 && b <= 0x39) {
                return b - 0x30;
            }
            if (b >= 0x41 && b <= 0x46) {
                return b - 0x41 + 10;
            }
            if (b >= 0x61 && b <= 0x66) {
                return b - 0x61 + 10;
            }
            return -1;
        }

    }

    class UnknownLengthBodyParser implements BodyParser {
        final Consumer<Throwable> onComplete;
        final Logger debug = Utils.getDebugLogger(this::dbgString, Utils.DEBUG);
        final String dbgTag = ResponseContent.this.dbgTag + "/UnknownLengthBodyParser";
        volatile Throwable closedExceptionally;
        volatile AbstractSubscription sub;
        volatile int breceived = 0;

        UnknownLengthBodyParser(Consumer<Throwable> onComplete) {
            this.onComplete = onComplete;
        }

        String dbgString() {
            return dbgTag;
        }

        @Override
        public void onSubscribe(AbstractSubscription sub) {
            if (debug.on())
                debug.log("onSubscribe: " + pusher.getClass().getName());
            pusher.onSubscribe(this.sub = sub);
        }

        @Override
        public void onError(Throwable t) {
            closedExceptionally = t;
            onComplete.accept(t);
        }

        @Override
        public String currentStateMessage() {
            return format("http1_0 content, bytes received: %d", breceived);
        }

        @Override
        public void accept(ByteBuffer b) {
            if (closedExceptionally != null) {
                if (debug.on())
                    debug.log("already closed: " + closedExceptionally);
                return;
            }
            try {
                if (debug.on())
                    debug.log("Parser got %d bytes ", b.remaining());

                if (b.hasRemaining()) {
                    boolean hasDemand = sub.demand().tryDecrement();
                    assert hasDemand;
                    breceived += b.remaining();
                    pusher.onNext(List.of(b.asReadOnlyBuffer()));
                }
            } catch (Throwable t) {
                if (debug.on()) debug.log("Unexpected exception", t);
                closedExceptionally = t;
                onComplete.accept(t);
            }
        }

        /**
         * Must be called externally when connection has closed
         * and therefore no more bytes can be read
         */
        public void complete() {
            if (debug.on())
                debug.log("Parser got all expected bytes: completing");
            assert closedExceptionally == null;
            onFinished.run();
            pusher.onComplete();
            onComplete.accept(closedExceptionally); 
        }
    }

    class FixedLengthBodyParser implements BodyParser {
        final long contentLength;
        final Consumer<Throwable> onComplete;
        final Logger debug = Utils.getDebugLogger(this::dbgString, Utils.DEBUG);
        final String dbgTag = ResponseContent.this.dbgTag + "/FixedLengthBodyParser";
        volatile long remaining;
        volatile Throwable closedExceptionally;
        volatile AbstractSubscription sub;
        FixedLengthBodyParser(long contentLength, Consumer<Throwable> onComplete) {
            this.contentLength = this.remaining = contentLength;
            this.onComplete = onComplete;
        }

        String dbgString() {
            return dbgTag;
        }

        @Override
        public void onSubscribe(AbstractSubscription sub) {
            if (debug.on())
                debug.log("length=" + contentLength +", onSubscribe: "
                           + pusher.getClass().getName());
            pusher.onSubscribe(this.sub = sub);
            try {
                if (contentLength == 0) {
                    onFinished.run();
                    pusher.onComplete();
                    onComplete.accept(null);
                }
            } catch (Throwable t) {
                closedExceptionally = t;
                try {
                    pusher.onError(t);
                } finally {
                    onComplete.accept(t);
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            if (contentLength != 0) {
                closedExceptionally = t;
                onComplete.accept(t);
            } else {
                onComplete.accept(null);
            }
        }

        @Override
        public String currentStateMessage() {
            return format("fixed content-length: %d, bytes received: %d",
                          contentLength, contentLength - remaining);
        }

        @Override
        public void accept(ByteBuffer b) {
            if (closedExceptionally != null) {
                if (debug.on())
                    debug.log("already closed: " + closedExceptionally);
                return;
            }
            boolean completed = false;
            try {
                long unfulfilled = remaining;
                if (debug.on())
                    debug.log("Parser got %d bytes (%d remaining / %d)",
                              b.remaining(), unfulfilled, contentLength);
                assert unfulfilled != 0 || contentLength == 0 || b.remaining() == 0;

                if (unfulfilled == 0 && contentLength > 0) return;

                if (b.hasRemaining() && unfulfilled > 0) {
                    boolean hasDemand = sub.demand().tryDecrement();
                    assert hasDemand;
                    int amount = (int)Math.min(b.remaining(), unfulfilled); 
                    unfulfilled = remaining -= amount;
                    ByteBuffer buffer = Utils.sliceWithLimitedCapacity(b, amount);
                    pusher.onNext(List.of(buffer.asReadOnlyBuffer()));
                }
                if (unfulfilled == 0) {
                    if (debug.on())
                        debug.log("Parser got all expected bytes: completing");
                    assert closedExceptionally == null;
                    onFinished.run();
                    pusher.onComplete();
                    completed = true;
                    onComplete.accept(closedExceptionally); 
                } else {
                    assert b.remaining() == 0;
                }
            } catch (Throwable t) {
                if (debug.on()) debug.log("Unexpected exception", t);
                closedExceptionally = t;
                if (!completed) {
                    onComplete.accept(t);
                }
            }
        }
    }
}
