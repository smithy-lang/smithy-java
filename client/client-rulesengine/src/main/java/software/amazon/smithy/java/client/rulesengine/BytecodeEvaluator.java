package software.amazon.smithy.java.client.rulesengine;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.Substring;
import software.amazon.smithy.rulesengine.logic.ConditionEvaluator;

final class BytecodeEvaluator implements ConditionEvaluator {

    // Minimum size for temp arrays when it's lazily allocated.
    private static final int MIN_TEMP_ARRAY_SIZE = 8;

    private final Bytecode bytecode;
    private final Object[] registers;
    private final List<RulesExtension> extensions;
    private final Context context;
    private Object[] tempArray = new Object[8];
    private int tempArraySize = 8;
    private Object[] stack = new Object[8];
    private int stackPosition = 0;
    private int pc;

    BytecodeEvaluator(
            Bytecode bytecode,
            Context context,
            Map<String, Object> parameters,
            Map<String, Function<Context, Object>> builtinProviders,
            List<RulesExtension> extensions
    ) {
        this.bytecode = bytecode;
        this.context = context;
        this.extensions = extensions;
        this.registers = bytecode.createRegisters(context, parameters, builtinProviders);
    }

    @Override
    public boolean test(int conditionIndex) {
        // Reset stack position for fresh evaluation
        stackPosition = 0;
        int start = bytecode.getConditionStartOffset(conditionIndex);
        Object result = runBytecode(start);
        return result != null && result != Boolean.FALSE;
    }

    public Endpoint resolveResult(int resultIndex) {
        if (resultIndex <= -1) {
            return null;
        }

        stackPosition = 0;
        return runBytecode(bytecode.getResultOffset(resultIndex));
    }

    @SuppressWarnings("unchecked")
    <T> T runBytecode(int start) {
        try {
            return (T) run(start);
        } catch (ClassCastException e) {
            throw createError("Unexpected value type encountered while evaluating rules engine", e);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw createError("Malformed bytecode encountered while evaluating rules engine", e);
        } catch (NullPointerException e) {
            throw createError("Rules engine encountered an unexpected null value", e);
        }
    }

    private RulesEvaluationError createError(String message, RuntimeException e) {
        var report = message + ". Encountered at address " + pc + " of bytecode";
        throw new RulesEvaluationError(report, pc, e);
    }

    private void push(Object value) {
        if (stackPosition == stack.length) {
            resizeStack();
        }
        stack[stackPosition++] = value;
    }

    private void resizeStack() {
        int newCapacity = stack.length + (stack.length >> 1);
        Object[] newStack = new Object[newCapacity];
        System.arraycopy(stack, 0, newStack, 0, stack.length);
        stack = newStack;
    }

    private Object[] getTempArray(int requiredSize) {
        if (tempArraySize < requiredSize) {
            resizeTempArray(requiredSize);
        }
        return tempArray;
    }

    private void resizeTempArray(int requiredSize) {
        // Resize to a power of two.
        int newSize = MIN_TEMP_ARRAY_SIZE;
        while (newSize < requiredSize) {
            newSize <<= 1;
        }

        tempArray = new Object[newSize];
        tempArraySize = newSize;
    }

