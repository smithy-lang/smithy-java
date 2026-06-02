/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ContextTest {

    static final Context.Key<String> FOO = Context.key("Foo");
    static final Context.Key<Integer> BAR = Context.key("Bar");
    static final Context.Key<Boolean> BAZ = Context.key("Baz");

    static final Context.Key<Set<String>> SAD_SET = Context.key("Sad set");
    static final Context.Key<Set<String>> HAPPY_SET = Context.key("Happy set", HashSet::new);

    @Test
    public void getTypedValue() {
        var context = Context.create();
        context.put(FOO, "Hi");
        context.put(BAR, 1);

        assertThat(context.get(FOO), equalTo("Hi"));
        assertThat(context.expect(FOO), equalTo("Hi"));

        assertThat(context.get(BAR), is(1));
        assertThat(context.expect(BAR), is(1));
    }

    @Test
    public void returnsNullWhenNotFound() {
        var context = Context.create();

        assertThat(context.get(FOO), nullValue());
    }

    @Test
    public void throwsWhenExpectedAndNotFound() {
        var context = Context.create();

        assertThrows(NullPointerException.class, () -> context.expect(FOO));
    }

    @Test
    public void computesAndSets() {
        var context = Context.create();

        assertThat(context.computeIfAbsent(FOO, key -> "hi"), equalTo("hi"));
        assertThat(context.computeIfAbsent(FOO, key -> "bye"), equalTo("hi"));
    }

    @Test
    public void unmodifiableView() {
        var context = Context.create();
        context.put(FOO, "hi");
        context.put(BAR, 1);

        Context unmodifiableView = Context.unmodifiableView(context);

        assertThat(unmodifiableView.get(FOO), equalTo("hi"));
        assertThat(unmodifiableView.get(BAR), is(1));

        assertThrows(UnsupportedOperationException.class, () -> unmodifiableView.put(FOO, "bye"));
        assertThrows(UnsupportedOperationException.class, () -> unmodifiableView.putIfAbsent(FOO, "bye"));
        assertThrows(UnsupportedOperationException.class, () -> unmodifiableView.computeIfAbsent(FOO, key -> "bye"));
        assertThrows(UnsupportedOperationException.class, () -> {
            var ctx = Context.create();
            ctx.put(FOO, "bye");
            ctx.copyTo(unmodifiableView);
        });

        Context unmodifiableView2 = Context.unmodifiableView(unmodifiableView);
        assertThat(unmodifiableView2, sameInstance(unmodifiableView));

        context.put(FOO, "bye");
        context.put(BAZ, true);

        assertThat(unmodifiableView.get(FOO), equalTo("bye"));
        assertThat(unmodifiableView.get(BAZ), equalTo(true));
    }

    @Test
    public void unmodifiableCopy() {
        var context = Context.create();
        context.put(FOO, "hi");
        context.put(BAR, 1);

        Context unmodifiableCopy = Context.unmodifiableCopy(context);

        assertThat(unmodifiableCopy.get(FOO), equalTo("hi"));
        assertThat(unmodifiableCopy.get(BAR), is(1));

        assertThrows(UnsupportedOperationException.class, () -> unmodifiableCopy.put(FOO, "bye"));
        assertThrows(UnsupportedOperationException.class, () -> unmodifiableCopy.putIfAbsent(FOO, "bye"));
        assertThrows(UnsupportedOperationException.class, () -> unmodifiableCopy.computeIfAbsent(FOO, key -> "bye"));
        assertThrows(UnsupportedOperationException.class, () -> {
            var ctx = Context.create();
            ctx.put(FOO, "bye");
            ctx.copyTo(unmodifiableCopy);
        });

        Context unmodifiableCopy2 = Context.unmodifiableCopy(unmodifiableCopy);
        assertThat(unmodifiableCopy2, not(sameInstance(unmodifiableCopy)));

        context.put(FOO, "bye");
        context.put(BAZ, true);

        assertThat(unmodifiableCopy2.get(FOO), equalTo("hi"));
        assertThat(unmodifiableCopy2.get(BAZ), nullValue());
    }

    @Test
    public void modifiableCopy() {
        var context = Context.create();
        context.put(FOO, "hi");
        context.put(BAR, 1);

        Context modifiableCopy = Context.modifiableCopy(context);

        assertThat(modifiableCopy.get(FOO), equalTo("hi"));
        assertThat(modifiableCopy.get(BAR), is(1));

        modifiableCopy.put(FOO, "bye");
        modifiableCopy.put(BAZ, true);

        assertThat(modifiableCopy.get(FOO), equalTo("bye"));
        assertThat(context.get(FOO), equalTo("hi"));
        assertThat(modifiableCopy.get(BAZ), equalTo(true));
        assertThat(context.get(BAZ), nullValue());

        context.put(FOO, "hello");
        context.put(BAZ, false);

        assertThat(modifiableCopy.get(FOO), equalTo("bye"));
        assertThat(modifiableCopy.get(BAZ), equalTo(true));
    }

    @Test
    public void putAll() {
        var context = Context.create();
        context.put(FOO, "hi");
        context.put(BAR, 1);

        var overrides = Context.create();
        overrides.put(FOO, "bye");
        overrides.put(BAZ, true);

        overrides.copyTo(context);

        assertThat(context.get(FOO), equalTo("bye"));
        assertThat(context.get(BAR), is(1));
        assertThat(context.get(BAZ), equalTo(true));
    }

    @Test
    public void putAllUnmodifiable() {
        var context = Context.create();
        context.put(FOO, "hi");
        context.put(BAR, 1);

        var overrides = Context.create();
        overrides.put(FOO, "bye");
        overrides.put(BAZ, true);

        var unmodifiableOverrides = Context.unmodifiableView(overrides);
        unmodifiableOverrides.copyTo(context);

        assertThat(context.get(FOO), equalTo("bye"));
        assertThat(context.get(BAR), is(1));
        assertThat(context.get(BAZ), equalTo(true));
    }

    @Test
    public void dynamicKeyAddition() {
        var context = Context.create();
        context.put(FOO, "hi");

        // Create a new key dynamically.
        Context.Key<Integer> key = Context.key("dynamic-key");
        context.put(key, 10);

        assertThat(context.get(FOO), equalTo("hi"));
        assertThat(context.get(key), equalTo(10));
    }

    @Test
    public void putIfAbsent() {
        var ctx = Context.create();

        ctx.putIfAbsent(FOO, "hi");
        assertThat(ctx.get(FOO), equalTo("hi"));

        ctx.putIfAbsent(FOO, "hi2");
        assertThat(ctx.get(FOO), equalTo("hi"));
    }

    @Test
    public void usesChunkedArrayStorage() {
        var context = Context.create();
        assertThat(context, instanceOf(ChunkedArrayStorageContext.class));
    }

    @Test
    public void perCallOverlayReadsThroughToParent() {
        var parent = Context.create();
        parent.put(FOO, "hi");
        parent.put(BAR, 1);

        var overlay = Context.perCallOverlay(Context.unmodifiableView(parent));

        assertThat(overlay, instanceOf(OverlayContext.class));
        // Reads miss the (empty) overlay and fall through to the parent.
        assertThat(overlay.get(FOO), equalTo("hi"));
        assertThat(overlay.get(BAR), is(1));
        assertThat(overlay.get(BAZ), nullValue());
    }

    @Test
    public void perCallOverlayWritesShadowParentWithoutMutatingIt() {
        var parent = Context.create();
        parent.put(FOO, "hi");
        parent.put(BAR, 1);

        var overlay = Context.perCallOverlay(Context.unmodifiableView(parent));
        overlay.put(FOO, "bye"); // shadow an existing parent key
        overlay.put(BAZ, true); // a key the parent doesn't have

        // Overlay sees its own writes...
        assertThat(overlay.get(FOO), equalTo("bye"));
        assertThat(overlay.get(BAZ), equalTo(true));
        // ...and still reads through for untouched keys.
        assertThat(overlay.get(BAR), is(1));

        // The parent is never mutated by overlay writes.
        assertThat(parent.get(FOO), equalTo("hi"));
        assertThat(parent.get(BAZ), nullValue());
    }

    @Test
    public void perCallOverlayPutIfAbsentAndComputeIfAbsentSeeParent() {
        var parent = Context.create();
        parent.put(FOO, "hi");
        var overlay = Context.perCallOverlay(Context.unmodifiableView(parent));

        // putIfAbsent must observe the parent's value through the overlay and not overwrite it.
        overlay.putIfAbsent(FOO, "bye");
        assertThat(overlay.get(FOO), equalTo("hi"));

        // computeIfAbsent only computes on a true miss (parent + overlay).
        assertThat(overlay.computeIfAbsent(FOO, k -> "computed"), equalTo("hi"));
        assertThat(overlay.computeIfAbsent(BAZ, k -> Boolean.TRUE), equalTo(true));
        assertThat(overlay.get(BAZ), equalTo(true));
        assertThat(parent.get(BAZ), nullValue());
    }

    @Test
    public void perCallOverlayCopyToFlattensParentThenOverlay() {
        var parent = Context.create();
        parent.put(FOO, "hi");
        parent.put(BAR, 1);

        var overlay = Context.perCallOverlay(Context.unmodifiableView(parent));
        overlay.put(FOO, "bye"); // overlay shadows FOO
        overlay.put(BAZ, true);

        var target = Context.create();
        overlay.copyTo(target);

        // Parent contributes BAR; overlay wins for FOO and adds BAZ.
        assertThat(target.get(FOO), equalTo("bye"));
        assertThat(target.get(BAR), is(1));
        assertThat(target.get(BAZ), equalTo(true));
    }

    @Test
    public void perCallOverlayCopyToCopiesMutableValuesForIsolation() {
        var parent = Context.create();
        parent.put(HAPPY_SET, new HashSet<>(Set.of("a")));

        var overlay = Context.perCallOverlay(Context.unmodifiableView(parent));
        // Overlay writes its own mutable set (the pattern the request pipeline uses for FEATURE_IDS).
        overlay.put(HAPPY_SET, new HashSet<>(Set.of("x")));

        var target = Context.create();
        overlay.copyTo(target);
        target.get(HAPPY_SET).add("y");

        // HAPPY_SET uses a copying key, so the target's set is independent of the overlay's.
        assertThat(overlay.get(HAPPY_SET), containsInAnyOrder("x"));
        assertThat(target.get(HAPPY_SET), containsInAnyOrder("x", "y"));
    }

    @Test
    public void perCallOverlayCopyToRejectsUnmodifiableTarget() {
        var overlay = Context.perCallOverlay(Context.empty());
        var unmodifiable = Context.unmodifiableView(Context.create());
        assertThrows(UnsupportedOperationException.class, () -> overlay.copyTo(unmodifiable));
    }

    @Test
    public void canCopyKeyValues() {
        var ctx = Context.create();
        ctx.put(SAD_SET, new HashSet<>());
        ctx.put(HAPPY_SET, new HashSet<>());
        ctx.get(SAD_SET).add("a");
        ctx.get(HAPPY_SET).add("a");

        var copy = Context.create();
        ctx.copyTo(copy);
        copy.get(SAD_SET).add("b");
        copy.get(HAPPY_SET).add("b");

        // Sad set wasn't copied, so a mutation to the copied context affected the original.
        assertThat(ctx.get(SAD_SET), containsInAnyOrder("a", "b"));
        // Happy set was copied, so it maintained its independence.
        assertThat(ctx.get(HAPPY_SET), containsInAnyOrder("a"));

        assertThat(copy.get(SAD_SET), containsInAnyOrder("a", "b"));
        // happy set also copied the original values from ctx when the copy was made.
        assertThat(copy.get(HAPPY_SET), containsInAnyOrder("a", "b"));
    }
}
