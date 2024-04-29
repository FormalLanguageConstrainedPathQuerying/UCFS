/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
 */

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xerces.internal.impl;

import static com.sun.org.apache.xerces.internal.impl.Constants.XML_VERSION_1_0;
import static com.sun.org.apache.xerces.internal.impl.Constants.XML_VERSION_1_1;
import com.sun.org.apache.xerces.internal.impl.XMLScanner.NameType;
import com.sun.org.apache.xerces.internal.impl.io.ASCIIReader;
import com.sun.org.apache.xerces.internal.impl.io.UCSReader;
import com.sun.org.apache.xerces.internal.impl.io.UTF8Reader;
import com.sun.org.apache.xerces.internal.impl.msg.XMLMessageFormatter;
import com.sun.org.apache.xerces.internal.util.EncodingMap;
import com.sun.org.apache.xerces.internal.util.SymbolTable;
import com.sun.org.apache.xerces.internal.util.XMLChar;
import com.sun.org.apache.xerces.internal.util.XMLStringBuffer;
import com.sun.org.apache.xerces.internal.xni.*;
import com.sun.org.apache.xerces.internal.xni.parser.XMLComponentManager;
import com.sun.org.apache.xerces.internal.xni.parser.XMLConfigurationException;
import com.sun.xml.internal.stream.Entity;
import com.sun.xml.internal.stream.Entity.ScannedEntity;
import com.sun.xml.internal.stream.XMLBufferListener;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Locale;
import jdk.xml.internal.XMLLimitAnalyzer;
import jdk.xml.internal.XMLSecurityManager;
import jdk.xml.internal.XMLSecurityManager.Limit;

/**
 * Implements the entity scanner methods.
 *
 * @author Neeraj Bajaj, Sun Microsystems
 * @author Andy Clark, IBM
 * @author Arnaud  Le Hors, IBM
 * @author K.Venugopal Sun Microsystems
 *
 * @LastModified: July 2023
 */
public class XMLEntityScanner implements XMLLocator  {

    protected Entity.ScannedEntity fCurrentEntity = null;
    protected int fBufferSize = XMLEntityManager.DEFAULT_BUFFER_SIZE;

    protected XMLEntityManager fEntityManager;

    /** Security manager. */
    protected XMLSecurityManager fSecurityManager = null;

    /** Limit analyzer. */
    protected XMLLimitAnalyzer fLimitAnalyzer = null;

    /** Debug switching readers for encodings. */
    private static final boolean DEBUG_ENCODINGS = false;

    /** Listeners which should know when load is being called */
    private ArrayList<XMLBufferListener> listeners = new ArrayList<>();

    private static final boolean [] VALID_NAMES = new boolean[127];

    /**
     * Debug printing of buffer. This debugging flag works best when you
     * resize the DEFAULT_BUFFER_SIZE down to something reasonable like
     * 64 characters.
     */
    private static final boolean DEBUG_BUFFER = false;
    private static final boolean DEBUG_SKIP_STRING = false;
    /**
     * To signal the end of the document entity, this exception will be thrown.
     */
    private static final EOFException END_OF_DOCUMENT_ENTITY = new EOFException() {
        private static final long serialVersionUID = 980337771224675268L;
        public Throwable fillInStackTrace() {
            return this;
        }
    };

    protected SymbolTable fSymbolTable = null;
    protected XMLErrorReporter fErrorReporter = null;
    int [] whiteSpaceLookup = new int[100];
    int whiteSpaceLen = 0;
    boolean whiteSpaceInfoNeeded = true;

    /**
     * Allow Java encoding names. This feature identifier is:
     * http:
     */
    protected boolean fAllowJavaEncodings;


    /** Property identifier: symbol table. */
    protected static final String SYMBOL_TABLE =
            Constants.XERCES_PROPERTY_PREFIX + Constants.SYMBOL_TABLE_PROPERTY;

    /** Property identifier: error reporter. */
    protected static final String ERROR_REPORTER =
            Constants.XERCES_PROPERTY_PREFIX + Constants.ERROR_REPORTER_PROPERTY;

    /** Feature identifier: allow Java encodings. */
    protected static final String ALLOW_JAVA_ENCODINGS =
            Constants.XERCES_FEATURE_PREFIX + Constants.ALLOW_JAVA_ENCODINGS_FEATURE;

    protected PropertyManager fPropertyManager = null ;

    boolean isExternal = false;
    static {

        for(int i=0x0041;i<=0x005A ; i++){
            VALID_NAMES[i]=true;
        }
        for(int i=0x0061;i<=0x007A; i++){
            VALID_NAMES[i]=true;
        }
        for(int i=0x0030;i<=0x0039; i++){
            VALID_NAMES[i]=true;
        }
        VALID_NAMES[45]=true;
        VALID_NAMES[46]=true;
        VALID_NAMES[58]=true;
        VALID_NAMES[95]=true;
    }

    protected boolean xmlVersionSetExplicitly = false;

    boolean detectingVersion = false;

    int offset = 0;

    int newlines = 0;

    boolean counted = false;


    /** Default constructor. */
    public XMLEntityScanner() {
    } 


    /**  private constructor, this class can only be instantiated within this class. Instance of this class should
     *    be obtained using getEntityScanner() or getEntityScanner(ScannedEntity scannedEntity)
     *    @see getEntityScanner()
     *    @see getEntityScanner(ScannedEntity)
     */
    public XMLEntityScanner(PropertyManager propertyManager, XMLEntityManager entityManager) {
        fEntityManager = entityManager ;
        reset(propertyManager);
    } 


    public final void setBufferSize(int size) {
        fBufferSize = size;
    }

    /**
     * Resets the components.
     */
    public void reset(PropertyManager propertyManager){
        fSymbolTable = (SymbolTable)propertyManager.getProperty(SYMBOL_TABLE) ;
        fErrorReporter = (XMLErrorReporter)propertyManager.getProperty(ERROR_REPORTER) ;
        resetCommon();
    }

    /**
     * Resets the component. The component can query the component manager
     * about any features and properties that affect the operation of the
     * component.
     *
     * @param componentManager The component manager.
     *
     * @throws SAXException Thrown by component on initialization error.
     *                      For example, if a feature or property is
     *                      required for the operation of the component, the
     *                      component manager may throw a
     *                      SAXNotRecognizedException or a
     *                      SAXNotSupportedException.
     */
    public void reset(XMLComponentManager componentManager)
    throws XMLConfigurationException {
        fAllowJavaEncodings = componentManager.getFeature(ALLOW_JAVA_ENCODINGS, false);

        fSymbolTable = (SymbolTable)componentManager.getProperty(SYMBOL_TABLE);
        fErrorReporter = (XMLErrorReporter)componentManager.getProperty(ERROR_REPORTER);
        resetCommon();
    } 


    public final void reset(SymbolTable symbolTable, XMLEntityManager entityManager,
            XMLErrorReporter reporter) {
        fCurrentEntity = null;
        fSymbolTable = symbolTable;
        fEntityManager = entityManager;
        fErrorReporter = reporter;
        fLimitAnalyzer = fEntityManager.fLimitAnalyzer;
        fSecurityManager = fEntityManager.fSecurityManager;
    }

    private void resetCommon() {
        fCurrentEntity = null;
        whiteSpaceLen = 0;
        whiteSpaceInfoNeeded = true;
        listeners.clear();
        fLimitAnalyzer = fEntityManager.fLimitAnalyzer;
        fSecurityManager = fEntityManager.fSecurityManager;
    }

    /**
     * Returns the XML version of the current entity. This will normally be the
     * value from the XML or text declaration or defaulted by the parser. Note that
     * that this value may be different than the version of the processing rules
     * applied to the current entity. For instance, an XML 1.1 document may refer to
     * XML 1.0 entities. In such a case the rules of XML 1.1 are applied to the entire
     * document. Also note that, for a given entity, this value can only be considered
     * final once the XML or text declaration has been read or once it has been
     * determined that there is no such declaration.
     */
    public final String getXMLVersion() {
        if (fCurrentEntity != null) {
            return fCurrentEntity.xmlVersion;
        }
        return null;
    } 

