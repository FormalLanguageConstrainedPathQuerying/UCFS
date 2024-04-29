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


import java.io.Writer;
import java.io.StringWriter;
import java.io.IOException;


/**
 * Extends {@link Printer} and adds support for indentation and line
 * wrapping.
 *
 * @author <a href="mailto:arkin@intalio.com">Assaf Arkin</a>
 *
 * @deprecated As of JDK 9, Xerces 2.9.0, Xerces DOM L3 Serializer implementation
 * is replaced by that of Xalan. Main class
 * {@link com.sun.org.apache.xml.internal.serialize.DOMSerializerImpl} is replaced
 * by {@link com.sun.org.apache.xml.internal.serializer.dom3.LSSerializerImpl}.
 */
@Deprecated
public class IndentPrinter
    extends Printer
{


    /**
     * Holds the currently accumulating text line. This buffer will constantly
     * be reused by deleting its contents instead of reallocating it.
     */
    private StringBuffer    _line;


    /**
     * Holds the currently accumulating text that follows {@link #_line}.
     * When the end of the part is identified by a call to {@link #printSpace}
     * or {@link #breakLine}, this part is added to the accumulated line.
     */
    private StringBuffer    _text;


    /**
     * Counts how many white spaces come between the accumulated line and the
     * current accumulated text. Multiple spaces at the end of the a line
     * will not be printed.
     */
    private int             _spaces;


    /**
     * Holds the indentation for the current line that is now accumulating in
     * memory and will be sent for printing shortly.
     */
    private int             _thisIndent;


    /**
     * Holds the indentation for the next line to be printed. After this line is
     * printed, {@link #_nextIndent} is assigned to {@link #_thisIndent}.
     */
    private int             _nextIndent;


    public IndentPrinter( Writer writer, OutputFormat format)
    {
        super( writer, format );
        _line = new StringBuffer( 80 );
        _text = new StringBuffer( 20 );
        _spaces = 0;
        _thisIndent = _nextIndent = 0;
    }


    /**
     * Called by any of the DTD handlers to enter DTD mode.
     * Once entered, all output will be accumulated in a string
     * that can be printed as part of the document's DTD.
     * This method may be called any number of time but will only
     * have affect the first time it's called. To exist DTD state
     * and get the accumulated DTD, call {@link #leaveDTD}.
     */
    public void enterDTD()
    {
        if ( _dtdWriter == null ) {
            _line.append( _text );
            _text = new StringBuffer( 20 );
            flushLine( false );
            _dtdWriter = new StringWriter();
            _docWriter = _writer;
            _writer = _dtdWriter;
        }
    }


    /**
     * Called by the root element to leave DTD mode and if any
     * DTD parts were printer, will return a string with their
     * textual content.
     */
    public String leaveDTD()
    {
        if ( _writer == _dtdWriter ) {
            _line.append( _text );
            _text = new StringBuffer( 20 );
            flushLine( false );
            _writer = _docWriter;
            return _dtdWriter.toString();
        } else
            return null;
    }


    /**
     * Called to print additional text. Each time this method is called
     * it accumulates more text. When a space is printed ({@link
     * #printSpace}) all the accumulated text becomes one part and is
     * added to the accumulate line. When a line is long enough, it can
     * be broken at its text boundary.
     *
     * @param text The text to print
     */
    public void printText( String text )
    {
        _text.append( text );
    }


    public void printText( StringBuffer text )
    {
        _text.append( text.toString() );
    }


    public void printText( char ch )
    {
        _text.append( ch );
    }


    public void printText( char[] chars, int start, int length )
    {
        _text.append( chars, start, length );
    }


    /**
     * Called to print a single space between text parts that may be
     * broken into separate lines. Must not be called to print a space
     * when preserving spaces. The text accumulated so far with {@link
     * #printText} will be added to the accumulated line, and a space
     * separator will be counted. If the line accumulated so far is
     * long enough, it will be printed.
     */
    public void printSpace()
    {

        if ( _text.length() > 0 ) {
            if ( _format.getLineWidth() > 0 &&
                 _thisIndent + _line.length() + _spaces + _text.length() > _format.getLineWidth() ) {
                flushLine( false );
                try {
                    _writer.write( _format.getLineSeparator() );
                } catch ( IOException except ) {
                    if ( _exception == null )
                        _exception = except;
                }
            }

            while ( _spaces > 0 ) {
                _line.append( ' ' );
                --_spaces;
            }
            _line.append( _text );
            _text = new StringBuffer( 20 );
        }
        ++_spaces;
    }


    /**
     * Called to print a line consisting of the text accumulated so
     * far. This is equivalent to calling {@link #printSpace} but
     * forcing the line to print and starting a new line ({@link
     * #printSpace} will only start a new line if the current line
     * is long enough).
     */
    public void breakLine()
    {
        breakLine( false );
    }


    public void breakLine( boolean preserveSpace )
    {
        if ( _text.length() > 0 ) {
            while ( _spaces > 0 ) {
                _line.append( ' ' );
                --_spaces;
            }
            _line.append( _text );
            _text = new StringBuffer( 20 );
        }
        flushLine( preserveSpace );
        try {
            _writer.write( _format.getLineSeparator() );
        } catch ( IOException except ) {
            if ( _exception == null )
                _exception = except;
        }
    }


    /**
     * Flushes the line accumulated so far to the writer and get ready
     * to accumulate the next line. This method is called by {@link
     * #printText} and {@link #printSpace} when the accumulated line plus
     * accumulated text are two long to fit on a given line. At the end of
     * this method _line is empty and _spaces is zero.
     */
    public void flushLine( boolean preserveSpace )
    {
        int     indent;

        if ( _line.length() > 0 ) {
            try {

                if ( _format.getIndenting() && ! preserveSpace ) {
                    indent = _thisIndent;
                    if ( ( 2 * indent ) > _format.getLineWidth() && _format.getLineWidth() > 0 )
                        indent = _format.getLineWidth() / 2;
                    while ( indent > 0 ) {
                        _writer.write( ' ' );
                        --indent;
                    }
                }
                _thisIndent = _nextIndent;

                _spaces = 0;
                _writer.write( _line.toString() );

                _line = new StringBuffer( 40 );
            } catch ( IOException except ) {
                if ( _exception == null )
                    _exception = except;
            }
        }
    }


    /**
     * Flush the output stream. Must be called when done printing
     * the document, otherwise some text might be buffered.
     */
    public void flush()
    {
        if ( _line.length() > 0 || _text.length() > 0 )
            breakLine();
        try {
            _writer.flush();
        } catch ( IOException except ) {
            if ( _exception == null )
                _exception = except;
        }
    }


    /**
     * Increment the indentation for the next line.
     */
    public void indent()
    {
        _nextIndent += _format.getIndent();
    }


    /**
     * Decrement the indentation for the next line.
     */
    public void unindent()
    {
        _nextIndent -= _format.getIndent();
        if ( _nextIndent < 0 )
            _nextIndent = 0;
        if ( ( _line.length() + _spaces + _text.length() ) == 0 )
            _thisIndent = _nextIndent;
    }


    public int getNextIndent()
    {
        return _nextIndent;
    }


    public void setNextIndent( int indent )
    {
        _nextIndent = indent;
    }


    public void setThisIndent( int indent )
    {
        _thisIndent = indent;
    }


}
