/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.sun.org.apache.xml.internal.serialize;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import com.sun.org.apache.xerces.internal.dom.DOMMessageFormatter;
import com.sun.org.apache.xerces.internal.util.NamespaceSupport;
import com.sun.org.apache.xerces.internal.util.SymbolTable;
import com.sun.org.apache.xerces.internal.util.XML11Char;
import com.sun.org.apache.xerces.internal.util.XMLChar;
import org.w3c.dom.DOMError;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Implements an XML serializer supporting both DOM and SAX pretty
 * serializing. For usage instructions see {@link Serializer}.
 * <p>
 * If an output stream is used, the encoding is taken from the
 * output format (defaults to <tt>UTF-8</tt>). If a writer is
 * used, make sure the writer uses the same encoding (if applies)
 * as specified in the output format.
 * <p>
 * The serializer supports both DOM and SAX. SAX serializing is done by firing
 * SAX events and using the serializer as a document handler. DOM serializing is done
 * by calling {@link #serialize(Document)} or by using DOM Level 3
 * {@link org.w3c.dom.ls.LSSerializer} and
 * serializing with {@link org.w3c.dom.ls.LSSerializer#write},
 * {@link org.w3c.dom.ls.LSSerializer#writeToString}.
 * <p>
 * If an I/O exception occurs while serializing, the serializer
 * will not throw an exception directly, but only throw it
 * at the end of serializing (either DOM or SAX's {@link
 * org.xml.sax.DocumentHandler#endDocument}.
 * <p>
 * For elements that are not specified as whitespace preserving,
 * the serializer will potentially break long text lines at space
 * boundaries, indent lines, and serialize elements on separate
 * lines. Line terminators will be regarded as spaces, and
 * spaces at beginning of line will be stripped.
 *
 * @author <a href="mailto:arkin@intalio.com">Assaf Arkin</a>
 * @author Rahul Srivastava
 * @author Elena Litani IBM
 * @see Serializer
 *
 * @deprecated As of JDK 9, Xerces 2.9.0, Xerces DOM L3 Serializer implementation
 * is replaced by that of Xalan. Main class
 * {@link com.sun.org.apache.xml.internal.serialize.DOMSerializerImpl} is replaced
 * by {@link com.sun.org.apache.xml.internal.serializer.dom3.LSSerializerImpl}.
 */
@Deprecated
public class XML11Serializer
extends XMLSerializer {


    protected static final boolean DEBUG = false;



    /** stores namespaces in scope */
    protected NamespaceSupport fNSBinder;

    /** stores all namespace bindings on the current element */
    protected NamespaceSupport fLocalNSBinder;

    /** symbol table for serialization */
    protected SymbolTable fSymbolTable;

    protected boolean fDOML1 = false;
    protected int fNamespaceCounter = 1;
    protected final static String PREFIX = "NS";

    /**
     * Controls whether namespace fixup should be performed during
     * the serialization.
     * NOTE: if this field is set to true the following
     * fields need to be initialized: fNSBinder, fLocalNSBinder, fSymbolTable,
     * XMLSymbols.EMPTY_STRING, fXmlSymbol, fXmlnsSymbol, fNamespaceCounter.
     */
    protected boolean fNamespaces = false;

    /**
     * Constructs a new serializer. The serializer cannot be used without
     * calling {@link #setOutputCharStream} or {@link #setOutputByteStream}
     * first.
     */
    public XML11Serializer() {
        super( );
        _format.setVersion("1.1");
    }


    /**
     * Constructs a new serializer. The serializer cannot be used without
     * calling {@link #setOutputCharStream} or {@link #setOutputByteStream}
     * first.
     */
    public XML11Serializer( OutputFormat format ) {
        super( format );
        _format.setVersion("1.1");
    }


    /**
     * Constructs a new serializer that writes to the specified writer
     * using the specified output format. If <tt>format</tt> is null,
     * will use a default output format.
     *
     * @param writer The writer to use
     * @param format The output format to use, null for the default
     */
    public XML11Serializer( Writer writer, OutputFormat format ) {
        super( writer, format );
        _format.setVersion("1.1");
    }


    /**
     * Constructs a new serializer that writes to the specified output
     * stream using the specified output format. If <tt>format</tt>
     * is null, will use a default output format.
     *
     * @param output The output stream to use
     * @param format The output format to use, null for the default
     */
    public XML11Serializer( OutputStream output, OutputFormat format ) {
        super( output, format != null ? format : new OutputFormat( Method.XML, null, false ) );
        _format.setVersion("1.1");
    }



    public void characters( char[] chars, int start, int length )
        throws SAXException
    {
        ElementState state;

        try {
            state = content();


            if ( state.inCData || state.doCData ) {
                int          saveIndent;

                if ( ! state.inCData ) {
                    _printer.printText( "<![CDATA[" );
                    state.inCData = true;
                }
                saveIndent = _printer.getNextIndent();
                _printer.setNextIndent( 0 );
                char ch;
                final int end = start + length;
                for ( int index = start; index < end; ++index ) {
                    ch = chars[index];
                    if ( ch == ']' && index + 2 < end &&
                        chars[ index + 1 ] == ']' && chars[ index + 2 ] == '>' ) {
                        _printer.printText("]]]]><![CDATA[>");
                        index +=2;
                        continue;
                    }
                    if (!XML11Char.isXML11Valid(ch)) {
                        if (++index < end) {
                            surrogates(ch, chars[index], true);
                        }
                        else {
                            fatalError("The character '"+ch+"' is an invalid XML character");
                        }
                        continue;
                    }
                    if ( _encodingInfo.isPrintable(ch) && XML11Char.isXML11ValidLiteral(ch)) {
                        _printer.printText(ch);
                    }
                    else {
                        _printer.printText("]]>&#x");
                        _printer.printText(Integer.toHexString(ch));
                        _printer.printText(";<![CDATA[");
                    }
                }
                _printer.setNextIndent( saveIndent );

            }
            else {

                int saveIndent;

                if ( state.preserveSpace ) {
                    saveIndent = _printer.getNextIndent();
                    _printer.setNextIndent( 0 );
                    printText( chars, start, length, true, state.unescaped );
                    _printer.setNextIndent( saveIndent );
                }
                else {
                    printText( chars, start, length, false, state.unescaped );
                }
            }
        }
        catch ( IOException except ) {
            throw new SAXException( except );
        }
    }

    protected void printEscaped( String source ) throws IOException {
        int length = source.length();
        for ( int i = 0 ; i < length ; ++i ) {
            int ch = source.charAt(i);
            if (!XML11Char.isXML11Valid(ch)) {
                if (++i <length) {
                    surrogates(ch, source.charAt(i), false);
                }
                else {
                    fatalError("The character '"+(char)ch+"' is an invalid XML character");
                }
                continue;
            }
            if (ch == '\n' || ch == '\r' || ch == '\t' || ch == 0x0085 || ch == 0x2028) {
                printHex(ch);
            }
            else if (ch == '<') {
                _printer.printText("&lt;");
            }
            else if (ch == '&') {
                _printer.printText("&amp;");
            }
            else if (ch == '"') {
                _printer.printText("&quot;");
            }
            else if ((ch >= ' ' && _encodingInfo.isPrintable((char) ch))) {
                _printer.printText((char) ch);
            }
            else {
                printHex(ch);
            }
        }
    }

    protected final void printCDATAText(String text) throws IOException {
        int length = text.length();
        char ch;

        for (int index = 0; index < length; ++index) {
            ch = text.charAt(index);

            if (ch == ']'
                && index + 2 < length
                && text.charAt(index + 1) == ']'
                && text.charAt(index + 2) == '>') { 
                if (fDOMErrorHandler != null){
                if ((features & DOMSerializerImpl.SPLITCDATA) == 0
                    && (features & DOMSerializerImpl.WELLFORMED) == 0) {
                    String msg =
                        DOMMessageFormatter.formatMessage(
                            DOMMessageFormatter.SERIALIZER_DOMAIN,
                            "EndingCDATA",
                            null);
                    modifyDOMError(
                        msg,
                        DOMError.SEVERITY_FATAL_ERROR,
                        null, fCurrentNode);
                    boolean continueProcess =
                        fDOMErrorHandler.handleError(fDOMError);
                    if (!continueProcess) {
                        throw new IOException();
                    }
                } else {
                    String msg =
                        DOMMessageFormatter.formatMessage(
                            DOMMessageFormatter.SERIALIZER_DOMAIN,
                            "SplittingCDATA",
                            null);
                    modifyDOMError(
                        msg,
                        DOMError.SEVERITY_WARNING,
                        null, fCurrentNode);
                    fDOMErrorHandler.handleError(fDOMError);
                }
                }
                _printer.printText("]]]]><![CDATA[>");
                index += 2;
                continue;
            }

            if (!XML11Char.isXML11Valid(ch)) {
                if (++index < length) {
                    surrogates(ch, text.charAt(index), true);
                }
                else {
                    fatalError("The character '" + ch + "' is an invalid XML character");
                }
                continue;
            }
            if (_encodingInfo.isPrintable(ch)
                && XML11Char.isXML11ValidLiteral(ch)) {
                _printer.printText(ch);
            }
            else {
                _printer.printText("]]>&#x");
                _printer.printText(Integer.toHexString(ch));
                _printer.printText(";<![CDATA[");
            }
        }
    }

    protected final void printXMLChar( int ch ) throws IOException {

        if (ch == '\r' || ch == 0x0085 || ch == 0x2028) {
            printHex(ch);
        }
        else if ( ch == '<') {
            _printer.printText("&lt;");
        }
        else if (ch == '&') {
            _printer.printText("&amp;");
        }
        else if (ch == '>'){
            _printer.printText("&gt;");
        }
        else if ( _encodingInfo.isPrintable((char)ch) && XML11Char.isXML11ValidLiteral(ch)) {
            _printer.printText((char)ch);
        }
        else {
            printHex(ch);
        }
    }



    protected final void surrogates(int high, int low, boolean inContent) throws IOException{
        if (XMLChar.isHighSurrogate(high)) {
            if (!XMLChar.isLowSurrogate(low)) {
                fatalError("The character '"+(char)low+"' is an invalid XML character");
            }
            else {
                int supplemental = XMLChar.supplemental((char)high, (char)low);
                if (!XML11Char.isXML11Valid(supplemental)) {
                    fatalError("The character '"+(char)supplemental+"' is an invalid XML character");
                }
                else {
                    if (inContent && content().inCData) {
                        _printer.printText("]]>&#x");
                        _printer.printText(Integer.toHexString(supplemental));
                        _printer.printText(";<![CDATA[");
                    }
                    else {
                                                printHex(supplemental);
                    }
                }
            }
        }
        else {
            fatalError("The character '"+(char)high+"' is an invalid XML character");
        }

    }


    protected void printText( String text, boolean preserveSpace, boolean unescaped )
    throws IOException {
        int index;
        char ch;
        int length = text.length();
        if ( preserveSpace ) {
            for ( index = 0 ; index < length ; ++index ) {
                ch = text.charAt( index );
                if (!XML11Char.isXML11Valid(ch)) {
                    if (++index <length) {
                        surrogates(ch, text.charAt(index), true);
                    } else {
                        fatalError("The character '"+ch+"' is an invalid XML character");
                    }
                    continue;
                }
                if ( unescaped  && XML11Char.isXML11ValidLiteral(ch)) {
                    _printer.printText( ch );
                }
                else {
                    printXMLChar( ch );
                }
            }
        }
        else {
            for ( index = 0 ; index < length ; ++index ) {
                ch = text.charAt( index );
                if (!XML11Char.isXML11Valid(ch)) {
                    if (++index <length) {
                        surrogates(ch, text.charAt(index), true);
                    } else {
                        fatalError("The character '"+ch+"' is an invalid XML character");
                    }
                    continue;
                }

                if ( unescaped && XML11Char.isXML11ValidLiteral(ch) ) {
                    _printer.printText( ch );
                }
                else {
                    printXMLChar( ch );
                }
            }
        }
    }

    protected void printText( char[] chars, int start, int length,
                              boolean preserveSpace, boolean unescaped ) throws IOException {

        if ( preserveSpace ) {
            while ( length-- > 0 ) {
                char ch = chars[start++];
                if (!XML11Char.isXML11Valid(ch)) {
                    if ( length-- > 0) {
                        surrogates(ch, chars[start++], true);
                    } else {
                        fatalError("The character '"+ch+"' is an invalid XML character");
                    }
                    continue;
                }
                if ( unescaped && XML11Char.isXML11ValidLiteral(ch)) {
                    _printer.printText( ch );
                }
                else {
                    printXMLChar( ch );
                }
            }
        }
        else {
            while ( length-- > 0 ) {
                char ch = chars[start++];
                if (!XML11Char.isXML11Valid(ch)) {
                    if ( length-- > 0) {
                        surrogates(ch, chars[start++], true);
                    } else {
                        fatalError("The character '"+ch+"' is an invalid XML character");
                    }
                    continue;
                }

                if ( unescaped && XML11Char.isXML11ValidLiteral(ch)) {
                    _printer.printText( ch );
                }
                else {
                    printXMLChar( ch );
                }
            }
        }
    }

    public boolean reset() {
        super.reset();
        return true;
    }

}
