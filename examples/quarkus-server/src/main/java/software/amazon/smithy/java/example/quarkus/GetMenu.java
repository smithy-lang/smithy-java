/*
 * Example file license header.
 * File header line two
 */

package software.amazon.smithy.java.example.quarkus;

import java.util.List;
import software.amazon.smithy.java.example.quarkus.model.CoffeeItem;
import software.amazon.smithy.java.example.quarkus.model.CoffeeType;
import software.amazon.smithy.java.example.quarkus.model.GetMenuInput;
import software.amazon.smithy.java.example.quarkus.model.GetMenuOutput;
import software.amazon.smithy.java.example.quarkus.service.GetMenuOperation;
import software.amazon.smithy.java.server.RequestContext;

/**
 * Returns the menu for the coffee shop.
 */
final class GetMenu implements GetMenuOperation {
    private static final List<CoffeeItem> MENU = List.of(
            CoffeeItem.builder()
                    .type(CoffeeType.DRIP)
                    .description("""
                            A clean-bodied, rounder, and more simplistic flavour profile.
                            Often praised for mellow and less intense notes.
                            Far less concentrated than espresso.
                            """)
                    .build(),
            CoffeeItem.builder()
                    .type(CoffeeType.POUR_OVER)
                    .description("""
                            Similar to drip coffee, but with a process that brings out more subtle nuances in flavor.
                            More concentrated than drip, but less than espresso.
                            """)
                    .build(),
            CoffeeItem.builder()
                    .type(CoffeeType.LATTE)
                    .description("""
                            A creamier, milk-based drink made with espresso.
                            A subtle coffee taste, with smooth texture.
                            High milk-to-coffee ratio.
                            """)
                    .build(),
            CoffeeItem.builder()
                    .type(CoffeeType.ESPRESSO)
                    .description("""
                            A highly concentrated form of coffee, brewed under high pressure.
                            Syrupy, thick liquid in a small serving size.
                            Full bodied and intensely aromatic.
                            """)
                    .build());

    @Override
    public GetMenuOutput getMenu(GetMenuInput input, RequestContext context) {
        return GetMenuOutput.builder().items(MENU).build();
    }
}
