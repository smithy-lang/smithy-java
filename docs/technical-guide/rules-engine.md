# Rules Engine

> **Last updated:** April 29, 2026

The rules engine is Smithy-Java's implementation of the
[Smithy endpoint rules engine specification](https://smithy.io/2.0/additional-specs/rules-engine/index.html). It allows
model authors to define rules that determine which endpoint is used for each operation. Smithy-Java developed an
innovative approach: instead of interpreting the rules tree directly, it transforms the rules into a Binary Decision
Diagram (BDD) and compiles them to bytecode executed by a stack-based virtual machine. This eliminates redundant
condition evaluation, enables aggressive optimization, and achieves near-zero allocation on the hot path.

**Source:** [`rules-engine/`](https://github.com/smithy-lang/smithy-java/tree/main/rules-engine)

## Architecture Overview

The rules engine has a three-stage pipeline:

```
EndpointRuleSet → CFG → BDD (with sifting + node reversal) → Bytecode → VM Evaluation
```

Two endpoint resolver implementations exist:

- **`BytecodeEndpointResolver`** — The high-performance path. Compiles rules to bytecode and evaluates via a stack-based
  VM traversing a BDD. Used by generated clients.
- **`DecisionTreeEndpointResolver`** — The slow fallback. Interprets the ruleset decision tree directly using Smithy's
  `RuleEvaluator`. Used by the dynamic client where BDD compilation isn't practical.

## RulesEngineBuilder — Entry Point

[`RulesEngineBuilder`](https://github.com/smithy-lang/smithy-java/blob/main/rules-engine/src/main/java/software/amazon/smithy/java/rulesengine/RulesEngineBuilder.java)
is the central factory. It:

1. Auto-discovers `RulesExtension` implementations via `ServiceLoader` (always includes `StdExtension`)
2. Collects functions, builtin providers, and builtin context keys from extensions
3. Provides two compilation paths and a loading path

### Compilation from EndpointRuleSet

```java
public Bytecode compile(EndpointRuleSet rules) {
    var cfg = Cfg.from(rules);                                    // 1. Convert to Control Flow Graph
    var originalTrait = EndpointBddTrait.from(cfg);               // 2. Build BDD from CFG
    var optimizedTrait = SiftingOptimization.builder()            // 3. Sifting optimization
                            .cfg(cfg).build().apply(originalTrait);
    var reversedTrait = new NodeReversal().apply(optimizedTrait); // 4. Node reversal
    return compile(reversedTrait);                                // 5. Compile to bytecode
}
```

### Compilation from pre-built BDD

```java
public Bytecode compile(EndpointBddTrait bdd) {
    return new BytecodeCompiler(extensions, bdd, functions, builtinProviders, builtinKeys).compile();
}
```

### Loading pre-compiled bytecode

```java
public Bytecode load(byte[] data) { ... } // Validates header, loads all sections
```

## The BDD Transformation

The BDD (Binary Decision Diagram) is the core data structure that replaces the traditional if-else decision tree. The
transformation happens in the `smithy-rules` library (external dependency), but the rules engine consumes it.

### What the BDD Represents

- Each **internal node** tests a single condition (by index)
- Each node has a **high** (true) and **low** (false) branch
- **Terminal nodes**: `TRUE (1)`, `FALSE (-1)`, or `RESULT (100_000_000 + resultIndex)`
- **Complement edges** (negative references) encode logical NOT without extra nodes

### BDD Node Encoding

Flat `int[]` array, 3 ints per node (12 bytes):

```
[conditionIndex][highRef][lowRef]
```

Reference encoding:
- `1` = TRUE terminal
- `-1` = FALSE terminal
- `2, 3, ...` = Node at index `ref - 1`
- `-2, -3, ...` = Complement of node at index `-ref - 1`
- `100_000_000+` = Result terminal (result index = `ref - 100_000_000`)

### Optimization Pipeline

1. **`Cfg.from(rules)`** — Converts the `EndpointRuleSet` into a Control Flow Graph
2. **`EndpointBddTrait.from(cfg)`** — Builds the initial BDD from the CFG. Conditions are shared/deduplicated across
   paths.
3. **`SiftingOptimization`** — Reorders BDD variables to minimize node count. Classic BDD optimization that tries
   different variable orderings.
4. **`NodeReversal`** — Reverses node ordering for better cache locality during traversal.

The `EndpointBddTrait` stores:
- Parameters (input variables with types, defaults, builtins)
- Conditions (each is a Smithy `Condition` expression)
- Results (each is an `EndpointRule`, `ErrorRule`, or `NoMatchRule`)
- The BDD graph itself

## Bytecode Compilation

[`BytecodeCompiler`](https://github.com/smithy-lang/smithy-java/blob/main/rules-engine/src/main/java/software/amazon/smithy/java/rulesengine/BytecodeCompiler.java)
transforms the BDD trait into a `Bytecode` object. It compiles each condition and result into stack-based bytecode
instructions.

### Compilation Process

```java
Bytecode compile() {
    // 1. Compile all conditions into bytecode sequences
    for (int i = 0; i < bdd.getConditions().size(); i++) {
        writer.markConditionStart();
        compileCondition(bdd.getConditions().get(i));
    }
    // 2. Compile all results into bytecode sequences
    for (Rule result : bdd.getResults()) {
        writer.markResultStart();
        // EndpointRule, ErrorRule, or NoMatchRule
    }
    return buildProgram();
}
```

### Expression Compilation

The compiler uses Smithy's `ExpressionVisitor` to walk the AST and emit bytecode:

- **Literals** → `LOAD_CONST` / `LOAD_CONST_W`
- **References** → `LOAD_REGISTER`
- **String templates** → Multiple loads + `RESOLVE_TEMPLATE`
- **GetAttr** → `GET_PROPERTY`, `GET_INDEX`, or register-optimized variants
- **IsSet** → `TEST_REGISTER_ISSET` (optimized for references) or `ISSET`
- **Not** → `TEST_REGISTER_NOT_SET` (optimized) or `NOT`
- **BoolEquals** → `TEST_REGISTER_IS_TRUE`/`TEST_REGISTER_IS_FALSE` (optimized) or `BOOLEAN_EQUALS`
- **StringEquals** → `STRING_EQUALS_REG_CONST` (optimized for ref==const) or `STRING_EQUALS`
- **Library functions** → Special opcodes for builtins or generic `FN0`-`FN3`/`FN` for custom functions

### Fused Opcodes

The compiler aggressively fuses common patterns into single opcodes:

| Fused Opcode | Pattern | Benefit |
|---|---|---|
| `SUBSTRING_EQ` | `stringEquals(coalesce(substring(ref, s, e, rev), ""), "xx")` | Single opcode instead of 5+ |
| `SPLIT_GET` | `split(ref, delim, 0)#[index]` | Avoids allocating the split array |
| `SELECT_BOOL_REG` | `ite(register, constA, constB)` | Single opcode for if-then-else |
| `STRING_EQUALS_REG_CONST` | `stringEquals(ref, "literal")` | Avoids stack manipulation |
| `SET_REG_RETURN` | `SET_REGISTER` + `RETURN_VALUE` | Fuses two instructions |
| `BUILD_URI` | URL template decomposition | Constructs `SmithyUri` directly, avoids `URI.create()` |
| `STRUCTN` | Small records (≤8 entries) | Uses `ArrayPropertyGetter` (linear scan) instead of `HashMap` |

### Register Allocation

[`RegisterAllocator`](https://github.com/smithy-lang/smithy-java/blob/main/rules-engine/src/main/java/software/amazon/smithy/java/rulesengine/RegisterAllocator.java)
assigns register indices:
- Input parameters get registers 0..N-1
- Temporary variables (condition results) get registers N..M
- Maximum 256 registers (byte-indexed)

## The Bytecode Format

### Binary Format (44-byte header)

```
Offset  Size  Description
0       4     Magic number (0x52554C45 = "RULE")
4       2     Version (currently 1)
6       2     Condition count
8       2     Result count
10      2     Register count
12      2     Constant pool size
14      2     Function count
16      4     BDD node count
20      4     BDD root reference
24      4     Condition table offset
28      4     Result table offset
32      4     Function table offset
36      4     Constant pool offset
40      4     BDD table offset
```

### File Layout

1. **Header** (44 bytes)
2. **Condition Table** — Array of 4-byte absolute offsets to condition bytecode
3. **Result Table** — Array of 4-byte absolute offsets to result bytecode
4. **Function Table** — Length-prefixed UTF-8 function names
5. **Register Definitions** — Serialized parameter metadata
6. **BDD Table** — Flat array of BDD nodes (12 bytes each)
7. **Instruction Section** — Compiled bytecode for all conditions and results
8. **Constant Pool** — Type-tagged constants (null, string, integer, boolean, list, map)

### Condition Classification (Inline BDD Optimization)

At construction time, `Bytecode.classifyConditions()` scans each condition's bytecode to identify trivial patterns that
can be evaluated inline during BDD traversal without entering the full VM dispatch loop:

```java
COND_ISSET = 1;              // TEST_REGISTER_ISSET + RETURN_VALUE
COND_IS_TRUE = 2;            // TEST_REGISTER_IS_TRUE + RETURN_VALUE
COND_IS_FALSE = 3;           // TEST_REGISTER_IS_FALSE + RETURN_VALUE
COND_NOT_SET = 4;            // TEST_REGISTER_NOT_SET + RETURN_VALUE
COND_STRING_EQ_REG_CONST = 5; // STRING_EQUALS_REG_CONST + RETURN_VALUE
```

These are stored in `conditionTypes[]` and `conditionOperands[]` arrays for O(1) lookup during BDD traversal.

## The VM Implementation

[`BytecodeEvaluator`](https://github.com/smithy-lang/smithy-java/blob/main/rules-engine/src/main/java/software/amazon/smithy/java/rulesengine/BytecodeEvaluator.java)
is a stack-based VM with a fixed-size stack (`Object[64]`) and a register file (`Object[]`).

### Core Evaluation Loop

```java
private Object runLoop(byte[] instructions, RulesFunction[] functions, Object[] constantPool) {
    int pc = this.pc;
    int sp = this.stackPosition;
    Object[] stack = this.stack;
    Object[] regs = this.registers;

    while (pc < instructions.length) {
        int opcode = instructions[pc++] & 0xFF;
        switch (opcode) {
            case Opcodes.LOAD_CONST -> stack[sp++] = constantPool[instructions[pc++] & 0xFF];
            case Opcodes.LOAD_REGISTER -> stack[sp++] = regs[instructions[pc++] & 0xFF];
            // ... 40+ opcodes
        }
    }
}
```

Key design: `pc`, `sp`, `stack`, `regs` are local variables to avoid field access overhead and help JIT register
allocation.

### BDD Evaluation with Inline Optimization

The critical hot path is `evaluateBdd()`:

```java
Endpoint evaluateBdd() {
    int ref = bytecode.getBddRootRef();
    int[] nodes = bytecode.getBddNodes();
    byte[] condTypes = bytecode.conditionTypes;
    int[] condOps = bytecode.conditionOperands;
    Object[] regs = this.registers;

    while (Bdd.isNodeReference(ref)) {
        int idx = ref > 0 ? ref - 1 : -ref - 1;
        int base = idx * 3;
        int condIdx = nodes[base];

        boolean result = switch (condTypes[condIdx]) {
            case COND_ISSET -> regs[condOps[condIdx]] != null;           // Inline!
            case COND_IS_TRUE -> regs[condOps[condIdx]] == Boolean.TRUE; // Inline!
            case COND_STRING_EQ_REG_CONST -> { ... }                    // Inline!
            default -> test(condIdx);  // Full bytecode evaluation
        };

        // Handle complement edges (XOR with sign bit)
        ref = (result ^ (ref < 0)) ? nodes[base + 1] : nodes[base + 2];
    }

    if (Bdd.isTerminal(ref)) return null;
    return resolveResult(ref - Bdd.RESULT_OFFSET);
}
```

For most real-world rulesets, the majority of conditions are simple register checks (isSet, isTrue, stringEquals) that
get inlined — avoiding the overhead of the full bytecode dispatch loop.

### Thread-Local Evaluators

`BytecodeEndpointResolver` uses `ThreadLocal<BytecodeEvaluator>` — each thread reuses its evaluator with no
synchronization overhead.

## Register Filling

### RegisterFiller

Two implementations selected at construction time:
- **`FastRegisterFiller`** (< 64 registers) — Uses `long` bitmasks for O(1) tracking of filled/required registers
- **`LargeRegisterFiller`** (≥ 64 registers) — Uses `boolean[]` arrays

Fill order:
1. Copy register template (defaults)
2. Apply input parameters from the request
3. Apply builtin providers (key-based first, then function-based)
4. Validate required parameters

### RegisterSink (Zero-Allocation Parameter Passing)

`ContextProvider.RegisterSink` writes endpoint parameters directly into register indices, avoiding `Map<String, Object>`
allocation:

```java
void put(String name, Object value) {
    Integer i = registerMap.get(name);
    if (i != null) {
        values[i] = value;
        if (i < 64) filled |= 1L << i;
    }
}
```

## How Endpoints Are Resolved

### BytecodeEndpointResolver.resolveEndpoint(EndpointResolverParams params)

1. Get thread-local `BytecodeEvaluator`
2. Write endpoint params into `RegisterSink` via `ContextProvider.createEndpointParams()`
3. Call `evaluator.resetFromSink(ctx)` — copies template, drains sink, fills builtins, validates
4. Call `evaluator.evaluateBdd()` — traverses BDD, evaluates conditions, resolves result
5. Result bytecode builds an `Endpoint` via `RETURN_ENDPOINT` opcode

### Context Parameter Collection

[`ContextProvider`](https://github.com/smithy-lang/smithy-java/blob/main/rules-engine/src/main/java/software/amazon/smithy/java/rulesengine/ContextProvider.java)
is a sealed interface hierarchy that extracts parameters from operations:

- **`OrchestratingProvider`** — Top-level, caches per-operation providers in a `ConcurrentHashMap<ShapeId, ContextProvider>`
- **`ContextParamProvider`** — Reads `smithy.rules#contextParam` trait from input members
- **`ContextPathProvider`** — Evaluates JMESPath expressions from `smithy.rules#operationContextParams`
- **`StaticParamsProvider`** — Returns fixed values from `smithy.rules#staticContextParams`
- **`MultiContextParamProvider`** — Composes multiple providers

## Built-in Functions and Extensibility

### RulesExtension SPI

```java
public interface RulesExtension {
    void putBuiltinProviders(Map<String, Function<Context, Object>> providers);
    void putBuiltinKeys(Map<String, Context.Key<?>> keys);
    Iterable<RulesFunction> getFunctions();
    void extractEndpointProperties(Endpoint.Builder builder, Context context,
                                    Map<String, Object> properties, Map<String, List<String>> headers);
}
```

Extensions are discovered via `ServiceLoader` and always include `StdExtension`.

### RulesFunction Interface

```java
public interface RulesFunction {
    int getArgumentCount();
    String getFunctionName();
    Object apply(Object... arguments);   // Generic N-arg
    Object apply0();                      // 0-arg fast path
    Object apply1(Object arg1);           // 1-arg fast path
    Object apply2(Object arg1, Object arg2); // 2-arg fast path
}
```

Functions with 0-3 args use specialized methods to avoid varargs array allocation. The compiler emits `FN0`-`FN3`
opcodes accordingly.

### Built-in Functions with Dedicated Opcodes

| Function | Opcode | Notes |
|---|---|---|
| `substring` | `SUBSTRING` | Inline start/end/reverse as operands |
| `isValidHostLabel` | `IS_VALID_HOST_LABEL` | Validates DNS labels |
| `parseURL` | `PARSE_URL` | Uses `UriFactory` LRU cache |
| `uriEncode` | `URI_ENCODE` | Delegates to `URLEncoding.encodeUnreserved()` |
| `split` | `SPLIT` | String splitting |
| `coalesce` | `JNN_OR_POP` chain | Short-circuit null coalescing |
| `ite` | `JMP_IF_FALSE`+`JUMP` or `SELECT_BOOL_REG` | If-then-else |
| `isSet` | `TEST_REGISTER_ISSET` / `ISSET` | Null check |
| `not` | `TEST_REGISTER_NOT_SET` / `NOT` | Boolean negation |

## Performance: BDD/VM vs Traditional Evaluation

| Aspect | Traditional (`DecisionTreeEndpointResolver`) | BDD/VM (`BytecodeEndpointResolver`) |
|---|---|---|
| Condition evaluation | Redundant across shared paths | Each condition evaluated at most once (BDD sharing) |
| Hot path allocation | `Map<Identifier, Value>` per resolution | Zero allocation (`RegisterSink` + `long` bitmasks) |
| Simple conditions | Full expression evaluation | Inline array lookup + comparison (no VM dispatch) |
| Thread safety | New state per call | Thread-local evaluator reuse |
| Constants | Parsed per evaluation | Pre-loaded constant pool |
| URI construction | `URI.create(string)` | `BUILD_URI` decomposes template, `UriFactory` LRU cache |
| Small records | `HashMap` | `ArrayPropertyGetter` (linear scan, ≤8 entries) |
| Interpreter state | Object fields | Local variables (JIT-friendly) |

### Key Performance Numbers

- Stack size: 64 slots (fixed, no resizing)
- Max registers: 256 (byte-indexed)
- Max constants: 65,536 (short-indexed)
- BDD nodes: flat `int[]` array (12 bytes each), excellent cache locality
- Condition classification: O(1) lookup via parallel `byte[]` and `int[]` arrays
- URI cache: LRU (32 entries) with hot-slot optimization

## Key Classes Summary

| Class | Role |
|---|---|
| `RulesEngineBuilder` | Factory: compiles rulesets to bytecode, loads bytecode, manages extensions |
| `BytecodeCompiler` | Compiles `EndpointBddTrait` conditions/results into bytecode |
| `BytecodeWriter` | Incrementally builds the binary bytecode format with jump patching |
| `BytecodeReader` | Reads/deserializes the binary bytecode format |
| `Bytecode` | Immutable compiled program: bytecode, offsets, constant pool, BDD, condition classification |
| `Opcodes` | Constants for all 40+ instruction opcodes |
| `BytecodeEvaluator` | Stack-based VM that executes bytecode, contains `evaluateBdd()` hot path |
| `BytecodeEndpointResolver` | `EndpointResolver` using bytecode VM with thread-local evaluators |
| `DecisionTreeEndpointResolver` | Fallback `EndpointResolver` using Smithy's `RuleEvaluator` |
| `RegisterAllocator` | Assigns register indices to parameters and temp variables |
| `RegisterFiller` | Abstract class with `FastRegisterFiller` (bitmask) and `LargeRegisterFiller` (array) |
| `ContextProvider` | Sealed interface hierarchy for extracting endpoint parameters from operations |
| `RulesExtension` | SPI for extending the engine with custom builtins and functions |
| `RulesFunction` | Interface for custom functions with arity-specialized methods |
| `StdExtension` | Default extension providing `SDK::Endpoint` builtin |
| `EndpointUtils` | Static helpers: property access, substring, splitGet, value conversion |
| `UriFactory` | LRU cache for URI parsing with hot-slot optimization |
| `BytecodeDisassembler` | Human-readable disassembly (used by `Bytecode.toString()`) |
| `BytecodeWalker` | Utility for walking bytecode instructions respecting operand boundaries |

## Testing

The test suite includes:
- **Unit tests**: `BytecodeCompilerTest`, `BytecodeEvaluatorTest`, `BytecodeWriterTest`, `BytecodeReaderTest`,
  `RegisterAllocatorTest`, `RegisterFillerTest`, `EndpointUtilsTest`, `UriFactoryTest`
- **Integration tests**: `BytecodeEndpointResolverTest`, `DecisionTreeEndpointResolverTest`
- **Fuzz tests** (using Jazzer): `BytecodeEvaluatorFuzzTest`, `BytecodeReaderFuzzTest`, `BytecodeLoadFuzzTest`,
  `BytecodeWalkerFuzzTest` — test robustness against malformed bytecode inputs
- **Disassembler tests**: `BytecodeDisassemblerTest`
