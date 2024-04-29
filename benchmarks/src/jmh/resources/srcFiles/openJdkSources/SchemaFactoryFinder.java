/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.validation;

import com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import jdk.xml.internal.SecuritySupport;

/**
 * Implementation of {@link SchemaFactory#newInstance(String)}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.5
 */
class SchemaFactoryFinder  {

    /** debug support code. */
    private static boolean debug = false;

    private static final String DEFAULT_PACKAGE = "com.sun.org.apache.xerces.internal";

    static {
        try {
            debug = SecuritySupport.getSystemProperty("jaxp.debug") != null;
        } catch (Exception unused) {
            debug = false;
        }
    }

    /**
     * <p>Conditional debug printing.</p>
     *
     * @param msgGen Supplier function that returns debug message
     */
    private static void debugPrintln(Supplier<String> msgGen) {
        if (debug) {
            System.err.println("JAXP: " + msgGen.get());
        }
    }

    /**
     * <p><code>ClassLoader</code> to use to find <code>SchemaFactory</code>.</p>
     */
    private final ClassLoader classLoader;

    /**
     * <p>Constructor that specifies <code>ClassLoader</code> to use
     * to find <code>SchemaFactory</code>.</p>
     *
     * @param loader
     *      to be used to load resource, {@link SchemaFactory}, and
     *      {@link SchemaFactoryLoader} implementations during
     *      the resolution process.
     *      If this parameter is null, the default system class loader
     *      will be used.
     */
    public SchemaFactoryFinder(ClassLoader loader) {
        this.classLoader = loader;
        if( debug ) {
            debugDisplayClassLoader();
        }
    }

    private void debugDisplayClassLoader() {
        try {
            if( classLoader == SecuritySupport.getContextClassLoader() ) {
                debugPrintln(()->"using thread context class loader ("+classLoader+") for search");
                return;
            }
        } catch( Throwable unused ) {
        }

        if( classLoader==ClassLoader.getSystemClassLoader() ) {
            debugPrintln(()->"using system class loader ("+classLoader+") for search");
            return;
        }

        debugPrintln(()->"using class loader ("+classLoader+") for search");
    }

    /**
     * <p>Creates a new {@link SchemaFactory} object for the specified
     * schema language.</p>
     *
     * @param schemaLanguage
     *      See {@link SchemaFactory Schema Language} table in <code>SchemaFactory</code>
     *      for the list of available schema languages.
     *
     * @return <code>null</code> if the callee fails to create one.
     *
     * @throws NullPointerException
     *      If the <code>schemaLanguage</code> parameter is null.
     * @throws SchemaFactoryConfigurationError
     *      If a configuration error is encountered.
     */
    public SchemaFactory newFactory(String schemaLanguage) {
        if(schemaLanguage==null) {
            throw new NullPointerException();
        }
        SchemaFactory f = _newFactory(schemaLanguage);
        if (f != null) {
            debugPrintln(()->"factory '" + f.getClass().getName() + "' was found for " + schemaLanguage);
        } else {
            debugPrintln(()->"unable to find a factory for " + schemaLanguage);
        }
        return f;
    }

