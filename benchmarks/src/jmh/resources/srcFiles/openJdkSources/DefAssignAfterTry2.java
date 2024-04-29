/*
 * @test /nodynamiccopyright/
 * @bug 4240487
 * @summary Verify that we keep track of init/uninits in Try statement
 * without finalizer.
 *
 * @compile/fail/ref=DefAssignAfterTry2.out -XDrawDiagnostics  DefAssignAfterTry2.java
 */

class E1 extends Exception {}
class E2 extends Exception {}

public class DefAssignAfterTry2 {
    public static void meth() {
        boolean t = true;
        E1 se1 = new E1();
        E2 se2 = new E2();
        int i;
        try {
            if (t) {
                i = 0;
                throw se1;
            } else {
                throw se2;
            }
        } catch (E1 e) {
        } catch (E2 e) {
            i = 0;
        }
        System.out.println(i);
        System.out.println("Error : there should be compile-time errors");
    }
}