    /**
     * Sets the XML version. This method is used by the
     * scanners to report the value of the version pseudo-attribute
     * in an XML or text declaration.
     *
     * @param xmlVersion the XML version of the current entity
     */
    public final void setXMLVersion(String xmlVersion) {
        xmlVersionSetExplicitly = true;
        fCurrentEntity.xmlVersion = xmlVersion;
    } 


    /** set the instance of current scanned entity.
     *   @param ScannedEntity
     */

    public final void setCurrentEntity(Entity.ScannedEntity scannedEntity){
        fCurrentEntity = scannedEntity ;
        if(fCurrentEntity != null){
            isExternal = fCurrentEntity.isExternal();
            if(DEBUG_BUFFER)
                System.out.println("Current Entity is "+scannedEntity.name);
        }
    }

    public  Entity.ScannedEntity getCurrentEntity(){
        return fCurrentEntity ;
    }

    /**
     * Returns the base system identifier of the currently scanned
     * entity, or null if none is available.
     */
    public final String getBaseSystemId() {
        return (fCurrentEntity != null && fCurrentEntity.entityLocation != null) ? fCurrentEntity.entityLocation.getExpandedSystemId() : null;
    } 

    /**
     * @see com.sun.org.apache.xerces.internal.xni.XMLResourceIdentifier#setBaseSystemId(String)
     */
    public void setBaseSystemId(String systemId) {
    }

    public final int getLineNumber(){
        return fCurrentEntity != null ? fCurrentEntity.lineNumber : -1 ;
    }

    /**
     * @see com.sun.org.apache.xerces.internal.xni.XMLLocator#setLineNumber(int)
     */
    public void setLineNumber(int line) {
    }


    public final int getColumnNumber(){
        return fCurrentEntity != null ? fCurrentEntity.columnNumber : -1 ;
    }

    /**
     * @see com.sun.org.apache.xerces.internal.xni.XMLLocator#setColumnNumber(int)
     */
    public void setColumnNumber(int col) {
    }


    public final int getCharacterOffset(){
        return fCurrentEntity != null ? fCurrentEntity.fTotalCountTillLastLoad + fCurrentEntity.position : -1 ;
    }

    /** Returns the expanded system identifier.  */
    public final String getExpandedSystemId() {
        return (fCurrentEntity != null && fCurrentEntity.entityLocation != null) ? fCurrentEntity.entityLocation.getExpandedSystemId() : null;
    }

    /**
     * @see com.sun.org.apache.xerces.internal.xni.XMLResourceIdentifier#setExpandedSystemId(String)
     */
    public void setExpandedSystemId(String systemId) {
    }

    /** Returns the literal system identifier.  */
    public final String getLiteralSystemId() {
        return (fCurrentEntity != null && fCurrentEntity.entityLocation != null) ? fCurrentEntity.entityLocation.getLiteralSystemId() : null;
    }

    /**
     * @see com.sun.org.apache.xerces.internal.xni.XMLResourceIdentifier#setLiteralSystemId(String)
     */
    public void setLiteralSystemId(String systemId) {
    }

    /** Returns the public identifier.  */
    public final String getPublicId() {
        return (fCurrentEntity != null && fCurrentEntity.entityLocation != null) ? fCurrentEntity.entityLocation.getPublicId() : null;
    }

    /**
     * @see com.sun.org.apache.xerces.internal.xni.XMLResourceIdentifier#setPublicId(String)
     */
    public void setPublicId(String publicId) {
    }


    /** the version of the current entity being scanned */
    public void setVersion(String version){
        fCurrentEntity.version = version;
    }

    public String getVersion(){
        if (fCurrentEntity != null)
            return fCurrentEntity.version ;
        return null;
    }

    /**
     * Returns the encoding of the current entity.
     * Note that, for a given entity, this value can only be
     * considered final once the encoding declaration has been read (or once it
     * has been determined that there is no such declaration) since, no encoding
     * having been specified on the XMLInputSource, the parser
     * will make an initial "guess" which could be in error.
     */
    public final String getEncoding() {
        if (fCurrentEntity != null) {
            return fCurrentEntity.encoding;
        }
        return null;
    } 

    /**
     * Sets the encoding of the scanner. This method is used by the
     * scanners if the XMLDecl or TextDecl line contains an encoding
     * pseudo-attribute.
     * <p>
     * <strong>Note:</strong> The underlying character reader on the
     * current entity will be changed to accomodate the new encoding.
     * However, the new encoding is ignored if the current reader was
     * not constructed from an input stream (e.g. an external entity
     * that is resolved directly to the appropriate java.io.Reader
     * object).
     *
     * @param encoding The IANA encoding name of the new encoding.
     *
     * @throws IOException Thrown if the new encoding is not supported.
     *
     * @see com.sun.org.apache.xerces.internal.util.EncodingMap
     */
    public final void setEncoding(String encoding) throws IOException {

        if (DEBUG_ENCODINGS) {
            System.out.println("$$$ setEncoding: "+encoding);
        }

        if (fCurrentEntity.stream != null) {
            if (fCurrentEntity.encoding == null ||
                    !fCurrentEntity.encoding.equals(encoding)) {
                if(fCurrentEntity.encoding != null && fCurrentEntity.encoding.startsWith("UTF-16")) {
                    String ENCODING = encoding.toUpperCase(Locale.ENGLISH);
                    if(ENCODING.equals("UTF-16")) return;
                    if(ENCODING.equals("ISO-10646-UCS-4")) {
                        if(fCurrentEntity.encoding.equals("UTF-16BE")) {
                            fCurrentEntity.reader = new UCSReader(fCurrentEntity.stream, UCSReader.UCS4BE);
                        } else {
                            fCurrentEntity.reader = new UCSReader(fCurrentEntity.stream, UCSReader.UCS4LE);
                        }
                        return;
                    }
                    if(ENCODING.equals("ISO-10646-UCS-2")) {
                        if(fCurrentEntity.encoding.equals("UTF-16BE")) {
                            fCurrentEntity.reader = new UCSReader(fCurrentEntity.stream, UCSReader.UCS2BE);
                        } else {
                            fCurrentEntity.reader = new UCSReader(fCurrentEntity.stream, UCSReader.UCS2LE);
                        }
                        return;
                    }
                }
                if (DEBUG_ENCODINGS) {
                    System.out.println("$$$ creating new reader from stream: "+
                            fCurrentEntity.stream);
                }
                fCurrentEntity.reader = createReader(fCurrentEntity.stream, encoding, null);
                fCurrentEntity.encoding = encoding;

            } else {
                if (DEBUG_ENCODINGS)
                    System.out.println("$$$ reusing old reader on stream");
            }
        }

    } 

    /** Returns true if the current entity being scanned is external. */
    public final boolean isExternal() {
        return fCurrentEntity.isExternal();
    } 

    public int getChar(int relative) throws IOException{
        if(arrangeCapacity(relative + 1, false)){
            return fCurrentEntity.ch[fCurrentEntity.position + relative];
        }else{
            return -1;
        }
    }

