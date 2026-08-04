/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient.compiler;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Extracts the string keys from a {@code Map.of("k1", v1, "k2", v2, ...)} or {@code Map.ofEntries(...)}-free literal
 * so input members can be checked at compile time.
 */
final class MapLiteral {

    private MapLiteral() {}

    /**
     * @return the list of literal string keys if {@code expr} is a fully-resolvable {@code Map.of(...)} call with an
     *         even argument count and constant keys; otherwise {@code null} (meaning "not a checkable map literal").
     */
    static List<String> keysOf(ExpressionTree expr, Function<ExpressionTree, String> constantResolver) {
        if (!(expr instanceof MethodInvocationTree call)
                || !(call.getMethodSelect() instanceof MemberSelectTree select)
                || !select.getIdentifier().contentEquals("of")
                || !select.getExpression().toString().endsWith("Map")) {
            return null;
        }
        var args = call.getArguments();
        if (args.isEmpty() || args.size() % 2 != 0) {
            return null; // Map.of() empty is fine (no keys to check) but returns empty below; odd => not Map.of(k,v..).
        }
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < args.size(); i += 2) {
            String key = constantResolver.apply(args.get(i));
            if (key == null) {
                return null; // a non-constant key: give up on key checking rather than half-check.
            }
            keys.add(key);
        }
        return keys;
    }
}
