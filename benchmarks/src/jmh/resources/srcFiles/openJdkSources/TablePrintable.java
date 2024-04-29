/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

package javax.swing;

import javax.swing.table.*;
import java.awt.*;
import java.awt.print.*;
import java.awt.geom.*;
import java.text.MessageFormat;

/**
 * An implementation of <code>Printable</code> for printing
 * <code>JTable</code>s.
 * <p>
 * This implementation spreads table rows naturally in sequence
 * across multiple pages, fitting as many rows as possible per page.
 * The distribution of columns, on the other hand, is controlled by a
 * printing mode parameter passed to the constructor. When
 * <code>JTable.PrintMode.NORMAL</code> is used, the implementation
 * handles columns in a similar manner to how it handles rows, spreading them
 * across multiple pages (in an order consistent with the table's
 * <code>ComponentOrientation</code>).
 * When <code>JTable.PrintMode.FIT_WIDTH</code> is given, the implementation
 * scales the output smaller if necessary, to ensure that all columns fit on
 * the page. (Note that width and height are scaled equally, ensuring that the
 * aspect ratio remains the same).
 * <p>
 * The portion of table printed on each page is headed by the
 * appropriate section of the table's <code>JTableHeader</code>.
 * <p>
 * Header and footer text can be added to the output by providing
 * <code>MessageFormat</code> instances to the constructor. The
 * printing code requests Strings from the formats by calling
 * their <code>format</code> method with a single parameter:
 * an <code>Object</code> array containing a single element of type
 * <code>Integer</code>, representing the current page number.
 * <p>
 * There are certain circumstances where this <code>Printable</code>
 * cannot fit items appropriately, resulting in clipped output.
 * These are:
 * <ul>
 *   <li>In any mode, when the header or footer text is too wide to
 *       fit completely in the printable area. The implementation
 *       prints as much of the text as possible starting from the beginning,
 *       as determined by the table's <code>ComponentOrientation</code>.
 *   <li>In any mode, when a row is too tall to fit in the
 *       printable area. The upper most portion of the row
 *       is printed and no lower border is shown.
 *   <li>In <code>JTable.PrintMode.NORMAL</code> when a column
 *       is too wide to fit in the printable area. The center of the
 *       column is printed and no left and right borders are shown.
 * </ul>
 * <p>
 * It is entirely valid for a developer to wrap this <code>Printable</code>
 * inside another in order to create complex reports and documents. They may
 * even request that different pages be rendered into different sized
 * printable areas. The implementation was designed to handle this by
 * performing most of its calculations on the fly. However, providing different
 * sizes works best when <code>JTable.PrintMode.FIT_WIDTH</code> is used, or
 * when only the printable width is changed between pages. This is because when
 * it is printing a set of rows in <code>JTable.PrintMode.NORMAL</code> and the
 * implementation determines a need to distribute columns across pages,
 * it assumes that all of those rows will fit on each subsequent page needed
 * to fit the columns.
 * <p>
 * It is the responsibility of the developer to ensure that the table is not
 * modified in any way after this <code>Printable</code> is created (invalid
 * modifications include changes in: size, renderers, or underlying data).
 * The behavior of this <code>Printable</code> is undefined if the table is
 * changed at any time after creation.
 *
 * @author  Shannon Hickey
 */
class TablePrintable implements Printable {

    /** The table to print. */
    private JTable table;

    /** For quick reference to the table's header. */
    private JTableHeader header;

    /** For quick reference to the table's column model. */
    private TableColumnModel colModel;

    /** To save multiple calculations of total column width. */
    private int totalColWidth;

    /** The printing mode of this printable. */
    private JTable.PrintMode printMode;

    /** Provides the header text for the table. */
    private MessageFormat headerFormat;

    /** Provides the footer text for the table. */
    private MessageFormat footerFormat;

    /** The most recent page index asked to print. */
    private int last = -1;

    /** The next row to print. */
    private int row = 0;

    /** The next column to print. */
    private int col = 0;

    /** Used to store an area of the table to be printed. */
    private final Rectangle clip = new Rectangle(0, 0, 0, 0);

    /** Used to store an area of the table's header to be printed. */
    private final Rectangle hclip = new Rectangle(0, 0, 0, 0);

    /** Saves the creation of multiple rectangles. */
    private final Rectangle tempRect = new Rectangle(0, 0, 0, 0);

    /** Vertical space to leave between table and header/footer text. */
    private static final int H_F_SPACE = 8;

    /** Font size for the header text. */
    private static final float HEADER_FONT_SIZE = 18.0f;

    /** Font size for the footer text. */
    private static final float FOOTER_FONT_SIZE = 12.0f;

    /** The font to use in rendering header text. */
    private Font headerFont;

    /** The font to use in rendering footer text. */
    private Font footerFont;

