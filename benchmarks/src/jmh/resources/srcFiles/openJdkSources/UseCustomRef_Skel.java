/*
 * Copyright (c) 1998, 1999, Oracle and/or its affiliates. All rights reserved.
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


public final class UseCustomRef_Skel
    implements java.rmi.server.Skeleton
{
    private static final java.rmi.server.Operation[] operations = {
        new java.rmi.server.Operation("void ping()"),
        new java.rmi.server.Operation("void receiveAndPing(Ping)")
    };

    private static final long interfaceHash = 8457085322718440165L;

    public java.rmi.server.Operation[] getOperations() {
        return (java.rmi.server.Operation[]) operations.clone();
    }

    public void dispatch(java.rmi.Remote obj, java.rmi.server.RemoteCall call, int opnum, long hash)
        throws java.lang.Exception
    {
        if (opnum < 0) {
            if (hash == 5866401369815527589L) {
                opnum = 0;
            } else if (hash == -5964458762870933304L) {
                opnum = 1;
            } else {
                throw new java.rmi.UnmarshalException("invalid method hash");
            }
        } else {
            if (hash != interfaceHash)
                throw new java.rmi.server.SkeletonMismatchException("interface hash mismatch");
        }

        UseCustomRef server = (UseCustomRef) obj;
        switch (opnum) {
        case 0: 
        {
            call.releaseInputStream();
            server.ping();
            try {
                call.getResultStream(true);
            } catch (java.io.IOException e) {
                throw new java.rmi.MarshalException("error marshalling return", e);
            }
            break;
        }

        case 1: 
        {
            Ping $param_Ping_1;
            try {
                java.io.ObjectInput in = call.getInputStream();
                $param_Ping_1 = (Ping) in.readObject();
            } catch (java.io.IOException e) {
                throw new java.rmi.UnmarshalException("error unmarshalling arguments", e);
            } catch (java.lang.ClassNotFoundException e) {
                throw new java.rmi.UnmarshalException("error unmarshalling arguments", e);
            } finally {
                call.releaseInputStream();
            }
            server.receiveAndPing($param_Ping_1);
            try {
                call.getResultStream(true);
            } catch (java.io.IOException e) {
                throw new java.rmi.MarshalException("error marshalling return", e);
            }
            break;
        }

        default:
            throw new java.rmi.UnmarshalException("invalid method number");
        }
    }
}