    /**
     * Returns the next character on the input.
     * <p>
     * <strong>Note:</strong> The character is <em>not</em> consumed.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    public int peekChar() throws IOException {
        if (DEBUG_BUFFER) {
            System.out.print("(peekChar: ");
            print();
            System.out.println();
        }

        if (fCurrentEntity.position == fCurrentEntity.count) {
            load(0, true, true);
        }

        int c = fCurrentEntity.ch[fCurrentEntity.position];

        if (DEBUG_BUFFER) {
            System.out.print(")peekChar: ");
            print();
            if (isExternal) {
                System.out.println(" -> '"+(c!='\r'?(char)c:'\n')+"'");
            } else {
                System.out.println(" -> '"+(char)c+"'");
            }
        }
        if (isExternal) {
            return c != '\r' ? c : '\n';
        } else {
            return c;
        }

    } 

    /**
     * Returns the next character on the input.
     * <p>
     * <strong>Note:</strong> The character is consumed.
     *
     * @param nt The type of the name (element or attribute)
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    protected int scanChar(NameType nt) throws IOException {
        if (DEBUG_BUFFER) {
            System.out.print("(scanChar: ");
            print();
            System.out.println();
        }

        if (fCurrentEntity.position == fCurrentEntity.count) {
            load(0, true, true);
        }

        offset = fCurrentEntity.position;
        int c = fCurrentEntity.ch[fCurrentEntity.position++];
        if (c == '\n' || (c == '\r' && isExternal)) {
            fCurrentEntity.lineNumber++;
            fCurrentEntity.columnNumber = 1;
            if (fCurrentEntity.position == fCurrentEntity.count) {
                invokeListeners(1);
                fCurrentEntity.ch[0] = (char)c;
                load(1, true, false);
                offset = 0;
            }
            if (c == '\r' && isExternal && fCurrentEntity.position < fCurrentEntity.count) {
                if (fCurrentEntity.ch[fCurrentEntity.position++] != '\n') {
                    fCurrentEntity.position--;
                }
                c = '\n';
            }
        }

        if (DEBUG_BUFFER) {
            System.out.print(")scanChar: ");
            print();
            System.out.println(" -> '"+(char)c+"'");
        }
        fCurrentEntity.columnNumber++;
        if (!detectingVersion) {
            checkEntityLimit(nt, fCurrentEntity, offset, fCurrentEntity.position - offset);
        }
        return c;

    } 

    /**
     * Returns a string matching the NMTOKEN production appearing immediately
     * on the input as a symbol, or null if NMTOKEN Name string is present.
     * <p>
     * <strong>Note:</strong> The NMTOKEN characters are consumed.
     * <p>
     * <strong>Note:</strong> The string returned must be a symbol. The
     * SymbolTable can be used for this purpose.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     *
     * @see com.sun.org.apache.xerces.internal.util.SymbolTable
     * @see com.sun.org.apache.xerces.internal.util.XMLChar#isName
     */
    protected String scanNmtoken() throws IOException {
        if (DEBUG_BUFFER) {
            System.out.print("(scanNmtoken: ");
            print();
            System.out.println();
        }

        if (fCurrentEntity.position == fCurrentEntity.count) {
            load(0, true, true);
        }

        offset = fCurrentEntity.position;
        boolean vc = false;
        char c;
        while (true){
            c = fCurrentEntity.ch[fCurrentEntity.position];
            if(c < 127){
                vc = VALID_NAMES[c];
            }else{
                vc = XMLChar.isName(c);
            }
            if(!vc)break;

            if (++fCurrentEntity.position == fCurrentEntity.count) {
                int length = fCurrentEntity.position - offset;
                invokeListeners(length);
                if (length == fCurrentEntity.fBufferSize) {
                    char[] tmp = new char[fCurrentEntity.fBufferSize * 2];
                    System.arraycopy(fCurrentEntity.ch, offset,
                            tmp, 0, length);
                    fCurrentEntity.ch = tmp;
                    fCurrentEntity.fBufferSize *= 2;
                } else {
                    System.arraycopy(fCurrentEntity.ch, offset,
                            fCurrentEntity.ch, 0, length);
                }
                offset = 0;
                if (load(length, false, false)) {
                    break;
                }
            }
        }
        int length = fCurrentEntity.position - offset;
        fCurrentEntity.columnNumber += length;

        String symbol = null;
        if (length > 0) {
            symbol = fSymbolTable.addSymbol(fCurrentEntity.ch, offset, length);
        }
        if (DEBUG_BUFFER) {
            System.out.print(")scanNmtoken: ");
            print();
            System.out.println(" -> "+String.valueOf(symbol));
        }
        return symbol;

    } 

    /**
     * Returns a string matching the Name production appearing immediately
     * on the input as a symbol, or null if no Name string is present.
     * <p>
     * <strong>Note:</strong> The Name characters are consumed.
     * <p>
     * <strong>Note:</strong> The string returned must be a symbol. The
     * SymbolTable can be used for this purpose.
     *
     * @param nt The type of the name (element or attribute)
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     *
     * @see com.sun.org.apache.xerces.internal.util.SymbolTable
     * @see com.sun.org.apache.xerces.internal.util.XMLChar#isName
     * @see com.sun.org.apache.xerces.internal.util.XMLChar#isNameStart
     */
    protected String scanName(NameType nt) throws IOException {
        if (DEBUG_BUFFER) {
            System.out.print("(scanName: ");
            print();
            System.out.println();
        }

        if (fCurrentEntity.position == fCurrentEntity.count) {
            load(0, true, true);
        }

        offset = fCurrentEntity.position;
        int length;
        if (XMLChar.isNameStart(fCurrentEntity.ch[offset])) {
            if (++fCurrentEntity.position == fCurrentEntity.count) {
                invokeListeners(1);
                fCurrentEntity.ch[0] = fCurrentEntity.ch[offset];
                offset = 0;
                if (load(1, false, false)) {
                    fCurrentEntity.columnNumber++;
                    String symbol = fSymbolTable.addSymbol(fCurrentEntity.ch, 0, 1);

                    if (DEBUG_BUFFER) {
                        System.out.print(")scanName: ");
                        print();
                        System.out.println(" -> "+String.valueOf(symbol));
                    }
                    return symbol;
                }
            }
            boolean vc =false;
            while (true ){
                char c = fCurrentEntity.ch[fCurrentEntity.position];
                if(c < 127){
                    vc = VALID_NAMES[c];
                }else{
                    vc = XMLChar.isName(c);
                }
                if(!vc)break;
                if ((length = checkBeforeLoad(fCurrentEntity, offset, offset)) > 0) {
                    offset = 0;
                    if (load(length, false, false)) {
                        break;
                    }
                }
            }
        }
        length = fCurrentEntity.position - offset;
        fCurrentEntity.columnNumber += length;

        String symbol;
        if (length > 0) {
            checkLimit(Limit.MAX_NAME_LIMIT, fCurrentEntity, offset, length);
            checkEntityLimit(nt, fCurrentEntity, offset, length);
            symbol = fSymbolTable.addSymbol(fCurrentEntity.ch, offset, length);
        } else
            symbol = null;
        if (DEBUG_BUFFER) {
            System.out.print(")scanName: ");
            print();
            System.out.println(" -> "+String.valueOf(symbol));
        }
        return symbol;

    } 

    /**
     * Scans a qualified name from the input, setting the fields of the
     * QName structure appropriately.
     * <p>
     * <strong>Note:</strong> The qualified name characters are consumed.
     * <p>
     * <strong>Note:</strong> The strings used to set the values of the
     * QName structure must be symbols. The SymbolTable can be used for
     * this purpose.
     *
     * @param qname The qualified name structure to fill.
     * @param nt The type of the name (element or attribute)
     *
     * @return Returns true if a qualified name appeared immediately on
     *         the input and was scanned, false otherwise.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     *
     * @see com.sun.org.apache.xerces.internal.util.SymbolTable
     * @see com.sun.org.apache.xerces.internal.util.XMLChar#isName
     * @see com.sun.org.apache.xerces.internal.util.XMLChar#isNameStart
     */
    protected boolean scanQName(QName qname, NameType nt) throws IOException {
        if (DEBUG_BUFFER) {
            System.out.print("(scanQName, "+qname+": ");
            print();
            System.out.println();
        }

        if (fCurrentEntity.position == fCurrentEntity.count) {
            load(0, true, true);
        }

        offset = fCurrentEntity.position;


        if (XMLChar.isNameStart(fCurrentEntity.ch[offset])) {
            if (++fCurrentEntity.position == fCurrentEntity.count) {
                invokeListeners(1);
                fCurrentEntity.ch[0] = fCurrentEntity.ch[offset];
                offset = 0;

                if (load(1, false, false)) {
                    fCurrentEntity.columnNumber++;
                    String name = fSymbolTable.addSymbol(fCurrentEntity.ch, 0, 1);
                    qname.setValues(null, name, name, null);
                    if (DEBUG_BUFFER) {
                        System.out.print(")scanQName, "+qname+": ");
                        print();
                        System.out.println(" -> true");
                    }
                    checkEntityLimit(nt, fCurrentEntity, 0, 1);
                    return true;
                }
            }
            int index = -1;
            boolean vc = false;
            int length;
            while ( true){

                char c = fCurrentEntity.ch[fCurrentEntity.position];
                if(c < 127){
                    vc = VALID_NAMES[c];
                }else{
                    vc = XMLChar.isName(c);
                }
                if(!vc)break;
                if (c == ':') {
                    if (index != -1) {
                        break;
                    }
                    index = fCurrentEntity.position;
                    checkLimit(Limit.MAX_NAME_LIMIT, fCurrentEntity, offset, index - offset);
                }
                if ((length = checkBeforeLoad(fCurrentEntity, offset, index)) > 0) {
                    if (index != -1) {
                        index = index - offset;
                    }
                    offset = 0;
                    if (load(length, false, false)) {
                        break;
                    }
                }
            }
            length = fCurrentEntity.position - offset;
            fCurrentEntity.columnNumber += length;
            if (length > 0) {
                String prefix = null;
                String localpart = null;
                String rawname = fSymbolTable.addSymbol(fCurrentEntity.ch,
                        offset, length);

                if (index != -1) {
                    int prefixLength = index - offset;
                    checkLimit(Limit.MAX_NAME_LIMIT, fCurrentEntity, offset, prefixLength);
                    prefix = fSymbolTable.addSymbol(fCurrentEntity.ch,
                            offset, prefixLength);
                    int len = length - prefixLength - 1;
                    int startLocal = index +1;
                    if (!XMLChar.isNCNameStart(fCurrentEntity.ch[startLocal])){
                        fErrorReporter.reportError(XMLMessageFormatter.XML_DOMAIN,
                                                 "IllegalQName",
                                                  new Object[]{rawname},
                                                  XMLErrorReporter.SEVERITY_FATAL_ERROR);
                    }

                    checkLimit(Limit.MAX_NAME_LIMIT, fCurrentEntity, index + 1, len);
                    localpart = fSymbolTable.addSymbol(fCurrentEntity.ch,
                            index + 1, len);

                } else {
                    localpart = rawname;
                    checkLimit(Limit.MAX_NAME_LIMIT, fCurrentEntity, offset, length);
                }
                qname.setValues(prefix, localpart, rawname, null);
                if (DEBUG_BUFFER) {
                    System.out.print(")scanQName, "+qname+": ");
                    print();
                    System.out.println(" -> true");
                }
                checkEntityLimit(nt, fCurrentEntity, offset, length);
                return true;
            }
        }

        if (DEBUG_BUFFER) {
            System.out.print(")scanQName, "+qname+": ");
            print();
            System.out.println(" -> false");
        }
        return false;

    } 

