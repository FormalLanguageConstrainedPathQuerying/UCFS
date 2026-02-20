>[!CAUTION]
>For demo purposes only!
>Do not expect big graphs to be processed successfully (in reasonable time or without out-of-memory errors).

Requirements: 11 java

To run (from project root):

```bash
./gradlew :cfpq-app:run
```

Input graphs: ```src/main/resources/```

Grammar and code for paths extraction: ```src/main/kotlin/me/vkutuev/Main.kt```

SPPF traversal

SPPF is a derivation-tree-like structure that represents **all** possible paths satisfying the specified grammar. If the number of such paths is infinite, the SPPF contains cycles.

>[!NOTE] 
> We implemented a very naive path extraction algorithm solely to demonstrate SPPF traversal.

## Examples




### Example 1
Code snippet: 
```java
val n = new X()
val y = new Y()
val z = new Z()
val l = n
val t = y
l.u = y
t.v = z
```

Respective graph:

![Graph for example 1](./src/main/resources/figures/graph_1.dot.svg)

![SPPF for example 1](./src/main/resources/figures/graph_1_sppf.dot.svg)

Paths:
[(1-PointsTo->0)]

[(1-Alias->2), (2-store_0->3), (3-Alias->5), (5-store_1->6), (6-PointsTo->7)]

[(1-Alias->2), (2-store_0->3), (3-PointsTo->4)]


Trivial. Will be omitted in further examples


### Example 2

Code snippet: 
```java
val n = new X()
val l = n
while (...){    
    l.next = new X()
    l = l.next
}
```

![Graph for example 2](./src/main/resources/figures/graph_2.dot.svg)
![SPPF for example 2](./src/main/resources/figures/graph_2_sppf.dot.svg)

Paths:

[(0-Alias->2), (2-store_0->3), (3-PointsTo->4)]

[(0-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-PointsTo->4)]

[(0-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-PointsTo->4)]

[(0-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-PointsTo->4)]

[(0-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-PointsTo->4)]

[(0-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-Alias->2), (2-store_0->3), (3-PointsTo->4)]



### Example 3

Code snippet:
```java
val n = new X()
val l = n
while (...){
    val t = new X()
    l.next = t
    l = t
}
```

![Graph for example 3](./src/main/resources/figures/graph_3.dot.svg)
![SPPF for example 3](./src/main/resources/figures/graph_3_sppf.dot.svg)

Paths:

[(0-Alias->1), (1-store_0->2), (2-PointsTo->3)]

[(0-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-PointsTo->3)]

[(0-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-PointsTo->3)]

[(0-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-PointsTo->3)]

[(0-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-PointsTo->3)]

[(0-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-Alias->1), (1-store_0->2), (2-PointsTo->3)]


### Example 4

Code snippet:

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

![Graph for example 4](./src/main/resources/figures/graph_4.dot.svg)

Paths:

[(1-Alias->9), (9-store_3->11), (11-PointsTo->13)]

[(1-Alias->8), (8-store_2->10), (10-PointsTo->12)]

[(8-Alias->9), (9-store_3->11), (11-PointsTo->13)]

[(8-store_2->10), (10-PointsTo->12)]

[(8-Alias->8), (8-store_2->10), (10-PointsTo->12)]
