/*
 * SKIPLICENSECHECK
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package software.amazon.smithy.java.logging;

import static java.lang.Character.toLowerCase;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.util.Chars;

/**
 * Supports parameter formatting as used in ParameterizedMessage and ReusableParameterizedMessage.
 */
final class ParameterFormatter {
    /**
     * Prefix for recursion.
     */
    static final String RECURSION_PREFIX = "[...";
    /**
     * Suffix for recursion.
     */
    static final String RECURSION_SUFFIX = "...]";

    /**
     * Prefix for errors.
     */
    static final String ERROR_PREFIX = "[!!!";
    /**
     * Separator for errors.
     */
    static final String ERROR_SEPARATOR = "=>";
    /**
     * Separator for error messages.
     */
    static final String ERROR_MSG_SEPARATOR = ":";
    /**
     * Suffix for errors.
     */
    static final String ERROR_SUFFIX = "!!!]";

    private static final char DELIM_START = '{';
    private static final char DELIM_STOP = '}';
    private static final char ESCAPE_CHAR = '\\';

    private static ThreadLocal<SimpleDateFormat> threadLocalSimpleDateFormat = new ThreadLocal<>();

    private ParameterFormatter() {
    }

    /**
     * Counts the number of unescaped placeholders in the given messagePattern.
     *
     * @param messagePattern the message pattern to be analyzed.
     * @return the number of unescaped placeholders.
     */
    static int countArgumentPlaceholders(final String messagePattern) {
        if (messagePattern == null) {
            return 0;
        }
        final int length = messagePattern.length();
        int result = 0;
        boolean isEscaped = false;
        for (int i = 0; i < length - 1; i++) {
            final char curChar = messagePattern.charAt(i);
            if (curChar == ESCAPE_CHAR) {
                isEscaped = !isEscaped;
            } else if (curChar == DELIM_START) {
                if (!isEscaped && messagePattern.charAt(i + 1) == DELIM_STOP) {
                    result++;
                    i++;
                }
                isEscaped = false;
            } else {
                isEscaped = false;
            }
        }
        return result;
    }

    /**
     * Counts the number of unescaped placeholders in the given messagePattern.
     *
     * @param messagePattern the message pattern to be analyzed.
     * @return the number of unescaped placeholders.
     */
    static int countArgumentPlaceholders2(final String messagePattern, final int[] indices) {
        if (messagePattern == null) {
            return 0;
        }
        final int length = messagePattern.length();
        int result = 0;
        boolean isEscaped = false;
        for (int i = 0; i < length - 1; i++) {
            final char curChar = messagePattern.charAt(i);
            if (curChar == ESCAPE_CHAR) {
                isEscaped = !isEscaped;
                indices[0] = -1; // escaping means fast path is not available...
                result++;
            } else if (curChar == DELIM_START) {
                if (!isEscaped && messagePattern.charAt(i + 1) == DELIM_STOP) {
                    indices[result] = i;
                    result++;
                    i++;
                }
                isEscaped = false;
            } else {
                isEscaped = false;
            }
        }
        return result;
    }

    /**
     * Counts the number of unescaped placeholders in the given messagePattern.
     *
     * @param messagePattern the message pattern to be analyzed.
     * @return the number of unescaped placeholders.
     */
    static int countArgumentPlaceholders3(final char[] messagePattern, final int length, final int[] indices) {
        int result = 0;
        boolean isEscaped = false;
        for (int i = 0; i < length - 1; i++) {
            final char curChar = messagePattern[i];
            if (curChar == ESCAPE_CHAR) {
                isEscaped = !isEscaped;
            } else if (curChar == DELIM_START) {
                if (!isEscaped && messagePattern[i + 1] == DELIM_STOP) {
                    indices[result] = i;
                    result++;
                    i++;
                }
                isEscaped = false;
            } else {
                isEscaped = false;
            }
        }
        return result;
    }