    /**
     * Checks whether the end of the entity buffer has been reached. If yes,
     * checks against the limit and buffer size before loading more characters.
     *
     * @param entity the current entity
     * @param offset the offset from which the current read was started
     * @param nameOffset the offset from which the current name starts
     * @return the length of characters scanned before the end of the buffer,
     * zero if there is more to be read in the buffer
     */
    protected int checkBeforeLoad(Entity.ScannedEntity entity, int offset,
            int nameOffset) throws IOException {
        int length = 0;
        if (++entity.position == entity.count) {
            length = entity.position - offset;
            int nameLength = length;
            if (nameOffset != -1) {
                nameOffset = nameOffset - offset;
                nameLength = length - nameOffset;
            } else {
                nameOffset = offset;
            }
            checkLimit(Limit.MAX_NAME_LIMIT, entity, nameOffset, nameLength);
            invokeListeners(length);
            if (length == entity.ch.length) {
                char[] tmp = new char[entity.fBufferSize * 2];
                System.arraycopy(entity.ch, offset, tmp, 0, length);
                entity.ch = tmp;
                entity.fBufferSize *= 2;
            }
            else {
                System.arraycopy(entity.ch, offset, entity.ch, 0, length);
            }
        }
        return length;
    }

    /**
     * If the current entity is an Entity reference, check the accumulated size
     * against the limit.
     *
     * @param nt type of name (element, attribute or entity)
     * @param entity The current entity
     * @param offset The index of the first byte
     * @param length The length of the entity scanned
     */
    protected void checkEntityLimit(NameType nt, ScannedEntity entity, int offset, int length) {
        if (entity == null || !entity.isGE) {
            return;
        }

        if (nt != NameType.REFERENCE) {
            checkLimit(Limit.GENERAL_ENTITY_SIZE_LIMIT, entity, offset, length);
        }
        if (nt == NameType.ELEMENTSTART || nt == NameType.ATTRIBUTENAME) {
            checkNodeCount(entity);
        }
    }

    /**
     * If the current entity is an Entity reference, counts the total nodes in
     * the entity and checks the accumulated value against the limit.
     *
     * @param entity The current entity
     */
    protected void checkNodeCount(ScannedEntity entity) {
        if (entity != null && entity.isGE) {
            checkLimit(Limit.ENTITY_REPLACEMENT_LIMIT, entity, 0, 1);
        }
    }

    /**
     * Checks whether the value of the specified Limit exceeds its limit
     *
     * @param limit The Limit to be checked
     * @param entity The current entity
     * @param offset The index of the first byte
     * @param length The length of the entity scanned
     */
    protected void checkLimit(Limit limit, ScannedEntity entity, int offset, int length) {
        fLimitAnalyzer.addValue(limit, entity.name, length);
        if (fSecurityManager.isOverLimit(limit, fLimitAnalyzer)) {
            fSecurityManager.debugPrint(fLimitAnalyzer);
            Object[] e = (limit == Limit.ENTITY_REPLACEMENT_LIMIT) ?
                    new Object[]{fLimitAnalyzer.getValue(limit),
                        fSecurityManager.getLimit(limit), fSecurityManager.getStateLiteral(limit)} :
                    new Object[]{entity.name, fLimitAnalyzer.getValue(limit),
                        fSecurityManager.getLimit(limit), fSecurityManager.getStateLiteral(limit)};
            fErrorReporter.reportError(XMLMessageFormatter.XML_DOMAIN, limit.key(),
                    e, XMLErrorReporter.SEVERITY_FATAL_ERROR);
        }
        if (fSecurityManager.isOverLimit(Limit.TOTAL_ENTITY_SIZE_LIMIT, fLimitAnalyzer)) {
            fSecurityManager.debugPrint(fLimitAnalyzer);
            fErrorReporter.reportError(XMLMessageFormatter.XML_DOMAIN, "TotalEntitySizeLimit",
                    new Object[]{fLimitAnalyzer.getTotalValue(Limit.TOTAL_ENTITY_SIZE_LIMIT),
                fSecurityManager.getLimit(Limit.TOTAL_ENTITY_SIZE_LIMIT),
                fSecurityManager.getStateLiteral(Limit.TOTAL_ENTITY_SIZE_LIMIT)},
                    XMLErrorReporter.SEVERITY_FATAL_ERROR);
        }
    }

