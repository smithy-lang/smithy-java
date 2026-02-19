
package software.amazon.smithy.java.example.standalone.model;

import software.amazon.smithy.utils.SmithyGenerated;

/**
 * An interface mixin that extends another interface mixin
 */
@SmithyGenerated
public interface HasFullName extends HasName {
    String getLastName();

    interface Builder<B extends Builder<B>> extends HasName.Builder<B> {
        B lastName(String lastName);

    }
}

