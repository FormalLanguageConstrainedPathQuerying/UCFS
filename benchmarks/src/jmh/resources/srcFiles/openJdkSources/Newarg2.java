/*
 * @test /nodynamiccopyright/
 * @bug 4851039
 * @summary explicit type arguments
 * @author gafter
 *
 * @compile/fail/ref=Newarg2.out -XDrawDiagnostics Newarg2.java
 */


class T {

    class U<Y> extends T {
        <B> U(B b) {}
    }

    public static void meth() {
        new T().new <Integer>U<Integer>("");
    }
}