    /**
     * CHANGED:
     * Scans a range of parsed character data, This function appends the character data to
     * the supplied buffer.
     * <p>
     * <strong>Note:</strong> The characters are consumed.
     * <p>
     * <strong>Note:</strong> This method does not guarantee to return
     * the longest run of parsed character data. This method may return
     * before markup due to reaching the end of the input buffer or any
     * other reason.
     * <p>
     *
     * @param content The content structure to fill.
     *
     * @return Returns the next character on the input, if known. This
     *         value may be -1 but this does <em>note</em> designate
     *         end of file.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    protected int scanContent(XMLString content) throws IOException {
        if (DEBUG_BUFFER) {
            System.out.print("(scanContent: ");
            print();
            System.out.println();
        }

        if (fCurrentEntity.position == fCurrentEntity.count) {
            load(0, true, true);
        } else if (fCurrentEntity.position == fCurrentEntity.count - 1) {
            invokeListeners(1);
            fCurrentEntity.ch[0] = fCurrentEntity.ch[fCurrentEntity.count - 1];
            load(1, false, false);
            fCurrentEntity.position = 0;
        }

        if (normalizeNewlines(XML_VERSION_1_0, content, false, false, null)) {
            return -1;
        }

        int c;
        while (fCurrentEntity.position < fCurrentEntity.count) {
            c = fCurrentEntity.ch[fCurrentEntity.position++];
            if (!XMLChar.isContent(c)) {
                fCurrentEntity.position--;
                break;
            }
        }
        int length = fCurrentEntity.position - offset;
        fCurrentEntity.columnNumber += length - newlines;
        if (!counted) {
            checkEntityLimit(null, fCurrentEntity, offset, length);
        }

        content.setValues(fCurrentEntity.ch, offset, length);
        if (fCurrentEntity.position != fCurrentEntity.count) {
            c = fCurrentEntity.ch[fCurrentEntity.position];
            if (c == '\r' && isExternal) {
                c = '\n';
            }
        } else {
            c = -1;
        }
        if (DEBUG_BUFFER) {
            System.out.print(")scanContent: ");
            print();
            System.out.println(" -> '"+(char)c+"'");
        }
        return c;

    } 

    /**
     * Scans a range of attribute value data, setting the fields of the
     * XMLString structure, appropriately.
     * <p>
     * <strong>Note:</strong> The characters are consumed.
     * <p>
     * <strong>Note:</strong> This method does not guarantee to return
     * the longest run of attribute value data. This method may return
     * before the quote character due to reaching the end of the input
     * buffer or any other reason.
     * <p>
     * <strong>Note:</strong> The fields contained in the XMLString
     * structure are not guaranteed to remain valid upon subsequent calls
     * to the entity scanner. Therefore, the caller is responsible for
     * immediately using the returned character data or making a copy of
     * the character data.
     *
     * @param quote   The quote character that signifies the end of the
     *                attribute value data.
     * @param content The content structure to fill.
     * @param isNSURI a flag indicating whether the content is a Namespace URI
     *
     * @return Returns the next character on the input, if known. This
     *         value may be -1 but this does <em>note</em> designate
     *         end of file.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    protected int scanLiteral(int quote, XMLString content, boolean isNSURI)
    throws IOException {
        if (DEBUG_BUFFER) {
            System.out.print("(scanLiteral, '"+(char)quote+"': ");
            print();
            System.out.println();
        }
        if (fCurrentEntity.position == fCurrentEntity.count) {
            load(0, true, true);
        } else if (fCurrentEntity.position == fCurrentEntity.count - 1) {
            invokeListeners(1);
            fCurrentEntity.ch[0] = fCurrentEntity.ch[fCurrentEntity.count - 1];
            load(1, false, false);
            fCurrentEntity.position = 0;
        }

        if(whiteSpaceInfoNeeded)
            whiteSpaceLen=0;

        if (normalizeNewlines(XML_VERSION_1_0, content, false, true, null)) {
            return -1;
        }

        int c;
        for (; fCurrentEntity.position<fCurrentEntity.count; fCurrentEntity.position++) {
            c = fCurrentEntity.ch[fCurrentEntity.position];
            if ((c == quote &&
                    (!fCurrentEntity.literal || isExternal)) ||
                    c == '%' || !XMLChar.isContent(c) || c == '\r' && !isExternal) {
                break;
            }
            if (whiteSpaceInfoNeeded && c == '\t') {
                storeWhiteSpace(fCurrentEntity.position);
            }
        }
        int length = fCurrentEntity.position - offset;
        fCurrentEntity.columnNumber += length - newlines;

        checkEntityLimit(null, fCurrentEntity, offset, length);
        if (isNSURI) {
            checkLimit(Limit.MAX_NAME_LIMIT, fCurrentEntity, offset, length);
        }
        content.setValues(fCurrentEntity.ch, offset, length);

        if (fCurrentEntity.position != fCurrentEntity.count) {
            c = fCurrentEntity.ch[fCurrentEntity.position];
            if (c == quote && fCurrentEntity.literal) {
                c = -1;
            }
        } else {
            c = -1;
        }
        if (DEBUG_BUFFER) {
            System.out.print(")scanLiteral, '"+(char)quote+"': ");
            print();
            System.out.println(" -> '"+(char)c+"'");
        }
        return c;

    } 

    /**
     * Save whitespace information. Increase the whitespace buffer by 100
     * when needed.
     *
     * For XML 1.0, legal characters below 0x20 are 0x09 (TAB), 0x0A (LF) and 0x0D (CR).
     *
     * @param whiteSpacePos position of a whitespace in the scanner entity buffer
     */
    void storeWhiteSpace(int whiteSpacePos) {
        if (whiteSpaceLen >= whiteSpaceLookup.length) {
            int [] tmp = new int[whiteSpaceLookup.length + 100];
            System.arraycopy(whiteSpaceLookup, 0, tmp, 0, whiteSpaceLookup.length);
            whiteSpaceLookup = tmp;
        }

        whiteSpaceLookup[whiteSpaceLen++] = whiteSpacePos;
    }

    /**
     * Scans a range of character data up to the specified delimiter,
     * setting the fields of the XMLString structure, appropriately.
     * <p>
     * <strong>Note:</strong> The characters are consumed.
     * <p>
     * <strong>Note:</strong> This assumes that the delimiter contains at
     * least one character.
     * <p>
     * <strong>Note:</strong> This method does not guarantee to return
     * the longest run of character data. This method may return before
     * the delimiter due to reaching the end of the input buffer or any
     * other reason.
     * <p>
     * @param delimiter The string that signifies the end of the character
     *                  data to be scanned.
     * @param buffer    The XMLStringBuffer to fill.
     * @param chunkLimit the size limit of the data to be scanned. Zero by default
     * indicating no limit.
     *
     * @return Returns true if there is more data to scan, false otherwise.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    protected boolean scanData(String delimiter, XMLStringBuffer buffer, int chunkLimit)
    throws IOException {

        boolean done = false;
        int delimLen = delimiter.length();
        char charAt0 = delimiter.charAt(0);
        do {
            if (DEBUG_BUFFER) {
                System.out.print("(scanData: ");
                print();
                System.out.println();
            }


            if (fCurrentEntity.position == fCurrentEntity.count) {
                load(0, true, false);
            }

            boolean bNextEntity = false;

            while ((fCurrentEntity.position > fCurrentEntity.count - delimLen)
                && (!bNextEntity))
            {
              System.arraycopy(fCurrentEntity.ch,
                               fCurrentEntity.position,
                               fCurrentEntity.ch,
                               0,
                               fCurrentEntity.count - fCurrentEntity.position);

              bNextEntity = load(fCurrentEntity.count - fCurrentEntity.position, false, false);
              fCurrentEntity.position = 0;
              fCurrentEntity.startPosition = 0;
            }

            if (fCurrentEntity.position > fCurrentEntity.count - delimLen) {
                int length = fCurrentEntity.count - fCurrentEntity.position;
                checkEntityLimit(NameType.COMMENT, fCurrentEntity, fCurrentEntity.position, length);
                buffer.append (fCurrentEntity.ch, fCurrentEntity.position, length);
                fCurrentEntity.columnNumber += fCurrentEntity.count;
                fCurrentEntity.baseCharOffset += (fCurrentEntity.position - fCurrentEntity.startPosition);
                fCurrentEntity.position = fCurrentEntity.count;
                fCurrentEntity.startPosition = fCurrentEntity.count;
                load(0, true, false);
                return false;
            }

            if (normalizeNewlines(XML_VERSION_1_0, buffer, true, false, NameType.COMMENT)) {
                return true;
            }

            int c;
            OUTER: while (fCurrentEntity.position < fCurrentEntity.count) {
                c = fCurrentEntity.ch[fCurrentEntity.position++];
                if (c == charAt0) {
                    int delimOffset = fCurrentEntity.position - 1;
                    for (int i = 1; i < delimLen; i++) {
                        if (fCurrentEntity.position == fCurrentEntity.count) {
                            fCurrentEntity.position -= i;
                            break OUTER;
                        }
                        c = fCurrentEntity.ch[fCurrentEntity.position++];
                        if (delimiter.charAt(i) != c) {
                            fCurrentEntity.position -= i;
                            break;
                        }
                    }
                    if (fCurrentEntity.position == delimOffset + delimLen) {
                        done = true;
                        break;
                    }
                } else if (c == '\n' || (isExternal && c == '\r')) {
                    fCurrentEntity.position--;
                    break;
                } else if (XMLChar.isInvalid(c)) {
                    fCurrentEntity.position--;
                    int length = fCurrentEntity.position - offset;
                    fCurrentEntity.columnNumber += length - newlines;
                    checkEntityLimit(NameType.COMMENT, fCurrentEntity, offset, length);
                    buffer.append(fCurrentEntity.ch, offset, length);
                    return true;
                }
                if (chunkLimit > 0 &&
                        (buffer.length + fCurrentEntity.position - offset) >= chunkLimit) {
                    break;
                }
            }
            int length = fCurrentEntity.position - offset;
            fCurrentEntity.columnNumber += length - newlines;
            checkEntityLimit(NameType.COMMENT, fCurrentEntity, offset, length);
            if (done) {
                length -= delimLen;
            }
            buffer.append(fCurrentEntity.ch, offset, length);

            if (DEBUG_BUFFER) {
                System.out.print(")scanData: ");
                print();
                System.out.println(" -> " + done);
            }
            if (chunkLimit > 0 && buffer.length >= chunkLimit) {
                break;
            }
        } while (!done && chunkLimit == 0);
        return !done;

    } 

    /**
     * Skips a character appearing immediately on the input.
     * <p>
     * <strong>Note:</strong> The character is consumed only if it matches
     * the specified character.
     *
     * @param c The character to skip.
     * @param nt The type of the name (element or attribute)
     *
     * @return Returns true if the character was skipped.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    protected boolean skipChar(int c, NameType nt) throws IOException {
        if (DEBUG_BUFFER) {
            System.out.print("(skipChar, '"+(char)c+"': ");
            print();
            System.out.println();
        }

        if (fCurrentEntity.position == fCurrentEntity.count) {
            load(0, true, true);
        }

        offset = fCurrentEntity.position;
        int cc = fCurrentEntity.ch[fCurrentEntity.position];
        if (cc == c) {
            fCurrentEntity.position++;
            if (c == '\n') {
                fCurrentEntity.lineNumber++;
                fCurrentEntity.columnNumber = 1;
            } else {
                fCurrentEntity.columnNumber++;
            }
            if (DEBUG_BUFFER) {
                System.out.print(")skipChar, '"+(char)c+"': ");
                print();
                System.out.println(" -> true");
            }
            checkEntityLimit(nt, fCurrentEntity, offset, fCurrentEntity.position - offset);
            return true;
        }

        if (DEBUG_BUFFER) {
            System.out.print(")skipChar, '"+(char)c+"': ");
            print();
            System.out.println(" -> false");
        }
        return false;

    } 

    public boolean isSpace(char ch){
        return (ch == ' ') || (ch == '\n') || (ch == '\t') || (ch == '\r');
    }
    /**
     * Skips space characters appearing immediately on the input.
     * <p>
     * <strong>Note:</strong> The characters are consumed only if they are
     * space characters.
     *
     * @return Returns true if at least one space character was skipped.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     *
     * @see com.sun.org.apache.xerces.internal.util.XMLChar#isSpace
     */
    protected boolean skipSpaces() throws IOException {
        if (DEBUG_BUFFER) {
            System.out.print("(skipSpaces: ");
            print();
            System.out.println();
        }
        if (fCurrentEntity.position == fCurrentEntity.count) {
            load(0, true, true);
        }

        if(fCurrentEntity == null){
            return false ;
        }

        int c = fCurrentEntity.ch[fCurrentEntity.position];
        offset = fCurrentEntity.position - 1;
        if (XMLChar.isSpace(c)) {
            do {
                boolean entityChanged = false;
                if (c == '\n' || (isExternal && c == '\r')) {
                    fCurrentEntity.lineNumber++;
                    fCurrentEntity.columnNumber = 1;
                    if (fCurrentEntity.position == fCurrentEntity.count - 1) {
                        invokeListeners(1);
                        fCurrentEntity.ch[0] = (char)c;
                        entityChanged = load(1, true, false);
                        if (!entityChanged){
                            fCurrentEntity.position = 0;
                        }else if(fCurrentEntity == null){
                            return true ;
                        }
                    }
                    if (c == '\r' && isExternal) {
                        if (fCurrentEntity.ch[++fCurrentEntity.position] != '\n') {
                            fCurrentEntity.position--;
                        }
                    }
                } else {
                    fCurrentEntity.columnNumber++;
                }

                checkEntityLimit(null, fCurrentEntity, offset, fCurrentEntity.position - offset);
                offset = fCurrentEntity.position;

                if (!entityChanged){
                    fCurrentEntity.position++;
                }

                if (fCurrentEntity.position == fCurrentEntity.count) {
                    load(0, true, true);


                    if(fCurrentEntity == null){
                        return true ;
                    }

                }
            } while (XMLChar.isSpace(c = fCurrentEntity.ch[fCurrentEntity.position]));
            if (DEBUG_BUFFER) {
                System.out.print(")skipSpaces: ");
                print();
                System.out.println(" -> true");
            }
            return true;
        }

        if (DEBUG_BUFFER) {
            System.out.print(")skipSpaces: ");
            print();
            System.out.println(" -> false");
        }
        return false;

    } 


