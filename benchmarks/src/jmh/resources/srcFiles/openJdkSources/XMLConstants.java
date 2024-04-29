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

package javax.xml;

/**
 * Defines constants for XML Processing APIs.
 *
 * <h2 id="EAP">External Access Properties</h2>
 * The value of the external access properties, including {@link #ACCESS_EXTERNAL_DTD},
 * {@link #ACCESS_EXTERNAL_SCHEMA}, and {@link #ACCESS_EXTERNAL_STYLESHEET},
 * is defined as follows.
 *
 * <h3 id="EAPValue">Value:</h3>
 * A list of protocols separated by comma. A protocol is the scheme portion of a
 * {@link java.net.URI}, or in the case of the JAR protocol, "jar" plus the scheme
 * portion separated by colon. A scheme is defined as:
 *
 * <blockquote>
 * scheme = alpha *( alpha | digit | "+" | "-" | "." )<br>
 * where alpha = a-z and A-Z.<br><br>
 *
 * And the JAR protocol:<br>
 *
 * jar[:scheme]<br><br>
 *
 * Protocols including the keyword "jar" are case-insensitive. Any whitespaces as defined by
 * {@link java.lang.Character#isSpaceChar } in the value will be ignored.
 * Examples of protocols are file, http, jar:file.
 *
 * </blockquote>
 *
 * <h3>Default value:</h3>
 * The default value is implementation specific and therefore not specified.
 * The following options are provided for consideration:
 * <blockquote>
 * <UL>
 *     <LI>an empty string to deny all access to external references;</LI>
 *     <LI>a specific protocol, such as file, to give permission to only the protocol;</LI>
 *     <LI>the keyword "all" to grant  permission to all protocols.</LI>
 * </UL><br>
 *      When FEATURE_SECURE_PROCESSING is enabled,  it is recommended that implementations
 *      restrict external connections by default, though this may cause problems for applications
 *      that process XML/XSD/XSL with external references.
 * </blockquote>
 *
 * <h3>Granting all access:</h3>
 * The keyword "all" grants permission to all protocols.
 *
 * <h2 id="PropPrec">Property Precedence</h2>
 * Properties, including the <a href="#EAP">External Access Properties</a> and
 * {@link #USE_CATALOG}, can be specified through multiple configuration sources.
 * They follow the configuration process as defined in the
 * <a href="{@docRoot}/java.xml/module-summary.html#Conf">Configuration</a> section
 * of the module summary.
 *
 * @author Jeff Suttor
 * @see <a href="http:
 * @see <a href="http:
 * @see <a href="http:
 * @see <a href="http:
 * @see <a href="http:
 * @see <a href="http:
 * @since 1.5
 **/

public final class XMLConstants {

    /**
     * Private constructor to prevent instantiation.
     */
    private XMLConstants() {
    }

    /**
     * Namespace URI to use to represent that there is no Namespace.
     *
     * <p>Defined by the Namespace specification to be "".
     *
     * @see <a href="http:
     * Namespaces in XML, 5.2 Namespace Defaulting</a>
     */
    public static final String NULL_NS_URI = "";

    /**
     * Prefix to use to represent the default XML Namespace.
     *
     * <p>Defined by the XML specification to be "".
     *
     * @see <a
     * href="http:
     * Namespaces in XML, 3. Qualified Names</a>
     */
    public static final String DEFAULT_NS_PREFIX = "";

    /**
     * The official XML Namespace name URI.
     *
     * <p>Defined by the XML specification to be
     * "{@code http:
     *
     * @see <a
     * href="http:
     * Namespaces in XML, 3. Qualified Names</a>
     */
    public static final String XML_NS_URI =
        "http:

    /**
     * The official XML Namespace prefix.
     *
     * <p>Defined by the XML specification to be "{@code xml}".
     *
     * @see <a
     * href="http:
     * Namespaces in XML, 3. Qualified Names</a>
     */
    public static final String XML_NS_PREFIX = "xml";

    /**
     * The official XML attribute used for specifying XML Namespace
     * declarations, {@link #XMLNS_ATTRIBUTE
     * XMLConstants.XMLNS_ATTRIBUTE}, Namespace name URI.
     *
     * <p>Defined by the XML specification to be
     * "{@code http:
     *
     * @see <a
     * href="http:
     * Namespaces in XML, 3. Qualified Names</a>
     */
    public static final String XMLNS_ATTRIBUTE_NS_URI =
        "http:

    /**
     * The official XML attribute used for specifying XML Namespace
     * declarations.
     *
     * <p>It is <strong><em>NOT</em></strong> valid to use as a
     * prefix.  Defined by the XML specification to be
     * "{@code xmlns}".
     *
     * @see <a
     * href="http:
     * Namespaces in XML, 3. Qualified Names</a>
     */
    public static final String XMLNS_ATTRIBUTE = "xmlns";

    /**
     * W3C XML Schema Namespace URI.
     *
     * <p>Defined to be "{@code http:
     *
     * @see <a href=
     *  "http:
     *  XML Schema Part 1:
     *  Structures, 2.6 Schema-Related Markup in Documents Being Validated</a>
     */
    public static final String W3C_XML_SCHEMA_NS_URI =
        "http:

    /**
     * W3C XML Schema Instance Namespace URI.
     *
     * <p>Defined to be "{@code http:
     *
     * @see <a href=
     *  "http:
     *  XML Schema Part 1:
     *  Structures, 2.6 Schema-Related Markup in Documents Being Validated</a>
     */
    public static final String W3C_XML_SCHEMA_INSTANCE_NS_URI =
        "http:

