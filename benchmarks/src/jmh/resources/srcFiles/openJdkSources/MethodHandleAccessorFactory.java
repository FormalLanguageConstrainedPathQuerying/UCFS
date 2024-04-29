/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;

import static java.lang.invoke.MethodType.genericMethodType;
import static java.lang.invoke.MethodType.methodType;
import static jdk.internal.reflect.MethodHandleAccessorFactory.LazyStaticHolder.*;

final class MethodHandleAccessorFactory {
    /**
     * Creates a MethodAccessor for the given reflected method.
     *
     * If the given method is called before the java.lang.invoke initialization
     * or the given method is a native method, it will use the native VM reflection
     * support.
     *
     * If the given method is a caller-sensitive method and the corresponding
     * caller-sensitive adapter with the caller class parameter is present,
     * it will use the method handle of the caller-sensitive adapter.
     *
     * Otherwise, it will use the direct method handle of the given method.
     *
     * @see CallerSensitive
     * @see CallerSensitiveAdapter
     */
    static MethodAccessorImpl newMethodAccessor(Method method, boolean callerSensitive) {
        if (useNativeAccessor(method)) {
            return DirectMethodHandleAccessor.nativeAccessor(method, callerSensitive);
        }

        ensureClassInitialized(method.getDeclaringClass());

        try {
            if (callerSensitive) {
                var dmh = findCallerSensitiveAdapter(method);
                if (dmh != null) {
                    return DirectMethodHandleAccessor.callerSensitiveAdapter(method, dmh);
                }
            }
            var dmh = getDirectMethod(method, callerSensitive);
            return DirectMethodHandleAccessor.methodAccessor(method, dmh);
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Creates a ConstructorAccessor for the given reflected constructor.
     *
     * If a given constructor is called before the java.lang.invoke initialization,
     * it will use the native VM reflection support.
     *
     * Otherwise, it will use the direct method handle of the given constructor.
     */
    static ConstructorAccessorImpl newConstructorAccessor(Constructor<?> ctor) {
        if (useNativeAccessor(ctor)) {
            return DirectConstructorHandleAccessor.nativeAccessor(ctor);
        }

        ensureClassInitialized(ctor.getDeclaringClass());
        try {
            MethodHandle target = makeConstructorHandle(JLIA.unreflectConstructor(ctor));
            return DirectConstructorHandleAccessor.constructorAccessor(ctor, target);
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Creates a ConstructorAccessor that is capable of creating instances
     * of the given class and instantiated by the given constructor.
     *
     * @param decl the class to instantiate
     * @param ctor the constructor to call
     * @return an accessible constructor
     */
    static ConstructorAccessorImpl newSerializableConstructorAccessor(Class<?> decl, Constructor<?> ctor) {
        if (!constructorInSuperclass(decl, ctor)) {
            throw new UnsupportedOperationException(ctor + " not a superclass of " + decl.getName());
        }

        ensureClassInitialized(decl);
        try {
            MethodHandle target = makeConstructorHandle(JLIA.serializableConstructor(decl, ctor));
            return DirectConstructorHandleAccessor.constructorAccessor(ctor, target);
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    private static boolean constructorInSuperclass(Class<?> decl, Constructor<?> ctor) {
        if (decl == ctor.getDeclaringClass())
            return true;

        Class<?> cl = decl;
        while ((cl = cl.getSuperclass()) != null) {
            if (cl == ctor.getDeclaringClass()) {
                return true;
            }
        }
        return false;
    }

    private static MethodHandle makeConstructorHandle(MethodHandle ctor) {
        int paramCount = ctor.type().parameterCount();
        MethodHandle target = ctor.asFixedArity();
        MethodType mtype = specializedMethodTypeForConstructor(paramCount);
        if (paramCount > SPECIALIZED_PARAM_COUNT) {
            target = target.asSpreader(Object[].class, paramCount);
        }
        return target.asType(mtype);
    }

    /**
     * Creates a FieldAccessor for the given reflected field.
     *
     * Limitation: Field access via core reflection is only supported after
     * java.lang.invoke completes initialization.
     * java.lang.invoke initialization starts soon after System::initPhase1
     * and method handles are ready for use when initPhase2 begins.
     * During early VM startup (initPhase1), fields can be accessed directly
     * from the VM or through JNI.
     */
    static FieldAccessorImpl newFieldAccessor(Field field, boolean isReadOnly) {
        if (!VM.isJavaLangInvokeInited()) {
            throw new InternalError(field.getDeclaringClass().getName() + "::" + field.getName() +
                    " cannot be accessed reflectively before java.lang.invoke is initialized");
        }

        ensureClassInitialized(field.getDeclaringClass());

        try {
            var getter = JLIA.unreflectField(field, false);
            var setter = isReadOnly ? null : JLIA.unreflectField(field, true);
            Class<?> type = field.getType();
            if (type == Boolean.TYPE) {
                return MethodHandleBooleanFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Byte.TYPE) {
                return MethodHandleByteFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Short.TYPE) {
                return MethodHandleShortFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Character.TYPE) {
                return MethodHandleCharacterFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Integer.TYPE) {
                return MethodHandleIntegerFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Long.TYPE) {
                return MethodHandleLongFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Float.TYPE) {
                return MethodHandleFloatFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else if (type == Double.TYPE) {
                return MethodHandleDoubleFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            } else {
                return MethodHandleObjectFieldAccessorImpl.fieldAccessor(field, getter, setter, isReadOnly);
            }
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    private static MethodHandle getDirectMethod(Method method, boolean callerSensitive) throws IllegalAccessException {
        var mtype = methodType(method.getReturnType(), method.getParameterTypes());
        var isStatic = Modifier.isStatic(method.getModifiers());
        var dmh = isStatic ? JLIA.findStatic(method.getDeclaringClass(), method.getName(), mtype)
                                        : JLIA.findVirtual(method.getDeclaringClass(), method.getName(), mtype);
        if (callerSensitive) {
            return makeTarget(dmh, isStatic, false);
        }
        return makeSpecializedTarget(dmh, isStatic, false);
    }

    /**
     * Finds the method handle of a caller-sensitive adapter for the given
     * caller-sensitive method.  It has the same name as the given method
     * with a trailing caller class parameter.
     *
     * @see CallerSensitiveAdapter
     */
    private static MethodHandle findCallerSensitiveAdapter(Method method) throws IllegalAccessException {
        String name = method.getName();
        MethodType mtype = methodType(method.getReturnType(), method.getParameterTypes())
                                .appendParameterTypes(Class.class);
        boolean isStatic = Modifier.isStatic(method.getModifiers());

        MethodHandle dmh = isStatic ? JLIA.findStatic(method.getDeclaringClass(), name, mtype)
                                    : JLIA.findVirtual(method.getDeclaringClass(), name, mtype);
        return dmh != null ? makeSpecializedTarget(dmh, isStatic, true) : null;
    }

    /**
     * Transform the given dmh to a specialized target method handle.
     *
     * If {@code hasCallerParameter} parameter is true, transform the method handle
     * of this method type: {@code (Object, Object[], Class)Object} for the default
     * case.
     *
     * If {@code hasCallerParameter} parameter is false, transform the method handle
     * of this method type: {@code (Object, Object[])Object} for the default case.
     *
     * If the number of formal arguments is small, use a method type specialized
     * the number of formal arguments is 0, 1, and 2, for example, the method type
     * of a static method with one argument can be: {@code (Object)Object}
     *
     * If it's a static method, there is no leading Object parameter.
     *
     * @apiNote
     * This implementation avoids using MethodHandles::catchException to help
     * cold startup performance since this combination is very costly to setup.
     *
     * @param dmh DirectMethodHandle
     * @param isStatic whether given dmh represents static method or not
     * @param hasCallerParameter whether given dmh represents a method with an
     *                         additional caller Class parameter
     * @return transformed dmh to be used as a target in direct method accessors
     */
    static MethodHandle makeSpecializedTarget(MethodHandle dmh, boolean isStatic, boolean hasCallerParameter) {
        MethodHandle target = dmh.asFixedArity();

        int paramCount = dmh.type().parameterCount() - (isStatic ? 0 : 1) - (hasCallerParameter ? 1 : 0);
        MethodType mtype = specializedMethodType(isStatic, hasCallerParameter, paramCount);
        if (paramCount > SPECIALIZED_PARAM_COUNT) {
            int spreadArgPos = isStatic ? 0 : 1;
            target = target.asSpreader(spreadArgPos, Object[].class, paramCount);
        }
        if (isStatic) {
            target = MethodHandles.dropArguments(target, 0, Object.class);
        }
        return target.asType(mtype);
    }

    static final int SPECIALIZED_PARAM_COUNT = 3;
    static MethodType specializedMethodType(boolean isStatic, boolean hasCallerParameter, int paramCount) {
        return switch (paramCount) {
            case 0 -> hasCallerParameter ? methodType(Object.class, Object.class, Class.class)
                                         : genericMethodType(1);
            case 1 -> hasCallerParameter ? methodType(Object.class, Object.class, Object.class, Class.class)
                                         : genericMethodType(2);
            case 2 -> hasCallerParameter ? methodType(Object.class, Object.class, Object.class, Object.class, Class.class)
                                         : genericMethodType(3);
            case 3 -> hasCallerParameter ? methodType(Object.class, Object.class, Object.class, Object.class, Object.class, Class.class)
                                         : genericMethodType(4);
            default -> hasCallerParameter ? methodType(Object.class, Object.class, Object[].class, Class.class)
                                          : genericMethodType(1, true);
        };
    }

    static MethodType specializedMethodTypeForConstructor(int paramCount) {
        return switch (paramCount) {
            case 0 ->  genericMethodType(0);
            case 1 ->  genericMethodType(1);
            case 2 ->  genericMethodType(2);
            case 3 ->  genericMethodType(3);
            default -> genericMethodType(0, true);
        };
    }

    /**
     * Transforms the given dmh into a target method handle with the method type
     * {@code (Object, Object[])Object} or {@code (Object, Class, Object[])Object}
     */
    static MethodHandle makeTarget(MethodHandle dmh, boolean isStatic, boolean hasCallerParameter) {
        MethodType mtype = hasCallerParameter
                                ? methodType(Object.class, Object.class, Object[].class, Class.class)
                                : genericMethodType(1, true);
        int paramCount = dmh.type().parameterCount() - (isStatic ? 0 : 1) - (hasCallerParameter ? 1 : 0);
        int spreadArgPos = isStatic ? 0 : 1;
        MethodHandle target = dmh.asFixedArity().asSpreader(spreadArgPos, Object[].class, paramCount);
        if (isStatic) {
            target = MethodHandles.dropArguments(target, 0, Object.class);
        }
        return target.asType(mtype);
    }

    /**
     * Ensures the given class is initialized.  If this is called from <clinit>,
     * this method returns but defc's class initialization is not completed.
     */
    static void ensureClassInitialized(Class<?> defc) {
        if (UNSAFE.shouldBeInitialized(defc)) {
            UNSAFE.ensureClassInitialized(defc);
        }
    }

    /*
     * Returns true if NativeAccessor should be used.
     *
     * Native accessor, i.e. VM reflection implementation, is used if one of
     * the following conditions is met:
     * 1. during VM early startup before method handle support is fully initialized
     * 2. a Java native method
     * 3. -Djdk.reflect.useNativeAccessorOnly=true is set
     * 4. the member takes a variable number of arguments and the last parameter
     *    is not an array (see details below)
     * 5. the member's method type has an arity >= 255
     *
     * Otherwise, direct invocation of method handles is used.
     */
    private static boolean useNativeAccessor(Executable member) {
        if (!VM.isJavaLangInvokeInited())
            return true;

        if (Modifier.isNative(member.getModifiers()))
            return true;

        if (ReflectionFactory.useNativeAccessorOnly())  
            return true;

        int paramCount = member.getParameterCount();
        if (member.isVarArgs() &&
                (paramCount == 0 || !(member.getParameterTypes()[paramCount-1].isArray()))) {
            return true;
        }
        if (slotCount(member) >= MAX_JVM_ARITY) {
            return true;
        }
        return false;
    }

    private static final int MAX_JVM_ARITY = 255;  
    /*
     * Return number of slots of the given member.
     * - long/double args counts for two argument slots
     * - A non-static method consumes an extra argument for the object on which
     *   the method is called.
     * - A constructor consumes an extra argument for the object which is being constructed.
     */
    private static int slotCount(Executable member) {
        int slots = 0;
        Class<?>[] ptypes = member.getParameterTypes();
        for (Class<?> ptype : ptypes) {
            if (ptype == double.class || ptype == long.class) {
                slots++;
            }
        }
        return ptypes.length + slots +
                (Modifier.isStatic(member.getModifiers()) ? 0 : 1);
    }

    /*
     * Delay initializing these static fields until java.lang.invoke is fully initialized.
     */
    static class LazyStaticHolder {
        static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();
    }

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
}
