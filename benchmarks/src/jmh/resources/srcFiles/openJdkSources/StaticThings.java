/*
 * @test /nodynamiccopyright/
 * @bug 8006775
 * @summary the receiver parameter and static methods/classes
 * @author Werner Dietl
 * @compile/fail/ref=StaticThings.out -XDrawDiagnostics StaticThings.java
 */
class Test {
  static void test1(Test this) {}

  static Object test2(Test this) { return null; }

  class Nested1 {
    void test3a(Nested1 this) {}
    void test3b(Test.Nested1 this) {}
  }
  static class Nested2 {
    void test4a(Nested2 this) {}
    void test4b(Test.Nested2 this) {}
    static void test4c(Nested2 this) {}
    static void test4d(Test.Nested2 this) {}
  }
}
