/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6851973 8194486 8279520
 * @summary ignore incoming channel binding if acceptor does not set one
 * @library /test/lib
 * @run main jdk.test.lib.FileInstaller TestHosts TestHosts
 * @run main/othervm -Djdk.net.hosts.file=TestHosts IgnoreChannelBinding
 */

import java.net.InetAddress;
import org.ietf.jgss.ChannelBinding;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;
import sun.security.jgss.GSSUtil;

public class IgnoreChannelBinding {

    public static void main(String[] args)
            throws Exception {

        new OneKDC(null).writeJAASConf();
        test(GSSUtil.GSS_KRB5_MECH_OID);
        test(GSSUtil.GSS_SPNEGO_MECH_OID);
    }

    static void test(Oid mech) throws Exception {

        Context c = Context.fromJAAS("client");
        Context s = Context.fromJAAS("server");

        c.startAsClient(OneKDC.SERVER, mech);
        s.startAsServer(mech);
        Context.handshake(c, s);

        c.startAsClient(OneKDC.SERVER, mech);
        c.x().setChannelBinding(new ChannelBinding(
                InetAddress.getByName("client.rabbit.hole"),
                InetAddress.getByName("host.rabbit.hole"),
                new byte[0]
                ));
        s.startAsServer(mech);
        Context.handshake(c, s);

        c.startAsClient(OneKDC.SERVER, mech);
        c.x().setChannelBinding(new ChannelBinding(
                InetAddress.getByName("client.rabbit.hole"),
                InetAddress.getByName("host.rabbit.hole"),
                new byte[0]
                ));
        s.startAsServer(mech);
        s.x().setChannelBinding(new ChannelBinding(
                InetAddress.getByName("client.rabbit.hole"),
                InetAddress.getByName("host.rabbit.hole"),
                new byte[0]
                ));
        Context.handshake(c, s);

        c.startAsClient(OneKDC.SERVER, mech);
        c.x().setChannelBinding(new ChannelBinding(
                InetAddress.getByName("client.rabbit.hole"),
                InetAddress.getByName("host.rabbit.hole"),
                new byte[0]
                ));
        s.startAsServer(mech);
        s.x().setChannelBinding(new ChannelBinding(
                InetAddress.getByName("client.rabbit.hole"),
                InetAddress.getByName("host.rabbit.hole"),
                new byte[1]     
                ));
        try {
            Context.handshake(c, s);
            throw new Exception("Acceptor should reject initiator");
        } catch (GSSException ge) {
        }

        c.startAsClient(OneKDC.SERVER, mech);
        s.startAsServer(mech);
        s.x().setChannelBinding(new ChannelBinding(
                InetAddress.getByName("client.rabbit.hole"),
                InetAddress.getByName("host.rabbit.hole"),
                new byte[0]
                ));
        try {
            Context.handshake(c, s);
            throw new Exception("Acceptor should reject initiator");
        } catch (GSSException ge) {
            if (ge.getMajor() != GSSException.BAD_BINDINGS) {
                throw ge;
            }
        }
    }
}
