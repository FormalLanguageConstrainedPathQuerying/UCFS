/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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


package sun.awt.X11;

import jdk.internal.misc.Unsafe;

public class XQueryTree {
        private static Unsafe unsafe = XlibWrapper.unsafe;
        private boolean __executed = false;
        long _w;
        long root_ptr = unsafe.allocateMemory(Native.getLongSize());
        long parent_ptr = unsafe.allocateMemory(Native.getLongSize());
        long children_ptr = unsafe.allocateMemory(Native.getLongSize());
        long nchildren_ptr = unsafe.allocateMemory(Native.getIntSize());
    UnsafeXDisposerRecord disposer;
        public XQueryTree(
                long w  )
        {
                set_w(w);
                sun.java2d.Disposer.addRecord(this, disposer = new UnsafeXDisposerRecord("XQueryTree",
                                                                                         new long[]{root_ptr, parent_ptr, nchildren_ptr},
                                                                                         new long[] {children_ptr}));
                set_children(0);
        }
        public int execute() {
                return execute(null);
        }
        public int execute(XErrorHandler errorHandler) {
                XToolkit.awtLock();
                try {
                    if (isDisposed()) {
                        throw new IllegalStateException("Disposed");
                    }
                        if (__executed) {
                            throw new IllegalStateException("Already executed");
                        }
                        __executed = true;
                        if (errorHandler != null) {
                            XErrorHandlerUtil.WITH_XERROR_HANDLER(errorHandler);
                        }
                        Native.putLong(children_ptr, 0);
                        int status =
                        XlibWrapper.XQueryTree(XToolkit.getDisplay(),
                                get_w(),
                                root_ptr,
                                parent_ptr,
                                children_ptr,
                                nchildren_ptr                   );
                        if (errorHandler != null) {
                            XErrorHandlerUtil.RESTORE_XERROR_HANDLER();
                        }
                        return status;
                } finally {
                    XToolkit.awtUnlock();
                }
        }
        public boolean isExecuted() {
            return __executed;
        }

        public boolean isDisposed() {
            return disposer.disposed;
        }
        public void dispose() {
            XToolkit.awtLock();
            try {
                if (isDisposed()) {
                    return;
                }
                disposer.dispose();
            } finally {
                XToolkit.awtUnlock();
            }
        }
        public long get_w() {
                if (isDisposed()) {
                    throw new IllegalStateException("Disposed");
                }
                if (!__executed) {
                    throw new IllegalStateException("Not executed");
                }
                return _w;
        }
        public void set_w(long data) {
                _w = data;
        }
        public long get_root() {
                if (isDisposed()) {
                    throw new IllegalStateException("Disposed");
                }
                if (!__executed) {
                    throw new IllegalStateException("Not executed");
                }
                return Native.getLong(root_ptr);
        }
        public void set_root(long data) {
                Native.putLong(root_ptr, data);
        }
        public long get_parent() {
                if (isDisposed()) {
                    throw new IllegalStateException("Disposed");
                }
                if (!__executed) {
                    throw new IllegalStateException("Not executed");
                }
                return Native.getLong(parent_ptr);
        }
        public void set_parent(long data) {
                Native.putLong(parent_ptr, data);
        }
        public long get_children() {
                if (isDisposed()) {
                    throw new IllegalStateException("Disposed");
                }
                if (!__executed) {
                    throw new IllegalStateException("Not executed");
                }
                return Native.getLong(children_ptr);
        }
        public void set_children(long data) {
                Native.putLong(children_ptr, data);
        }
        public int get_nchildren() {
                if (isDisposed()) {
                    throw new IllegalStateException("Disposed");
                }
                if (!__executed) {
                    throw new IllegalStateException("Not executed");
                }
                return Native.getInt(nchildren_ptr);
        }
        public void set_nchildren(int data) {
                Native.putInt(nchildren_ptr, data);
        }
}
