/*
 * Copyright (c) 1995, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.net.spi.InetAddressResolver.LookupPolicy;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.NavigableSet;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Scanner;
import java.io.File;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectInputStream.GetField;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import java.io.Serializable;
import java.lang.annotation.Native;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import jdk.internal.access.JavaNetInetAddressAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Blocker;
import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.Stable;
import sun.net.ResolverProviderConfiguration;
import sun.security.action.*;
import sun.net.InetAddressCachePolicy;
import sun.net.util.IPAddressUtil;
import sun.nio.cs.UTF_8;

import static java.net.spi.InetAddressResolver.LookupPolicy.IPV4;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV4_FIRST;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV6;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV6_FIRST;

/**
 * This class represents an Internet Protocol (IP) address.
 *
 * <p> An IP address is either a 32-bit or 128-bit unsigned number
 * used by IP, a lower-level protocol on which protocols like UDP and
 * TCP are built. The IP address architecture is defined by <a
 * href="http:
 * Assigned Numbers</i></a>, <a
 * href="http:
 * Address Allocation for Private Internets</i></a>, <a
 * href="http:
 * Administratively Scoped IP Multicast</i></a>, and <a
 * href="http:
 * Version 6 Addressing Architecture</i></a>. An instance of an
 * InetAddress consists of an IP address and possibly its
 * corresponding host name (depending on whether it is constructed
 * with a host name or whether it has already done reverse host name
 * resolution).
 *
 * <h2> Address types </h2>
 *
 * <table class="striped" style="margin-left:2em">
 *   <caption style="display:none">Description of unicast and multicast address types</caption>
 *   <thead>
 *   <tr><th scope="col">Address Type</th><th scope="col">Description</th></tr>
 *   </thead>
 *   <tbody>
 *   <tr><th scope="row" style="vertical-align:top">unicast</th>
 *       <td>An identifier for a single interface. A packet sent to
 *         a unicast address is delivered to the interface identified by
 *         that address.
 *
 *         <p> The Unspecified Address -- Also called anylocal or wildcard
 *         address. It must never be assigned to any node. It indicates the
 *         absence of an address. One example of its use is as the target of
 *         bind, which allows a server to accept a client connection on any
 *         interface, in case the server host has multiple interfaces.
 *
 *         <p> The <i>unspecified</i> address must not be used as
 *         the destination address of an IP packet.
 *
 *         <p> The <i>Loopback</i> Addresses -- This is the address
 *         assigned to the loopback interface. Anything sent to this
 *         IP address loops around and becomes IP input on the local
 *         host. This address is often used when testing a
 *         client.</td></tr>
 *   <tr><th scope="row" style="vertical-align:top">multicast</th>
 *       <td>An identifier for a set of interfaces (typically belonging
 *         to different nodes). A packet sent to a multicast address is
 *         delivered to all interfaces identified by that address.</td></tr>
 * </tbody>
 * </table>
 *
 * <h3> IP address scope </h3>
 *
 * <p> <i>Link-local</i> addresses are designed to be used for addressing
 * on a single link for purposes such as auto-address configuration,
 * neighbor discovery, or when no routers are present.
 *
 * <p> <i>Site-local</i> addresses are designed to be used for addressing
 * inside of a site without the need for a global prefix.
 *
 * <p> <i>Global</i> addresses are unique across the internet.
 *
 * <h3> <a id="format">Textual representation of IP addresses</a> </h3>
 *
 * The textual representation of an IP address is address family specific.
 *
 * <p>
 *
 * For IPv4 address format, please refer to <A
 * HREF="Inet4Address.html#format">Inet4Address#format</A>; For IPv6
 * address format, please refer to <A
 * HREF="Inet6Address.html#format">Inet6Address#format</A>.
 *
 * <p> There is a <a href="doc-files/net-properties.html#Ipv4IPv6">couple of
 * System Properties</a> affecting how IPv4 and IPv6 addresses are used.
 *
 * <h2 id="host-name-resolution"> Host Name Resolution </h2>
 *
 * <p> The InetAddress class provides methods to resolve host names to
 * their IP addresses and vice versa. The actual resolution is delegated to an
 * {@linkplain InetAddressResolver InetAddress resolver}.
 *
 * <p> <i>Host name-to-IP address resolution</i> maps a host name to an IP address.
 * For any host name, its corresponding IP address is returned.
 *
 * <p> <i>Reverse name resolution</i> means that for any IP address,
 * the host associated with the IP address is returned.
 *
 * <p id="built-in-resolver"> The built-in InetAddress resolver implementation does
 * host name-to-IP address resolution and vice versa through the use of
 * a combination of local machine configuration information and network
 * naming services such as the Domain Name System (DNS) and the Lightweight Directory
 * Access Protocol (LDAP).
 * The particular naming services that the built-in resolver uses by default
 * depends on the configuration of the local machine.
 *
 * <p> {@code InetAddress} has a service provider mechanism for InetAddress resolvers
 * that allows a custom InetAddress resolver to be used instead of the built-in implementation.
 * {@link InetAddressResolverProvider} is the service provider class. Its API docs provide all the
 * details on this mechanism.
 *
 * <h2> InetAddress Caching </h2>
 *
 * The InetAddress class has a cache to store successful as well as
 * unsuccessful host name resolutions.
 *
 * <p> By default, when a security manager is installed, in order to
 * protect against DNS spoofing attacks,
 * the result of positive host name resolutions are
 * cached forever. When a security manager is not installed, the default
 * behavior is to cache entries for a finite (implementation dependent)
 * period of time. The result of unsuccessful host
 * name resolution is cached for a very short period of time (10
 * seconds) to improve performance.
 *
 * <p> If the default behavior is not desired, then a Java security property
 * can be set to a different Time-to-live (TTL) value for positive
 * caching. Likewise, a system admin can configure a different
 * negative caching TTL value when needed or extend the usage of the stale data.
 *
 * <p> Three Java {@linkplain java.security.Security security} properties control
 * the TTL values used for positive and negative host name resolution caching:
 *
 * <dl style="margin-left:2em">
 * <dt><b>networkaddress.cache.ttl</b></dt>
 * <dd>Indicates the caching policy for successful name lookups from
 * the name service. The value is specified as an integer to indicate
 * the number of seconds to cache the successful lookup. The default
 * setting is to cache for an implementation specific period of time.
 * <p>
 * A value of -1 indicates "cache forever".
 * </dd>
 * <dt><b>networkaddress.cache.stale.ttl</b></dt>
 * <dd>Indicates the caching policy for stale names. The value is specified as
 * an integer to indicate the number of seconds that stale names will be kept in
 * the cache. A name is considered stale if the TTL has expired and an attempt
 * to lookup the host name again was not successful. This property is useful if
 * it is preferable to use a stale name rather than fail due to an unsuccessful
 * lookup. The default setting is to cache for an implementation specific period
 * of time.
 * <p>
 * If the value of this property is larger than "networkaddress.cache.ttl" then
 * "networkaddress.cache.ttl" will be used as a refresh interval of the name in
 * the cache. For example, if this property is set to 1 day and
 * "networkaddress.cache.ttl" is set to 30 seconds, then the positive response
 * will be cached for 1 day but an attempt to refresh it will be done every
 * 30 seconds.
 * <p>
 * A value of 0 (zero) or if the property is not set means do not use stale
 * names. Negative values are ignored.
 * </dd>
 * <dt><b>networkaddress.cache.negative.ttl</b> (default: 10)</dt>
 * <dd>Indicates the caching policy for un-successful name lookups
 * from the name service. The value is specified as an integer to
 * indicate the number of seconds to cache the failure for
 * un-successful lookups.
 * <p>
 * A value of 0 indicates "never cache".
 * A value of -1 indicates "cache forever".
 * </dd>
 * </dl>
 *
 * @spec https:
 *      RFC 1918: Address Allocation for Private Internets
 * @spec https:
 *      RFC 2365: Administratively Scoped IP Multicast
 * @spec https:
 *      RFC 2373: IP Version 6 Addressing Architecture
 * @spec https:
 *      RFC 790: Assigned numbers
 * @author  Chris Warth
 * @see     java.net.InetAddress#getByAddress(byte[])
 * @see     java.net.InetAddress#getByAddress(java.lang.String, byte[])
 * @see     java.net.InetAddress#getAllByName(java.lang.String)
 * @see     java.net.InetAddress#getByName(java.lang.String)
 * @see     java.net.InetAddress#getLocalHost()
 * @since 1.0
 * @sealedGraph
 */
