/*
 * @test (important: no SCCS keywords to affect offsets in golden file.)  /nodynamiccopyright/
 * @bug 6304921
 * @compile/fail/ref=T6304921.out -XDcompilePolicy=bytodo -XDrawDiagnostics -Xjcov -Xlint:all,-path -Werror T6304921.java
 */

import java.util.ArrayList;
import java.util.List;

class T6304921 {
    void m1(int i) {
        switch (i) {
        case 1:
            i++;
        default:
        }

        try {
            i++;
        }
        finally {
            throw new Error();
        }
    }

    void m2() {
        List<Integer> list = new ArrayList();
    }
}

class X {
    void m1() {
        System.orr.println("abc"); 
    }
    boolean m2() {
        return 123 + true; 
    }
}
