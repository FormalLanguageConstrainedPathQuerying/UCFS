# UCFS

> Note: project under heavy development!

Please, see [documentation](https://formallanguageconstrainedpathquerying.github.io/UCFS/) for details.

## What is UCFS?

UCFS is an **U**niversal **C**ontext-**F**ree **S**olver: a GLL‑based tool for problems at the intersection of context‑free languages 
over edge‑labeled directed graphs. Examples of such problems:

- Parsing
- Context-free path querying (CFPQ)
- Context-free language reachability (CFL-R)
- static code analysis 

**Highlights**

* Kotlin implementation with a concise Grammar DSL (EBNF‑friendly).
* Input: arbitrary edge‑labeled directed graphs.
* Output: SPPF -- finite structure for all‑paths queries.



### Repository layout (high‑level)
```
docs/         # documentation pages  
generator/    # Parser & AST node‑class generator <in progress>
solver/       # Core UCFS logic (GLL + RSM)
test-shared/  # Testcases, grammars, inputs, ANTLR4 comparison
              # grammar examples and experiments
```

### Requirements
- JDK 11+ (toolchain targets 11).  
- Gradle Wrapper included (`./gradlew`).

### Typical workflow
1) **Describe grammar** in Kotlin DSL.  
2) **Load graph** (for now `dot` format is supported).  
3) **Run query**.  
4) **Inspect results**.


## Core Algorithm
UCFS is based on Generalized LL (GLL) parsing algorithm modified to handle language specification in form of Recursive State Machines (RSM) and input in form of arbitratry directed edge-labelled graph. Basic ideas described [here](https://arxiv.org/pdf/2312.11925.pdf). 
