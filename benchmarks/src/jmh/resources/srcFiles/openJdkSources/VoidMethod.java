/*
 * @test /nodynamiccopyright/
 * @bug 6843077 8006775
 * @summary test invalid location of TypeUse and TypeParameter
 * @author Mahmood Ali
 * @compile/fail/ref=VoidMethod.out -XDrawDiagnostics VoidMethod.java
 */

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

class VoidMethod {
  @A void test1() { }
  @B void test2() { }
  @C void test3() { }
  @D void test4() { }
}

@Target(ElementType.TYPE_USE)
@interface A { }

@Target({ElementType.TYPE_USE, ElementType.METHOD})
@interface B { }

@Target(ElementType.TYPE_PARAMETER)
@interface C { }

@Target({ElementType.TYPE_PARAMETER, ElementType.METHOD})
@interface D { }
