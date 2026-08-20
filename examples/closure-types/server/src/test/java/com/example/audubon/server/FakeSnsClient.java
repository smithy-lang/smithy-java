/*
 * Example license header.
 * File header line two
 */

package com.example.audubon.server;

import java.util.List;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/** Collects published messages instead of reaching SNS. */
record FakeSnsClient(List<PublishRequest> published) implements SnsClient {

    @Override
    public PublishResponse publish(PublishRequest request) {
        published.add(request);
        return PublishResponse.builder().messageId("id").build();
    }

    @Override
    public String serviceName() {
        return SnsClient.SERVICE_NAME;
    }

    @Override
    public void close() {}
}