    /**
     * Create a new <code>TablePrintable</code> for the given
     * <code>JTable</code>. Header and footer text can be specified using the
     * two <code>MessageFormat</code> parameters. When called upon to provide
     * a String, each format is given the current page number.
     *
     * @param  table         the table to print
     * @param  printMode     the printing mode for this printable
     * @param  headerFormat  a <code>MessageFormat</code> specifying the text to
     *                       be used in printing a header, or null for none
     * @param  footerFormat  a <code>MessageFormat</code> specifying the text to
     *                       be used in printing a footer, or null for none
     * @throws IllegalArgumentException if passed an invalid print mode
     */
    public TablePrintable(JTable table,
                          JTable.PrintMode printMode,
                          MessageFormat headerFormat,
                          MessageFormat footerFormat) {

        this.table = table;

        header = table.getTableHeader();
        colModel = table.getColumnModel();
        totalColWidth = colModel.getTotalColumnWidth();

        if (header != null) {
            hclip.height = header.getHeight();
        }

        this.printMode = printMode;

        this.headerFormat = headerFormat;
        this.footerFormat = footerFormat;

        headerFont = table.getFont().deriveFont(Font.BOLD,
                                                HEADER_FONT_SIZE);
        footerFont = table.getFont().deriveFont(Font.PLAIN,
                                                FOOTER_FONT_SIZE);
    }