public sealed class InetAddress implements Serializable permits Inet4Address, Inet6Address {

    /**
     * Specify the address family: Internet Protocol, Version 4
     * @since 1.4
     */
    @Native static final int IPv4 = 1;

    /**
     * Specify the address family: Internet Protocol, Version 6
     * @since 1.4
     */
    @Native static final int IPv6 = 2;

    static class InetAddressHolder {
        /**
         * Reserve the original application specified hostname.
         *
         * The original hostname is useful for domain-based endpoint
         * identification (see RFC 2818 and RFC 6125).  If an address
         * was created with a raw IP address, a reverse name lookup
         * may introduce endpoint identification security issue via
         * DNS forging.
         *
         * Oracle JSSE provider is using this original hostname, via
         * jdk.internal.misc.JavaNetAccess, for SSL/TLS endpoint identification.
         *
         * Note: May define a new public method in the future if necessary.
         */
        String originalHostName;

        InetAddressHolder() {}

        InetAddressHolder(String hostName, int address, int family) {
            this.originalHostName = hostName;
            this.hostName = hostName;
            this.address = address;
            this.family = family;
        }

        void init(String hostName, int family) {
            this.originalHostName = hostName;
            this.hostName = hostName;
            if (family != -1) {
                this.family = family;
            }
        }

        String hostName;

        String getHostName() {
            return hostName;
        }

        String getOriginalHostName() {
            return originalHostName;
        }

        /**
         * Holds a 32-bit IPv4 address.
         */
        int address;

        int getAddress() {
            return address;
        }

        /**
         * Specifies the address family type, for instance, '1' for IPv4
         * addresses, and '2' for IPv6 addresses.
         */
        int family;

        int getFamily() {
            return family;
        }
    }

    /* Used to store the serializable fields of InetAddress */
    final transient InetAddressHolder holder;

    InetAddressHolder holder() {
        return holder;
    }

    /* Used to store the system-wide resolver */
    @Stable
    private static volatile InetAddressResolver resolver;

    private static final InetAddressResolver BUILTIN_RESOLVER;

    /**
     * Used to store the best available hostname.
     * Lazily initialized via a data race; safe because Strings are immutable.
     */
    private transient String canonicalHostName = null;

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    @java.io.Serial
    private static final long serialVersionUID = 3286316764910316507L;

    private static final String PREFER_IPV4_STACK_VALUE;

    private static final String PREFER_IPV6_ADDRESSES_VALUE;

    private static final String HOSTS_FILE_NAME;

    /*
     * Load net library into runtime, and perform initializations.
     */
    static {
        PREFER_IPV4_STACK_VALUE =
                GetPropertyAction.privilegedGetProperty("java.net.preferIPv4Stack");
        PREFER_IPV6_ADDRESSES_VALUE =
                GetPropertyAction.privilegedGetProperty("java.net.preferIPv6Addresses");
        HOSTS_FILE_NAME =
                GetPropertyAction.privilegedGetProperty("jdk.net.hosts.file");
        jdk.internal.loader.BootLoader.loadLibrary("net");
        SharedSecrets.setJavaNetInetAddressAccess(
                new JavaNetInetAddressAccess() {
                    public String getOriginalHostName(InetAddress ia) {
                        return ia.holder.getOriginalHostName();
                    }

                    public int addressValue(Inet4Address inet4Address) {
                        return inet4Address.addressValue();
                    }

                    public byte[] addressBytes(Inet6Address inet6Address) {
                        return inet6Address.addressBytes();
                    }
                }
        );
        init();
    }

    /**
     * Creates an address lookup policy from {@code "java.net.preferIPv4Stack"},
     * {@code "java.net.preferIPv6Addresses"} system property values, and O/S configuration.
     */
    private static final LookupPolicy initializePlatformLookupPolicy() {
        boolean ipv4Available = isIPv4Available();
        if ("true".equals(PREFER_IPV4_STACK_VALUE) && ipv4Available) {
            return LookupPolicy.of(IPV4);
        }
        if (InetAddress.impl instanceof Inet4AddressImpl) {
            return LookupPolicy.of(IPV4);
        }
        if (!ipv4Available) {
            return LookupPolicy.of(IPV6);
        }
        if (PREFER_IPV6_ADDRESSES_VALUE != null) {
            if (PREFER_IPV6_ADDRESSES_VALUE.equalsIgnoreCase("true")) {
                return LookupPolicy.of(IPV4 | IPV6 | IPV6_FIRST);
            }
            if (PREFER_IPV6_ADDRESSES_VALUE.equalsIgnoreCase("false")) {
                return LookupPolicy.of(IPV4 | IPV6 | IPV4_FIRST);
            }
            if (PREFER_IPV6_ADDRESSES_VALUE.equalsIgnoreCase("system")) {
                return LookupPolicy.of(IPV4 | IPV6);
            }
        }
        return LookupPolicy.of(IPV4 | IPV6 | IPV4_FIRST);
    }

    static boolean systemAddressesOrder(int lookupCharacteristics) {
        return (lookupCharacteristics & (IPV4_FIRST | IPV6_FIRST)) == 0;
    }

    static boolean ipv4AddressesFirst(int lookupCharacteristics) {
        return (lookupCharacteristics & IPV4_FIRST) != 0;
    }

    static boolean ipv6AddressesFirst(int lookupCharacteristics) {
        return (lookupCharacteristics & IPV6_FIRST) != 0;
    }

    private static native boolean isIPv4Available();

    private static native boolean isIPv6Supported();

    /**
     * The {@code RuntimePermission("inetAddressResolverProvider")} is
     * necessary to subclass and instantiate the {@code InetAddressResolverProvider}
     * class, as well as to obtain resolver from an instance of that class,
     * and it is also required to obtain the operating system name resolution configurations.
     */
    private static final RuntimePermission INET_ADDRESS_RESOLVER_PERMISSION =
            new RuntimePermission("inetAddressResolverProvider");

    private static final ReentrantLock RESOLVER_LOCK = new ReentrantLock();
    private static volatile InetAddressResolver bootstrapResolver;