    /**
     * Replace placeholders in the given messagePattern with arguments.
     *
     * @param messagePattern the message pattern containing placeholders.
     * @param arguments      the arguments to be used to replace placeholders.
     * @return the formatted message.
     */
    static String format(final String messagePattern, final Object[] arguments) {
        final StringBuilder result = new StringBuilder();
        final int argCount = arguments == null ? 0 : arguments.length;
        formatMessage(result, messagePattern, arguments, argCount);
        return result.toString();
    }

    /**
     * Replace placeholders in the given messagePattern with arguments.
     *
     * @param buffer the buffer to write the formatted message into
     * @param messagePattern the message pattern containing placeholders.
     * @param arguments      the arguments to be used to replace placeholders.
     */
    static void formatMessage2(
        final StringBuilder buffer,
        final String messagePattern,
        final Object[] arguments,
        final int argCount,
        final int[] indices
    ) {
        if (messagePattern == null || arguments == null || argCount == 0) {
            buffer.append(messagePattern);
            return;
        }
        int previous = 0;
        for (int i = 0; i < argCount; i++) {
            buffer.append(messagePattern, previous, indices[i]);
            previous = indices[i] + 2;
            recursiveDeepToString(arguments[i], buffer, null);
        }
        buffer.append(messagePattern, previous, messagePattern.length());
    }

    /**
     * Replace placeholders in the given messagePattern with arguments.
     *
     * @param buffer the buffer to write the formatted message into
     * @param messagePattern the message pattern containing placeholders.
     * @param arguments      the arguments to be used to replace placeholders.
     */
    static void formatMessage3(
        final StringBuilder buffer,
        final char[] messagePattern,
        final int patternLength,
        final Object[] arguments,
        final int argCount,
        final int[] indices
    ) {
        if (messagePattern == null) {
            return;
        }
        if (arguments == null || argCount == 0) {
            buffer.append(messagePattern);
            return;
        }
        int previous = 0;
        for (int i = 0; i < argCount; i++) {
            buffer.append(messagePattern, previous, indices[i]);
            previous = indices[i] + 2;
            recursiveDeepToString(arguments[i], buffer, null);
        }
        buffer.append(messagePattern, previous, patternLength);
    }

    /**
     * Replace placeholders in the given messagePattern with arguments.
     *
     * @param buffer the buffer to write the formatted message into
     * @param messagePattern the message pattern containing placeholders.
     * @param arguments      the arguments to be used to replace placeholders.
     */
    static void formatMessage(
        final StringBuilder buffer,
        final String messagePattern,
        final Object[] arguments,
        final int argCount
    ) {
        if (messagePattern == null || arguments == null || argCount == 0) {
            buffer.append(messagePattern);
            return;
        }
        int escapeCounter = 0;
        int currentArgument = 0;
        int i = 0;
        final int len = messagePattern.length();
        for (; i < len - 1; i++) { // last char is excluded from the loop
            final char curChar = messagePattern.charAt(i);
            if (curChar == ESCAPE_CHAR) {
                escapeCounter++;
            } else {
                if (isDelimPair(curChar, messagePattern, i)) { // looks ahead one char
                    i++;

                    // write escaped escape chars
                    writeEscapedEscapeChars(escapeCounter, buffer);

                    if (isOdd(escapeCounter)) {
                        // i.e. escaped: write escaped escape chars
                        writeDelimPair(buffer);
                    } else {
                        // unescaped
                        writeArgOrDelimPair(arguments, argCount, currentArgument, buffer);
                        currentArgument++;
                    }
                } else {
                    handleLiteralChar(buffer, escapeCounter, curChar);
                }
                escapeCounter = 0;
            }
        }
        handleRemainingCharIfAny(messagePattern, len, buffer, escapeCounter, i);
    }

