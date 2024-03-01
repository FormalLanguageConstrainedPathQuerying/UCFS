/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.lang.reflect.ReflectPermission;
import java.net.NetPermission;
import java.net.SocketPermission;
import java.security.AllPermission;
import java.security.SecurityPermission;
import sun.security.action.GetPropertyAction;

/**
 * Permission constants and string constants used to create permissions
 * used throughout the JDK.
 */
public final class SecurityConstants {
    private SecurityConstants () {
    }


    public static final String FILE_DELETE_ACTION = "delete";
    public static final String FILE_EXECUTE_ACTION = "execute";
    public static final String FILE_READ_ACTION = "read";
    public static final String FILE_WRITE_ACTION = "write";
    public static final String FILE_READLINK_ACTION = "readlink";

    public static final String SOCKET_RESOLVE_ACTION = "resolve";
    public static final String SOCKET_CONNECT_ACTION = "connect";
    public static final String SOCKET_LISTEN_ACTION = "listen";
    public static final String SOCKET_ACCEPT_ACTION = "accept";
    public static final String SOCKET_CONNECT_ACCEPT_ACTION = "connect,accept";

    public static final String PROPERTY_RW_ACTION = "read,write";
    public static final String PROPERTY_READ_ACTION = "read";
    public static final String PROPERTY_WRITE_ACTION = "write";


    public static final AllPermission ALL_PERMISSION = new AllPermission();

    public static final NetPermission SPECIFY_HANDLER_PERMISSION =
       new NetPermission("specifyStreamHandler");

    public static final NetPermission SET_PROXYSELECTOR_PERMISSION =
       new NetPermission("setProxySelector");

    public static final NetPermission GET_PROXYSELECTOR_PERMISSION =
       new NetPermission("getProxySelector");

    public static final NetPermission SET_COOKIEHANDLER_PERMISSION =
       new NetPermission("setCookieHandler");

    public static final NetPermission GET_COOKIEHANDLER_PERMISSION =
       new NetPermission("getCookieHandler");

    public static final NetPermission SET_RESPONSECACHE_PERMISSION =
       new NetPermission("setResponseCache");

    public static final NetPermission GET_RESPONSECACHE_PERMISSION =
       new NetPermission("getResponseCache");

    public static final NetPermission SET_SOCKETIMPL_PERMISSION =
        new NetPermission("setSocketImpl");

    public static final RuntimePermission CREATE_CLASSLOADER_PERMISSION =
        new RuntimePermission("createClassLoader");

    public static final RuntimePermission CHECK_MEMBER_ACCESS_PERMISSION =
        new RuntimePermission("accessDeclaredMembers");

    public static final RuntimePermission MODIFY_THREAD_PERMISSION =
        new RuntimePermission("modifyThread");

    public static final RuntimePermission MODIFY_THREADGROUP_PERMISSION =
        new RuntimePermission("modifyThreadGroup");

    public static final RuntimePermission GET_PD_PERMISSION =
        new RuntimePermission("getProtectionDomain");

    public static final RuntimePermission GET_CLASSLOADER_PERMISSION =
        new RuntimePermission("getClassLoader");

    public static final RuntimePermission GET_STACK_TRACE_PERMISSION =
       new RuntimePermission("getStackTrace");

    public static final RuntimePermission SUBCLASS_IMPLEMENTATION_PERMISSION =
        new RuntimePermission("enableContextClassLoaderOverride");

    public static final SecurityPermission CREATE_ACC_PERMISSION =
       new SecurityPermission("createAccessControlContext");

    public static final SecurityPermission GET_COMBINER_PERMISSION =
       new SecurityPermission("getDomainCombiner");

    public static final SecurityPermission GET_POLICY_PERMISSION =
        new SecurityPermission ("getPolicy");

    public static final SocketPermission LOCAL_LISTEN_PERMISSION =
        new SocketPermission("localhost:0", SOCKET_LISTEN_ACTION);

    public static final String PROVIDER_VER =
        GetPropertyAction.privilegedGetProperty("java.specification.version");

    public static final ReflectPermission ACCESS_PERMISSION =
        new ReflectPermission("suppressAccessChecks");

    public static final RuntimePermission REFLECTION_FACTORY_ACCESS_PERMISSION =
        new RuntimePermission("reflectionFactoryAccess");

}