    @SuppressWarnings("removal")
    private static InetAddressResolver resolver() {
        InetAddressResolver cns = resolver;
        if (cns != null) {
            return cns;
        }
        if (VM.isBooted()) {
            RESOLVER_LOCK.lock();
            boolean bootstrapSet = false;
            try {
                cns = resolver;
                if (cns != null) {
                    return cns;
                }
                if (bootstrapResolver != null) {
                    return bootstrapResolver;
                }
                bootstrapResolver = BUILTIN_RESOLVER;
                bootstrapSet = true;

                if (HOSTS_FILE_NAME != null) {
                    cns = BUILTIN_RESOLVER;
                } else if (System.getSecurityManager() != null) {
                    PrivilegedAction<InetAddressResolver> pa = InetAddress::loadResolver;
                    cns = AccessController.doPrivileged(
                            pa, null, INET_ADDRESS_RESOLVER_PERMISSION);
                } else {
                    cns = loadResolver();
                }

                InetAddress.resolver = cns;
                return cns;
            } finally {
                if (bootstrapSet) {
                    bootstrapResolver = null;
                }
                RESOLVER_LOCK.unlock();
            }
        } else {
            return BUILTIN_RESOLVER;
        }
    }

    private static InetAddressResolver loadResolver() {
        return ServiceLoader.load(InetAddressResolverProvider.class)
                .findFirst()
                .map(nsp -> nsp.get(builtinConfiguration()))
                .orElse(BUILTIN_RESOLVER);
    }

    private static InetAddressResolverProvider.Configuration builtinConfiguration() {
        return new ResolverProviderConfiguration(BUILTIN_RESOLVER, () -> {
            try {
                return impl.getLocalHostName();
            } catch (UnknownHostException unknownHostException) {
                return "localhost";
            }
        });
    }

    /**
     * Constructor for the Socket.accept() method.
     * This creates an empty InetAddress, which is filled in by
     * the accept() method.  This InetAddress, however, is not
     * put in the address cache, since it is not created by name.
     */
    InetAddress() {
        holder = new InetAddressHolder();
    }

    /**
     * Replaces the de-serialized object with an Inet4Address object.
     *
     * @return the alternate object to the de-serialized object.
     *
     * @throws ObjectStreamException if a new object replacing this
     * object could not be created
     */
    @java.io.Serial
    private Object readResolve() throws ObjectStreamException {
        return new Inet4Address(holder().getHostName(), holder().getAddress());
    }

    /**
     * Utility routine to check if the InetAddress is an
     * IP multicast address.
     * @return a {@code boolean} indicating if the InetAddress is
     * an IP multicast address
     * @since   1.1
     */
    public boolean isMulticastAddress() {
        return false;
    }

    /**
     * Utility routine to check if the InetAddress is a wildcard address.
     * @return a {@code boolean} indicating if the InetAddress is
     *         a wildcard address.
     * @since 1.4
     */
    public boolean isAnyLocalAddress() {
        return false;
    }

    /**
     * Utility routine to check if the InetAddress is a loopback address.
     *
     * @return a {@code boolean} indicating if the InetAddress is
     * a loopback address; or false otherwise.
     * @since 1.4
     */
    public boolean isLoopbackAddress() {
        return false;
    }

    /**
     * Utility routine to check if the InetAddress is a link local address.
     *
     * @return a {@code boolean} indicating if the InetAddress is
     * a link local address; or false if address is not a link local unicast address.
     * @since 1.4
     */
    public boolean isLinkLocalAddress() {
        return false;
    }

    /**
     * Utility routine to check if the InetAddress is a site local address.
     *
     * @return a {@code boolean} indicating if the InetAddress is
     * a site local address; or false if address is not a site local unicast address.
     * @since 1.4
     */
    public boolean isSiteLocalAddress() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has global scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of global scope, false if it is not
     *         of global scope or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCGlobal() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has node scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of node-local scope, false if it is not
     *         of node-local scope or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCNodeLocal() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has link scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of link-local scope, false if it is not
     *         of link-local scope or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCLinkLocal() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has site scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of site-local scope, false if it is not
     *         of site-local scope or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCSiteLocal() {
        return false;
    }

    /**
     * Utility routine to check if the multicast address has organization scope.
     *
     * @return a {@code boolean} indicating if the address has
     *         is a multicast address of organization-local scope,
     *         false if it is not of organization-local scope
     *         or it is not a multicast address
     * @since 1.4
     */
    public boolean isMCOrgLocal() {
        return false;
    }


    /**
     * Test whether that address is reachable. Best effort is made by the
     * implementation to try to reach the host, but firewalls and server
     * configuration may block requests resulting in an unreachable status
     * while some specific ports may be accessible.
     * A typical implementation will use ICMP ECHO REQUESTs if the
     * privilege can be obtained, otherwise it will try to establish
     * a TCP connection on port 7 (Echo) of the destination host.
     * <p>
     * The timeout value, in milliseconds, indicates the maximum amount of time
     * the try should take. If the operation times out before getting an
     * answer, the host is deemed unreachable. A negative value will result
     * in an IllegalArgumentException being thrown.
     *
     * @param   timeout the time, in milliseconds, before the call aborts
     * @return a {@code boolean} indicating if the address is reachable.
     * @throws IOException if a network error occurs
     * @throws  IllegalArgumentException if {@code timeout} is negative.
     * @since 1.5
     */
    public boolean isReachable(int timeout) throws IOException {
        return isReachable(null, 0 , timeout);
    }

    /**
     * Test whether that address is reachable. Best effort is made by the
     * implementation to try to reach the host, but firewalls and server
     * configuration may block requests resulting in a unreachable status
     * while some specific ports may be accessible.
     * A typical implementation will use ICMP ECHO REQUESTs if the
     * privilege can be obtained, otherwise it will try to establish
     * a TCP connection on port 7 (Echo) of the destination host.
     * <p>
     * The {@code network interface} and {@code ttl} parameters
     * let the caller specify which network interface the test will go through
     * and the maximum number of hops the packets should go through.
     * A negative value for the {@code ttl} will result in an
     * IllegalArgumentException being thrown.
     * <p>
     * The timeout value, in milliseconds, indicates the maximum amount of time
     * the try should take. If the operation times out before getting an
     * answer, the host is deemed unreachable. A negative value will result
     * in an IllegalArgumentException being thrown.
     *
     * @param   netif   the NetworkInterface through which the
     *                    test will be done, or null for any interface
     * @param   ttl     the maximum numbers of hops to try or 0 for the
     *                  default
     * @param   timeout the time, in milliseconds, before the call aborts
     * @throws  IllegalArgumentException if either {@code timeout}
     *                          or {@code ttl} are negative.
     * @return a {@code boolean} indicating if the address is reachable.
     * @throws IOException if a network error occurs
     * @since 1.5
     */
    public boolean isReachable(NetworkInterface netif, int ttl,
                               int timeout) throws IOException {
        if (ttl < 0)
            throw new IllegalArgumentException("ttl can't be negative");
        if (timeout < 0)
            throw new IllegalArgumentException("timeout can't be negative");

        return impl.isReachable(this, timeout, netif, ttl);
    }

    /**
     * Gets the host name for this IP address.
     *
     * <p>If this InetAddress was created with a host name,
     * this host name will be remembered and returned;
     * otherwise, a reverse name lookup will be performed
     * and the result will be returned based on the system-wide
     * resolver. If a lookup of the name service
     * is required, call
     * {@link #getCanonicalHostName() getCanonicalHostName}.
     *
     * <p>If there is a security manager, its
     * {@code checkConnect} method is first called
     * with the hostname and {@code -1}
     * as its arguments to see if the operation is allowed.
     * If the operation is not allowed, it will return
     * the textual representation of the IP address.
     *
     * @return  the host name for this IP address, or if the operation
     *    is not allowed by the security check, the textual
     *    representation of the IP address.
     *
     * @see InetAddress#getCanonicalHostName
     * @see SecurityManager#checkConnect
     */
    public String getHostName() {
        return getHostName(true);
    }