    /**
     * Prints the specified page of the table into the given {@link Graphics}
     * context, in the specified format.
     *
     * @param   graphics    the context into which the page is drawn
     * @param   pageFormat  the size and orientation of the page being drawn
     * @param   pageIndex   the zero based index of the page to be drawn
     * @return  PAGE_EXISTS if the page is rendered successfully, or
     *          NO_SUCH_PAGE if a non-existent page index is specified
     * @throws  PrinterException if an error causes printing to be aborted
     */
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex)
                                                       throws PrinterException {
        final int imgWidth = (int)pageFormat.getImageableWidth();
        final int imgHeight = (int)pageFormat.getImageableHeight();
        if (imgWidth <= 0) {
            throw new PrinterException("Width of printable area is too small.");
        }

        Object[] pageNumber = new Object[]{Integer.valueOf(pageIndex + 1)};

        String headerText = null;
        if (headerFormat != null) {
            headerText = headerFormat.format(pageNumber);
        }

        String footerText = null;
        if (footerFormat != null) {
            footerText = footerFormat.format(pageNumber);
        }

        Rectangle2D hRect = null;
        Rectangle2D fRect = null;

        int headerTextSpace = 0;
        int footerTextSpace = 0;

        int availableSpace = imgHeight;

        if (headerText != null) {
            graphics.setFont(headerFont);
            hRect = graphics.getFontMetrics().getStringBounds(headerText,
                                                              graphics);

            headerTextSpace = (int)Math.ceil(hRect.getHeight());
            availableSpace -= headerTextSpace + H_F_SPACE;
        }

        if (footerText != null) {
            graphics.setFont(footerFont);
            fRect = graphics.getFontMetrics().getStringBounds(footerText,
                                                              graphics);

            footerTextSpace = (int)Math.ceil(fRect.getHeight());
            availableSpace -= footerTextSpace + H_F_SPACE;
        }

        if (availableSpace <= 0) {
            throw new PrinterException("Height of printable area is too small.");
        }

        double sf = 1.0D;
        if (printMode == JTable.PrintMode.FIT_WIDTH &&
                totalColWidth > imgWidth) {

            assert imgWidth > 0;

            assert totalColWidth > 1;

            sf = (double)imgWidth / (double)totalColWidth;
        }

        assert sf > 0;

        Rectangle bounds = table.getBounds();
        bounds.x = bounds.y = 0;

        while (last < pageIndex) {
            if (row >= table.getRowCount() && col == 0) {
                return NO_SUCH_PAGE;
            }

            int scaledWidth = (int)(imgWidth / sf);
            int scaledHeight = (int)((availableSpace - hclip.height) / sf);
            findNextClip(scaledWidth, scaledHeight);

            if (!(bounds.intersects(clip))) {
                return NO_SUCH_PAGE;
            }

            last++;
        }

        Graphics2D g2d = (Graphics2D)graphics.create();

        g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

        AffineTransform oldTrans;

        if (footerText != null) {
            oldTrans = g2d.getTransform();

            g2d.translate(0, imgHeight - footerTextSpace);

            printText(g2d, footerText, fRect, footerFont, imgWidth);

            g2d.setTransform(oldTrans);
        }

        if (headerText != null) {
            printText(g2d, headerText, hRect, headerFont, imgWidth);

            g2d.translate(0, headerTextSpace + H_F_SPACE);
        }

        tempRect.x = 0;
        tempRect.y = 0;
        tempRect.width = imgWidth;
        tempRect.height = availableSpace;
        g2d.clip(tempRect);
        if (sf != 1.0D) {
            g2d.scale(sf, sf);

        } else {
            int diff = (imgWidth - clip.width) / 2;
            g2d.translate(diff, 0);
        }

        oldTrans = g2d.getTransform();
        Shape oldClip = g2d.getClip();

        if (header != null) {
            hclip.x = clip.x;
            hclip.width = clip.width;

            g2d.translate(-hclip.x, 0);
            g2d.clip(hclip);
            header.print(g2d);

            g2d.setTransform(oldTrans);
            g2d.setClip(oldClip);

            g2d.translate(0, hclip.height);
        }

        g2d.translate(-clip.x, -clip.y);
        g2d.clip(clip);

        if (printMode == JTable.PrintMode.FIT_WIDTH) {
            table.putClientProperty("Table.printMode", JTable.PrintMode.FIT_WIDTH);
        }
        table.print(g2d);

        g2d.setTransform(oldTrans);
        g2d.setClip(oldClip);

        g2d.setColor(Color.BLACK);


        Rectangle visibleBounds = clip.intersection(bounds);
        Point upperLeft = visibleBounds.getLocation();
        Point lowerRight = new Point(visibleBounds.x + visibleBounds.width - 1,
                                     visibleBounds.y + visibleBounds.height - 1);

        int rMin = table.rowAtPoint(upperLeft);
        int rMax = table.rowAtPoint(lowerRight);
        if (rMin == -1) {
            rMin = 0;
        }
        if (rMax == -1) {
            rMax = table.getRowCount();
        }
        int rowHeight = 0;
        for(int visrow = rMin; visrow <= rMax; visrow++) {
            rowHeight += table.getRowHeight(visrow);
        }
        if (printMode == JTable.PrintMode.FIT_WIDTH) {
            g2d.drawRect(0, 0, clip.width, hclip.height + rowHeight);
        } else {
            g2d.drawRect(0, 0, visibleBounds.width, hclip.height + rowHeight);
        }

        if (printMode == JTable.PrintMode.FIT_WIDTH) {
            table.putClientProperty("Table.printMode", null);
        }
        g2d.dispose();

        return PAGE_EXISTS;
    }

    /**
     * A helper method that encapsulates common code for rendering the
     * header and footer text.
     *
     * @param  g2d       the graphics to draw into
     * @param  text      the text to draw, non null
     * @param  rect      the bounding rectangle for this text,
     *                   as calculated at the given font, non null
     * @param  font      the font to draw the text in, non null
     * @param  imgWidth  the width of the area to draw into
     */
    private void printText(Graphics2D g2d,
                           String text,
                           Rectangle2D rect,
                           Font font,
                           int imgWidth) {

            int tx;

            if (rect.getWidth() < imgWidth) {
                tx = (int)((imgWidth - rect.getWidth()) / 2);

            } else if (table.getComponentOrientation().isLeftToRight()) {
                tx = 0;

            } else {
                tx = -(int)(Math.ceil(rect.getWidth()) - imgWidth);
            }

            int ty = (int)Math.ceil(Math.abs(rect.getY()));
            g2d.setColor(Color.BLACK);
            g2d.setFont(font);
            g2d.drawString(text, tx, ty);
    }

    /**
     * Calculate the area of the table to be printed for
     * the next page. This should only be called if there
     * are rows and columns left to print.
     *
     * To avoid an infinite loop in printing, this will
     * always put at least one cell on each page.
     *
     * @param  pw  the width of the area to print in
     * @param  ph  the height of the area to print in
     */
    private void findNextClip(int pw, int ph) {
        final boolean ltr = table.getComponentOrientation().isLeftToRight();

        if (col == 0) {
            if (ltr) {
                clip.x = 0;
            } else {
                clip.x = totalColWidth;
            }

            clip.y += clip.height;

            clip.width = 0;
            clip.height = 0;

            int rowCount = table.getRowCount();
            int rowHeight = table.getRowHeight(row);
            do {
                clip.height += rowHeight;

                if (++row >= rowCount) {
                    break;
                }

                rowHeight = table.getRowHeight(row);
            } while (clip.height + rowHeight <= ph);
        }

        if (printMode == JTable.PrintMode.FIT_WIDTH) {
            clip.x = 0;
            clip.width = totalColWidth;
            return;
        }

        if (ltr) {
            clip.x += clip.width;
        }

        clip.width = 0;

        int colCount = table.getColumnCount();
        int colWidth = colModel.getColumn(col).getWidth();
        do {
            clip.width += colWidth;
            if (!ltr) {
                clip.x -= colWidth;
            }

            if (++col >= colCount) {
                col = 0;
                break;
            }

            colWidth = colModel.getColumn(col).getWidth();
        } while (clip.width + colWidth <= pw);

    }
}
