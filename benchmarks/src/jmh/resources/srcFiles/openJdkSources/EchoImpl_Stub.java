/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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


public final class EchoImpl_Stub
    extends java.rmi.server.RemoteStub
    implements Echo, java.rmi.Remote
{
    private static final long serialVersionUID = 2;

    private static java.lang.reflect.Method $method_echoNot_0;

    static {
        try {
            $method_echoNot_0 = Echo.class.getMethod("echoNot", new java.lang.Class[] {byte[].class});
        } catch (java.lang.NoSuchMethodException e) {
            throw new java.lang.NoSuchMethodError(
                "stub class initialization failed");
        }
    }

    public EchoImpl_Stub(java.rmi.server.RemoteRef ref) {
        super(ref);
    }


    public byte[] echoNot(byte[] $param_arrayOf_byte_1)
        throws java.rmi.RemoteException
    {
        try {
            Object $result = ref.invoke(this, $method_echoNot_0, new java.lang.Object[] {$param_arrayOf_byte_1}, -4295721514897591756L);
            return ((byte[]) $result);
        } catch (java.lang.RuntimeException e) {
            throw e;
        } catch (java.rmi.RemoteException e) {
            throw e;
        } catch (java.lang.Exception e) {
            throw new java.rmi.UnexpectedException("undeclared checked exception", e);
        }
    }
}
