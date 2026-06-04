/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.clickup.util;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit6.TestExecutionConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for ClickUp tests that exercise the webhook auto-registration against a mocked ClickUp API.
 * <p>
 * It centralises all the environmental setup that is not the subject of the tests: it starts the mocked ClickUp API in
 * a dedicated {@link DefaultCamelContext}, waits for it to become reachable and registers the webhook route on the test
 * context. Subclasses only supply the mock endpoints through {@link #createMockRoutes()} and assert the behaviour under
 * test, while keeping full control over when the test context is started or stopped (which is what triggers the
 * registration and unregistration being verified).
 */
public abstract class ClickUpMockApiTestSupport extends ClickUpTestSupport {

    private DefaultCamelContext mockApiContext;

    @Override
    public void configureTest(TestExecutionConfiguration testExecutionConfiguration) {
        super.configureTest(testExecutionConfiguration);

        // each test starts/stops the test context explicitly, as that lifecycle is the behaviour under test
        testExecutionConfiguration.withUseRouteBuilder(false);
    }

    @BeforeEach
    void startMockApiAndRegisterWebhookRoute() throws Exception {
        mockApiContext = new DefaultCamelContext();
        mockApiContext.addRoutes(getMockRoutes());
        mockApiContext.start();

        waitForClickUpMockAPI();

        context().addRoutes(webhookRouteBuilder());
    }

    @AfterEach
    void stopMockApi() {
        if (mockApiContext != null) {
            mockApiContext.stop();
        }
    }

    private RouteBuilder webhookRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String apiMockBaseUrl = "http://localhost:" + port + "/clickup-api-mock";

                fromF("webhook:clickup:%d?authorizationToken=%s&webhookSecret=%s&events=%s&webhookAutoRegister=true&baseUrl=%s",
                        WORKSPACE_ID, AUTHORIZATION_TOKEN, WEBHOOK_SECRET, String.join(",", EVENTS), apiMockBaseUrl)
                        .id("webhook")
                        .to("mock:endpoint");
            }
        };
    }

}
