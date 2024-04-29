/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.runtime;

public interface ClassConstants
{

    public static final int JVM_CONSTANT_Utf8               = 1;
    public static final int JVM_CONSTANT_Unicode            = 2; 
    public static final int JVM_CONSTANT_Integer            = 3;
    public static final int JVM_CONSTANT_Float              = 4;
    public static final int JVM_CONSTANT_Long               = 5;
    public static final int JVM_CONSTANT_Double             = 6;
    public static final int JVM_CONSTANT_Class              = 7;
    public static final int JVM_CONSTANT_String             = 8;
    public static final int JVM_CONSTANT_Fieldref           = 9;
    public static final int JVM_CONSTANT_Methodref          = 10;
    public static final int JVM_CONSTANT_InterfaceMethodref = 11;
    public static final int JVM_CONSTANT_NameAndType        = 12;
    public static final int JVM_CONSTANT_MethodHandle       = 15;
    public static final int JVM_CONSTANT_MethodType         = 16;
    public static final int JVM_CONSTANT_Dynamic            = 17;
    public static final int JVM_CONSTANT_InvokeDynamic      = 18;
    public static final int JVM_CONSTANT_Module             = 19;
    public static final int JVM_CONSTANT_Package            = 20;

    public static final int JVM_REF_getField                = 1;
    public static final int JVM_REF_getStatic               = 2;
    public static final int JVM_REF_putField                = 3;
    public static final int JVM_REF_putStatic               = 4;
    public static final int JVM_REF_invokeVirtual           = 5;
    public static final int JVM_REF_invokeStatic            = 6;
    public static final int JVM_REF_invokeSpecial           = 7;
    public static final int JVM_REF_newInvokeSpecial        = 8;
    public static final int JVM_REF_invokeInterface         = 9;


    public static final int JVM_CONSTANT_Invalid            = 0;

    public static final int JVM_CONSTANT_UnresolvedClass          = 100;  
    public static final int JVM_CONSTANT_ClassIndex               = 101;  
    public static final int JVM_CONSTANT_StringIndex              = 102;  
    public static final int JVM_CONSTANT_UnresolvedClassInError   = 103;  
    public static final int JVM_CONSTANT_MethodHandleInError      = 104;  
    public static final int JVM_CONSTANT_MethodTypeInError        = 105;  

    public static final short MAJOR_VERSION = 49;
    public static final short MINOR_VERSION = 0;

    public static final short MAJOR_VERSION_OLD = 46;
    public static final short MINOR_VERSION_OLD = 0;

    public static final long JVM_ACC_PUBLIC       = 0x0001; /* visible to everyone */
    public static final long JVM_ACC_PRIVATE      = 0x0002; /* visible only to the defining class */
    public static final long JVM_ACC_PROTECTED    = 0x0004; /* visible to subclasses */
    public static final long JVM_ACC_STATIC       = 0x0008; /* instance variable is static */
    public static final long JVM_ACC_FINAL        = 0x0010; /* no further subclassing, overriding */
    public static final long JVM_ACC_SYNCHRONIZED = 0x0020; /* wrap method call in monitor lock */
    public static final long JVM_ACC_SUPER        = 0x0020; /* funky handling of invokespecial */
    public static final long JVM_ACC_VOLATILE     = 0x0040; /* can not cache in registers */
    public static final long JVM_ACC_BRIDGE       = 0x0040; /* bridge method generated by compiler */
    public static final long JVM_ACC_TRANSIENT    = 0x0080; /* not persistent */
    public static final long JVM_ACC_VARARGS      = 0x0080; /* method declared with variable number of args */
    public static final long JVM_ACC_NATIVE       = 0x0100; /* implemented in C */
    public static final long JVM_ACC_INTERFACE    = 0x0200; /* class is an interface */
    public static final long JVM_ACC_ABSTRACT     = 0x0400; /* no definition provided */
    public static final long JVM_ACC_STRICT       = 0x0800; /* strict floating point */
    public static final long JVM_ACC_SYNTHETIC    = 0x1000; /* compiler-generated class, method or field */
    public static final long JVM_ACC_ANNOTATION   = 0x2000; /* annotation type */
    public static final long JVM_ACC_ENUM         = 0x4000; /* field is declared as element of enum */



    public static final long JVM_ACC_WRITTEN_FLAGS = 0x00007FFF;

    public static final long JVM_ACC_HAS_FINALIZER            = 0x40000000;
    public static final long JVM_ACC_IS_CLONEABLE             = 0x80000000;

    public static final long JVM_ACC_FIELD_FLAGS = 0x00008000 | JVM_ACC_WRITTEN_FLAGS;


    public static final long JVM_RECOGNIZED_CLASS_MODIFIERS   = (JVM_ACC_PUBLIC |
                                                                 JVM_ACC_FINAL |
                                                                 JVM_ACC_SUPER |
                                                                 JVM_ACC_INTERFACE |
                                                                 JVM_ACC_ABSTRACT |
                                                                 JVM_ACC_ANNOTATION |
                                                                 JVM_ACC_ENUM |
                                                                 JVM_ACC_SYNTHETIC);


    public static final long JVM_RECOGNIZED_FIELD_MODIFIERS  = (JVM_ACC_PUBLIC |
                                                                JVM_ACC_PRIVATE |
                                                                JVM_ACC_PROTECTED |
                                                                JVM_ACC_STATIC |
                                                                JVM_ACC_FINAL |
                                                                JVM_ACC_VOLATILE |
                                                                JVM_ACC_TRANSIENT |
                                                                JVM_ACC_ENUM |
                                                                JVM_ACC_SYNTHETIC);

    public static final long JVM_RECOGNIZED_METHOD_MODIFIERS  = (JVM_ACC_PUBLIC |
                                                                 JVM_ACC_PRIVATE |
                                                                 JVM_ACC_PROTECTED |
                                                                 JVM_ACC_STATIC |
                                                                 JVM_ACC_FINAL |
                                                                 JVM_ACC_SYNCHRONIZED |
                                                                 JVM_ACC_BRIDGE |
                                                                 JVM_ACC_VARARGS |
                                                                 JVM_ACC_NATIVE |
                                                                 JVM_ACC_ABSTRACT |
                                                                 JVM_ACC_STRICT |
                                                                 JVM_ACC_SYNTHETIC);
}