    /**
     * Returns the hostname for this address.
     * If the host is equal to null, then this address refers to any
     * of the local machine's available network addresses.
     * this is package private so SocketPermission can make calls into
     * here without a security check.
     *
     * <p>If there is a security manager, this method first
     * calls its {@code checkConnect} method
     * with the hostname and {@code -1}
     * as its arguments to see if the calling code is allowed to know
     * the hostname for this IP address, i.e., to connect to the host.
     * If the operation is not allowed, it will return
     * the textual representation of the IP address.
     *
     * @return  the host name for this IP address, or if the operation
     *    is not allowed by the security check, the textual
     *    representation of the IP address.
     *
     * @param check make security check if true
     *
     * @see SecurityManager#checkConnect
     */
    String getHostName(boolean check) {
        if (holder().getHostName() == null) {
            holder().hostName = InetAddress.getHostFromNameService(this, check);
        }
        return holder().getHostName();
    }

    /**
     * Gets the fully qualified domain name for this
     * {@linkplain InetAddress#getAddress() IP address} using the system-wide
     * {@linkplain InetAddressResolver resolver}.
     *
     * <p>The system-wide resolver will be used to do a reverse name lookup of the IP address.
     * The lookup can fail for many reasons that include the host not being registered with the name
     * service. If the resolver is unable to determine the fully qualified
     * domain name, this method returns the {@linkplain #getHostAddress() textual representation}
     * of the IP address.
     *
     * <p>If there is a security manager, this method first
     * calls its {@code checkConnect} method
     * with the hostname and {@code -1}
     * as its arguments to see if the calling code is allowed to know
     * the hostname for this IP address, i.e., to connect to the host.
     * If the operation is not allowed, it will return
     * the textual representation of the IP address.
     *
     * @return  the fully qualified domain name for this IP address.
     *          If either the operation is not allowed by the security check
     *          or the system-wide resolver wasn't able to determine the
     *          fully qualified domain name for the IP address, the textual
     *          representation of the IP address is returned instead.
     *
     * @see SecurityManager#checkConnect
     *
     * @since 1.4
     */
    public String getCanonicalHostName() {
        String value = canonicalHostName;
        if (value == null)
            canonicalHostName = value =
                InetAddress.getHostFromNameService(this, true);
        return value;
    }

    /**
     * Returns the fully qualified domain name for the given address.
     *
     * <p>If there is a security manager, this method first
     * calls its {@code checkConnect} method
     * with the hostname and {@code -1}
     * as its arguments to see if the calling code is allowed to know
     * the hostname for the given IP address, i.e., to connect to the host.
     * If the operation is not allowed, it will return
     * the textual representation of the IP address.
     *
     * @param check make security check if true
     *
     * @return  the fully qualified domain name for the given IP address.
     *          If either the operation is not allowed by the security check
     *          or the system-wide resolver wasn't able to determine the
     *          fully qualified domain name for the IP address, the textual
     *          representation of the IP address is returned instead.
     *
     * @see SecurityManager#checkConnect
     */
    private static String getHostFromNameService(InetAddress addr, boolean check) {
        String host;
        var resolver = resolver();
        try {
            host = resolver.lookupByAddress(addr.getAddress());

            /* check to see if calling code is allowed to know
             * the hostname for this IP address, ie, connect to the host
             */
            if (check) {
                @SuppressWarnings("removal")
                SecurityManager sec = System.getSecurityManager();
                if (sec != null) {
                    sec.checkConnect(host, -1);
                }
            }

            /* now get all the IP addresses for this hostname,
             * and make sure one of them matches the original IP
             * address. We do this to try and prevent spoofing.
             */

            InetAddress[] arr = InetAddress.getAllByName0(host, check);
            boolean ok = false;

            if (arr != null) {
                for (int i = 0; !ok && i < arr.length; i++) {
                    ok = addr.equals(arr[i]);
                }
            }

            if (!ok) {
                host = addr.getHostAddress();
                return host;
            }
        } catch (RuntimeException | UnknownHostException e) {
            host = addr.getHostAddress();
        }
        return host;
    }

    /**
     * Returns the raw IP address of this {@code InetAddress}
     * object. The result is in network byte order: the highest order
     * byte of the address is in {@code getAddress()[0]}.
     *
     * @return  the raw IP address of this object.
     */
    public byte[] getAddress() {
        return null;
    }

    /**
     * Returns the IP address string in textual presentation.
     *
     * @return  the raw IP address in a string format.
     * @since   1.0.2
     */
    public String getHostAddress() {
        return null;
     }

    /**
     * Returns a hashcode for this IP address.
     *
     * @return  a hash code value for this IP address.
     */
    public int hashCode() {
        return -1;
    }

    /**
     * Compares this object against the specified object.
     * The result is {@code true} if and only if the argument is
     * not {@code null} and it represents the same IP address as
     * this object.
     * <p>
     * Two instances of {@code InetAddress} represent the same IP
     * address if the length of the byte arrays returned by
     * {@code getAddress} is the same for both, and each of the
     * array components is the same for the byte arrays.
     *
     * @param   obj   the object to compare against.
     * @return  {@code true} if the objects are the same;
     *          {@code false} otherwise.
     * @see     java.net.InetAddress#getAddress()
     */
    public boolean equals(Object obj) {
        return false;
    }

    /**
     * Converts this IP address to a {@code String}. The
     * string returned is of the form: hostname / literal IP
     * address.
     *
     * If the host name is unresolved, no reverse lookup
     * is performed. The hostname part will be represented
     * by an empty string.
     *
     * @return  a string representation of this IP address.
     */
    public String toString() {
        String hostName = holder().getHostName();
        return Objects.toString(hostName, "")
            + "/" + getHostAddress();
    }

    private static final ConcurrentMap<String, Addresses> cache =
        new ConcurrentHashMap<>();

    private static final NavigableSet<CachedLookup> expirySet =
        new ConcurrentSkipListSet<>();

    private interface Addresses {
        InetAddress[] get() throws UnknownHostException;
    }

    /**
     * A cached result of a name service lookup. The result can be either valid
     * addresses or invalid (ie a failed lookup) containing no addresses.
     */
    private static class CachedLookup implements Addresses, Comparable<CachedLookup> {
        private static final AtomicLong seq = new AtomicLong();
        final String host;
        volatile InetAddress[] inetAddresses;
        /**
         * Time of expiry (in terms of System.nanoTime()). Can be modified only
         * when the record is not added to the "expirySet".
         */
        volatile long expiryTime;
        final long id = seq.incrementAndGet(); 

        CachedLookup(String host, InetAddress[] inetAddresses, long expiryTime) {
            this.host = host;
            this.inetAddresses = inetAddresses;
            this.expiryTime = expiryTime;
        }

        @Override
        public InetAddress[] get() throws UnknownHostException {
            if (inetAddresses == null) {
                throw new UnknownHostException(host);
            }
            return inetAddresses;
        }

        @Override
        public int compareTo(CachedLookup other) {
            long diff = this.expiryTime - other.expiryTime;
            if (diff < 0L) return -1;
            if (diff > 0L) return 1;
            return Long.compare(this.id, other.id);
        }

        /**
         * Checks if the current cache record is expired or not. Expired records
         * are removed from the expirySet and cache.
         *
         * @return {@code true} if the record was removed
         */
        public boolean tryRemoveExpiredAddress(long now) {
            if ((expiryTime - now) < 0L) {
                if (expirySet.remove(this)) {
                    cache.remove(host, this);
                }
                return true;
            }
            return false;
        }
    }

