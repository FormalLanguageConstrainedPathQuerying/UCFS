/*
 * @test /nodynamiccopyright/
 * @bug 7196163
 * @summary Twr with resource variables as lambda expressions and method references
 * @compile/fail/ref=TwrAndLambda.out -XDrawDiagnostics TwrAndLambda.java
 */

public class TwrAndLambda {

    public static void meth() {

        AutoCloseable v1 = () -> {};
        AutoCloseable v2 = TwrAndLambda::close1;
        AutoCloseable v3 = new TwrAndLambda()::close2;
        Runnable r1 = () -> {};
        Runnable r2 = TwrAndLambda::close1;
        Runnable r3 = new TwrAndLambda()::close2;

        try (v1) {
        } catch(Exception e) {}
        try (v2) {
        } catch(Exception e) {}
        try (v3) {
        } catch(Exception e) {}
        try (r1) {
        } catch(Exception e) {}
        try (r2) {
        } catch(Exception e) {}
        try (r3) {
        } catch(Exception e) {}

        I i = (x) -> { try(x) { } catch (Exception e) { } };
        i.m(v1);
        i.m(v2);
        i.m(v3);
        i.m(r1);
        i.m(r2);
        i.m(r3);
    }

    static interface I {
        public void m(AutoCloseable r);
    }

    public static void close1() { }

    public void close2() { }
}