    /**
     * Returns {@code true} if the specified char and the char at {@code curCharIndex + 1} in the specified message
     * pattern together form a "{}" delimiter pair, returns {@code false} otherwise.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 22 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static boolean isDelimPair(final char curChar, final String messagePattern, final int curCharIndex) {
        return curChar == DELIM_START && messagePattern.charAt(curCharIndex + 1) == DELIM_STOP;
    }

    /**
     * Detects whether the message pattern has been fully processed or if an unprocessed character remains and processes
     * it if necessary, returning the resulting position in the result char array.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 28 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void handleRemainingCharIfAny(
        final String messagePattern,
        final int len,
        final StringBuilder buffer,
        final int escapeCounter,
        final int i
    ) {
        if (i == len - 1) {
            final char curChar = messagePattern.charAt(i);
            handleLastChar(buffer, escapeCounter, curChar);
        }
    }

    /**
     * Processes the last unprocessed character and returns the resulting position in the result char array.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 28 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void handleLastChar(final StringBuilder buffer, final int escapeCounter, final char curChar) {
        if (curChar == ESCAPE_CHAR) {
            writeUnescapedEscapeChars(escapeCounter + 1, buffer);
        } else {
            handleLiteralChar(buffer, escapeCounter, curChar);
        }
    }

    /**
     * Processes a literal char (neither an '\' escape char nor a "{}" delimiter pair) and returns the resulting
     * position.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 16 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void handleLiteralChar(final StringBuilder buffer, final int escapeCounter, final char curChar) {
        // any other char beside ESCAPE or DELIM_START/STOP-combo
        // write unescaped escape chars
        writeUnescapedEscapeChars(escapeCounter, buffer);
        buffer.append(curChar);
    }

    /**
     * Writes "{}" to the specified result array at the specified position and returns the resulting position.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 18 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void writeDelimPair(final StringBuilder buffer) {
        buffer.append(DELIM_START);
        buffer.append(DELIM_STOP);
    }

    /**
     * Returns {@code true} if the specified parameter is odd.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 11 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static boolean isOdd(final int number) {
        return (number & 1) == 1;
    }

    /**
     * Writes a '\' char to the specified result array (starting at the specified position) for each <em>pair</em> of
     * '\' escape chars encountered in the message format and returns the resulting position.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 11 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void writeEscapedEscapeChars(final int escapeCounter, final StringBuilder buffer) {
        final int escapedEscapes = escapeCounter >> 1; // divide by two
        writeUnescapedEscapeChars(escapedEscapes, buffer);
    }

    /**
     * Writes the specified number of '\' chars to the specified result array (starting at the specified position) and
     * returns the resulting position.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 20 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void writeUnescapedEscapeChars(int escapeCounter, final StringBuilder buffer) {
        while (escapeCounter > 0) {
            buffer.append(ESCAPE_CHAR);
            escapeCounter--;
        }
    }

    /**
     * Appends the argument at the specified argument index (or, if no such argument exists, the "{}" delimiter pair) to
     * the specified result char array at the specified position and returns the resulting position.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 25 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void writeArgOrDelimPair(
        final Object[] arguments,
        final int argCount,
        final int currentArgument,
        final StringBuilder buffer
    ) {
        if (currentArgument < argCount) {
            recursiveDeepToString(arguments[currentArgument], buffer, null);
        } else {
            writeDelimPair(buffer);
        }
    }

    /**
     * This method performs a deep toString of the given Object.
     * Primitive arrays are converted using their respective Arrays.toString methods while
     * special handling is implemented for "container types", i.e. Object[], Map and Collection because those could
     * contain themselves.
     * <p>
     * It should be noted that neither AbstractMap.toString() nor AbstractCollection.toString() implement such a
     * behavior. They only check if the container is directly contained in itself, but not if a contained container
     * contains the original one. Because of that, Arrays.toString(Object[]) isn't safe either.
     * Confusing? Just read the last paragraph again and check the respective toString() implementation.
     * </p>
     * <p>
     * This means, in effect, that logging would produce a usable output even if an ordinary System.out.println(o)
     * would produce a relatively hard-to-debug StackOverflowError.
     * </p>
     * @param o The object.
     * @return The String representation.
     */
    static String deepToString(final Object o) {
        if (o == null) {
            return null;
        }
        // Check special types to avoid unnecessary StringBuilder usage
        if (o instanceof String) {
            return (String) o;
        }
        if (o instanceof Integer) {
            return Integer.toString((Integer) o);
        }
        if (o instanceof Long) {
            return Long.toString((Long) o);
        }
        if (o instanceof Double) {
            return Double.toString((Double) o);
        }
        if (o instanceof Boolean) {
            return Boolean.toString((Boolean) o);
        }
        if (o instanceof Character) {
            return Character.toString((Character) o);
        }
        if (o instanceof Short) {
            return Short.toString((Short) o);
        }
        if (o instanceof Float) {
            return Float.toString((Float) o);
        }
        if (o instanceof Byte) {
            return Byte.toString((Byte) o);
        }
        final StringBuilder str = new StringBuilder();
        recursiveDeepToString(o, str, null);
        return str.toString();
    }