    /**
     * A cached valid lookup containing addresses whose validity may be
     * temporarily extended by an additional stale period pending the mapping
     * being refreshed or updated.
     */
    private static final class ValidCachedLookup extends CachedLookup {
        /**
         * Time to refresh (in terms of System.nanoTime()).
         */
        private volatile long refreshTime;
        /**
         * For how long the stale data should be used after TTL expiration.
         * Initially equal to the expiryTime, but increased over time after each
         * successful lookup.
         */
        private volatile long staleTime;

        /**
         * only one thread is doing lookup to name service
         * for particular host at any time.
         */
        private final Lock lookupLock = new ReentrantLock();

        ValidCachedLookup(String host, InetAddress[] inetAddresses,
                          long staleTime, long refreshTime)
        {
            super(host, inetAddresses, staleTime);
            this.refreshTime = refreshTime;
            this.staleTime = staleTime;
        }

        @Override
        public InetAddress[] get() {
            long now = System.nanoTime();
            if ((refreshTime - now) < 0L && lookupLock.tryLock()) {
                try {
                    refreshTime = now + InetAddressCachePolicy.get() * 1000_000_000L;
                    inetAddresses = getAddressesFromNameService(host);
                    staleTime = refreshTime + InetAddressCachePolicy.getStale() * 1000_000_000L;
                } catch (UnknownHostException ignore) {
                } finally {
                    lookupLock.unlock();
                }
            }
            return inetAddresses;
        }

        /**
         * Overrides the parent method to skip deleting the record from the
         * cache if the stale data can still be used. Note to update the
         * "expiryTime" field we have to remove the record from the expirySet
         * and add it back. It is not necessary to remove/add it here, we can do
         * that in the "get()" method above, but extracting it minimizes
         * contention on "expirySet".
         */
        @Override
        public boolean tryRemoveExpiredAddress(long now) {
            if ((expiryTime - now) < 0L) {
                if ((staleTime - now) < 0L) {
                    return super.tryRemoveExpiredAddress(now);
                }
                if (expirySet.remove(this)) {
                    expiryTime = staleTime;
                    expirySet.add(this);
                }
            }
            return false;
        }
    }

    private static final class NameServiceAddresses implements Addresses {
        private final String host;
        private final ReentrantLock lookupLock = new ReentrantLock();

        NameServiceAddresses(String host) {
            this.host = host;
        }

        @Override
        public InetAddress[] get() throws UnknownHostException {
            Addresses addresses;
            lookupLock.lock();
            try {
                addresses = cache.putIfAbsent(host, this);
                if (addresses == null) {
                    addresses = this;
                }
                if (addresses == this) {
                    InetAddress[] inetAddresses;
                    UnknownHostException ex;
                    int cachePolicy;
                    try {
                        inetAddresses = getAddressesFromNameService(host);
                        ex = null;
                        cachePolicy = InetAddressCachePolicy.get();
                    } catch (UnknownHostException uhe) {
                        inetAddresses = null;
                        ex = uhe;
                        cachePolicy = InetAddressCachePolicy.getNegative();
                    }
                    if (cachePolicy == InetAddressCachePolicy.NEVER) {
                        cache.remove(host, this);
                    } else {
                        long now = System.nanoTime();
                        long expiryTime =
                                cachePolicy == InetAddressCachePolicy.FOREVER ?
                                0L
                                : now + 1000_000_000L * cachePolicy;
                        CachedLookup cachedLookup;
                        if (InetAddressCachePolicy.getStale() > 0 &&
                                ex == null && expiryTime > 0)
                        {
                            long refreshTime = expiryTime;
                            expiryTime = refreshTime + 1000_000_000L *
                                    InetAddressCachePolicy.getStale();
                            cachedLookup = new ValidCachedLookup(host,
                                                                 inetAddresses,
                                                                 expiryTime,
                                                                 refreshTime);
                        } else {
                            cachedLookup = new CachedLookup(host,
                                                            inetAddresses,
                                                            expiryTime);
                        }
                        if (cache.replace(host, this, cachedLookup) &&
                            cachePolicy != InetAddressCachePolicy.FOREVER) {
                            expirySet.add(cachedLookup);
                        }
                    }
                    if (inetAddresses == null || inetAddresses.length == 0) {
                        throw ex == null ? new UnknownHostException(host) : ex;
                    }
                    return inetAddresses;
                }
            } finally {
                lookupLock.unlock();
            }
            return addresses.get();
        }
    }

    /**
     * The default InetAddressResolver implementation, which delegates to the underlying
     * OS network libraries to resolve host address mappings.
     *
     * @since 9
     */
    private static final class PlatformResolver implements InetAddressResolver {

        public Stream<InetAddress> lookupByName(String host, LookupPolicy policy)
                throws UnknownHostException {
            Objects.requireNonNull(host);
            Objects.requireNonNull(policy);
            validate(host);
            InetAddress[] addrs;
            boolean attempted = Blocker.begin();
            try {
                addrs = impl.lookupAllHostAddr(host, policy);
            } finally {
                Blocker.end(attempted);
            }
            return Arrays.stream(addrs);
        }

        public String lookupByAddress(byte[] addr) throws UnknownHostException {
            Objects.requireNonNull(addr);
            if (addr.length != Inet4Address.INADDRSZ && addr.length != Inet6Address.INADDRSZ) {
                throw new IllegalArgumentException("Invalid address length");
            }
            boolean attempted = Blocker.begin();
            try {
                return impl.getHostByAddr(addr);
            } finally {
                Blocker.end(attempted);
            }
        }
    }

    /**
     * The HostsFileResolver provides host address mapping
     * by reading the entries in a hosts file, which is specified by
     * {@code jdk.net.hosts.file} system property
     *
     * <p>The file format is that which corresponds with the /etc/hosts file
     * IP Address host alias list.
     *
     * <p>When the file lookup is enabled it replaces the default InetAddressResolver
     * implementation
     *
     * @since 9
     */
    private static final class HostsFileResolver implements InetAddressResolver {

        private final String hostsFile;

        public HostsFileResolver(String hostsFileName) {
            this.hostsFile = hostsFileName;
        }

