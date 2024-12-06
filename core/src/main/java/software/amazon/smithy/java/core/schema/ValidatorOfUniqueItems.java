/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.document.DocumentParser;

/**
 * Receives list values, turns them into documents, and makes sure they are all unique.
 */
final class ValidatorOfUniqueItems extends InterceptingSerializer {
    private final Schema container;
    private final DocumentParser parser = new DocumentParser();
    private final Set<Document> values = new HashSet<>();
    private final Validator.ShapeValidator validator;
    private int position = 0;

    static <T> void validate(
        Schema container,
        T state,
        BiConsumer<T, ShapeSerializer> consumer,
        Validator.ShapeValidator validator
    ) {
        consumer.accept(state, new ValidatorOfUniqueItems(container, validator));
    }

    private ValidatorOfUniqueItems(Schema container, Validator.ShapeValidator validator) {
        this.container = container;
        this.validator = validator;
    }

    @Override
    protected ShapeSerializer before(Schema schema) {
        return parser;
    }

    @Override
    protected void after(Schema schema) {
        if (!values.add(parser.getResult())) {
            validator.swapPath(position);
            validator.addError(new ValidationError.UniqueItemConflict(validator.createPath(), position, container));
        }
        position++;
    }
}
