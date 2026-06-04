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
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.clickup.model.WebhookCreationCommand;
import org.apache.camel.component.clickup.util.ClickUpMockApiTestSupport;
import org.apache.camel.component.clickup.util.ClickUpMockRoutes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ClickUpWebhookRegistrationTest extends ClickUpMockApiTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String WEBHOOK_CREATED_JSON = "messages/webhook-created.json";

    @Test
    public void testAutomaticRegistration() throws Exception {
        final ClickUpMockRoutes.MockProcessor<String> creationMock
                = getMockRoutes().getMock("POST", "team/" + WORKSPACE_ID + "/webhook");

        context().start();

        assertWebhookCreationRequested(creationMock);
    }

    @Test
    public void testAutomaticUnregistration() throws Exception {
        final ClickUpMockRoutes.MockProcessor<String> deletionMock = getMockRoutes().getMock("DELETE", "webhook/");

        context().start();
        context().stop();

        assertWebhookDeletionRequested(deletionMock);
    }

    private static void assertWebhookCreationRequested(ClickUpMockRoutes.MockProcessor<String> creationMock) {
        final List<String> recordedMessages = creationMock.awaitRecordedMessages(1, 5000);
        assertEquals(1, recordedMessages.size());

        final String recordedMessage = recordedMessages.get(0);
        assertDoesNotThrow(() -> assertInstanceOf(WebhookCreationCommand.class,
                MAPPER.readValue(recordedMessage, WebhookCreationCommand.class)));
    }

    private static void assertWebhookDeletionRequested(ClickUpMockRoutes.MockProcessor<String> deletionMock) {
        final List<String> recordedMessages = deletionMock.awaitRecordedMessages(1, 5000);
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

        try (InputStream content = getClass().getClassLoader().getResourceAsStream(WEBHOOK_CREATED_JSON)) {
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
                "webhook/",
                "DELETE",
                false,
                String.class,
                () -> "{}");

        return clickUpMockRoutes;
    }
}
