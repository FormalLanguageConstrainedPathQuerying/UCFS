/*
 * Copyright (C) 2009 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 * http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.io;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.io.Files.simplifyPath;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import junit.framework.TestCase;

/**
 * Unit tests for {@link Files#simplifyPath}.
 *
 * @author Pablo Bellver
 */
public class FilesSimplifyPathTest extends TestCase {

  public void testSimplifyEmptyString() {
    assertEquals(".", simplifyPath(""));
  }

  public void testSimplifyDot() {
    assertEquals(".", simplifyPath("."));
  }

  public void testSimplifyWhiteSpace() {
    assertEquals(" ", simplifyPath(" "));
  }

  public void testSimplify2() {
    assertEquals("x", simplifyPath("x"));
  }

  public void testSimplify3() {
    assertEquals("/a/b/c/d", simplifyPath("/a/b/c/d"));
  }

  public void testSimplify4() {
    assertEquals("/a/b/c/d", simplifyPath("/a/b/c/d/"));
  }

  public void testSimplify5() {
    assertEquals("/a/b", simplifyPath("/a
  }

  public void testSimplify6() {
    assertEquals("/a/b", simplifyPath("
  }

  public void testSimplify7() {
    assertEquals("/", simplifyPath("/.."));
  }

  public void testSimplify8() {
    assertEquals("/", simplifyPath("/././././"));
  }

  public void testSimplify9() {
    assertEquals("/a", simplifyPath("/a/b/.."));
  }

  public void testSimplify10() {
    assertEquals("/", simplifyPath("/a/b/../../.."));
  }

  public void testSimplify11() {
    assertEquals("/", simplifyPath("
  }

  public void testSimplify12() {
    assertEquals("/x", simplifyPath("
  }

  public void testSimplify13() {
    assertEquals("../c", simplifyPath("a/b/../../../c"));
  }

  public void testSimplifyDotDot() {
    assertEquals("..", simplifyPath(".."));
  }

  public void testSimplifyDotDotSlash() {
    assertEquals("..", simplifyPath("../"));
    assertEquals("..", simplifyPath("a/../.."));
    assertEquals("..", simplifyPath("a/../../"));
  }

  public void testSimplifyDotDots() {
    assertEquals("../..", simplifyPath("a/../../.."));
    assertEquals("../../..", simplifyPath("a/../../../.."));
  }

  public void testSimplifyRootedDotDots() {
    assertEquals("/", simplifyPath("/../../.."));
    assertEquals("/", simplifyPath("/../../../"));
  }

  public void testMadbotsBug() {
    assertEquals("../this", simplifyPath("../this"));
    assertEquals("../this/is/ok", simplifyPath("../this/is/ok"));
    assertEquals("../ok", simplifyPath("../this/../ok"));
  }

  public void test705() {
    assertEquals("../b", simplifyPath("x/../../b"));
    assertEquals("b", simplifyPath("x/../b"));
  }

  public void test716() {
    assertEquals("b", simplifyPath("./b"));
    assertEquals("b", simplifyPath("./b/."));
    assertEquals("b", simplifyPath("././b/./."));
    assertEquals("b", simplifyPath("././b"));
    assertEquals("a/b", simplifyPath("./a/b"));
  }

  public void testHiddenFiles() {
    assertEquals(".b", simplifyPath(".b"));
    assertEquals(".b", simplifyPath("./.b"));
    assertEquals(".metadata/b", simplifyPath(".metadata/b"));
    assertEquals(".metadata/b", simplifyPath("./.metadata/b"));
  }

  public void testMultipleDotFilenames() {
    assertEquals("..a", simplifyPath("..a"));
    assertEquals("/..a", simplifyPath("/..a"));
    assertEquals("/..a/..b", simplifyPath("/..a/..b"));
    assertEquals("/.....a/..b", simplifyPath("/.....a/..b"));
    assertEquals("..../....", simplifyPath("..../...."));
    assertEquals("..a../..b..", simplifyPath("..a../..b.."));
  }

  public void testSlashDot() {
    assertEquals("/", simplifyPath("/."));
  }

  public void testInitialSlashDotDot() {
    assertEquals("/c", simplifyPath("/../c"));
  }

  public void testInitialSlashDot() {
    assertEquals("/a", simplifyPath("/./a"));
    assertEquals("/.a", simplifyPath("/.a/a/.."));
  }

  public void testConsecutiveParentsAfterPresent() {
    assertEquals("../..", simplifyPath("./../../"));
    assertEquals("../..", simplifyPath("./.././../"));
  }

  /*
   * We co-opt some URI resolution tests for our purposes.
   * Some of the tests have queries and anchors that are a little silly here.
   */

  /** http:
  public void testRfc2396Normal() {
    assertEquals("/a/b/c/g", simplifyPath("/a/b/c/g"));
    assertEquals("/a/b/c/g", simplifyPath("/a/b/c/./g"));
    assertEquals("/a/b/c/g", simplifyPath("/a/b/c/g/"));

    assertEquals("/a/b/c/g?y", simplifyPath("/a/b/c/g?y"));
    assertEquals("/a/b/c/g#s", simplifyPath("/a/b/c/g#s"));
    assertEquals("/a/b/c/g?y#s", simplifyPath("/a/b/c/g?y#s"));
    assertEquals("/a/b/c/;x", simplifyPath("/a/b/c/;x"));
    assertEquals("/a/b/c/g;x", simplifyPath("/a/b/c/g;x"));
    assertEquals("/a/b/c/g;x?y#s", simplifyPath("/a/b/c/g;x?y#s"));
    assertEquals("/a/b/c", simplifyPath("/a/b/c/."));
    assertEquals("/a/b/c", simplifyPath("/a/b/c/./"));
    assertEquals("/a/b", simplifyPath("/a/b/c/.."));
    assertEquals("/a/b", simplifyPath("/a/b/c/../"));
    assertEquals("/a/b/g", simplifyPath("/a/b/c/../g"));
    assertEquals("/a", simplifyPath("/a/b/c/../.."));
    assertEquals("/a", simplifyPath("/a/b/c/../../"));
    assertEquals("/a/g", simplifyPath("/a/b/c/../../g"));
  }

  /** http:
  public void testRfc2396Abnormal() {
    assertEquals("/a/b/c/g.", simplifyPath("/a/b/c/g."));
    assertEquals("/a/b/c/.g", simplifyPath("/a/b/c/.g"));
    assertEquals("/a/b/c/g..", simplifyPath("/a/b/c/g.."));
    assertEquals("/a/b/c/..g", simplifyPath("/a/b/c/..g"));
    assertEquals("/a/b/g", simplifyPath("/a/b/c/./../g"));
    assertEquals("/a/b/c/g", simplifyPath("/a/b/c/./g/."));
    assertEquals("/a/b/c/g/h", simplifyPath("/a/b/c/g/./h"));
    assertEquals("/a/b/c/h", simplifyPath("/a/b/c/g/../h"));
    assertEquals("/a/b/c/g;x=1/y", simplifyPath("/a/b/c/g;x=1/./y"));
    assertEquals("/a/b/c/y", simplifyPath("/a/b/c/g;x=1/../y"));
  }

  /** http:
  public void testRfc3986Normal() {
    assertEquals("/a/b/c/g", simplifyPath("/a/b/c/g"));
    assertEquals("/a/b/c/g", simplifyPath("/a/b/c/./g"));
    assertEquals("/a/b/c/g", simplifyPath("/a/b/c/g/"));

    assertEquals("/a/b/c/g?y", simplifyPath("/a/b/c/g?y"));
    assertEquals("/a/b/c/g#s", simplifyPath("/a/b/c/g#s"));
    assertEquals("/a/b/c/g?y#s", simplifyPath("/a/b/c/g?y#s"));
    assertEquals("/a/b/c/;x", simplifyPath("/a/b/c/;x"));
    assertEquals("/a/b/c/g;x", simplifyPath("/a/b/c/g;x"));
    assertEquals("/a/b/c/g;x?y#s", simplifyPath("/a/b/c/g;x?y#s"));

    assertEquals("/a/b/c", simplifyPath("/a/b/c/."));
    assertEquals("/a/b/c", simplifyPath("/a/b/c/./"));
    assertEquals("/a/b", simplifyPath("/a/b/c/.."));
    assertEquals("/a/b", simplifyPath("/a/b/c/../"));
    assertEquals("/a/b/g", simplifyPath("/a/b/c/../g"));
    assertEquals("/a", simplifyPath("/a/b/c/../.."));
    assertEquals("/a", simplifyPath("/a/b/c/../../"));
    assertEquals("/a/g", simplifyPath("/a/b/c/../../g"));
  }

  /** http:
  public void testRfc3986Abnormal() {
    assertEquals("/g", simplifyPath("/a/b/c/../../../g"));
    assertEquals("/g", simplifyPath("/a/b/c/../../../../g"));

    assertEquals("/a/b/c/g.", simplifyPath("/a/b/c/g."));
    assertEquals("/a/b/c/.g", simplifyPath("/a/b/c/.g"));
    assertEquals("/a/b/c/g..", simplifyPath("/a/b/c/g.."));
    assertEquals("/a/b/c/..g", simplifyPath("/a/b/c/..g"));
    assertEquals("/a/b/g", simplifyPath("/a/b/c/./../g"));
    assertEquals("/a/b/c/g", simplifyPath("/a/b/c/./g/."));
    assertEquals("/a/b/c/g/h", simplifyPath("/a/b/c/g/./h"));
    assertEquals("/a/b/c/h", simplifyPath("/a/b/c/g/../h"));
    assertEquals("/a/b/c/g;x=1/y", simplifyPath("/a/b/c/g;x=1/./y"));
    assertEquals("/a/b/c/y", simplifyPath("/a/b/c/g;x=1/../y"));
  }

  public void testExtensiveWithAbsolutePrefix() throws IOException {
    doExtensiveTest("testdata/simplifypathwithabsoluteprefixtests.txt");
  }

  public void testExtensiveNoPrefix() throws IOException {
    /*
     * Inputs are <every possible 10-character string of characters "a./">
     *
     * Expected outputs are generated by the code itself, but they've been
     * checked against the inputs under Bash in order to confirm that the two
     * forms are equivalent (though not necessarily minimal, though we hope this
     * to be the case). Thus, this test is more of a regression test.
     *
     * Rough instructions to regenerate the test outputs and verify correctness:
     * - Temporarily change this test:
     * --- Comment out assertEquals.
     * --- System.out.println(input + " " + simplifyPath(input));
     * --- fail(). (If the test were to pass, its output would be hidden.)
     * - Run the test.
     * - Pull the relevant lines of output from the test into a testcases file.
     * - Test the output:
     * --- cat testcases | while read L; do
     *       X=($L)
     *       A=$( cd /b/c && sudo mkdir -p ${X[0]} && cd ${X[0]} && pwd |
     *           sed -e 's#^
     *       B=$( cd /b/c && cd ${X[1]} && pwd )
     *       cmp -s <(echo $A) <(echo $B) || echo "$X[0] -> $A vs. $B"
     *     done | tee testoutput
     * - Move that testcases file to the appropriate name under testdata.
     *
     * The last test will take hours, and if it passes, the output will be empty.
     */
    doExtensiveTest("testdata/simplifypathnoprefixtests.txt");
  }

  private void doExtensiveTest(String resourceName) throws IOException {
    Splitter splitter = Splitter.on(CharMatcher.whitespace());
    URL url = getClass().getResource(resourceName);
    for (String line : Resources.readLines(url, UTF_8)) {
      Iterator<String> iterator = splitter.split(line).iterator();
      String input = iterator.next();
      String expectedOutput = iterator.next();
      assertFalse(iterator.hasNext());
      assertEquals(expectedOutput, simplifyPath(input));
    }
  }
}