    /**
     * This method performs a deep toString of the given Object.
     * Primitive arrays are converted using their respective Arrays.toString methods while
     * special handling is implemented for "container types", i.e. Object[], Map and Collection because those could
     * contain themselves.
     * <p>
     * dejaVu is used in case of those container types to prevent an endless recursion.
     * </p>
     * <p>
     * It should be noted that neither AbstractMap.toString() nor AbstractCollection.toString() implement such a
     * behavior.
     * They only check if the container is directly contained in itself, but not if a contained container contains the
     * original one. Because of that, Arrays.toString(Object[]) isn't safe either.
     * Confusing? Just read the last paragraph again and check the respective toString() implementation.
     * </p>
     * <p>
     * This means, in effect, that logging would produce a usable output even if an ordinary System.out.println(o)
     * would produce a relatively hard-to-debug StackOverflowError.
     * </p>
     *
     * @param o      the Object to convert into a String
     * @param str    the StringBuilder that o will be appended to
     * @param dejaVu a list of container identities that were already used.
     */
    static void recursiveDeepToString(final Object o, final StringBuilder str, final Set<String> dejaVu) {
        if (appendSpecialTypes(o, str)) {
            return;
        }
        if (isMaybeRecursive(o)) {
            appendPotentiallyRecursiveValue(o, str, dejaVu);
        } else {
            tryObjectToString(o, str);
        }
    }

    private static boolean appendSpecialTypes(final Object o, final StringBuilder str) {
        return StringBuilders.appendSpecificTypes(str, o) || appendDate(o, str);
    }

    private static boolean appendDate(final Object o, final StringBuilder str) {
        if (!(o instanceof Date)) {
            return false;
        }
        final Date date = (Date) o;
        final SimpleDateFormat format = getSimpleDateFormat();
        str.append(format.format(date));
        return true;
    }

    private static SimpleDateFormat getSimpleDateFormat() {
        SimpleDateFormat result = threadLocalSimpleDateFormat.get();
        if (result == null) {
            result = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            threadLocalSimpleDateFormat.set(result);
        }
        return result;
    }

    /**
     * Returns {@code true} if the specified object is an array, a Map or a Collection.
     */
    private static boolean isMaybeRecursive(final Object o) {
        return o.getClass().isArray() || o instanceof Map || o instanceof Collection;
    }

    private static void appendPotentiallyRecursiveValue(
        final Object o,
        final StringBuilder str,
        final Set<String> dejaVu
    ) {
        final Class<?> oClass = o.getClass();
        if (oClass.isArray()) {
            appendArray(o, str, dejaVu, oClass);
        } else if (o instanceof Map) {
            appendMap(o, str, dejaVu);
        } else if (o instanceof Collection) {
            appendCollection(o, str, dejaVu);
        }
    }

