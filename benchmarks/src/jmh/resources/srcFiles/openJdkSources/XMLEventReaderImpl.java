/*
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.stream;

import com.sun.xml.internal.stream.events.XMLEventAllocatorImpl;
import java.util.NoSuchElementException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.EntityReference;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.XMLEventAllocator;

/**
 * @author  @author  Neeraj Bajaj Sun Microsystems
 *
 */

public class XMLEventReaderImpl implements javax.xml.stream.XMLEventReader{

    protected XMLStreamReader fXMLReader ;
    protected XMLEventAllocator fXMLEventAllocator;

    public XMLEventReaderImpl(XMLStreamReader reader) throws  XMLStreamException {
        fXMLReader = reader ;
        fXMLEventAllocator = (XMLEventAllocator)reader.getProperty(XMLInputFactory.ALLOCATOR);
        if(fXMLEventAllocator == null){
            fXMLEventAllocator = new XMLEventAllocatorImpl();
        }
        fPeekedEvent = fXMLEventAllocator.allocate(fXMLReader);
    }


    public boolean hasNext() {
        if(fPeekedEvent != null)return true;
        boolean next = false ;
        try{
            next = fXMLReader.hasNext();
        }catch(XMLStreamException ex){
            return false;
        }
        return next ;
    }


    public XMLEvent nextEvent() throws XMLStreamException {
        if(fPeekedEvent != null){
            fLastEvent = fPeekedEvent ;
            fPeekedEvent = null;
            return fLastEvent ;
        }
        else if(fXMLReader.hasNext()){
            fXMLReader.next();
            return fLastEvent = fXMLEventAllocator.allocate(fXMLReader);
        }
        else{
            fLastEvent = null;
            throw new NoSuchElementException();
        }
    }

    public void remove(){
        throw new java.lang.UnsupportedOperationException();
    }


    public void close() throws XMLStreamException {
        fXMLReader.close();
    }

    /** Reads the content of a text-only element. Precondition:
     * the current event is START_ELEMENT. Postcondition:
     * The current event is the corresponding END_ELEMENT.
     * @throws XMLStreamException if the current event is not a START_ELEMENT
     * or if a non text element is encountered
     */
    public String getElementText() throws XMLStreamException {
        if(fLastEvent.getEventType() != XMLEvent.START_ELEMENT){
            throw new XMLStreamException(
            "parser must be on START_ELEMENT to read next text", fLastEvent.getLocation());
        }



        String data = null;
        if(fPeekedEvent != null){
            XMLEvent event = fPeekedEvent ;
            fPeekedEvent = null;
            int type = event.getEventType();

            if(  type == XMLEvent.CHARACTERS || type == XMLEvent.SPACE ||
            type == XMLEvent.CDATA){
                data = event.asCharacters().getData();
            }
            else if(type == XMLEvent.ENTITY_REFERENCE){
                data = ((EntityReference)event).getDeclaration().getReplacementText();
            }
            else if(type == XMLEvent.COMMENT || type == XMLEvent.PROCESSING_INSTRUCTION){
            } else if(type == XMLEvent.START_ELEMENT) {
                throw new XMLStreamException(
                "elementGetText() function expects text only elment but START_ELEMENT was encountered.", event.getLocation());
            }else if(type == XMLEvent.END_ELEMENT){
                return "";
            }

            StringBuilder buffer = new StringBuilder();
            if(data != null && data.length() > 0 ) {
                buffer.append(data);
            }
            event = nextEvent();
            while ((type = event.getEventType()) != XMLEvent.END_ELEMENT) {
                if (type == XMLEvent.CHARACTERS || type == XMLEvent.SPACE ||
                    type == XMLEvent.CDATA){
                    data = event.asCharacters().getData();
                }
                else if(type == XMLEvent.ENTITY_REFERENCE){
                    data = ((EntityReference)event).getDeclaration().getReplacementText();
                }
                else if(type == XMLEvent.COMMENT || type == XMLEvent.PROCESSING_INSTRUCTION){
                    data = null;
                } else if(type == XMLEvent.END_DOCUMENT) {
                    throw new XMLStreamException("unexpected end of document when reading element text content");
                } else if(type == XMLEvent.START_ELEMENT) {
                    throw new XMLStreamException(
                    "elementGetText() function expects text only elment but START_ELEMENT was encountered.", event.getLocation());
                } else {
                    throw new XMLStreamException(
                    "Unexpected event type "+ type, event.getLocation());
                }
                if(data != null && data.length() > 0 ) {
                    buffer.append(data);
                }
                event = nextEvent();
            }
            return buffer.toString();
        }

        data = fXMLReader.getElementText();
        fLastEvent = fXMLEventAllocator.allocate(fXMLReader);
        return data;
    }

    /** Get the value of a feature/property from the underlying implementation
     * @param name The name of the property
     * @return The value of the property
     * @throws IllegalArgumentException if the property is not supported
     */
    public Object getProperty(java.lang.String name) throws java.lang.IllegalArgumentException {
        return fXMLReader.getProperty(name) ;
    }

    /** Skips any insignificant space events until a START_ELEMENT or
     * END_ELEMENT is reached. If anything other than space characters are
     * encountered, an exception is thrown. This method should
     * be used when processing element-only content because
     * the parser is not able to recognize ignorable whitespace if
     * the DTD is missing or not interpreted.
     * @throws XMLStreamException if anything other than space characters are encountered
     */
    public XMLEvent nextTag() throws XMLStreamException {
        if(fPeekedEvent != null){
            XMLEvent event = fPeekedEvent;
            fPeekedEvent = null ;
            int eventType = event.getEventType();
            if( (event.isCharacters() && event.asCharacters().isWhiteSpace())
            || eventType == XMLStreamConstants.PROCESSING_INSTRUCTION
            || eventType == XMLStreamConstants.COMMENT
            || eventType == XMLStreamConstants.START_DOCUMENT){
                event = nextEvent();
                eventType = event.getEventType();
            }

            while((event.isCharacters() && event.asCharacters().isWhiteSpace())
            || eventType == XMLStreamConstants.PROCESSING_INSTRUCTION
            || eventType == XMLStreamConstants.COMMENT){

                event = nextEvent();
                eventType = event.getEventType();
            }

            if (eventType != XMLStreamConstants.START_ELEMENT && eventType != XMLStreamConstants.END_ELEMENT) {
                throw new XMLStreamException("expected start or end tag", event.getLocation());
            }
            return event;
        }

        fXMLReader.nextTag();
        return (fLastEvent = fXMLEventAllocator.allocate(fXMLReader));
    }

    public Object next() {
        Object object = null;
        try{
            object = nextEvent();
        }catch(XMLStreamException streamException){
            fLastEvent = null ;
            NoSuchElementException e = new NoSuchElementException(streamException.getMessage());
            e.initCause(streamException.getCause());
            throw e;

        }
        return object;
    }

    public XMLEvent peek() throws XMLStreamException{
        if(fPeekedEvent != null) return fPeekedEvent;

        if(hasNext()){

            fXMLReader.next();
            fPeekedEvent = fXMLEventAllocator.allocate(fXMLReader);
            return fPeekedEvent;
        }else{
            return null;
        }
    }

    private XMLEvent fPeekedEvent;
    private XMLEvent fLastEvent;

}
