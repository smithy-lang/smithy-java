package software.amazon.smithy.java.cli;

public enum ProtocolType {
    AWS_JSON("awsjson"),
    RPC_V2_CBOR("rpcv2-cbor"),
    REST_JSON("restjson"),
    REST_XML("restxml");

    private final String value;

    ProtocolType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProtocolType fromString(String text) {
        for (ProtocolType type : ProtocolType.values()) {
            if (type.value.equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported protocol type: " + text);
    }
}