    private static void appendArray(
        final Object o,
        final StringBuilder str,
        Set<String> dejaVu,
        final Class<?> oClass
    ) {
        if (oClass == byte[].class) {
            str.append(Arrays.toString((byte[]) o));
        } else if (oClass == short[].class) {
            str.append(Arrays.toString((short[]) o));
        } else if (oClass == int[].class) {
            str.append(Arrays.toString((int[]) o));
        } else if (oClass == long[].class) {
            str.append(Arrays.toString((long[]) o));
        } else if (oClass == float[].class) {
            str.append(Arrays.toString((float[]) o));
        } else if (oClass == double[].class) {
            str.append(Arrays.toString((double[]) o));
        } else if (oClass == boolean[].class) {
            str.append(Arrays.toString((boolean[]) o));
        } else if (oClass == char[].class) {
            str.append(Arrays.toString((char[]) o));
        } else {
            if (dejaVu == null) {
                dejaVu = new HashSet<>();
            }
            // special handling of container Object[]
            final String id = identityToString(o);
            if (dejaVu.contains(id)) {
                str.append(RECURSION_PREFIX).append(id).append(RECURSION_SUFFIX);
            } else {
                dejaVu.add(id);
                final Object[] oArray = (Object[]) o;
                str.append('[');
                boolean first = true;
                for (final Object current : oArray) {
                    if (first) {
                        first = false;
                    } else {
                        str.append(", ");
                    }
                    recursiveDeepToString(current, str, new HashSet<>(dejaVu));
                }
                str.append(']');
            }
            //str.append(Arrays.deepToString((Object[]) o));
        }
    }

    private static void appendMap(final Object o, final StringBuilder str, Set<String> dejaVu) {
        // special handling of container Map
        if (dejaVu == null) {
            dejaVu = new HashSet<>();
        }
        final String id = identityToString(o);
        if (dejaVu.contains(id)) {
            str.append(RECURSION_PREFIX).append(id).append(RECURSION_SUFFIX);
        } else {
            dejaVu.add(id);
            final Map<?, ?> oMap = (Map<?, ?>) o;
            str.append('{');
            boolean isFirst = true;
            for (final Object o1 : oMap.entrySet()) {
                final Map.Entry<?, ?> current = (Map.Entry<?, ?>) o1;
                if (isFirst) {
                    isFirst = false;
                } else {
                    str.append(", ");
                }
                final Object key = current.getKey();
                final Object value = current.getValue();
                recursiveDeepToString(key, str, new HashSet<>(dejaVu));
                str.append('=');
                recursiveDeepToString(value, str, new HashSet<>(dejaVu));
            }
            str.append('}');
        }
    }

