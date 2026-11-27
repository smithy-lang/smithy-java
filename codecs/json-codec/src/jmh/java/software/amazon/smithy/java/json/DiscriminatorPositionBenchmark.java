/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.json.jackson.JacksonJsonSerdeProvider;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DiscriminatedTrait;

/**
 * Measures how the position of the discriminator affects decoding a {@code @discriminated} union on both JSON
 * backends ({@code backend}), with a variant large enough to show what each decode strategy keeps or throws away.
 *
 * <pre>
 * {@literal @}discriminated("type")
 * union Event      { order: OrderData, cancel: CancelData }
 * union PlainEvent { order: OrderData, cancel: CancelData }
 * structure OrderData {
 *     id: String, quantity: Integer, total: Double, note: String, address: Address, items: ItemList, tags: TagList
 * }
 * structure Address   { street: String, city: String, zip: String }
 * structure Item      { sku: String, count: Integer, price: Double }
 * structure CancelData { id: String }
 * </pre>
 *
 * <p>{@code position} places the discriminator in the {@code order} object:
 * <ul>
 *     <li>{@code first}: before every field (the fast path).</li>
 *     <li>{@code second}: after {@code id}, one scalar in front of it.</li>
 *     <li>{@code last}: after every field, including the nested ones.</li>
 *     <li>{@code plain}: no discriminator, the standard tagged encoding of the same value, as a reference.</li>
 * </ul>
 * {@code items} sets the length of the nested list, which sets how much nested content can sit in front of the
 * discriminator. {@code content} selects what that list holds: {@code numeric} fills {@code items} with structs
 * of a string and two numbers, {@code text} fills {@code tags} with 40-character strings instead, so the cost
 * of skipping numbers and strings can be told apart.
 */
@State(Scope.Benchmark)
public class DiscriminatorPositionBenchmark {

    private static final String NS = "smithy.benchmark#";

    private static final Schema ITEM_LIST;
    private static final Schema TAG_LIST;
    private static final Schema EVENT;
    private static final Schema PLAIN_EVENT;

    static {
        var address = Schema.structureBuilder(ShapeId.from(NS + "Address"));
        address.putMember("street", PreludeSchemas.STRING);
        address.putMember("city", PreludeSchemas.STRING);
        address.putMember("zip", PreludeSchemas.STRING);

        var item = Schema.structureBuilder(ShapeId.from(NS + "Item"));
        item.putMember("sku", PreludeSchemas.STRING);
        item.putMember("count", PreludeSchemas.INTEGER);
        item.putMember("price", PreludeSchemas.DOUBLE);

        var itemList = Schema.listBuilder(ShapeId.from(NS + "ItemList"));
        itemList.putMember("member", item);

        var tagList = Schema.listBuilder(ShapeId.from(NS + "TagList"));
        tagList.putMember("member", PreludeSchemas.STRING);

        var order = Schema.structureBuilder(ShapeId.from(NS + "OrderData"));
        order.putMember("id", PreludeSchemas.STRING);
        order.putMember("quantity", PreludeSchemas.INTEGER);
        order.putMember("total", PreludeSchemas.DOUBLE);
        order.putMember("note", PreludeSchemas.STRING);
        order.putMember("address", address);
        order.putMember("items", itemList);
        order.putMember("tags", tagList);

        var cancel = Schema.structureBuilder(ShapeId.from(NS + "CancelData"));
        cancel.putMember("id", PreludeSchemas.STRING);

        var event = Schema.unionBuilder(ShapeId.from(NS + "Event"), new DiscriminatedTrait("type"));
        event.putMember("order", order);
        event.putMember("cancel", cancel);

        var plainEvent = Schema.unionBuilder(ShapeId.from(NS + "PlainEvent"));
        plainEvent.putMember("order", order);
        plainEvent.putMember("cancel", cancel);

        ITEM_LIST = itemList.build();
        TAG_LIST = tagList.build();
        EVENT = event.build();
        PLAIN_EVENT = plainEvent.build();
    }

    @Param({"first", "second", "last", "plain"})
    public String position;

    @Param({"3", "30"})
    public int items;

    @Param({"numeric", "text"})
    public String content;

    @Param({"smithy", "jackson"})
    public String backend;

    private JsonCodec codec;
    private Schema unionSchema;
    private byte[] payload;

    @Setup
    public void setup() {
        codec = JsonCodec.builder()
                .overrideSerdeProvider(
                        backend.equals("jackson") ? new JacksonJsonSerdeProvider() : new SmithyJsonSerdeProvider())
                .build();
        unionSchema = position.equals("plain") ? PLAIN_EVENT : EVENT;
        payload = payload(position, items, content.equals("text")).getBytes(StandardCharsets.UTF_8);
        // Fail fast if a payload does not decode to the expected value.
        if (!(decode() instanceof Order order) || order.address() == null
                || (content.equals("text") ? order.tags().size() : order.items().size()) != items) {
            throw new IllegalStateException("Bad decode for position " + position);
        }
    }

