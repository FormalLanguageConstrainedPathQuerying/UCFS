/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @test
 * @bug 8255380 8257445
 * @summary Test that Zip FS can access the LOC offset from the Zip64 extra field
 * @modules jdk.zipfs
 * @run junit TestLocOffsetFromZip64EF
 */
public class TestLocOffsetFromZip64EF {

    private static final String ZIP_FILE_NAME = "LocOffsetFromZip64.zip";

    private static short ZIP64_DATA_SIZE = (short) Long.BYTES 
            + Long.BYTES     
            + Long.BYTES;    

    private static short EXTRA_HEADER_SIZE = Short.BYTES 
            + Short.BYTES; 

    private static final int ZIP64_SIZE = EXTRA_HEADER_SIZE + ZIP64_DATA_SIZE;

    private static final int ZIP64_MAGIC_VALUE = 0XFFFFFFFF;
    private static final short UNKNOWN_TAG = (short) 0x9902;
    private static final short ZIP64_TAG = (short) 0x1;

    /**
     * Create the files used by this test
     *
     * @throws IOException if an error occurs
     */
    @BeforeEach
    public void setUp() throws IOException {
        cleanup();
        createZipWithZip64Ext();
    }

    /**
     * Delete files used by this test
     * @throws IOException if an error occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(Path.of(ZIP_FILE_NAME));
    }

    /*
     * MethodSource used to verify that a Zip file that contains a Zip64 Extra
     * (EXT) header can be traversed
     */
    static Stream<Map<String, String>> zipInfoTimeMap() {
        return Stream.of(
                Map.of(),
                Map.of("zipinfo-time", "False"),
                Map.of("zipinfo-time", "true"),
                Map.of("zipinfo-time", "false")
        );
    }

    /**
     * Navigate through the Zip file entries using Zip FS
     * @param env Zip FS properties to use when accessing the Zip file
     * @throws IOException if an error occurs
     */
    @ParameterizedTest
    @MethodSource("zipInfoTimeMap")
    public void walkZipFSTest(final Map<String, String> env) throws IOException {
        Set<String> entries = new HashSet<>();

        try (FileSystem fs =
                     FileSystems.newFileSystem(Paths.get(ZIP_FILE_NAME), env)) {
            for (Path root : fs.getRootDirectories()) {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes
                            attrs) throws IOException {
                        entries.add(file.getFileName().toString());
                        System.out.println(Files.readAttributes(file,
                                BasicFileAttributes.class).toString());
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
        assertEquals(Set.of("entry", "entry2", "entry3"), entries);
    }

    /**
     * Navigate through the Zip file entries using ZipFile
     * @throws IOException if an error occurs
     */
    @Test
    public void walkZipFileTest() throws IOException {
        try (ZipFile zip = new ZipFile(ZIP_FILE_NAME)) {
            zip.stream().forEach(z -> System.out.printf("%s, %s, %s%n",
                    z.getName(), z.getMethod(), z.getLastModifiedTime()));

            assertEquals(zip.stream().map(ZipEntry::getName).collect(Collectors.toSet()),
                    Set.of("entry", "entry2", "entry3"));
        }
    }

    /**
     * This produces a ZIP with similar features as the one created by 'Info-ZIP' which
     * caused 'Extended timestamp' parsing to fail before JDK-8255380.
     *
     * The issue was sensitive to the ordering of 'Info-ZIP extended timestamp' fields and
     * 'Zip64 extended information' fields. ZipOutputStream and 'Info-ZIP' order these differently.
     *
     * ZipFileSystem tried to read the Local file header while parsing the extended timestamp,
     * but if the Zip64 extra field was not read yet, ZipFileSystem would incorrecly try to read
     * the Local File header from offset 0xFFFFFFFF.
     *
     * This method creates a ZIP file which includes a CEN with the following features:
     *
     * - Its extra field has a 'Info-ZIP extended timestamp' field followed by a
     *   'Zip64 extended information' field.
     * - The sizes and offset fields values of the CEN are set to 0xFFFFFFFF (Zip64 magic values)
     *
     */
    public void createZipWithZip64Ext() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(out)) {

            ZipEntry e = new ZipEntry("entry");
            e.setMethod(ZipEntry.STORED);
            e.setSize(0);
            e.setCrc(0);
            zo.putNextEntry(e);

            ZipEntry e2 = new ZipEntry("entry2");
            e2.setMethod(ZipEntry.STORED);
            e2.setSize(0);
            e2.setCrc(0);
            zo.putNextEntry(e2);

            ZipEntry e3 = new ZipEntry("entry3");
            e3.setMethod(ZipEntry.DEFLATED);
            zo.putNextEntry(e3);
            zo.write("Hello".getBytes(StandardCharsets.UTF_8));

            zo.closeEntry(); 

            e.setLastModifiedTime(FileTime.from(Instant.now()));
            e.setLastAccessTime(FileTime.from(Instant.now()));

            byte[] zip64 = makeOpaqueExtraField();
            e.setExtra(zip64);

            zo.finish(); 
        }

        byte[] zip = out.toByteArray();

        updateToZip64(zip);
        Files.write(Path.of(ZIP_FILE_NAME), zip);
    }

    /**
     * Returns an opaque extra field with the tag 'unknown', which makes ZipEntry.setExtra ignore it.
     * The returned field has the expected field and data size of a Zip64 extended information field
     * including the fields 'uncompressed size' (8 bytes), 'compressed size' (8 bytes) and
     * 'local header offset' (8 bytes).
     */
    private static byte[] makeOpaqueExtraField() {
        byte[] zip64 = new byte[ZIP64_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(zip64).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(UNKNOWN_TAG);
        buffer.putShort(ZIP64_DATA_SIZE);
        return zip64;
    }

    /**
     * Update the CEN record to Zip64 format
     */
    private static void updateToZip64(byte[] bytes) throws IOException {

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        int cenOff = getCenOffet(buffer);

        short nlen = buffer.getShort(cenOff + ZipFile.CENNAM);
        short elen = buffer.getShort(cenOff + ZipFile.CENEXT);

        buffer.putInt(cenOff + ZipFile.CENLEN, ZIP64_MAGIC_VALUE);
        buffer.putInt(cenOff + ZipFile.CENSIZ, ZIP64_MAGIC_VALUE);
        buffer.putInt(cenOff + ZipFile.CENOFF, ZIP64_MAGIC_VALUE);

        int extraOff = cenOff + ZipFile.CENHDR + nlen;

        int zip64ExtraOff = extraOff + elen - ZIP64_SIZE;

        buffer.putShort(zip64ExtraOff, ZIP64_TAG);
    }

    /**
     * Look up the CEN offset field from the End of central directory header
     */
    private static int getCenOffet(ByteBuffer buffer) {
        return buffer.getInt(buffer.capacity() - ZipFile.ENDHDR + ZipFile.ENDOFF);
    }
}