        /**
         * Lookup the host name  corresponding to the IP address provided.
         * Search the configured host file a host name corresponding to
         * the specified IP address.
         *
         * @param addr byte array representing an IP address
         * @return {@code String} representing the host name mapping
         * @throws UnknownHostException if no host found for the specified IP address
         * @throws IllegalArgumentException if IP address is of illegal length
         * @throws NullPointerException     if addr is {@code null}
         */
        @Override
        public String lookupByAddress(byte[] addr) throws UnknownHostException {
            String hostEntry;
            String host = null;
            Objects.requireNonNull(addr);
            if (addr.length != Inet4Address.INADDRSZ && addr.length != Inet6Address.INADDRSZ) {
                throw new IllegalArgumentException("Invalid address length");
            }

            try (Scanner hostsFileScanner = new Scanner(new File(hostsFile),
                                                        UTF_8.INSTANCE)) {
                while (hostsFileScanner.hasNextLine()) {
                    hostEntry = hostsFileScanner.nextLine();
                    if (!hostEntry.startsWith("#")) {
                        hostEntry = removeComments(hostEntry);
                        String[] mapping = hostEntry.split("\\s+");
                        if (mapping.length >= 2 &&
                            Arrays.equals(addr, createAddressByteArray(mapping[0]))) {
                            host = mapping[1];
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                throw new UnknownHostException("Unable to resolve address "
                        + Arrays.toString(addr) + " as hosts file " + hostsFile
                        + " not found ");
            }

            if ((host == null) || (host.isEmpty()) || (host.equals(" "))) {
                throw new UnknownHostException("Requested address "
                        + Arrays.toString(addr)
                        + " resolves to an invalid entry in hosts file "
                        + hostsFile);
            }
            return host;
        }

        /**
         * <p>Lookup a host mapping by name. Retrieve the IP addresses
         * associated with a host.
         *
         * <p>Search the configured hosts file for the addresses associated
         * with the specified host name.
         *
         * @param host the specified hostname
         * @param lookupPolicy IP addresses lookup policy which specifies addresses
         *                     family and their order
         * @return stream of IP addresses for the requested host
         * @throws NullPointerException if either parameter is {@code null}
         * @throws UnknownHostException
         *             if no IP address for the {@code host} could be found
         */
        public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy)
                throws UnknownHostException {
            String hostEntry;
            String addrStr;
            byte addr[];

            Objects.requireNonNull(host);
            Objects.requireNonNull(lookupPolicy);
            List<InetAddress> inetAddresses = new ArrayList<>();
            List<InetAddress> inet4Addresses = new ArrayList<>();
            List<InetAddress> inet6Addresses = new ArrayList<>();
            int flags = lookupPolicy.characteristics();
            boolean needIPv4 = (flags & IPv4) != 0;
            boolean needIPv6 = (flags & IPv6) != 0;

            try (Scanner hostsFileScanner = new Scanner(new File(hostsFile),
                    UTF_8.INSTANCE)) {
                while (hostsFileScanner.hasNextLine()) {
                    hostEntry = hostsFileScanner.nextLine();
                    if (!hostEntry.startsWith("#")) {
                        hostEntry = removeComments(hostEntry);
                        if (hostEntry.contains(host)) {
                            addrStr = extractHostAddr(hostEntry, host);
                            if ((addrStr != null) && (!addrStr.isEmpty())) {
                                addr = createAddressByteArray(addrStr);
                                if (addr != null) {
                                    InetAddress address = InetAddress.getByAddress(host, addr);
                                    inetAddresses.add(address);
                                    if (address instanceof Inet4Address && needIPv4) {
                                        inet4Addresses.add(address);
                                    }
                                    if (address instanceof Inet6Address && needIPv6) {
                                        inet6Addresses.add(address);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new UnknownHostException("Unable to resolve host " + host
                        + " as hosts file " + hostsFile + " not found ");
            }
            if (needIPv4 && !needIPv6) {
                checkResultsList(inet4Addresses, host);
                return inet4Addresses.stream();
            }
            if (!needIPv4 && needIPv6) {
                checkResultsList(inet6Addresses, host);
                return inet6Addresses.stream();
            }
            checkResultsList(inetAddresses, host);
            if (ipv6AddressesFirst(flags)) {
                return Stream.concat(inet6Addresses.stream(), inet4Addresses.stream());
            } else if (ipv4AddressesFirst(flags)) {
                return Stream.concat(inet4Addresses.stream(), inet6Addresses.stream());
            }
            assert systemAddressesOrder(flags);
            return inetAddresses.stream();
        }

        private void checkResultsList(List<InetAddress> addressesList, String hostName)
                throws UnknownHostException {
            if (addressesList.isEmpty()) {
                throw new UnknownHostException("Unable to resolve host " + hostName
                        + " in hosts file " + hostsFile);
            }
        }

        private String removeComments(String hostsEntry) {
            String filteredEntry = hostsEntry;
            int hashIndex;

            if ((hashIndex = hostsEntry.indexOf("#")) != -1) {
                filteredEntry = hostsEntry.substring(0, hashIndex);
            }
            return filteredEntry;
        }

        private byte [] createAddressByteArray(String addrStr) {
            byte[] addrArray;
            try {
                addrArray = IPAddressUtil.validateNumericFormatV4(addrStr, false);
            } catch (IllegalArgumentException iae) {
                return null;
            }
            if (addrArray == null) {
                addrArray = IPAddressUtil.textToNumericFormatV6(addrStr);
            }
            return addrArray;
        }

        /** host to ip address mapping */
        private String extractHostAddr(String hostEntry, String host) {
            String[] mapping = hostEntry.split("\\s+");
            String hostAddr = null;

            if (mapping.length >= 2) {
                for (int i = 1; i < mapping.length; i++) {
                    if (mapping[i].equalsIgnoreCase(host)) {
                        hostAddr = mapping[0];
                    }
                }
            }
            return hostAddr;
        }
    }

    static final InetAddressImpl  impl;

    /**
     * Platform-wide {@code LookupPolicy} initialized from {@code "java.net.preferIPv4Stack"},
     * {@code "java.net.preferIPv6Addresses"} system properties.
     */
    static final LookupPolicy PLATFORM_LOOKUP_POLICY;

    static {
        impl = isIPv6Supported() ?
                new Inet6AddressImpl() : new Inet4AddressImpl();

        PLATFORM_LOOKUP_POLICY = initializePlatformLookupPolicy();

        BUILTIN_RESOLVER = createBuiltinInetAddressResolver();
    }

    /**
     * Create an instance of the InetAddressResolver interface based on
     * the setting of the {@code jdk.net.hosts.file} system property.
     *
     * <p>The default InetAddressResolver is the PlatformResolver, which typically
     * delegates name and address resolution calls to the underlying
     * OS network libraries.
     *
     * <p> A HostsFileResolver is created if the {@code jdk.net.hosts.file}
     * system property is set. If the specified file doesn't exist, the name or
     * address lookup will result in an UnknownHostException. Thus, non existent
     * hosts file is handled as if the file is empty.
     *
     * @return an InetAddressResolver
     */
    private static InetAddressResolver createBuiltinInetAddressResolver() {
        InetAddressResolver theResolver;
        if (HOSTS_FILE_NAME != null) {
            theResolver = new HostsFileResolver(HOSTS_FILE_NAME);
        } else {
            theResolver = new PlatformResolver();
        }
        return theResolver;
    }

    /**
     * Creates an InetAddress based on the provided host name and IP address.
     * The system-wide {@linkplain InetAddressResolver resolver} is not used to check
     * the validity of the address.
     *
     * <p> The host name can either be a machine name, such as
     * "{@code www.example.com}", or a textual representation of its IP
     * address.
     * <p> No validity checking is done on the host name either.
     *
     * <p> If addr specifies an IPv4 address an instance of Inet4Address
     * will be returned; otherwise, an instance of Inet6Address
     * will be returned.
     *
     * <p> IPv4 address byte array must be 4 bytes long and IPv6 byte array
     * must be 16 bytes long
     *
     * @param host the specified host
     * @param addr the raw IP address in network byte order
     * @return  an InetAddress object created from the raw IP address.
     * @throws     UnknownHostException  if IP address is of illegal length
     * @since 1.4
     */
    public static InetAddress getByAddress(String host, byte[] addr)
        throws UnknownHostException {
        if (host != null && !host.isEmpty() && host.charAt(0) == '[') {
            if (host.charAt(host.length()-1) == ']') {
                host = host.substring(1, host.length() -1);
            }
        }
        if (addr != null) {
            if (addr.length == Inet4Address.INADDRSZ) {
                return new Inet4Address(host, addr);
            } else if (addr.length == Inet6Address.INADDRSZ) {
                byte[] newAddr
                    = IPAddressUtil.convertFromIPv4MappedAddress(addr);
                if (newAddr != null) {
                    return new Inet4Address(host, newAddr);
                } else {
                    return new Inet6Address(host, addr);
                }
            }
        }
        throw new UnknownHostException("addr is of illegal length");
    }


    /**
     * Determines the IP address of a host, given the host's name.
     *
     * <p> The host name can either be a machine name, such as
     * "{@code www.example.com}", or a textual representation of its
     * IP address. If a literal IP address is supplied, only the
     * validity of the address format is checked.
     *
     * <p> For {@code host} specified in literal IPv6 address,
     * either the form defined in RFC 2732 or the literal IPv6 address
     * format defined in RFC 2373 is accepted. IPv6 scoped addresses are also
     * supported. See <a href="Inet6Address.html#scoped">here</a> for a description of IPv6
     * scoped addresses.
     *
     * <p> If the host is {@code null} or {@code host.length()} is equal
     * to zero, then an {@code InetAddress} representing an address of the
     * loopback interface is returned.
     * See <a href="http:
     * section&nbsp;2 and <a href="http:
     * section&nbsp;2.5.3.
     *
     * <p> If there is a security manager, and {@code host} is not {@code null}
     * or {@code host.length() } is not equal to zero, the security manager's
     * {@code checkConnect} method is called with the hostname and {@code -1}
     * as its arguments to determine if the operation is allowed.
     *
     * @param      host   the specified host, or {@code null}.
     * @return     an IP address for the given host name.
     * @throws     UnknownHostException  if no IP address for the
     *               {@code host} could be found, or if a scope_id was specified
     *               for a global IPv6 address.
     * @throws     SecurityException if a security manager exists
     *             and its checkConnect method doesn't allow the operation
     *
     * @spec https:
     * @spec https:
     */
    public static InetAddress getByName(String host)
        throws UnknownHostException {
        return InetAddress.getAllByName(host)[0];
    }

    /**
     * Given the name of a host, returns an array of its IP addresses,
     * based on the system-wide {@linkplain InetAddressResolver resolver}.
     *
     * <p> The host name can either be a machine name, such as
     * "{@code www.example.com}", or a textual representation of its IP
     * address. If a literal IP address is supplied, only the
     * validity of the address format is checked.
     *
     * <p> For {@code host} specified in <i>literal IPv6 address</i>,
     * either the form defined in RFC 2732 or the literal IPv6 address
     * format defined in RFC 2373 is accepted. A literal IPv6 address may
     * also be qualified by appending a scoped zone identifier or scope_id.
     * The syntax and usage of scope_ids is described
     * <a href="Inet6Address.html#scoped">here</a>.
     *
     * <p> If the host is {@code null} or {@code host.length()} is equal
     * to zero, then an {@code InetAddress} representing an address of the
     * loopback interface is returned.
     * See <a href="http:
     * section&nbsp;2 and <a href="http:
     * section&nbsp;2.5.3. </p>
     *
     * <p> If there is a security manager, and {@code host} is not {@code null}
     * or {@code host.length() } is not equal to zero, the security manager's
     * {@code checkConnect} method is called with the hostname and {@code -1}
     * as its arguments to determine if the operation is allowed.
     *
     * @param      host   the name of the host, or {@code null}.
     * @return     an array of all the IP addresses for a given host name.
     *
     * @throws     UnknownHostException  if no IP address for the
     *               {@code host} could be found, or if a scope_id was specified
     *               for a global IPv6 address.
     * @throws     SecurityException  if a security manager exists and its
     *               {@code checkConnect} method doesn't allow the operation.
     *
     * @spec https:
     * @spec https:
     * @see SecurityManager#checkConnect
     */
    public static InetAddress[] getAllByName(String host)
        throws UnknownHostException {

        if (host == null || host.isEmpty()) {
            InetAddress[] ret = new InetAddress[1];
            ret[0] = impl.loopbackAddress();
            return ret;
        }

        validate(host);
        boolean ipv6Expected = false;
        if (host.charAt(0) == '[') {
            if (host.length() > 2 && host.charAt(host.length()-1) == ']') {
                host = host.substring(1, host.length() -1);
                ipv6Expected = true;
            } else {
                throw invalidIPv6LiteralException(host, false);
            }
        }

        if (IPAddressUtil.digit(host.charAt(0), 16) != -1
            || (host.charAt(0) == ':')) {
            InetAddress inetAddress = null;
            if (!ipv6Expected) {
                try {
                    inetAddress = Inet4Address.parseAddressString(host, false);
                } catch (IllegalArgumentException iae) {
                    var uhe = new UnknownHostException(host);
                    uhe.initCause(iae);
                    throw uhe;
                }
            }
            if (inetAddress == null) {
                if ((inetAddress = Inet6Address.parseAddressString(host, false)) == null &&
                        (host.contains(":") || ipv6Expected)) {
                    throw invalidIPv6LiteralException(host, ipv6Expected);
                }
            }
            if (inetAddress != null) {
                return new InetAddress[]{inetAddress};
            }
        } else if (ipv6Expected) {
            throw invalidIPv6LiteralException(host, true);
        }
        return getAllByName0(host, true, true);
    }

    private static UnknownHostException invalidIPv6LiteralException(String host, boolean wrapInBrackets) {
        String hostString = wrapInBrackets ? "[" + host + "]" : host;
        return new UnknownHostException(hostString + ": invalid IPv6 address literal");
    }

    /**
     * Returns the loopback address.
     * <p>
     * The InetAddress returned will represent the IPv4
     * loopback address, 127.0.0.1, or the IPv6 loopback
     * address, ::1. The IPv4 loopback address returned
     * is only one of many in the form 127.*.*.*
     *
     * @return  the InetAddress loopback instance.
     * @since 1.7
     */
    public static InetAddress getLoopbackAddress() {
        return impl.loopbackAddress();
    }

    /**
     * package private so SocketPermission can call it
     */
    static InetAddress[] getAllByName0 (String host, boolean check)
        throws UnknownHostException  {
        return getAllByName0(host, check, true);
    }

    /**
     * Creates an {@code InetAddress} based on the provided {@linkplain InetAddress##format
     * textual representation} of an IP address.
     * <p> The provided IP address literal is parsed as
     * {@linkplain Inet4Address#ofLiteral(String) an IPv4 address literal} first.
     * If it cannot be parsed as an IPv4 address literal, then the method attempts
     * to parse it as {@linkplain Inet6Address#ofLiteral(String) an IPv6 address literal}.
     * If neither attempts succeed an {@code IllegalArgumentException} is thrown.
     * <p> This method doesn't block, i.e. no reverse lookup is performed.
     *
     * @param ipAddressLiteral the textual representation of an IP address.
     * @return an {@link InetAddress} object with no hostname set, and constructed
     *         from the provided IP address literal.
     * @throws IllegalArgumentException if the {@code ipAddressLiteral} cannot be parsed
     *         as an IPv4 or IPv6 address literal.
     * @throws NullPointerException if the {@code ipAddressLiteral} is {@code null}.
     * @see Inet4Address#ofLiteral(String)
     * @see Inet6Address#ofLiteral(String)
     * @since 22
     */
    public static InetAddress ofLiteral(String ipAddressLiteral) {
        Objects.requireNonNull(ipAddressLiteral);
        InetAddress inetAddress;
        try {
            inetAddress = Inet4Address.ofLiteral(ipAddressLiteral);
        } catch (IllegalArgumentException iae) {
            inetAddress = Inet6Address.ofLiteral(ipAddressLiteral);
        }
        return inetAddress;
    }

    /**
     * Designated lookup method.
     *
     * @param host host name to look up
     * @param check perform security check
     * @param useCache use cached value if not expired else always
     *                 perform name service lookup (and cache the result)
     * @return array of InetAddress(es)
     * @throws UnknownHostException if host name is not found
     */
    private static InetAddress[] getAllByName0(String host,
                                               boolean check,
                                               boolean useCache)
        throws UnknownHostException  {

        /* If it gets here it is presumed to be a hostname */

        /* make sure the connection to the host is allowed, before we
         * give out a hostname
         */
        if (check) {
            @SuppressWarnings("removal")
            SecurityManager security = System.getSecurityManager();
            if (security != null) {
                security.checkConnect(host, -1);
            }
        }

        long now = System.nanoTime();
        for (CachedLookup caddrs : expirySet) {
            if (!caddrs.tryRemoveExpiredAddress(now)) {
                break;
            }
        }

        Addresses addrs;
        if (useCache) {
            addrs = cache.get(host);
        } else {
            addrs = cache.remove(host);
            if (addrs != null) {
                if (addrs instanceof CachedLookup) {
                    expirySet.remove(addrs);
                }
                addrs = null;
            }
        }

        if (addrs == null) {
            Addresses oldAddrs = cache.putIfAbsent(
                host,
                    addrs = new NameServiceAddresses(host)
            );
            if (oldAddrs != null) { 
                addrs = oldAddrs;
            }
        }

        return addrs.get().clone();
    }

    static InetAddress[] getAddressesFromNameService(String host)
            throws UnknownHostException {
        Stream<InetAddress> addresses = null;
        UnknownHostException ex = null;

        var resolver = resolver();
        try {
            addresses = resolver.lookupByName(host, PLATFORM_LOOKUP_POLICY);
        } catch (RuntimeException | UnknownHostException x) {
            if (host.equalsIgnoreCase("localhost")) {
                addresses = Stream.of(impl.loopbackAddress());
            } else if (x instanceof UnknownHostException uhe) {
                ex = uhe;
            } else {
                ex = new UnknownHostException();
                ex.initCause(x);
            }
        }
        InetAddress[] result = addresses == null ? null
                : addresses.toArray(InetAddress[]::new);
        if (result == null || result.length == 0) {
            throw ex == null ? new UnknownHostException(host) : ex;
        }
        return result;
    }

    /**
     * Returns an {@code InetAddress} object given the raw IP address .
     * The argument is in network byte order: the highest order
     * byte of the address is in {@code getAddress()[0]}.
     *
     * <p> This method doesn't block, i.e. no reverse lookup is performed.
     *
     * <p> IPv4 address byte array must be 4 bytes long and IPv6 byte array
     * must be 16 bytes long
     *
     * @param addr the raw IP address in network byte order
     * @return  an InetAddress object created from the raw IP address.
     * @throws     UnknownHostException  if IP address is of illegal length
     * @since 1.4
     */
    public static InetAddress getByAddress(byte[] addr)
        throws UnknownHostException {
        return getByAddress(null, addr);
    }

    private static final class CachedLocalHost {
        final String host;
        final InetAddress addr;
        final long expiryTime = System.nanoTime() + 5000_000_000L; 

        CachedLocalHost(String host, InetAddress addr) {
            this.host = host;
            this.addr = addr;
        }
    }

    private static volatile CachedLocalHost cachedLocalHost;

    /**
     * Returns the address of the local host. This is achieved by retrieving
     * the name of the host from the system, then resolving that name into
     * an {@code InetAddress}.
     *
     * <P>Note: The resolved address may be cached for a short period of time.
     * </P>
     *
     * <p>If there is a security manager, its
     * {@code checkConnect} method is called
     * with the local host name and {@code -1}
     * as its arguments to see if the operation is allowed.
     * If the operation is not allowed, an InetAddress representing
     * the loopback address is returned.
     *
     * @return     the address of the local host.
     *
     * @throws     UnknownHostException  if the local host name could not
     *             be resolved into an address.
     *
     * @see SecurityManager#checkConnect
     * @see java.net.InetAddress#getByName(java.lang.String)
     */
    public static InetAddress getLocalHost() throws UnknownHostException {

        @SuppressWarnings("removal")
        SecurityManager security = System.getSecurityManager();
        try {
            CachedLocalHost clh = cachedLocalHost;
            if (clh != null && (clh.expiryTime - System.nanoTime()) >= 0L) {
                if (security != null) {
                    security.checkConnect(clh.host, -1);
                }
                return clh.addr;
            }

            String local = impl.getLocalHostName();

            if (security != null) {
                security.checkConnect(local, -1);
            }

            InetAddress localAddr;
            if (local.equals("localhost")) {
                localAddr = impl.loopbackAddress();
            } else {
                try {
                    localAddr = getAllByName0(local, false, false)[0];
                } catch (UnknownHostException uhe) {
                    UnknownHostException uhe2 =
                        new UnknownHostException(local + ": " +
                                                 uhe.getMessage());
                    uhe2.initCause(uhe);
                    throw uhe2;
                }
            }
            cachedLocalHost = new CachedLocalHost(local, localAddr);
            return localAddr;
        } catch (java.lang.SecurityException e) {
            return impl.loopbackAddress();
        }
    }

    /**
     * Perform class load-time initializations.
     */
    private static native void init();


    /*
     * Returns the InetAddress representing anyLocalAddress
     * (typically 0.0.0.0 or ::0)
     */
    static InetAddress anyLocalAddress() {
        return impl.anyLocalAddress();
    }

    private static final jdk.internal.misc.Unsafe UNSAFE
            = jdk.internal.misc.Unsafe.getUnsafe();
    private static final long FIELDS_OFFSET
            = UNSAFE.objectFieldOffset(InetAddress.class, "holder");

    /**
     * Restores the state of this object from the stream.
     *
     * @param  s the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @java.io.Serial
    private void readObject (ObjectInputStream s) throws
                         IOException, ClassNotFoundException {
        GetField gf = s.readFields();
        String host = (String)gf.get("hostName", null);
        int address = gf.get("address", 0);
        int family = gf.get("family", 0);
        if (family != IPv4 && family != IPv6) {
            throw new InvalidObjectException("invalid address family type: " + family);
        }
        InetAddressHolder h = new InetAddressHolder(host, address, family);
        UNSAFE.putReference(this, FIELDS_OFFSET, h);
    }

    /* needed because the serializable fields no longer exist */

    /**
     * @serialField hostName String the hostname for this address
     * @serialField address int holds a 32-bit IPv4 address.
     * @serialField family int specifies the address family type, for instance,
     * {@code '1'} for IPv4 addresses, and {@code '2'} for IPv6 addresses.
     */
    @java.io.Serial
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("hostName", String.class),
        new ObjectStreamField("address", int.class),
        new ObjectStreamField("family", int.class),
    };

    /**
     * Writes the state of this object to the stream.
     *
     * @param  s the {@code ObjectOutputStream} to which data is written
     * @throws IOException if an I/O error occurs
     */
    @java.io.Serial
    private void writeObject (ObjectOutputStream s) throws IOException {
        PutField pf = s.putFields();
        pf.put("hostName", holder().getHostName());
        pf.put("address", holder().getAddress());
        pf.put("family", holder().getFamily());
        s.writeFields();
    }

    private static void validate(String host) throws UnknownHostException {
        if (host.indexOf(0) != -1) {
            throw new UnknownHostException("NUL character not allowed in hostname");
        }
    }
}
