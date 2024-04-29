/*
 * Copyright (C) 2009 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.io;

import static org.junit.Assert.assertThrows;

import com.google.common.testing.NullPointerTester;
import com.google.common.testing.NullPointerTester.Visibility;
import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.PatternSyntaxException;
import junit.framework.TestCase;

/**
 * Unit test for {@link PatternFilenameFilter}.
 *
 * @author Chris Nokleberg
 */
public class PatternFilenameFilterTest extends TestCase {

  public void testSyntaxException() {
    assertThrows(PatternSyntaxException.class, () -> new PatternFilenameFilter("("));
  }

  public void testAccept() {
    File dir = new File("foo");
    FilenameFilter filter = new PatternFilenameFilter("a+");
    assertTrue(filter.accept(dir, "a"));
    assertTrue(filter.accept(dir, "aaaa"));
    assertFalse(filter.accept(dir, "b"));

    assertTrue(filter.accept(null, "a"));
  }

  public void testNulls() throws Exception {
    NullPointerTester tester = new NullPointerTester();

    tester.testConstructors(PatternFilenameFilter.class, Visibility.PACKAGE);
    tester.testStaticMethods(PatternFilenameFilter.class, Visibility.PACKAGE); 

    tester.ignore(PatternFilenameFilter.class.getMethod("accept", File.class, String.class));
    tester.testInstanceMethods(new PatternFilenameFilter(".*"), Visibility.PACKAGE);
  }
}