    @SuppressWarnings("unchecked")
    private Object run(int start) {
        pc = start;

        var instructions = bytecode.getBytecode();
        var functions = bytecode.getFunctions();
        var constantPool = bytecode.getConstantPool();

        while (pc < instructions.length) {
            int opcode = instructions[pc++] & 0xFF;
            switch (opcode) {
                case Opcodes.LOAD_CONST -> {
                    var value = constantPool[instructions[pc++] & 0xFF];
                    push(value);
                }
                case Opcodes.LOAD_CONST_W -> {
                    int constIdx = ((instructions[pc] & 0xFF) << 8) | (instructions[pc + 1] & 0xFF);
                    push(constantPool[constIdx]);
                    pc += 2;
                }
                case Opcodes.SET_REGISTER -> {
                    int index = instructions[pc++] & 0xFF;
                    registers[index] = stack[stackPosition - 1];
                }
                case Opcodes.LOAD_REGISTER -> {
                    int index = instructions[pc++] & 0xFF;
                    push(registers[index]);
                }
                case Opcodes.NOT -> {
                    push(stack[--stackPosition] == Boolean.FALSE ? Boolean.TRUE : Boolean.FALSE);
                }
                case Opcodes.ISSET -> {
                    push(stack[--stackPosition] != null ? Boolean.TRUE : Boolean.FALSE);
                }
                case Opcodes.TEST_REGISTER_ISSET -> {
                    push(registers[instructions[pc++] & 0xFF] != null ? Boolean.TRUE : Boolean.FALSE);
                }
                case Opcodes.TEST_REGISTER_NOT_SET -> {
                    push(registers[instructions[pc++] & 0xFF] == null ? Boolean.TRUE : Boolean.FALSE);
                }
                case Opcodes.RETURN_ERROR -> {
                    throw new RulesEvaluationError((String) stack[--stackPosition], pc);
                }
                case Opcodes.RETURN_ENDPOINT -> {
                    var packed = instructions[pc++];
                    boolean hasHeaders = (packed & 1) != 0;
                    boolean hasProperties = (packed & 2) != 0;
                    var urlString = (String) stack[--stackPosition];
                    var properties = (Map<String, Object>) (hasProperties ? stack[--stackPosition] : Map.of());
                    var headers = (Map<String, List<String>>) (hasHeaders ? stack[--stackPosition] : Map.of());
                    var builder = Endpoint.builder().uri(EndpointUtils.parseUri(urlString));
                    if (!headers.isEmpty()) {
                        builder.putProperty(EndpointContext.HEADERS, headers);
                    }
                    for (var extension : extensions) {
                        extension.extractEndpointProperties(builder, context, properties, headers);
                    }
                    return builder.build();
                }
                case Opcodes.CREATE_LIST -> {
                    var size = instructions[pc++] & 0xFF;
                    push(switch (size) {
                        case 0 -> List.of();
                        case 1 -> Collections.singletonList(stack[--stackPosition]);
                        default -> {
                            var values = new Object[size];
                            for (var i = size - 1; i >= 0; i--) {
                                values[i] = stack[--stackPosition];
                            }
                            yield Arrays.asList(values);
                        }
                    });
                }
                case Opcodes.CREATE_MAP -> {
                    var size = instructions[pc++] & 0xFF;
                    push(switch (size) {
                        case 0 -> Map.of();
                        case 1 -> Map.of((String) stack[--stackPosition], stack[--stackPosition]);
                        default -> {
                            Map<String, Object> map = new HashMap<>((int) (size / 0.75f) + 1);
                            for (var i = 0; i < size; i++) {
                                map.put((String) stack[--stackPosition], stack[--stackPosition]);
                            }
                            yield map;
                        }
                    });
                }
                case Opcodes.RESOLVE_TEMPLATE -> {
                    int constIdx = ((instructions[pc] & 0xFF) << 8) | (instructions[pc + 1] & 0xFF);
                    var template = (StringTemplate) constantPool[constIdx];
                    var expressionCount = template.expressionCount();
                    var temp = getTempArray(expressionCount);
                    for (var i = 0; i < expressionCount; i++) {
                        temp[i] = stack[--stackPosition];
                    }
                    push(template.resolve(expressionCount, temp));
                    pc += 2;
                }
                case Opcodes.FN -> {
                    var fn = functions[instructions[pc++] & 0xFF];
                    push(switch (fn.getArgumentCount()) {
                        case 0 -> fn.apply0();
                        case 1 -> fn.apply1(stack[--stackPosition]);
                        case 2 -> {
                            Object b = stack[--stackPosition];
                            Object a = stack[--stackPosition];
                            yield fn.apply2(a, b);
                        }
                        default -> {
                            var temp = getTempArray(fn.getArgumentCount());
                            for (int i = fn.getArgumentCount() - 1; i >= 0; i--) {
                                temp[i] = stack[--stackPosition];
                            }
                            yield fn.apply(temp);
                        }
                    });
                }
                case Opcodes.GET_ATTR -> {
                    int constIdx = ((instructions[pc] & 0xFF) << 8) | (instructions[pc + 1] & 0xFF);
                    AttrExpression getAttr = (AttrExpression) constantPool[constIdx];
                    var target = stack[--stackPosition];
                    push(getAttr.apply(target));
                    pc += 2;
                }
                case Opcodes.IS_TRUE -> {
                    push(stack[--stackPosition] == Boolean.TRUE ? Boolean.TRUE : Boolean.FALSE);
                }
                case Opcodes.TEST_REGISTER_IS_TRUE -> {
                    push(registers[instructions[pc++] & 0xFF] == Boolean.TRUE ? Boolean.TRUE : Boolean.FALSE);
                }
                case Opcodes.TEST_REGISTER_IS_FALSE -> {
                    push(registers[instructions[pc++] & 0xFF] == Boolean.FALSE ? Boolean.TRUE : Boolean.FALSE);
                }
                case Opcodes.RETURN_VALUE -> {
                    return stack[--stackPosition];
                }
                case Opcodes.EQUALS -> {
                    push(Objects.equals(stack[--stackPosition], stack[--stackPosition]));
                }
                case Opcodes.SUBSTRING -> {
                    var string = (String) stack[--stackPosition];
                    var startPos = instructions[pc++] & 0xFF;
                    var endPos = instructions[pc++] & 0xFF;
                    var reverse = (instructions[pc++] & 0xFF) != 0 ? Boolean.TRUE : Boolean.FALSE;
                    push(Substring.getSubstring(string, startPos, endPos, reverse));
                }
                default -> throw new RulesEvaluationError("Unknown rules engine instruction: " + opcode, pc);
            }
        }

        throw new IllegalArgumentException("Expected to return a value during evaluation");
    }
}