    /**
     * <p>Lookup a <code>SchemaFactory</code> for the given <code>schemaLanguage</code>.</p>
     *
     * @param schemaLanguage Schema language to lookup <code>SchemaFactory</code> for.
     *
     * @return <code>SchemaFactory</code> for the given <code>schemaLanguage</code>.
     */
    private SchemaFactory _newFactory(String schemaLanguage) {
        SchemaFactory sf;

        String propertyName = SERVICE_CLASS.getName() + ":" + schemaLanguage;

        try {
            debugPrintln(()->"Looking up system property '"+propertyName+"'" );
            String r = SecuritySupport.getSystemProperty(propertyName);
            if(r!=null) {
                debugPrintln(()->"The value is '"+r+"'");
                sf = createInstance(r);
                if(sf!=null)    return sf;
            } else
                debugPrintln(()->"The property is undefined.");
        } catch( Throwable t ) {
            if( debug ) {
                debugPrintln(()->"failed to look up system property '"+propertyName+"'" );
                t.printStackTrace();
            }
        }

        String factoryClassName = SecuritySupport.readConfig(propertyName);
        if (factoryClassName != null) {
            sf = createInstance(factoryClassName);
            if(sf != null){
                return sf;
            }
        }

        final SchemaFactory factoryImpl = findServiceProvider(schemaLanguage);


        if (factoryImpl != null) {
            return factoryImpl;
        }

        if(schemaLanguage.equals("http:
            debugPrintln(()->"attempting to use the platform default XML Schema validator");
            return new XMLSchemaFactory();
        }

        debugPrintln(()->"all things were tried, but none was found. bailing out.");
        return null;
    }

    /** <p>Create class using appropriate ClassLoader.</p>
     *
     * @param className Name of class to create.
     * @return Created class or <code>null</code>.
     */
    @SuppressWarnings("removal")
    private Class<?> createClass(String className) {
        Class<?> clazz;
        boolean internal = false;
        if (System.getSecurityManager() != null) {
            if (className != null && className.startsWith(DEFAULT_PACKAGE)) {
                internal = true;
            }
        }

        try {
            if (classLoader != null && !internal) {
                clazz = Class.forName(className, false, classLoader);
            } else {
                clazz = Class.forName(className);
            }
        } catch (Throwable t) {
            if(debug)  {
                t.printStackTrace();
            }
            return null;
        }

        return clazz;
    }

    /**
     * <p>Creates an instance of the specified and returns it.</p>
     *
     * @param className
     *      fully qualified class name to be instantiated.
     *
     * @return null
     *      if it fails. Error messages will be printed by this method.
     */
    SchemaFactory createInstance(String className) {
        SchemaFactory schemaFactory = null;

        debugPrintln(()->"createInstance(" + className + ")");

        Class<?> clazz = createClass(className);
        if (clazz == null) {
            debugPrintln(()->"failed to getClass(" + className + ")");
            return null;
        }
        debugPrintln(()->"loaded " + className + " from " + which(clazz));

        try {
            if (!SchemaFactory.class.isAssignableFrom(clazz)) {
                throw new ClassCastException(clazz.getName()
                            + " cannot be cast to " + SchemaFactory.class);
            }
            schemaFactory = (SchemaFactory) clazz.getConstructor().newInstance();
        } catch (ClassCastException | IllegalAccessException | IllegalArgumentException |
            InstantiationException | InvocationTargetException | NoSuchMethodException |
            SecurityException ex) {
            debugPrintln(()->"could not instantiate " + clazz.getName());
            if (debug) {
                    ex.printStackTrace();
            }
            return null;
        }

        return schemaFactory;
    }

    @SuppressWarnings("removal")
    private boolean isSchemaLanguageSupportedBy(final SchemaFactory factory,
            final String schemaLanguage,
            AccessControlContext acc) {
        return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            public Boolean run() {
                return factory.isSchemaLanguageSupported(schemaLanguage);
            }
        }, acc);
    }

    /**
     * Finds a service provider subclass of SchemaFactory that supports the
     * given schema language using the ServiceLoader.
     *
     * @param schemaLanguage The schema language for which we seek a factory.
     * @return A SchemaFactory supporting the specified schema language, or null
     *         if none is found.
     * @throws SchemaFactoryConfigurationError if a configuration error is found.
     */
    @SuppressWarnings("removal")
    private SchemaFactory findServiceProvider(final String schemaLanguage) {
        assert schemaLanguage != null;
        final AccessControlContext acc = AccessController.getContext();
        try {
            return AccessController.doPrivileged(new PrivilegedAction<SchemaFactory>() {
                public SchemaFactory run() {
                    final ServiceLoader<SchemaFactory> loader =
                            ServiceLoader.load(SERVICE_CLASS);
                    for (SchemaFactory factory : loader) {
                        if (isSchemaLanguageSupportedBy(factory, schemaLanguage, acc)) {
                            return factory;
                        }
                    }
                    return null; 
                }
            });
        } catch (ServiceConfigurationError error) {
            throw new SchemaFactoryConfigurationError(
                    "Provider for " + SERVICE_CLASS + " cannot be created", error);
        }
    }

    private static final Class<SchemaFactory> SERVICE_CLASS = SchemaFactory.class;


    private static String which( Class<?> clazz ) {
        return SecuritySupport.getClassSource(clazz);
    }
}
