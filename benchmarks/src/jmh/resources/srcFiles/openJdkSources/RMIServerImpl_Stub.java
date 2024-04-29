/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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

package javax.management.remote.rmi;

/**
 * RMIServerImpl remote stub.
 *
 * @since 1.5
 */
@SuppressWarnings({"deprecation", "rawtypes", "unchecked"})
public final class RMIServerImpl_Stub
        extends java.rmi.server.RemoteStub
        implements javax.management.remote.rmi.RMIServer {
    @java.io.Serial
    private static final long serialVersionUID = 2;

    private static java.lang.reflect.Method $method_getVersion_0;
    private static java.lang.reflect.Method $method_newClient_1;

    static {
        try {
            $method_getVersion_0 = javax.management.remote.rmi.RMIServer.class.getMethod("getVersion", new java.lang.Class[]{});
            $method_newClient_1 = javax.management.remote.rmi.RMIServer.class.getMethod("newClient", new java.lang.Class[]{java.lang.Object.class});
        } catch (java.lang.NoSuchMethodException e) {
            throw new java.lang.NoSuchMethodError(
                    "stub class initialization failed");
        }
    }

    /**
     * Constructor.
     *
     * @param ref  a remote ref
     */
    public RMIServerImpl_Stub(java.rmi.server.RemoteRef ref) {
        super(ref);
    }


    public java.lang.String getVersion()
            throws java.rmi.RemoteException {
        try {
            Object $result = ref.invoke(this, $method_getVersion_0, null, -8081107751519807347L);
            return ((java.lang.String) $result);
        } catch (java.lang.RuntimeException e) {
            throw e;
        } catch (java.rmi.RemoteException e) {
            throw e;
        } catch (java.lang.Exception e) {
            throw new java.rmi.UnexpectedException("undeclared checked exception", e);
        }
    }

    public javax.management.remote.rmi.RMIConnection newClient(java.lang.Object $param_Object_1)
            throws java.io.IOException {
        try {
            Object $result = ref.invoke(this, $method_newClient_1, new java.lang.Object[]{$param_Object_1}, -1089742558549201240L);
            return ((javax.management.remote.rmi.RMIConnection) $result);
        } catch (java.lang.RuntimeException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw e;
        } catch (java.lang.Exception e) {
            throw new java.rmi.UnexpectedException("undeclared checked exception", e);
        }
    }
}