    static String payload(String position, int items, boolean text) {
        var itemsJson = new StringBuilder("[");
        var tagsJson = new StringBuilder("[");
        for (int i = 0; text && i < items; i++) {
            if (i > 0) {
                tagsJson.append(',');
            }
            tagsJson.append("\"tag-").append(1000 + i).append("-lorem-ipsum-dolor-sit-amet-consectetur\"");
        }
        tagsJson.append(']');
        for (int i = 0; !text && i < items; i++) {
            if (i > 0) {
                itemsJson.append(',');
            }
            itemsJson.append("{\"sku\":\"SKU-")
                    .append(1000 + i)
                    .append("\",\"count\":")
                    .append(i + 1)
                    .append(",\"price\":")
                    .append(9.99 + i)
                    .append('}');
        }
        itemsJson.append(']');

        var fields = new ArrayList<String>(List.of(
                "\"id\":\"order-8f14e45fceea167a\"",
                "\"quantity\":42",
                "\"total\":1234.56",
                "\"note\":\"Leave the package at the side door, please.\"",
                "\"address\":{\"street\":\"410 Terry Ave N\",\"city\":\"Seattle\",\"zip\":\"98109\"}",
                "\"items\":" + itemsJson,
                "\"tags\":" + tagsJson));
        String discriminator = "\"type\":\"order\"";
        switch (position) {
            case "first" -> fields.add(0, discriminator);
            case "second" -> fields.add(1, discriminator);
            case "last" -> fields.add(discriminator);
            case "plain" -> {
                return "{\"order\":{" + String.join(",", fields) + "}}";
            }
            default -> throw new IllegalArgumentException(position);
        }
        return "{" + String.join(",", fields) + "}";
    }

    @Benchmark
    public void decode(Blackhole bh) {
        bh.consume(decode());
    }

    private Object decode() {
        var builder = new EventBuilder();
        try (var de = codec.createDeserializer(payload)) {
            de.readStruct(unionSchema, builder, EventBuilder::accept);
        }
        return builder.build();
    }

    // ----- Values and builders mirroring generated code: read a member fully, then set it. -----

    record Order(
            String id,
            int quantity,
            double total,
            String note,
            Address address,
            List<Item> items,
            List<String> tags) {}

    record Address(String street, String city, String zip) {}

    record Item(String sku, int count, double price) {}

    record Cancel(String id) {}

    static final class EventBuilder {
        private Object value;

        Object build() {
            return value;
        }

        static void accept(EventBuilder state, Schema member, ShapeDeserializer de) {
            switch (member.memberIndex()) {
                case 0 -> {
                    var order = new OrderBuilder();
                    de.readStruct(member, order, OrderBuilder::accept);
                    state.value = order.build();
                }
                case 1 -> {
                    var cancel = new String[1];
                    de.readStruct(member, cancel, (s, m, d) -> s[0] = d.readString(m));
                    state.value = new Cancel(cancel[0]);
                }
                default -> throw new IllegalArgumentException(member.memberName());
            }
        }
    }

    static final class OrderBuilder {
        private String id;
        private int quantity;
        private double total;
        private String note;
        private Address address;
        private final List<Item> items = new ArrayList<>();
        private final List<String> tags = new ArrayList<>();

        Order build() {
            return new Order(id, quantity, total, note, address, items, tags);
        }

        static void accept(OrderBuilder state, Schema member, ShapeDeserializer de) {
            switch (member.memberIndex()) {
                case 0 -> state.id = de.readString(member);
                case 1 -> state.quantity = de.readInteger(member);
                case 2 -> state.total = de.readDouble(member);
                case 3 -> state.note = de.readString(member);
                case 4 -> {
                    var address = new AddressBuilder();
                    de.readStruct(member, address, AddressBuilder::accept);
                    state.address = address.build();
                }
                case 5 -> de.readList(member, state.items, (list, d) -> {
                    var item = new ItemBuilder();
                    d.readStruct(ITEM_LIST.listMember(), item, ItemBuilder::accept);
                    list.add(item.build());
                });
                case 6 -> de.readList(member, state.tags, (list, d) -> list.add(d.readString(TAG_LIST.listMember())));
                default -> throw new IllegalArgumentException(member.memberName());
            }
        }
    }

    static final class AddressBuilder {
        private String street;
        private String city;
        private String zip;

        Address build() {
            return new Address(street, city, zip);
        }

        static void accept(AddressBuilder state, Schema member, ShapeDeserializer de) {
            switch (member.memberIndex()) {
                case 0 -> state.street = de.readString(member);
                case 1 -> state.city = de.readString(member);
                case 2 -> state.zip = de.readString(member);
                default -> throw new IllegalArgumentException(member.memberName());
            }
        }
    }

    static final class ItemBuilder {
        private String sku;
        private int count;
        private double price;

        Item build() {
            return new Item(sku, count, price);
        }

        static void accept(ItemBuilder state, Schema member, ShapeDeserializer de) {
            switch (member.memberIndex()) {
                case 0 -> state.sku = de.readString(member);
                case 1 -> state.count = de.readInteger(member);
                case 2 -> state.price = de.readDouble(member);
                default -> throw new IllegalArgumentException(member.memberName());
            }
        }
    }
}
