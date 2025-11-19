package software.amazon.smithy.java.json;

import java.util.List;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

public class ErrorTypeSanitizerTest {
    private static final String SIMPLE_TYPE = "FooError";
    private static final String TYPE_WITH_URI = "FooError:http://amazon.com/smithy/com.amazon.smithy.validate/";
    private static final String TYPE_WITH_NAMESPACE = "aws.protocoltests.restjson#FooError";
    private static final String TYPE_WITH_NAMESPACE_AND_URI = "aws.protocoltests.restjson#FooError:http://amazon.com/smithy/com.amazon.smithy.validate/";
    @Test
    public void testRemoveUri() {
        String expected = "FooError";
        String expectedWithNamespace = "aws.protocoltests.restjson#FooError";
        assertEquals(expected, ErrorTypeSanitizer.removeUri(SIMPLE_TYPE));
        assertEquals(expected, ErrorTypeSanitizer.removeUri(TYPE_WITH_URI));
        assertEquals(expectedWithNamespace, ErrorTypeSanitizer.removeUri(TYPE_WITH_NAMESPACE));
        assertEquals(expectedWithNamespace, ErrorTypeSanitizer.removeUri(TYPE_WITH_NAMESPACE_AND_URI));
    }

    @Test
    public void testRemoveNameSpaceAndUri() {
        String expected = "FooError";
        assertEquals(expected, ErrorTypeSanitizer.removeNamespaceAndUri(SIMPLE_TYPE));
        assertEquals(expected, ErrorTypeSanitizer.removeNamespaceAndUri(TYPE_WITH_URI));
        assertEquals(expected, ErrorTypeSanitizer.removeNamespaceAndUri(TYPE_WITH_NAMESPACE));
        assertEquals(expected, ErrorTypeSanitizer.removeNamespaceAndUri(TYPE_WITH_NAMESPACE_AND_URI));
    }
}
