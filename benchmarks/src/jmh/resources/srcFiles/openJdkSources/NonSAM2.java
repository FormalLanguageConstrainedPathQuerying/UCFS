/*
 * @test /nodynamiccopyright/
 * @bug 8003280 7170058
 * @summary Add lambda tests
 *   This test is for identifying a non-SAM type: Having more than one methods due to inheritance, and none of them has a subsignature of all other methods
 * @compile/fail/ref=NonSAM2.out -XDrawDiagnostics NonSAM2.java Helper.java
 */

import java.util.List;

interface Foo1 { int getAge(String s);}
interface Bar1 { Integer getAge(String s);}
interface Foo1Bar1 extends Foo1, Bar1 {} 

interface AC extends A, C {} 
interface ABC extends A, B, C {} 
interface AD extends A, D {} 

interface Foo2<T> { void m(T arg);}
interface Bar2<S> { void m(S arg);}
interface Foo2Bar2<T1, T2> extends Foo2<T1>, Bar2<T2> {} 
