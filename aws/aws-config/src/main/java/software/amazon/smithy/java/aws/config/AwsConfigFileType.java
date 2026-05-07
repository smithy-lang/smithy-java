/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

/**
 * Identifies which of the two AWS shared configuration files is being parsed.
 */
public enum AwsConfigFileType {
    /** The configuration file (e.g., {@code ~/.aws/config}). */
    CONFIGURATION,

    /** The shared credentials file (e.g., {@code ~/.aws/credentials}). */
    CREDENTIALS
}
