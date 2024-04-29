/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.net;

import java.util.Formatter;
import java.util.Locale;
import sun.net.util.IPAddressUtil;

/**
 * Parses a string containing a host/domain name and port range
 */
class HostPortrange {

    String hostname;
    String scheme;
    int[] portrange;

    boolean wildcard;
    boolean literal;
    boolean ipv6, ipv4;
    static final int PORT_MIN = 0;
    static final int PORT_MAX = (1 << 16) -1;

    boolean equals(HostPortrange that) {
        return this.hostname.equals(that.hostname)
            && this.portrange[0] == that.portrange[0]
            && this.portrange[1] == that.portrange[1]
            && this.wildcard == that.wildcard
            && this.literal == that.literal;
    }

    public int hashCode() {
        return hostname.hashCode() + portrange[0] + portrange[1];
    }

    HostPortrange(String scheme, String str) {


        String hoststr, portstr = null;
        this.scheme = scheme;

        if (str.charAt(0) == '[') {
            ipv6 = literal = true;
            int rb = str.indexOf(']');
            if (rb != -1) {
                hoststr = str.substring(1, rb);
            } else {
                throw new IllegalArgumentException("invalid IPv6 address: " + str);
            }
            int sep = str.indexOf(':', rb + 1);
            if (sep != -1 && str.length() > sep) {
                portstr = str.substring(sep + 1);
            }
            byte[] ip = IPAddressUtil.textToNumericFormatV6(hoststr);
            if (ip == null) {
                throw new IllegalArgumentException("illegal IPv6 address");
            }
            StringBuilder sb = new StringBuilder();
            Formatter formatter = new Formatter(sb, Locale.US);
            formatter.format("%02x%02x:%02x%02x:%02x%02x:%02x"
                    + "%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x",
                    ip[0], ip[1], ip[2], ip[3], ip[4], ip[5], ip[6], ip[7], ip[8],
                    ip[9], ip[10], ip[11], ip[12], ip[13], ip[14], ip[15]);
            hostname = sb.toString();
        } else {

            int sep = str.indexOf(':');
            if (sep != -1 && str.length() > sep) {
                hoststr = str.substring(0, sep);
                portstr = str.substring(sep + 1);
            } else {
                hoststr = sep == -1 ? str : str.substring(0, sep);
            }
            if (hoststr.lastIndexOf('*') > 0) {
                throw new IllegalArgumentException("invalid host wildcard specification");
            } else if (hoststr.startsWith("*")) {
                wildcard = true;
                if (hoststr.equals("*")) {
                    hoststr = "";
                } else if (hoststr.startsWith("*.")) {
                    hoststr = toLowerCase(hoststr.substring(1));
                } else {
                    throw new IllegalArgumentException("invalid host wildcard specification");
                }
            } else {
                int lastdot = hoststr.lastIndexOf('.');
                if (lastdot != -1 && (hoststr.length() > 1)) {
                    boolean ipv4 = true;

                    for (int i = lastdot + 1, len = hoststr.length(); i < len; i++) {
                        char c = hoststr.charAt(i);
                        if (c < '0' || c > '9') {
                            ipv4 = false;
                            break;
                        }
                    }
                    this.ipv4 = this.literal = ipv4;
                    if (ipv4) {
                        byte[] ip = IPAddressUtil.validateNumericFormatV4(hoststr, false);
                        if (ip == null) {
                            throw new IllegalArgumentException("illegal IPv4 address");
                        }
                        StringBuilder sb = new StringBuilder();
                        Formatter formatter = new Formatter(sb, Locale.US);
                        formatter.format("%d.%d.%d.%d", ip[0], ip[1], ip[2], ip[3]);
                        hoststr = sb.toString();
                    } else {
                        hoststr = toLowerCase(hoststr);
                    }
                }
            }
            hostname = hoststr;
        }

        try {
            portrange = parsePort(portstr);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid port range: " + portstr);
        }
    }

    static final int CASE_DIFF = 'A' - 'a';

    /**
     * Convert to lower case, and check that all chars are ascii
     * alphanumeric, '-' or '.' only.
     */
    static String toLowerCase(String s) {
        int len = s.length();
        StringBuilder sb = null;

        for (int i=0; i<len; i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c == '.')) {
                if (sb != null)
                    sb.append(c);
            } else if ((c >= '0' && c <= '9') || (c == '-')) {
                if (sb != null)
                    sb.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                if (sb == null) {
                    sb = new StringBuilder(len);
                    sb.append(s, 0, i);
                }
                sb.append((char)(c - CASE_DIFF));
            } else {
                final String message = String.format("Invalid character \\u%04x in hostname", (int) c);
                throw new IllegalArgumentException(message);
            }
        }
        return sb == null ? s : sb.toString();
    }


    public boolean literal() {
        return literal;
    }

    public boolean ipv4Literal() {
        return ipv4;
    }

    public boolean ipv6Literal() {
        return ipv6;
    }

    public String hostname() {
        return hostname;
    }

    public int[] portrange() {
        return portrange;
    }

    /**
     * returns true if the hostname part started with *
     * hostname returns the remaining part of the host component
     * eg "*.foo.com" -> ".foo.com" or "*" -> ""
     *
     * @return
     */
    public boolean wildcard() {
        return wildcard;
    }

    static final int[] HTTP_PORT = {80, 80};
    static final int[] HTTPS_PORT = {443, 443};
    static final int[] NO_PORT = {-1, -1};

    int[] defaultPort() {
        if (scheme.equals("http")) {
            return HTTP_PORT;
        } else if (scheme.equals("https")) {
            return HTTPS_PORT;
        }
        return NO_PORT;
    }

    int[] parsePort(String port)
    {

        if (port == null || port.isEmpty()) {
            return defaultPort();
        }

        if (port.equals("*")) {
            return new int[] {PORT_MIN, PORT_MAX};
        }

        try {
            int dash = port.indexOf('-');

            if (dash == -1) {
                int p = Integer.parseInt(port);
                return new int[] {p, p};
            } else {
                String low = port.substring(0, dash);
                String high = port.substring(dash+1);
                int l,h;

                if (low.isEmpty()) {
                    l = PORT_MIN;
                } else {
                    l = Integer.parseInt(low);
                }

                if (high.isEmpty()) {
                    h = PORT_MAX;
                } else {
                    h = Integer.parseInt(high);
                }
                if (l < 0 || h < 0 || h<l) {
                    return defaultPort();
                }
                return new int[] {l, h};
             }
        } catch (IllegalArgumentException e) {
            return defaultPort();
        }
    }
}
