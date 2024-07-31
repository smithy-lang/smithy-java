/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import java.util.List;

public interface ServiceMatcher {
    List<Service> getCandidateServices(ServiceMatcherInput input);
}