    /**
     * W3C XPath Datatype Namespace URI.
     *
     * <p>Defined to be "{@code http:
     *
     * @see <a href="http:
     */
    public static final String W3C_XPATH_DATATYPE_NS_URI = "http:

    /**
     * XML Document Type Declaration Namespace URI as an arbitrary value.
     *
     * <p>Since not formally defined by any existing standard, arbitrarily define to be "{@code http:
     */
    public static final String XML_DTD_NS_URI = "http:

        /**
         * RELAX NG Namespace URI.
         *
         * <p>Defined to be "{@code http:
         *
         * @see <a href="http:
         */
        public static final String RELAXNG_NS_URI = "http:

        /**
         * Feature for secure processing.
         *
         * <ul>
         *   <li>
         *     {@code true} instructs the implementation to process XML securely.
         *     This may set limits on XML constructs to avoid conditions such as denial of service attacks.
         *   </li>
         *   <li>
         *     {@code false} instructs the implementation to process XML in accordance with the XML specifications
         *     ignoring security issues such as limits on XML constructs to avoid conditions such as denial of service attacks.
         *   </li>
         * </ul>
         *
         * @implNote
         * when the Java Security Manager is present, the JDK sets the value of
         * this feature to true and does not allow it to be turned off.
         */
        public static final String FEATURE_SECURE_PROCESSING = "http:


        /**
         * Property: accessExternalDTD
         *
         * <p>
         * Restrict access to external DTDs and external Entity References to the protocols specified.
         * If access is denied due to the restriction of this property, a runtime exception that
         * is specific to the context is thrown. In the case of {@link javax.xml.parsers.SAXParser}
         * for example, {@link org.xml.sax.SAXException} is thrown.
         *
         * <p>
         * <b>Value: </b> as defined in <a href="#EAP">the class description</a>.
         *
         * <p>
         * <b>System Property:</b> {@code javax.xml.accessExternalDTD}.
         *
         * <p>
         * <b>Configuration File:</b>
         * Yes. The property can be set in the
         * <a href="{@docRoot}/java.xml/module-summary.html#Conf_CF">configuration file</a>.
         *
         * @since 1.7
         */
        public static final String ACCESS_EXTERNAL_DTD = "http:

        /**
         * <p>Property: accessExternalSchema</p>
         *
         * <p>
         * Restrict access to the protocols specified for external reference set by the
         * schemaLocation attribute, Import and Include element. If access is denied
         * due to the restriction of this property, a runtime exception that is specific
         * to the context is thrown. In the case of {@link javax.xml.validation.SchemaFactory}
         * for example, org.xml.sax.SAXException is thrown.
         *
         * <p>
         * <b>Value: </b> as defined in <a href="#EAP">the class description</a>.
         *
         * <p>
         * <b>System Property:</b> {@code javax.xml.accessExternalSchema}
         *
         * <p>
         * <b>Configuration File:</b>
         * Yes. The property can be set in the
         * <a href="{@docRoot}/java.xml/module-summary.html#Conf_CF">configuration file</a>.
        *
         * @since 1.7
         */
        public static final String ACCESS_EXTERNAL_SCHEMA = "http:

        /**
         * Property: accessExternalStylesheet
         *
         * <p>
         * Restrict access to the protocols specified for external references set by the
         * stylesheet processing instruction, Import and Include element, and document function.
         * If access is denied due to the restriction of this property, a runtime exception
         * that is specific to the context is thrown. In the case of constructing new
         * {@link javax.xml.transform.Transformer} for example,
         * {@link javax.xml.transform.TransformerConfigurationException}
         * will be thrown by the {@link javax.xml.transform.TransformerFactory}.
         *
         * <p>
         * <b>Value: </b> as defined in <a href="#EAP">the class description</a>.
         *
         * <p>
         * <b>System Property:</b> {@code javax.xml.accessExternalStylesheet}
         *
         * <p>
         * <b>Configuration File:</b>
         * Yes. The property can be set in the
         * <a href="{@docRoot}/java.xml/module-summary.html#Conf_CF">configuration file</a>.
         *
         * @since 1.7
         */
        public static final String ACCESS_EXTERNAL_STYLESHEET = "http:


        /**
         * Feature: useCatalog
         *
         * <p>
         * Instructs XML processors to use XML Catalogs to resolve entity references.
         * Catalogs may be set through JAXP factories, system properties, or
         * configuration file by using the {@code javax.xml.catalog.files} property
         * defined in {@link javax.xml.catalog.CatalogFeatures}.
         * The following code enables Catalog on SAX parser:
         * {@snippet :
         *      SAXParserFactory spf = SAXParserFactory.newInstance();
         *      spf.setFeature(XMLConstants.USE_CATALOG, true);
         *      SAXParser parser = spf.newSAXParser();
         *      parser.setProperty(CatalogFeatures.Feature.FILES.getPropertyName(), "catalog.xml");
         * }
         *
         * <p>
         * <b>Value:</b> a boolean. If the value is true, and a catalog is set,
         * the XML parser will resolve external references using
         * {@link javax.xml.catalog.CatalogResolver}. If the value is false,
         * XML Catalog is ignored even if one is set. The default value is true.
         *
         * <p>
         * <b>System Property:</b> {@code javax.xml.useCatalog}
         *
         * <p>
         * <b>Configuration File:</b>
         * Yes. The property can be set in the
         * <a href="{@docRoot}/java.xml/module-summary.html#Conf_CF">configuration file</a>.
         *
         * @since 9
         */
        public static final String USE_CATALOG = "http:

}
