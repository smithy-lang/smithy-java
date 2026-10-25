/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockQueue;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A pooled HTTP connection is only returned when the response body is consumed or discarded, so every
 * path that skips deserialization must still discard the body.
 */
class QueryResponseBodyReleaseTest {

    private static final ShapeId METRICS = ShapeId.from("smithy.example#Metrics");
    private static final ShapeId INSTANCES = ShapeId.from("smithy.example#Instances");
    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", """
                    $version: "2"
                    namespace smithy.example

                    use aws.protocols#awsQuery
                    use aws.protocols#ec2Query

                    @awsQuery
                    @xmlNamespace(uri: "https://example.com")
                    service Metrics {
                        version: "2010-08-01"
                        operations: [PutMetric, GetMetric]
                    }

                    @ec2Query
                    @xmlNamespace(uri: "https://example.com")
                    service Instances {
                        version: "2016-11-15"
                        operations: [PutMetric, GetMetric]
                    }

                    operation PutMetric {
                        input := {
                            name: String
                        }
                        output: Unit
                    }

                    operation GetMetric {
                        input := {
                            name: String
                        }
                        output := {
                            value: Integer
                        }
                    }
                    """)
            .discoverModels()
            .assemble()
            .unwrap();

    private static final String PUT_RESPONSE = "<PutMetricResponse xmlns=\"https://example.com\">"
            + "<ResponseMetadata><RequestId>1</RequestId></ResponseMetadata></PutMetricResponse>";

    @Test
    void awsQueryDiscardsTheBodyOfAUnitOutput() {
        var body = new RecordingBody(DataStream.ofString(PUT_RESPONSE));

        call(METRICS, new AwsQueryClientProtocol(METRICS, "2010-08-01"), "PutMetric", body);

        assertThat(body.discarded, is(true));
        assertThat(body.reads, equalTo(0));
    }

    @Test
    void awsQueryDiscardsAnEmptyBody() {
        var body = new RecordingBody(DataStream.ofBytes(new byte[0]));

        call(METRICS, new AwsQueryClientProtocol(METRICS, "2010-08-01"), "GetMetric", body);

        assertThat(body.discarded, is(true));
    }

    @Test
    void awsQueryConsumesTheBodyOfARealOutput() {
        var body = new RecordingBody(DataStream.ofString("<GetMetricResponse xmlns=\"https://example.com\">"
                + "<GetMetricResult><value>7</value></GetMetricResult></GetMetricResponse>"));

        var output = call(METRICS, new AwsQueryClientProtocol(METRICS, "2010-08-01"), "GetMetric", body);

        assertThat(output.getMember("value").asInteger(), equalTo(7));
        assertThat(body.reads, equalTo(1));
    }

    @Test
    void ec2QueryDiscardsTheBodyOfAUnitOutput() {
        var body = new RecordingBody(DataStream.ofString(PUT_RESPONSE));

        call(INSTANCES, new Ec2QueryClientProtocol(INSTANCES, "2016-11-15"), "PutMetric", body);

        assertThat(body.discarded, is(true));
        assertThat(body.reads, equalTo(0));
    }

    @Test
    void ec2QueryDiscardsAnEmptyBody() {
        var body = new RecordingBody(DataStream.ofBytes(new byte[0]));

        call(INSTANCES, new Ec2QueryClientProtocol(INSTANCES, "2016-11-15"), "GetMetric", body);

        assertThat(body.discarded, is(true));
    }

    private static Document call(
            ShapeId service,
            ClientProtocol<HttpRequest, HttpResponse> protocol,
            String operation,
            DataStream body
    ) {
        var queue = new MockQueue().enqueue(HttpResponse.create()
                .setStatusCode(200)
                .setBody(body)
                .toUnmodifiable());
        var client = DynamicClient.builder()
                .serviceId(service)
                .model(MODEL)
                .protocol(protocol)
                .addPlugin(MockPlugin.builder().addQueue(queue).build())
                .endpointResolver(EndpointResolver.staticEndpoint("https://localhost:8081"))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .build();
        var output = client.call(operation, Document.ofObject(Map.of("name", "n")));
        assertThat(queue.remaining(), equalTo(0));
        return output;
    }

    private static final class RecordingBody implements DataStream {
        private final DataStream delegate;
        boolean discarded;
        int reads;

        RecordingBody(DataStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public long contentLength() {
            return delegate.contentLength();
        }

        @Override
        public String contentType() {
            return "text/xml";
        }

        @Override
        public boolean isReplayable() {
            return true;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean hasByteBuffer() {
            return true;
        }

        @Override
        public ByteBuffer asByteBuffer() {
            reads++;
            return delegate.asByteBuffer();
        }

        @Override
        public InputStream asInputStream() {
            reads++;
            return delegate.asInputStream();
        }

        @Override
        public void discard() throws IOException {
            discarded = true;
            delegate.discard();
        }
    }
}