    private static void appendCollection(final Object o, final StringBuilder str, Set<String> dejaVu) {
        // special handling of container Collection
        if (dejaVu == null) {
            dejaVu = new HashSet<>();
        }
        final String id = identityToString(o);
        if (dejaVu.contains(id)) {
            str.append(RECURSION_PREFIX).append(id).append(RECURSION_SUFFIX);
        } else {
            dejaVu.add(id);
            final Collection<?> oCol = (Collection<?>) o;
            str.append('[');
            boolean isFirst = true;
            for (final Object anOCol : oCol) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    str.append(", ");
                }
                recursiveDeepToString(anOCol, str, new HashSet<>(dejaVu));
            }
            str.append(']');
        }
    }

    private static void tryObjectToString(final Object o, final StringBuilder str) {
        // it's just some other Object, we can only use toString().
        try {
            str.append(o.toString());
        } catch (final Throwable t) {
            handleErrorInObjectToString(o, str, t);
        }
    }

    private static void handleErrorInObjectToString(final Object o, final StringBuilder str, final Throwable t) {
        str.append(ERROR_PREFIX);
        str.append(identityToString(o));
        str.append(ERROR_SEPARATOR);
        final String msg = t.getMessage();
        final String className = t.getClass().getName();
        str.append(className);
        if (!className.equals(msg)) {
            str.append(ERROR_MSG_SEPARATOR);
            str.append(msg);
        }
        str.append(ERROR_SUFFIX);
    }

    /**
     * This method returns the same as if Object.toString() would not have been
     * overridden in obj.
     * <p>
     * Note that this isn't 100% secure as collisions can always happen with hash codes.
     * </p>
     * <p>
     * Copied from Object.hashCode():
     * </p>
     * <blockquote>
     * As much as is reasonably practical, the hashCode method defined by
     * class {@code Object} does return distinct integers for distinct
     * objects. (This is typically implemented by converting the internal
     * address of the object into an integer, but this implementation
     * technique is not required by the Java&#8482; programming language.)
     * </blockquote>
     *
     * @param obj the Object that is to be converted into an identity string.
     * @return the identity string as also defined in Object.toString()
     */
    static String identityToString(final Object obj) {
        if (obj == null) {
            return null;
        }
        return obj.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(obj));
    }

    private static final class StringBuilders {

        private static final Class<?> timeClass;

        static {
            Class<?> clazz;
            try {
                clazz = Class.forName("java.sql.Time");
            } catch (Exception ex) {
                clazz = null;
            }
            timeClass = clazz;
        }

        private StringBuilders() {
        }

        /**
         * Appends in the following format: double quoted value.
         *
         * @param sb a string builder
         * @param value a value
         * @return {@code "value"}
         */
        public static StringBuilder appendDqValue(final StringBuilder sb, final Object value) {
            return sb.append(Chars.DQUOTE).append(value).append(Chars.DQUOTE);
        }

        /**
         * Appends in the following format: key=double quoted value.
         *
         * @param sb a string builder
         * @param entry a map entry
         * @return {@code key="value"}
         */
        public static StringBuilder appendKeyDqValue(final StringBuilder sb, final Map.Entry<String, String> entry) {
            return appendKeyDqValue(sb, entry.getKey(), entry.getValue());
        }

        /**
         * Appends in the following format: key=double quoted value.
         *
         * @param sb a string builder
         * @param key a key
         * @param value a value
         * @return the specified StringBuilder
         */
        public static StringBuilder appendKeyDqValue(final StringBuilder sb, final String key, final Object value) {
            return sb.append(key).append(Chars.EQ).append(Chars.DQUOTE).append(value).append(Chars.DQUOTE);
        }

        /**
         * Appends a text representation of the specified object to the specified StringBuilder,
         * if possible without allocating temporary objects.
         *
         * @param stringBuilder the StringBuilder to append the value to
         * @param obj the object whose text representation to append to the StringBuilder
         */
        public static void appendValue(final StringBuilder stringBuilder, final Object obj) {
            if (!appendSpecificTypes(stringBuilder, obj)) {
                stringBuilder.append(obj);
            }
        }

        public static boolean appendSpecificTypes(final StringBuilder stringBuilder, final Object obj) {
            if (obj == null || obj instanceof String) {
                stringBuilder.append((String) obj);
            } else if (obj instanceof CharSequence) {
                stringBuilder.append((CharSequence) obj);
            } else if (obj instanceof Integer) { // LOG4J2-1437 unbox auto-boxed primitives to avoid calling toString()
                stringBuilder.append(((Integer) obj).intValue());
            } else if (obj instanceof Long) {
                stringBuilder.append(((Long) obj).longValue());
            } else if (obj instanceof Double) {
                stringBuilder.append(((Double) obj).doubleValue());
            } else if (obj instanceof Boolean) {
                stringBuilder.append(((Boolean) obj).booleanValue());
            } else if (obj instanceof Character) {
                stringBuilder.append(((Character) obj).charValue());
            } else if (obj instanceof Short) {
                stringBuilder.append(((Short) obj).shortValue());
            } else if (obj instanceof Float) {
                stringBuilder.append(((Float) obj).floatValue());
            } else if (obj instanceof Byte) {
                stringBuilder.append(((Byte) obj).byteValue());
            } else if (isTime(obj) || obj instanceof java.time.temporal.Temporal) {
                stringBuilder.append(obj);
            } else {
                return false;
            }
            return true;
        }

        /*
            Check to see if obj is an instance of java.sql.time without requiring the java.sql module.
         */
        private static boolean isTime(final Object obj) {
            return timeClass != null && timeClass.isAssignableFrom(obj.getClass());
        }


        /**
         * Returns true if the specified section of the left CharSequence equals the specified section of the right
         * CharSequence.
         *
         * @param left the left CharSequence
         * @param leftOffset start index in the left CharSequence
         * @param leftLength length of the section in the left CharSequence
         * @param right the right CharSequence to compare a section of
         * @param rightOffset start index in the right CharSequence
         * @param rightLength length of the section in the right CharSequence
         * @return true if equal, false otherwise
         */
        public static boolean equals(
            final CharSequence left,
            final int leftOffset,
            final int leftLength,
            final CharSequence right,
            final int rightOffset,
            final int rightLength
        ) {
            if (leftLength == rightLength) {
                for (int i = 0; i < rightLength; i++) {
                    if (left.charAt(i + leftOffset) != right.charAt(i + rightOffset)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        /**
         * Returns true if the specified section of the left CharSequence equals, ignoring case, the specified section of
         * the right CharSequence.
         *
         * @param left the left CharSequence
         * @param leftOffset start index in the left CharSequence
         * @param leftLength length of the section in the left CharSequence
         * @param right the right CharSequence to compare a section of
         * @param rightOffset start index in the right CharSequence
         * @param rightLength length of the section in the right CharSequence
         * @return true if equal ignoring case, false otherwise
         */
        public static boolean equalsIgnoreCase(
            final CharSequence left,
            final int leftOffset,
            final int leftLength,
            final CharSequence right,
            final int rightOffset,
            final int rightLength
        ) {
            if (leftLength == rightLength) {
                for (int i = 0; i < rightLength; i++) {
                    if (toLowerCase(left.charAt(i + leftOffset)) != toLowerCase(right.charAt(i + rightOffset))) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        /**
         * Ensures that the char[] array of the specified StringBuilder does not exceed the specified number of characters.
         * This method is useful to ensure that excessively long char[] arrays are not kept in memory forever.
         *
         * @param stringBuilder the StringBuilder to check
         * @param maxSize the maximum number of characters the StringBuilder is allowed to have
         * @since 2.9
         */
        public static void trimToMaxSize(final StringBuilder stringBuilder, final int maxSize) {
            if (stringBuilder != null && stringBuilder.capacity() > maxSize) {
                stringBuilder.setLength(maxSize);
                stringBuilder.trimToSize();
            }
        }

        public static void escapeJson(final StringBuilder toAppendTo, final int start) {
            int escapeCount = 0;
            for (int i = start; i < toAppendTo.length(); i++) {
                final char c = toAppendTo.charAt(i);
                switch (c) {
                    case '\b':
                    case '\t':
                    case '\f':
                    case '\n':
                    case '\r':
                    case '"':
                    case '\\':
                        escapeCount++;
                        break;
                    default:
                        if (Character.isISOControl(c)) {
                            escapeCount += 5;
                        }
                }
            }

            final int lastChar = toAppendTo.length() - 1;
            toAppendTo.setLength(toAppendTo.length() + escapeCount);
            int lastPos = toAppendTo.length() - 1;

            for (int i = lastChar; lastPos > i; i--) {
                final char c = toAppendTo.charAt(i);
                switch (c) {
                    case '\b':
                        lastPos = escapeAndDecrement(toAppendTo, lastPos, 'b');
                        break;

                    case '\t':
                        lastPos = escapeAndDecrement(toAppendTo, lastPos, 't');
                        break;

                    case '\f':
                        lastPos = escapeAndDecrement(toAppendTo, lastPos, 'f');
                        break;

                    case '\n':
                        lastPos = escapeAndDecrement(toAppendTo, lastPos, 'n');
                        break;

                    case '\r':
                        lastPos = escapeAndDecrement(toAppendTo, lastPos, 'r');
                        break;

                    case '"':
                    case '\\':
                        lastPos = escapeAndDecrement(toAppendTo, lastPos, c);
                        break;

                    default:
                        if (Character.isISOControl(c)) {
                            // all iso control characters are in U+00xx, JSON output format is "\\u00XX"
                            toAppendTo.setCharAt(lastPos--, Chars.getUpperCaseHex(c & 0xF));
                            toAppendTo.setCharAt(lastPos--, Chars.getUpperCaseHex((c & 0xF0) >> 4));
                            toAppendTo.setCharAt(lastPos--, '0');
                            toAppendTo.setCharAt(lastPos--, '0');
                            toAppendTo.setCharAt(lastPos--, 'u');
                            toAppendTo.setCharAt(lastPos--, '\\');
                        } else {
                            toAppendTo.setCharAt(lastPos, c);
                            lastPos--;
                        }
                }
            }
        }

        private static int escapeAndDecrement(final StringBuilder toAppendTo, int lastPos, final char c) {
            toAppendTo.setCharAt(lastPos--, c);
            toAppendTo.setCharAt(lastPos--, '\\');
            return lastPos;
        }

        public static void escapeXml(final StringBuilder toAppendTo, final int start) {
            int escapeCount = 0;
            for (int i = start; i < toAppendTo.length(); i++) {
                final char c = toAppendTo.charAt(i);
                switch (c) {
                    case '&':
                        escapeCount += 4;
                        break;
                    case '<':
                    case '>':
                        escapeCount += 3;
                        break;
                    case '"':
                    case '\'':
                        escapeCount += 5;
                }
            }

            final int lastChar = toAppendTo.length() - 1;
            toAppendTo.setLength(toAppendTo.length() + escapeCount);
            int lastPos = toAppendTo.length() - 1;

            for (int i = lastChar; lastPos > i; i--) {
                final char c = toAppendTo.charAt(i);
                switch (c) {
                    case '&':
                        toAppendTo.setCharAt(lastPos--, ';');
                        toAppendTo.setCharAt(lastPos--, 'p');
                        toAppendTo.setCharAt(lastPos--, 'm');
                        toAppendTo.setCharAt(lastPos--, 'a');
                        toAppendTo.setCharAt(lastPos--, '&');
                        break;
                    case '<':
                        toAppendTo.setCharAt(lastPos--, ';');
                        toAppendTo.setCharAt(lastPos--, 't');
                        toAppendTo.setCharAt(lastPos--, 'l');
                        toAppendTo.setCharAt(lastPos--, '&');
                        break;
                    case '>':
                        toAppendTo.setCharAt(lastPos--, ';');
                        toAppendTo.setCharAt(lastPos--, 't');
                        toAppendTo.setCharAt(lastPos--, 'g');
                        toAppendTo.setCharAt(lastPos--, '&');
                        break;
                    case '"':
                        toAppendTo.setCharAt(lastPos--, ';');
                        toAppendTo.setCharAt(lastPos--, 't');
                        toAppendTo.setCharAt(lastPos--, 'o');
                        toAppendTo.setCharAt(lastPos--, 'u');
                        toAppendTo.setCharAt(lastPos--, 'q');
                        toAppendTo.setCharAt(lastPos--, '&');
                        break;
                    case '\'':
                        toAppendTo.setCharAt(lastPos--, ';');
                        toAppendTo.setCharAt(lastPos--, 's');
                        toAppendTo.setCharAt(lastPos--, 'o');
                        toAppendTo.setCharAt(lastPos--, 'p');
                        toAppendTo.setCharAt(lastPos--, 'a');
                        toAppendTo.setCharAt(lastPos--, '&');
                        break;
                    default:
                        toAppendTo.setCharAt(lastPos--, c);
                }
            }
        }
    }

}