    /**
     * @param length This function checks that following number of characters are available.
     * to the underlying buffer.
     * @return This function returns true if capacity asked is available.
     */
    public boolean arrangeCapacity(int length) throws IOException{
        return arrangeCapacity(length, false);
    }

    /**
     * @param length This function checks that following number of characters are available.
     * to the underlying buffer.
     * @param changeEntity a flag to indicate that the underlying function should change the entity
     * @return This function returns true if capacity asked is available.
     *
     */
    public boolean arrangeCapacity(int length, boolean changeEntity) throws IOException{
        if((fCurrentEntity.count - fCurrentEntity.position) >= length) {
            return true;
        }
        if(DEBUG_SKIP_STRING){
            System.out.println("fCurrentEntity.count = " + fCurrentEntity.count);
            System.out.println("fCurrentEntity.position = " + fCurrentEntity.position);
            System.out.println("length = " + length);
        }
        boolean entityChanged = false;
        while((fCurrentEntity.count - fCurrentEntity.position) < length){
            if( (fCurrentEntity.ch.length - fCurrentEntity.position) < length){
                invokeListeners(0);
                System.arraycopy(fCurrentEntity.ch, fCurrentEntity.position, fCurrentEntity.ch,0,fCurrentEntity.count - fCurrentEntity.position);
                fCurrentEntity.count = fCurrentEntity.count - fCurrentEntity.position;
                fCurrentEntity.position = 0;
            }

            if((fCurrentEntity.count - fCurrentEntity.position) < length){
                int pos = fCurrentEntity.position;
                invokeListeners(pos);
                entityChanged = load(fCurrentEntity.count, changeEntity, false);
                fCurrentEntity.position = pos;
                if(entityChanged)break;
            }
            if(DEBUG_SKIP_STRING){
                System.out.println("fCurrentEntity.count = " + fCurrentEntity.count);
                System.out.println("fCurrentEntity.position = " + fCurrentEntity.position);
                System.out.println("length = " + length);
            }
        }

        if((fCurrentEntity.count - fCurrentEntity.position) >= length) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Skips the specified string appearing immediately on the input.
     * <p>
     * <strong>Note:</strong> The characters are consumed only if all
     * the characters are skipped.
     *
     * @param s The string to skip.
     *
     * @return Returns true if the string was skipped.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    protected boolean skipString(String s) throws IOException {

        final int length = s.length();

        if(arrangeCapacity(length, false)){
            final int beforeSkip = fCurrentEntity.position ;
            int afterSkip = fCurrentEntity.position + length - 1 ;
            if(DEBUG_SKIP_STRING){
                System.out.println("skipString,length = " + s + "," + length);
                System.out.println("Buffer string to be skipped = " + new String(fCurrentEntity.ch, beforeSkip,  length));
            }

            int i = length - 1 ;
            while(s.charAt(i--) == fCurrentEntity.ch[afterSkip]){
                if(afterSkip-- == beforeSkip){
                    fCurrentEntity.position = fCurrentEntity.position + length ;
                    fCurrentEntity.columnNumber += length;
                    if (!detectingVersion) {
                        checkEntityLimit(null, fCurrentEntity, beforeSkip, length);
                    }
                    return true;
                }
            }
        }

        return false;
    } 

    protected boolean skipString(char [] s) throws IOException {

        final int length = s.length;
        if(arrangeCapacity(length, false)){
            int beforeSkip = fCurrentEntity.position;

            if(DEBUG_SKIP_STRING){
                System.out.println("skipString,length = " + new String(s) + "," + length);
                System.out.println("skipString,length = " + new String(s) + "," + length);
            }

            for(int i=0;i<length;i++){
                if(!(fCurrentEntity.ch[beforeSkip++]==s[i])){
                   return false;
                }
            }
            fCurrentEntity.position = fCurrentEntity.position + length ;
            fCurrentEntity.columnNumber += length;
            if (!detectingVersion) {
                checkEntityLimit(null, fCurrentEntity, beforeSkip, length);
            }
            return true;

        }

        return false;
    }


    /**
     * Loads a chunk of text.
     *
     * @param offset       The offset into the character buffer to
     *                     read the next batch of characters.
     * @param changeEntity True if the load should change entities
     *                     at the end of the entity, otherwise leave
     *                     the current entity in place and the entity
     *                     boundary will be signaled by the return
     *                     value.
     * @param notify       Determine whether to notify listeners of
     *                     the event
     *
     * @returns Returns true if the entity changed as a result of this
     *          load operation.
     */
    final boolean load(int offset, boolean changeEntity, boolean notify)
    throws IOException {
        if (DEBUG_BUFFER) {
            System.out.print("(load, "+offset+": ");
            print();
            System.out.println();
        }
        if (notify) {
            invokeListeners(offset);
        }
        fCurrentEntity.fTotalCountTillLastLoad = fCurrentEntity.fTotalCountTillLastLoad + fCurrentEntity.fLastCount ;
        int length = fCurrentEntity.ch.length - offset;
        if (!fCurrentEntity.mayReadChunks && length > XMLEntityManager.DEFAULT_XMLDECL_BUFFER_SIZE) {
            length = XMLEntityManager.DEFAULT_XMLDECL_BUFFER_SIZE;
        }
        if (DEBUG_BUFFER) System.out.println("  length to try to read: "+length);
        int count = fCurrentEntity.reader.read(fCurrentEntity.ch, offset, length);
        if (DEBUG_BUFFER) System.out.println("  length actually read:  "+count);

        boolean entityChanged = false;
        if (count != -1) {
            if (count != 0) {
                fCurrentEntity.fLastCount = count;
                fCurrentEntity.count = count + offset;
                fCurrentEntity.position = offset;
            }
        }
        else {
            fCurrentEntity.count = offset;
            fCurrentEntity.position = offset;
            entityChanged = true;

            if (changeEntity) {
                fEntityManager.endEntity();
                if(fCurrentEntity == null){
                    throw END_OF_DOCUMENT_ENTITY;
                }
                if (fCurrentEntity.position == fCurrentEntity.count) {
                    load(0, true, false);
                }
            }

        }
        if (DEBUG_BUFFER) {
            System.out.print(")load, "+offset+": ");
            print();
            System.out.println();
        }

        return entityChanged;

    } 

    /**
     * Creates a reader capable of reading the given input stream in
     * the specified encoding.
     *
     * @param inputStream  The input stream.
     * @param encoding     The encoding name that the input stream is
     *                     encoded using. If the user has specified that
     *                     Java encoding names are allowed, then the
     *                     encoding name may be a Java encoding name;
     *                     otherwise, it is an ianaEncoding name.
     * @param isBigEndian   For encodings (like uCS-4), whose names cannot
     *                      specify a byte order, this tells whether the order is bigEndian.  null menas
     *                      unknown or not relevant.
     *
     * @return Returns a reader.
     */
    protected Reader createReader(InputStream inputStream, String encoding, Boolean isBigEndian)
    throws IOException {

        if (encoding == null) {
            encoding = "UTF-8";
        }

        String ENCODING = encoding.toUpperCase(Locale.ENGLISH);
        if (ENCODING.equals("UTF-8")) {
            if (DEBUG_ENCODINGS) {
                System.out.println("$$$ creating UTF8Reader");
            }
            return new UTF8Reader(inputStream, fCurrentEntity.fBufferSize, fErrorReporter.getMessageFormatter(XMLMessageFormatter.XML_DOMAIN), fErrorReporter.getLocale() );
        }
        if (ENCODING.equals("US-ASCII")) {
            if (DEBUG_ENCODINGS) {
                System.out.println("$$$ creating ASCIIReader");
            }
            return new ASCIIReader(inputStream, fCurrentEntity.fBufferSize, fErrorReporter.getMessageFormatter(XMLMessageFormatter.XML_DOMAIN), fErrorReporter.getLocale());
        }
        if(ENCODING.equals("ISO-10646-UCS-4")) {
            if(isBigEndian != null) {
                boolean isBE = isBigEndian.booleanValue();
                if(isBE) {
                    return new UCSReader(inputStream, UCSReader.UCS4BE);
                } else {
                    return new UCSReader(inputStream, UCSReader.UCS4LE);
                }
            } else {
                fErrorReporter.reportError(XMLMessageFormatter.XML_DOMAIN,
                        "EncodingByteOrderUnsupported",
                        new Object[] { encoding },
                        XMLErrorReporter.SEVERITY_FATAL_ERROR);
            }
        }
        if(ENCODING.equals("ISO-10646-UCS-2")) {
            if(isBigEndian != null) { 
                boolean isBE = isBigEndian.booleanValue();
                if(isBE) {
                    return new UCSReader(inputStream, UCSReader.UCS2BE);
                } else {
                    return new UCSReader(inputStream, UCSReader.UCS2LE);
                }
            } else {
                fErrorReporter.reportError(XMLMessageFormatter.XML_DOMAIN,
                        "EncodingByteOrderUnsupported",
                        new Object[] { encoding },
                        XMLErrorReporter.SEVERITY_FATAL_ERROR);
            }
        }

        boolean validIANA = XMLChar.isValidIANAEncoding(encoding);
        boolean validJava = XMLChar.isValidJavaEncoding(encoding);
        if (!validIANA || (fAllowJavaEncodings && !validJava)) {
            fErrorReporter.reportError(XMLMessageFormatter.XML_DOMAIN,
                    "EncodingDeclInvalid",
                    new Object[] { encoding },
                    XMLErrorReporter.SEVERITY_FATAL_ERROR);
                    encoding = "ISO-8859-1";
        }

        String javaEncoding = EncodingMap.getIANA2JavaMapping(ENCODING);
        if (javaEncoding == null) {
            if(fAllowJavaEncodings) {
                javaEncoding = encoding;
            } else {
                fErrorReporter.reportError(XMLMessageFormatter.XML_DOMAIN,
                        "EncodingDeclInvalid",
                        new Object[] { encoding },
                        XMLErrorReporter.SEVERITY_FATAL_ERROR);
                        javaEncoding = "ISO8859_1";
            }
        }
        else if (javaEncoding.equals("ASCII")) {
            if (DEBUG_ENCODINGS) {
                System.out.println("$$$ creating ASCIIReader");
            }
            return new ASCIIReader(inputStream, fBufferSize, fErrorReporter.getMessageFormatter(XMLMessageFormatter.XML_DOMAIN), fErrorReporter.getLocale());
        }

        if (DEBUG_ENCODINGS) {
            System.out.print("$$$ creating Java InputStreamReader: encoding="+javaEncoding);
            if (javaEncoding == encoding) {
                System.out.print(" (IANA encoding)");
            }
            System.out.println();
        }
        return new InputStreamReader(inputStream, javaEncoding);

    } 

    /**
     * Returns the IANA encoding name that is auto-detected from
     * the bytes specified, with the endian-ness of that encoding where appropriate.
     *
     * @param b4    The first four bytes of the input.
     * @param count The number of bytes actually read.
     * @return a 2-element array:  the first element, an IANA-encoding string,
     *  the second element a Boolean which is true iff the document is big endian, false
     *  if it's little-endian, and null if the distinction isn't relevant.
     */
    protected Object[] getEncodingName(byte[] b4, int count) {

        if (count < 2) {
            return new Object[]{"UTF-8", null};
        }

        int b0 = b4[0] & 0xFF;
        int b1 = b4[1] & 0xFF;
        if (b0 == 0xFE && b1 == 0xFF) {
            return new Object [] {"UTF-16BE", true};
        }
        if (b0 == 0xFF && b1 == 0xFE) {
            return new Object [] {"UTF-16LE", false};
        }

        if (count < 3) {
            return new Object [] {"UTF-8", null};
        }

        int b2 = b4[2] & 0xFF;
        if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
            return new Object [] {"UTF-8", null};
        }

        if (count < 4) {
            return new Object [] {"UTF-8", null};
        }

        int b3 = b4[3] & 0xFF;
        if (b0 == 0x00 && b1 == 0x00 && b2 == 0x00 && b3 == 0x3C) {
            return new Object [] {"ISO-10646-UCS-4", true};
        }
        if (b0 == 0x3C && b1 == 0x00 && b2 == 0x00 && b3 == 0x00) {
            return new Object [] {"ISO-10646-UCS-4", false};
        }
        if (b0 == 0x00 && b1 == 0x00 && b2 == 0x3C && b3 == 0x00) {
            return new Object [] {"ISO-10646-UCS-4", null};
        }
        if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x00) {
            return new Object [] {"ISO-10646-UCS-4", null};
        }
        if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x3F) {
            return new Object [] {"UTF-16BE", true};
        }
        if (b0 == 0x3C && b1 == 0x00 && b2 == 0x3F && b3 == 0x00) {
            return new Object [] {"UTF-16LE", false};
        }
        if (b0 == 0x4C && b1 == 0x6F && b2 == 0xA7 && b3 == 0x94) {
            return new Object [] {"CP037", null};
        }

        return new Object [] {"UTF-8", null};

    } 

    /**
     * xxx not removing endEntity() so that i remember that we need to implement it.
     * Ends an entity.
     *
     * @throws XNIException Thrown by entity handler to signal an error.
     */
    /** Prints the contents of the buffer. */
    final void print() {
        if (DEBUG_BUFFER) {
            if (fCurrentEntity != null) {
                System.out.print('[');
                System.out.print(fCurrentEntity.count);
                System.out.print(' ');
                System.out.print(fCurrentEntity.position);
                if (fCurrentEntity.count > 0) {
                    System.out.print(" \"");
                    for (int i = 0; i < fCurrentEntity.count; i++) {
                        if (i == fCurrentEntity.position) {
                            System.out.print('^');
                        }
                        char c = fCurrentEntity.ch[i];
                        switch (c) {
                            case '\n': {
                                System.out.print("\\n");
                                break;
                            }
                            case '\r': {
                                System.out.print("\\r");
                                break;
                            }
                            case '\t': {
                                System.out.print("\\t");
                                break;
                            }
                            case '\\': {
                                System.out.print("\\\\");
                                break;
                            }
                            default: {
                                System.out.print(c);
                            }
                        }
                    }
                    if (fCurrentEntity.position == fCurrentEntity.count) {
                        System.out.print('^');
                    }
                    System.out.print('"');
                }
                System.out.print(']');
                System.out.print(" @ ");
                System.out.print(fCurrentEntity.lineNumber);
                System.out.print(',');
                System.out.print(fCurrentEntity.columnNumber);
            } else {
                System.out.print("*NO CURRENT ENTITY*");
            }
        }
    }

    /**
     * Registers the listener object and provides callback.
     * @param listener listener to which call back should be provided when scanner buffer
     * is being changed.
     */
    public void registerListener(XMLBufferListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     *
     * @param loadPos Starting position from which new data is being loaded into scanner buffer.
     */
    public void invokeListeners(int loadPos){
        for (int i=0; i<listeners.size(); i++) {
            listeners.get(i).refresh(loadPos);
        }
    }

    /**
     * Skips space characters appearing immediately on the input that would
     * match non-terminal S (0x09, 0x0A, 0x0D, 0x20) before end of line
     * normalization is performed. This is useful when scanning structures
     * such as the XMLDecl and TextDecl that can only contain US-ASCII
     * characters.
     * <p>
     * <strong>Note:</strong> The characters are consumed only if they would
     * match non-terminal S before end of line normalization is performed.
     *
     * @return Returns true if at least one space character was skipped.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     *
     * @see com.sun.org.apache.xerces.internal.util.XMLChar#isSpace
     */
    protected final boolean skipDeclSpaces() throws IOException {
        if (DEBUG_BUFFER) {
            System.out.print("(skipDeclSpaces: ");
            System.out.println();
        }

        if (fCurrentEntity.position == fCurrentEntity.count) {
            load(0, true, false);
        }

        int c = fCurrentEntity.ch[fCurrentEntity.position];
        if (XMLChar.isSpace(c)) {
            boolean external = fCurrentEntity.isExternal();
            do {
                boolean entityChanged = false;
                if (c == '\n' || (external && c == '\r')) {
                    fCurrentEntity.lineNumber++;
                    fCurrentEntity.columnNumber = 1;
                    if (fCurrentEntity.position == fCurrentEntity.count - 1) {
                        fCurrentEntity.ch[0] = (char)c;
                        entityChanged = load(1, true, false);
                        if (!entityChanged)
                            fCurrentEntity.position = 0;
                    }
                    if (c == '\r' && external) {
                        if (fCurrentEntity.ch[++fCurrentEntity.position] != '\n') {
                            fCurrentEntity.position--;
                        }
                    }
                    /*** NEWLINE NORMALIZATION ***
                     * else {
                     * if (fCurrentEntity.ch[fCurrentEntity.position + 1] == '\r'
                     * && external) {
                     * fCurrentEntity.position++;
                     * }
                     * }
                     * /***/
                } else {
                    fCurrentEntity.columnNumber++;
                }
                if (!entityChanged)
                    fCurrentEntity.position++;
                if (fCurrentEntity.position == fCurrentEntity.count) {
                    load(0, true, false);
                }
            } while (XMLChar.isSpace(c = fCurrentEntity.ch[fCurrentEntity.position]));
            if (DEBUG_BUFFER) {
                System.out.print(")skipDeclSpaces: ");
                System.out.println(" -> true");
            }
            return true;
        }

        if (DEBUG_BUFFER) {
            System.out.print(")skipDeclSpaces: ");
            System.out.println(" -> false");
        }
        return false;

    } 

    /**
     * Normalizes newlines. As specified in XML specification, this method
     * converts newlines, '\n', '\r' and '\r\n' to '\n' as 2.11 End-of-Line Handling.
     * Further, it may put them in a cache for later process as needed, for example
     * as specified in 3.3.3 Attribute-Value Normalization.
     *
     * @ImplNote this method does not limit to processing external parsed entities
     * as 2.11 required. It handles all cases where newlines need to be processed.
     *
     * @param buffer the current content buffer
     * @param append a flag indicating whether to append to the buffer
     * @param storeWS a flag indicating whether the whitespaces need to be stored
     * for later processing
     * @param nt the type of the entity
     * @return true if the cursor is at the end of the current entity, false otherwise.
     * @throws IOException
     */
    protected boolean normalizeNewlines(short version, XMLString buffer, boolean append,
            boolean storeWS, NameType nt)
            throws IOException {
        offset = fCurrentEntity.position;
        int c = fCurrentEntity.ch[offset];
        newlines = 0;
        counted = false;
        if ((c == '\n' || c == '\r') ||
                (version == XML_VERSION_1_1 && (c == 0x85 || c == 0x2028) && isExternal)) {
            do {
                c = fCurrentEntity.ch[fCurrentEntity.position++];
                if ((c == '\n' || c == '\r') ||
                    (version == XML_VERSION_1_1 && (c == 0x85 || c == 0x2028))) {
                    newlines++;
                    fCurrentEntity.lineNumber++;
                    fCurrentEntity.columnNumber = 1;
                    if (fCurrentEntity.position == fCurrentEntity.count) {
                        checkEntityLimit(nt, fCurrentEntity, offset, newlines);
                        offset = 0;
                        fCurrentEntity.position = newlines;
                        if (load(newlines, false, true)) {
                            counted = true;
                            break;
                        }
                    }
                    if (c == '\r' && isExternal) {
                        int cc = fCurrentEntity.ch[fCurrentEntity.position];
                        if (cc == '\n' || (version == XML_VERSION_1_1 && cc == 0x85)) {
                            fCurrentEntity.position++;
                            offset++;
                        }
                        /*** NEWLINE NORMALIZATION ***/
                        else {
                            newlines++;
                        }
                    }
                } else {
                    fCurrentEntity.position--;
                    break;
                }
            } while (fCurrentEntity.position < fCurrentEntity.count - 1);

            for (int i = offset; i < fCurrentEntity.position; i++) {
                fCurrentEntity.ch[i] = '\n';
                if (storeWS) {
                    storeWhiteSpace(i);
                }
            }

            int length = fCurrentEntity.position - offset;
            if (fCurrentEntity.position == fCurrentEntity.count - 1) {
                checkEntityLimit(nt, fCurrentEntity, offset, length);
                if (append) {
                    buffer.append(fCurrentEntity.ch, offset, length);
                } else {
                    buffer.setValues(fCurrentEntity.ch, offset, length);
                }

                return true;
            }
        }
        return false;
    }
} 
