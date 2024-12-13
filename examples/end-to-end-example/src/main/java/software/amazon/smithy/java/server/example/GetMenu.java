/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.example;

import java.util.List;
import software.amazon.smithy.java.example.model.CoffeeItem;
import software.amazon.smithy.java.example.model.CoffeeType;
import software.amazon.smithy.java.example.model.GetMenuInput;
import software.amazon.smithy.java.example.model.GetMenuOutput;
import software.amazon.smithy.java.example.service.GetMenuOperation;
import software.amazon.smithy.java.server.RequestContext;

/**
 * Returns the menu for the coffee shop
 */
final class GetMenu implements GetMenuOperation {
    private static final List<CoffeeItem> MENU = List.of(
        CoffeeItem.builder()
            .typeMember(CoffeeType.DRIP)
            .description("""
                A clean-bodied, rounder, and more simplistic flavour profile.
                Often praised for mellow and less intense notes.
                Far less concentrated than espresso.
                """)
            .build(),
        CoffeeItem.builder()
            .typeMember(CoffeeType.POUR_OVER)
            .description("""
                Similar to drip coffee, but with a process that brings out more subtle nuances in flavor.
                More concentrated than drip, but less than espresso.
                """)
            .build(),
        CoffeeItem.builder()
            .typeMember(CoffeeType.LATTE)
            .description("""
                A creamier, milk-based drink made with espresso.
                A subtle coffee taste, with smooth texture.
                High milk-to-coffee ratio.
                """)
            .build(),
        CoffeeItem.builder()
            .typeMember(CoffeeType.ESPRESSO)
            .description("""
                A highly concentrated form of coffee, brewed under high pressure.
                Syrupy, thick liquid in a small serving size.
                Full bodied and intensely aromatic.
                """)
            .build()
    );

    @Override
    public GetMenuOutput getMenu(GetMenuInput input, RequestContext context) {
        return GetMenuOutput.builder().items(MENU).build();
    }
}
