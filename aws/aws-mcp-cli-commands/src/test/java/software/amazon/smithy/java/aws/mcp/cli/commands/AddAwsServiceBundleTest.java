package software.amazon.smithy.java.aws.mcp.cli.commands;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.mcp.cli.APIStandardTerminology;
import software.amazon.smithy.java.aws.servicebundle.bundler.AwsServiceBundler;
import software.amazon.smithy.model.loader.ModelAssembler;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.io.InputStreamReader;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class AddAwsServiceBundleTest {

    @Test
    public void testReadAndWritePrefixes() {
        var bundler = AwsServiceBundler.builder()
                .serviceName("dynamodb")
                .resolver(serviceName -> getModel("dynamodb-2012-08-10.json"))
                .allowedPrefixes(APIStandardTerminology.readOnlyApiPrefixes)
                .blockedPrefixes(APIStandardTerminology.writeApiPrefixes)
                .build();
        ;
        var bundle = bundler.bundle();
        var bundleModel = new ModelAssembler().addUnparsedModel("model.json", bundle.getModel())
                .disableValidation()
                .putProperty(ModelAssembler.ALLOW_UNKNOWN_TRAITS, true)
                .assemble()
                .unwrap();

        var readOnlyOperations = Set.of("GetResourcePolicy", "DescribeImport", "ListTables", "GetItem", "BatchGetItem");
        var writeOperations = Set.of("UpdateItem", "CreateTable", "WriteItem", "BatchWriteItem", "DeleteTable");

        assertThat(bundleModel.getOperationShapes())
                .filteredOn(o -> readOnlyOperations.contains(o.getId().getName()))
                .hasSize(readOnlyOperations.size());

        assertThat(bundleModel.getOperationShapes())
                .filteredOn(o -> writeOperations.contains(o.getId().getName()))
                .isEmpty();
    }

    private static String getModel(String path) {
        try (var stream = new InputStreamReader(Objects
                .requireNonNull(AddAwsServiceBundleTest.class.getResourceAsStream(path), "No model named " + path))) {
            var builder = new StringBuilder();
            var buffer = new char[1024];
            while (true) {
                var read = stream.read(buffer);
                if (read == -1) {
                    break;
                }
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
