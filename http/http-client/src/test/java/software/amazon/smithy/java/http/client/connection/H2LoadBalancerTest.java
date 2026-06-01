/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class H2LoadBalancerTest {

    @Test
    void rejectsSoftLimitAboveHardLimit() {
        assertThrows(IllegalArgumentException.class, () -> H2LoadBalancer.watermark(11, 10));
    }

    @Test
    void roundRobinsAcrossConnectionsBelowSoftLimit() {
        H2LoadBalancer balancer = H2LoadBalancer.watermark(5, 10);
        int[] activeStreams = {0, 0, 0};

        assertEquals(0, balancer.select(activeStreams, 3, 3));
        assertEquals(1, balancer.select(activeStreams, 3, 3));
        assertEquals(2, balancer.select(activeStreams, 3, 3));
        assertEquals(0, balancer.select(activeStreams, 3, 3));
    }

    @Test
    void createsNewConnectionWhenAllConnectionsReachSoftLimitAndCapacityRemains() {
        H2LoadBalancer balancer = H2LoadBalancer.watermark(5, 10);
        int[] activeStreams = {5, 6};

        assertEquals(H2LoadBalancer.CREATE_NEW, balancer.select(activeStreams, 2, 3));
    }

    @Test
    void selectsLeastLoadedConnectionBelowHardLimitWhenPoolCannotExpand() {
        H2LoadBalancer balancer = H2LoadBalancer.watermark(5, 10);
        int[] activeStreams = {9, 5, 7};

        assertEquals(1, balancer.select(activeStreams, 3, 3));
    }

    @Test
    void returnsSaturatedWhenNoConnectionCanAcceptStreams() {
        H2LoadBalancer balancer = H2LoadBalancer.watermark(5, 10);
        int[] activeStreams = {10, -1};

        assertEquals(H2LoadBalancer.SATURATED, balancer.select(activeStreams, 2, 2));
    }

    @Test
    void skipsConnectionsThatAreNotAcceptingStreams() {
        H2LoadBalancer balancer = H2LoadBalancer.watermark(5, 10);
        int[] activeStreams = {-1, 3};

        assertEquals(1, balancer.select(activeStreams, 2, 2));
        assertTrue(balancer.select(activeStreams, 2, 2) >= 0);
    }
}
