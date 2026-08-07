/*
 * Example license header.
 * File header line two
 */

package com.example.audubon.server;

import com.example.audubon.server.service.BirdWatcher;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Assembles the generated service from the operation implementations.
 */
public final class BirdWatcherService {

    private BirdWatcherService() {}

    /**
     * Builds the service, publishing events to the given SNS topic. Serve the result
     * with a smithy-java server such as Netty or an AWS Lambda endpoint.
     */
    public static BirdWatcher create(SnsClient sns, String topicArn) {
        return create(new BirdWatcherHandlers(new EventPublisher(sns, topicArn)));
    }

    static BirdWatcher create(BirdWatcherHandlers handlers) {
        return BirdWatcher.builder()
                .addGetSightingOperation(handlers.getSighting())
                .addListSightingsOperation(handlers.listSightings())
                .addReportSightingOperation(handlers.reportSighting())
                .addWithdrawSightingOperation(handlers.withdrawSighting())
                .build();
    }
}
