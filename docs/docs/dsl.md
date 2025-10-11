# Domain Specific Language
The Universal Context-Free Solver provides an intuitive Kotlin-based inner DSL for defining and working with context-free grammars.
It enables developers to describe grammars using natural Kotlin syntax while benefiting from:

* development tools and syntax analysis from scratch;
* simple (for now) compile-time checks for rules.

## Grammar 


Each grammar is defined as a Kotlin class extending the base `Grammar` type and contains information about **non-terminals**, **terminals**, and **production rules**.


For example, the following grammar defines a simple language that recognizes a single word "a":

```kotlin 
class AGrammar : Grammar() {    
        val S by Nt("a").asStart()    
}    
```

The start non-terminal can be set using the `setStart(nt)` method or directly during initialization with `Nt(...).asStart()`.
Currently, only one start non-terminal is supported.


## Non-terminals
* Designed as field of `Grammar` for easy monitoring and reuse.
* **Declared** using Kotlin property delegates, e.g., `val S by Nt()`.
* **Initialization**:
* * in corresponds delegate constructor `val AB by Nt(A or B)`;
* * inside the `init` block using the `/=` operator, e.g., `AB /= A or B`.
* * Each non-terminal must be initialized exactly once.



## Terminals
* `Term` is a generic class that can store terminals of any type. For example:
```kotlin 
val A = Term("a")
val B = Term(42)
```
* Terminals are compared based on their content value.
* Initialization:
  * * as fields of grammar class, can be reused `val a = Temr("A")`;
  * * directly in production `BA \= Term("B") * Term("A")`.
* Strings can be handled without wrapping. For instance, `digit \= "1" or "0"`.


## Operations

| Operation     | EBNF                       | DSL                                                                        | 
|---------------|----------------------------|----------------------------------------------------------------------------|
| Production    | A ::= B                    | val A by Nt(B) &nbsp;//in grammar class body <br> A \\= B &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; //in init block | 
| Concatenation | A B                        | A * B                                                                      | 
| Alternatve    | A \| B                     | A or B                                                                     |
| Kleene Star   | $A^*$                      | many(A)                                                                    |
| Kleene Star+  | $A^+$                      | some(A)                                                                    |
| Optional      | A \| $\varepsilon$ <br> A? | opt(A)                                                                     |


Epsilon ($\varepsilon$) - constant terminal with behavior corresponding to the empty string.


## Examples

### Dyck language
These grammars define the Dyck language — all correctly nested parentheses, brackets, and braces.

#### *EBNF*

``` 
S = S1 | S2 | S3 | ε    
S1 = '(' S ')' S     
S2 = '[' S ']' S     
S3 = '{' S '}' S     
``` 

#### *DSL*
```kotlin
class DyckGrammar : Grammar() {
    val S       by Nt().asStart()
    val Round   by Nt("(" * S * ")")
    val Quadrat by Nt("[" * S * "]")
    val Curly   by Nt("{" * S * "}")

    init {
        //recursive initialization can be only in `init` block
        S /= S * (Round or Quadrat or Curly) or Epsilon
    }
}
```

### A* language

#### *EBNF*
``` 
A = "a"    
S = A*     
``` 
#### *DSL*  
Grammar for this language can be described in various ways. 
 
```kotlin 
class AStar : Grammar() {    
        var A = Term("a")    
        val S by Nt().asStart(many(A))    
    }    
```
or 
```kotlin 
class AStar : Grammar() {    
        val S by Nt().asStart()    
        init {
            S /= many("a")
        }
    }    
```
