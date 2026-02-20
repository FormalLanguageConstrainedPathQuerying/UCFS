>[!CAUTION]
>For demo purposes only!
>Do not expect big graphs to be processed successfully (in reasonable time or without out-of-memory errors).

Requirements: 11 java

To run (from project root):

```bash
./gradlew :cfpq-app:run
```

Input graphs: src/main/resources/
We assume that points-to analysis is performed as a first step, so points-to edges (```pt``` and ```pt_r```) are explicitly represented in the input graphs.

Grammar and SPPF traversal: src/main/kotlin/me/vkutuev/Main.kt
SPPF is a derivation-tree-like structure that represents **all** possible paths satisfying the specified grammar. If the number of such paths is infinite, the SPPF contains cycles.

>[!NOTE] 
> We implemented a very naive path extraction algorithm solely to demonstrate SPPF traversal.

## Examples

Code snippet for related graphs. 

Graph 1:
```java
val n = new X()
val y = new Y()
val z = new Z()
val l = n
val t = y
l.u = y
t.v = z
```


Graph 2:
```java
val n = new X()
val l = n
while (...){    
    l.next = new X()
    l = l.next
}
```

Graph 3:
```java
val n = new X()
val l = n
while (...){
    val t = new X()
    l.next = t
    l = t
}
```

Graph 4:
```java
val n = new X()
val z = new Z()
val u = new U()
z.x = n
u.y = n
val v = z.x
v.p = new Y()
val r = u.y
r.q = new P()
```