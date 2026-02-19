
package software.amazon.smithy.java.example.standalone.model;

import software.amazon.smithy.utils.SmithyGenerated;

/**
 * A simple interface mixin with basic members
 */
@SmithyGenerated
public interface HasName {
    String getName();

    int getId();

    interface Builder<B extends Builder<B>> {
        B name(String name);

        B id(int id);

    }
}

