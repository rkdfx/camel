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
package org.apache.camel.component.clickup;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Endpoint;
import org.apache.camel.component.clickup.model.Webhook;
import org.apache.camel.component.clickup.model.WebhookCreationCommand;
import org.apache.camel.component.clickup.model.WebhooksReadResult;
import org.apache.camel.component.clickup.util.ClickUpMockApiTestSupport;
import org.apache.camel.component.clickup.util.ClickUpMockRoutes;
import org.apache.camel.component.webhook.WebhookConfiguration;
import org.apache.camel.component.webhook.WebhookEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ClickUpWebhookRegistrationAlreadyExistsTest extends ClickUpMockApiTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String WEBHOOK_ALREADY_EXISTS_JSON = "messages/webhook-already-exists.json";
    public static final String WEBHOOKS = "messages/webhooks.json";

    @Test
    public void testAutomaticRegistrationWhenWebhookConfigurationAlreadyExists() throws Exception {
        final ClickUpMockRoutes.MockProcessor<String> creationMock
                = getMockRoutes().getMock("POST", "team/" + WORKSPACE_ID + "/webhook");
        final ClickUpMockRoutes.MockProcessor<String> readMock
                = getMockRoutes().getMock("GET", "team/" + WORKSPACE_ID + "/webhook");

        context().start();

        assertWebhookCreationRequested(creationMock);
        assertExistingWebhooksRead(readMock);
    }

    private static void assertWebhookCreationRequested(ClickUpMockRoutes.MockProcessor<String> creationMock) {
        final List<String> recordedMessages = creationMock.awaitRecordedMessages(1, 5000);
        assertEquals(1, recordedMessages.size());

        final String recordedMessage = recordedMessages.get(0);
        assertDoesNotThrow(() -> assertInstanceOf(WebhookCreationCommand.class,
                MAPPER.readValue(recordedMessage, WebhookCreationCommand.class)));
    }

    private static void assertExistingWebhooksRead(ClickUpMockRoutes.MockProcessor<String> readMock) {
        final List<String> recordedMessages = readMock.awaitRecordedMessages(1, 5000);
        assertEquals(1, recordedMessages.size());
        assertEquals("", recordedMessages.get(0));
    }

    @Override
    protected ClickUpMockRoutes createMockRoutes() {
        ClickUpMockRoutes clickUpMockRoutes = new ClickUpMockRoutes(port.getPort());

        clickUpMockRoutes.addEndpoint(
                "health",
                "GET",
                true,
                String.class,
                () -> "");

        try (InputStream content = getClass().getClassLoader().getResourceAsStream(WEBHOOK_ALREADY_EXISTS_JSON)) {
            assert content != null;

            String responseBody = new String(content.readAllBytes());

            clickUpMockRoutes.addEndpoint(
                    "team/" + WORKSPACE_ID + "/webhook",
                    "POST",
                    true,
                    String.class,
                    () -> responseBody);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        clickUpMockRoutes.addEndpoint(
                "team/" + WORKSPACE_ID + "/webhook",
                "GET",
                true,
                String.class,
                () -> {
                    String webhookExternalUrl;
                    try {
                        Optional<Endpoint> optionalEndpoint = context().getEndpoints().stream()
                                .filter(endpoint -> endpoint instanceof WebhookEndpoint)
                                .findFirst();

                        if (optionalEndpoint.isEmpty()) {
                            throw new RuntimeException("Could not find clickup webhook endpoint. This should never happen.");
                        }

                        WebhookEndpoint webhookEndpoint = (WebhookEndpoint) (optionalEndpoint.get());

                        WebhookConfiguration config = webhookEndpoint.getConfiguration();
                        webhookExternalUrl = config.computeFullExternalUrl();
                    } catch (UnknownHostException e) {
                        throw new RuntimeException(e);
                    }

                    WebhooksReadResult webhooksReadResult = getJSONResource(WEBHOOKS, WebhooksReadResult.class);
                    Optional<Webhook> webhook = webhooksReadResult.getWebhooks().stream().findFirst();
                    if (webhook.isEmpty()) {
                        throw new RuntimeException(
                                "Could not find the testing webhook. This should never happen, since its reading webhooks from a static file.");
                    }
                    webhook.get().setEndpoint(webhookExternalUrl);

                    String readWebhooksResponseBody;
                    try {
                        readWebhooksResponseBody = MAPPER.writeValueAsString(webhooksReadResult);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }

                    return readWebhooksResponseBody;
                });

        return clickUpMockRoutes;
    }
}
