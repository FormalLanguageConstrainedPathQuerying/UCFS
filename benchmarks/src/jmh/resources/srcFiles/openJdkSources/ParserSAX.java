/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.util.xml.impl;

import java.io.IOException;
import java.io.InputStream;
import jdk.internal.org.xml.sax.ContentHandler;
import jdk.internal.org.xml.sax.DTDHandler;
import jdk.internal.org.xml.sax.EntityResolver;
import jdk.internal.org.xml.sax.ErrorHandler;
import jdk.internal.org.xml.sax.InputSource;
import jdk.internal.org.xml.sax.Locator;
import jdk.internal.org.xml.sax.SAXException;
import jdk.internal.org.xml.sax.SAXParseException;
import jdk.internal.org.xml.sax.XMLReader;
import jdk.internal.org.xml.sax.helpers.DefaultHandler;

/**
 * XML non-validating push parser.
 * <p>
 * This non-validating parser conforms to <a href="http:
 * Extensible Markup Language (XML) 1.0</a> and
 * <a href="http:
 * specifications. The API supported by the parser are
 * <a href="https:
 * <a href="http:
 * <a href="https:
 * and <a href="http:
 *
 * @see org.xml.sax.XMLReader
 */

final class ParserSAX
    extends Parser implements XMLReader, Locator
{
    public static final String FEATURE_NS =
            "http:
    public static final String FEATURE_PREF =
            "http:
    private boolean mFNamespaces;
    private boolean mFPrefixes;
    private DefaultHandler mHand;      
    private ContentHandler mHandCont;  
    private DTDHandler mHandDtd;   
    private ErrorHandler mHandErr;   
    private EntityResolver mHandEnt;   

    /**
     * Constructor.
     */
    public ParserSAX() {
        super();

        mFNamespaces = true;
        mFPrefixes = false;

        mHand = new DefaultHandler();
        mHandCont = mHand;
        mHandDtd = mHand;
        mHandErr = mHand;
        mHandEnt = mHand;
    }

    /**
     * Return the current content handler.
     *
     * @return The current content handler, or null if none has been registered.
     * @see #setContentHandler
     */
    public ContentHandler getContentHandler() {
        return (mHandCont != mHand) ? mHandCont : null;
    }

    /**
     * Allow an application to register a content event handler.
     *
     * <p>If the application does not register a content handler, all content
     * events reported by the SAX parser will be silently ignored.</p>
     *
     * <p>Applications may register a new or different handler in the middle of
     * a parse, and the SAX parser must begin using the new handler
     * immediately.</p>
     *
     * @param handler The content handler.
     * @exception java.lang.NullPointerException If the handler argument is
     * null.
     * @see #getContentHandler
     */
    public void setContentHandler(ContentHandler handler) {
        if (handler == null) {
            throw new NullPointerException();
        }
        mHandCont = handler;
    }

    /**
     * Return the current DTD handler.
     *
     * @return The current DTD handler, or null if none has been registered.
     * @see #setDTDHandler
     */
    public DTDHandler getDTDHandler() {
        return (mHandDtd != mHand) ? mHandDtd : null;
    }

    /**
     * Allow an application to register a DTD event handler.
     *
     * <p>If the application does not register a DTD handler, all DTD events
     * reported by the SAX parser will be silently ignored.</p>
     *
     * <p>Applications may register a new or different handler in the middle of
     * a parse, and the SAX parser must begin using the new handler
     * immediately.</p>
     *
     * @param handler The DTD handler.
     * @exception java.lang.NullPointerException If the handler argument is
     * null.
     * @see #getDTDHandler
     */
    public void setDTDHandler(DTDHandler handler) {
        if (handler == null) {
            throw new NullPointerException();
        }
        mHandDtd = handler;
    }

    /**
     * Return the current error handler.
     *
     * @return The current error handler, or null if none has been registered.
     * @see #setErrorHandler
     */
    public ErrorHandler getErrorHandler() {
        return (mHandErr != mHand) ? mHandErr : null;
    }

    /**
     * Allow an application to register an error event handler.
     *
     * <p>If the application does not register an error handler, all error
     * events reported by the SAX parser will be silently ignored; however,
     * normal processing may not continue. It is highly recommended that all SAX
     * applications implement an error handler to avoid unexpected bugs.</p>
     *
     * <p>Applications may register a new or different handler in the middle of
     * a parse, and the SAX parser must begin using the new handler
     * immediately.</p>
     *
     * @param handler The error handler.
     * @exception java.lang.NullPointerException If the handler argument is
     * null.
     * @see #getErrorHandler
     */
    public void setErrorHandler(ErrorHandler handler) {
        if (handler == null) {
            throw new NullPointerException();
        }
        mHandErr = handler;
    }

    /**
     * Return the current entity resolver.
     *
     * @return The current entity resolver, or null if none has been registered.
     * @see #setEntityResolver
     */
    public EntityResolver getEntityResolver() {
        return (mHandEnt != mHand) ? mHandEnt : null;
    }

    /**
     * Allow an application to register an entity resolver.
     *
     * <p>If the application does not register an entity resolver, the XMLReader
     * will perform its own default resolution.</p>
     *
     * <p>Applications may register a new or different resolver in the middle of
     * a parse, and the SAX parser must begin using the new resolver
     * immediately.</p>
     *
     * @param resolver The entity resolver.
     * @exception java.lang.NullPointerException If the resolver argument is
     * null.
     * @see #getEntityResolver
     */
    public void setEntityResolver(EntityResolver resolver) {
        if (resolver == null) {
            throw new NullPointerException();
        }
        mHandEnt = resolver;
    }

    /**
     * Return the public identifier for the current document event.
     *
     * <p>The return value is the public identifier of the document entity or of
     * the external parsed entity in which the markup triggering the event
     * appears.</p>
     *
     * @return A string containing the public identifier, or null if none is
     * available.
     *
     * @see #getSystemId
     */
    public String getPublicId() {
        return (mInp != null) ? mInp.pubid : null;
    }

    /**
     * Return the system identifier for the current document event.
     *
     * <p>The return value is the system identifier of the document entity or of
     * the external parsed entity in which the markup triggering the event
     * appears.</p>
     *
     * <p>If the system identifier is a URL, the parser must resolve it fully
     * before passing it to the application.</p>
     *
     * @return A string containing the system identifier, or null if none is
     * available.
     *
     * @see #getPublicId
     */
    public String getSystemId() {
        return (mInp != null) ? mInp.sysid : null;
    }

    /**
     * Return the line number where the current document event ends.
     *
     * @return Always returns -1 indicating the line number is not available.
     *
     * @see #getColumnNumber
     */
    public int getLineNumber() {
        return -1;
    }

    /**
     * Return the column number where the current document event ends.
     *
     * @return Always returns -1 indicating the column number is not available.
     *
     * @see #getLineNumber
     */
    public int getColumnNumber() {
        return -1;
    }

    /**
     * Parse an XML document from a system identifier (URI).
     *
     * <p>This method is a shortcut for the common case of reading a document
     * from a system identifier. It is the exact equivalent of the
     * following:</p>
     *
     * <pre>
     * parse(new InputSource(systemId));
     * </pre>
     *
     * <p>If the system identifier is a URL, it must be fully resolved by the
     * application before it is passed to the parser.</p>
     *
     * @param systemId The system identifier (URI).
     * @exception org.xml.sax.SAXException Any SAX exception, possibly wrapping
     * another exception.
     * @exception java.io.IOException An IO exception from the parser, possibly
     * from a byte stream or character stream supplied by the application.
     * @see #parse(org.xml.sax.InputSource)
     */
    public void parse(String systemId) throws IOException, SAXException {
        parse(new InputSource(systemId));
    }

    /**
     * Parse an XML document.
     *
     * <p>The application can use this method to instruct the XML reader to
     * begin parsing an XML document from any valid input source (a character
     * stream, a byte stream, or a URI).</p>
     *
     * <p>Applications may not invoke this method while a parse is in progress
     * (they should create a new XMLReader instead for each nested XML
     * document). Once a parse is complete, an application may reuse the same
     * XMLReader object, possibly with a different input source.</p>
     *
     * <p>During the parse, the XMLReader will provide information about the XML
     * document through the registered event handlers.</p>
     *
     * <p>This method is synchronous: it will not return until parsing has
     * ended. If a client application wants to terminate parsing early, it
     * should throw an exception.</p>
     *
     * @param is The input source for the top-level of the XML document.
     * @exception org.xml.sax.SAXException Any SAX exception, possibly wrapping
     * another exception.
     * @exception java.io.IOException An IO exception from the parser, possibly
     * from a byte stream or character stream supplied by the application.
     * @see org.xml.sax.InputSource
     * @see #parse(java.lang.String)
     * @see #setEntityResolver
     * @see #setDTDHandler
     * @see #setContentHandler
     * @see #setErrorHandler
     */
    public void parse(InputSource is) throws IOException, SAXException {
        if (is == null) {
            throw new IllegalArgumentException("");
        }
        mInp = new Input(BUFFSIZE_READER);
        mPh = PH_BEFORE_DOC;  
        try {
            setinp(is);
        } catch (SAXException | IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            panic(e.toString());
        }
        parse();
    }

    /**
     * Parse the content of the given {@link java.io.InputStream} instance as
     * XML using the specified {@link org.xml.sax.helpers.DefaultHandler}.
     *
     * @param src InputStream containing the content to be parsed.
     * @param handler The SAX DefaultHandler to use.
     * @exception IOException If any IO errors occur.
     * @exception IllegalArgumentException If the given InputStream or handler
     * is null.
     * @exception SAXException If the underlying parser throws a SAXException
     * while parsing.
     * @see org.xml.sax.helpers.DefaultHandler
     */
    public void parse(InputStream src, DefaultHandler handler)
            throws SAXException, IOException {
        if ((src == null) || (handler == null)) {
            throw new IllegalArgumentException("");
        }
        parse(new InputSource(src), handler);
    }

    /**
     * Parse the content given {@link org.xml.sax.InputSource} as XML using the
     * specified {@link org.xml.sax.helpers.DefaultHandler}.
     *
     * @param is The InputSource containing the content to be parsed.
     * @param handler The SAX DefaultHandler to use.
     * @exception IOException If any IO errors occur.
     * @exception IllegalArgumentException If the InputSource or handler is
     * null.
     * @exception SAXException If the underlying parser throws a SAXException
     * while parsing.
     * @see org.xml.sax.helpers.DefaultHandler
     */
    public void parse(InputSource is, DefaultHandler handler)
        throws SAXException, IOException
    {
        if ((is == null) || (handler == null)) {
            throw new IllegalArgumentException("");
        }
        mHandCont = handler;
        mHandDtd = handler;
        mHandErr = handler;
        mHandEnt = handler;
        mInp = new Input(BUFFSIZE_READER);
        mPh = PH_BEFORE_DOC;  
        try {
            setinp(is);
        } catch (SAXException | IOException | RuntimeException saxe) {
            throw saxe;
        } catch (Exception e) {
            panic(e.toString());
        }
        parse();
    }

    /**
     * Parse the XML document content using specified handlers and an input
     * source.
     *
     * @exception IOException If any IO errors occur.
     * @exception SAXException If the underlying parser throws a SAXException
     * while parsing.
     */
    @SuppressWarnings("fallthrough")
    private void parse() throws SAXException, IOException {
        init();
        try {
            mHandCont.setDocumentLocator(this);
            mHandCont.startDocument();

            if (mPh != PH_MISC_DTD) {
                mPh = PH_MISC_DTD;  
            }
            int evt = EV_NULL;
            do {
                wsskip();
                switch (evt = step()) {
                    case EV_ELM:
                    case EV_ELMS:
                        mPh = PH_DOCELM;
                        break;

                    case EV_COMM:
                    case EV_PI:
                        break;

                    case EV_DTD:
                        if (mPh >= PH_DTD_MISC) {
                            panic(FAULT);
                        }
                        mPh = PH_DTD_MISC;  
                        break;

                    default:
                        panic(FAULT);
                }
            } while (mPh < PH_DOCELM);  
            do {
                switch (evt) {
                    case EV_ELM:
                    case EV_ELMS:
                        if (mIsNSAware == true) {
                            mHandCont.startElement(
                                    mElm.value,
                                    mElm.name,
                                    "",
                                    mAttrs);
                        } else {
                            mHandCont.startElement(
                                    "",
                                    "",
                                    mElm.name,
                                    mAttrs);
                        }
                        if (evt == EV_ELMS) {
                            evt = step();
                            break;
                        }

                    case EV_ELME:
                        if (mIsNSAware == true) {
                            mHandCont.endElement(mElm.value, mElm.name, "");
                        } else {
                            mHandCont.endElement("", "", mElm.name);
                        }
                        while (mPref.list == mElm) {
                            mHandCont.endPrefixMapping(mPref.name);
                            mPref = del(mPref);
                        }
                        mElm = del(mElm);
                        if (mElm == null) {
                            mPh = PH_DOCELM_MISC;
                        } else {
                            evt = step();
                        }
                        break;

                    case EV_TEXT:
                    case EV_WSPC:
                    case EV_CDAT:
                    case EV_COMM:
                    case EV_PI:
                    case EV_ENT:
                        evt = step();
                        break;

                    default:
                        panic(FAULT);
                }
            } while (mPh == PH_DOCELM);
            do {
                if (wsskip() == EOS) {
                    break;
                }

                switch (step()) {
                    case EV_COMM:
                    case EV_PI:
                        break;

                    default:
                        panic(FAULT);
                }
            } while (mPh == PH_DOCELM_MISC);
            mPh = PH_AFTER_DOC;  

        } catch (SAXException | IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            panic(e.toString());
        } finally {
            mHandCont.endDocument();
            cleanup();
        }
    }

    /**
     * Reports document type.
     *
     * @param name The name of the entity.
     * @param pubid The public identifier of the entity or <code>null</code>.
     * @param sysid The system identifier of the entity or <code>null</code>.
     */
    protected void docType(String name, String pubid, String sysid) throws SAXException {
        mHandDtd.startDTD(name, pubid, sysid);
    }

    /**
     * Reports the start of DTD internal subset.
     *
     * @throws SAXException if the receiver throws SAXException
     */
    public void startInternalSub () throws SAXException {
        mHandDtd.startInternalSub();
    }

    /**
     * Reports a comment.
     *
     * @param text The comment text starting from first character.
     * @param length The number of characters in comment.
     */
    protected void comm(char[] text, int length) {
    }

    /**
     * Reports a processing instruction.
     *
     * @param target The processing instruction target name.
     * @param body The processing instruction body text.
     */
    protected void pi(String target, String body) throws SAXException {
        mHandCont.processingInstruction(target, body);
    }

    /**
     * Reports new namespace prefix. The Namespace prefix (
     * <code>mPref.name</code>) being declared and the Namespace URI (
     * <code>mPref.value</code>) the prefix is mapped to. An empty string is
     * used for the default element namespace, which has no prefix.
     */
    protected void newPrefix() throws SAXException {
        mHandCont.startPrefixMapping(mPref.name, mPref.value);
    }

    /**
     * Reports skipped entity name.
     *
     * @param name The entity name.
     */
    protected void skippedEnt(String name) throws SAXException {
        mHandCont.skippedEntity(name);
    }

    /**
     * Returns an
     * <code>InputSource</code> for specified entity or
     * <code>null</code>.
     *
     * @param name The name of the entity.
     * @param pubid The public identifier of the entity.
     * @param sysid The system identifier of the entity.
     */
    protected InputSource resolveEnt(String name, String pubid, String sysid)
        throws SAXException, IOException
    {
        return mHandEnt.resolveEntity(pubid, sysid);
    }

    /**
     * Reports notation declaration.
     *
     * @param name The notation's name.
     * @param pubid The notation's public identifier, or null if none was given.
     * @param sysid The notation's system identifier, or null if none was given.
     */
    protected void notDecl(String name, String pubid, String sysid)
        throws SAXException
    {
        mHandDtd.notationDecl(name, pubid, sysid);
    }

    /**
     * Reports unparsed entity name.
     *
     * @param name The unparsed entity's name.
     * @param pubid The entity's public identifier, or null if none was given.
     * @param sysid The entity's system identifier.
     * @param notation The name of the associated notation.
     */
    protected void unparsedEntDecl(String name, String pubid, String sysid, String notation)
        throws SAXException
    {
        mHandDtd.unparsedEntityDecl(name, pubid, sysid, notation);
    }

    /**
     * Notifies the handler about fatal parsing error.
     *
     * @param msg The problem description message.
     */
    protected void panic(String msg) throws SAXException {
        SAXParseException spe = new SAXParseException(msg, this);
        mHandErr.fatalError(spe);
        throw spe;  
    }

    /**
     * Reports characters and empties the parser's buffer. This method is called
     * only if parser is going to return control to the main loop. This means
     * that this method may use parser buffer to report white space without
     * copying characters to temporary buffer.
     */
    protected void bflash() throws SAXException {
        if (mBuffIdx >= 0) {
            mHandCont.characters(mBuff, 0, (mBuffIdx + 1));
            mBuffIdx = -1;
        }
    }

    /**
     * Reports white space characters and empties the parser's buffer. This
     * method is called only if parser is going to return control to the main
     * loop. This means that this method may use parser buffer to report white
     * space without copying characters to temporary buffer.
     */
    protected void bflash_ws() throws SAXException {
        if (mBuffIdx >= 0) {

            mHandCont.characters(mBuff, 0, (mBuffIdx + 1));
            mBuffIdx = -1;
        }
    }

    public boolean getFeature(String name) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setFeature(String name, boolean value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Object getProperty(String name) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setProperty(String name, Object value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
