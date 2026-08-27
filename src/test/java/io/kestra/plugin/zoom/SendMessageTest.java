package io.kestra.plugin.zoom;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class SendMessageTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private ApplicationContext applicationContext;

    private EmbeddedServer embeddedServer;

    @SuperBuilder
    @lombok.NoArgsConstructor(force = true)
    public static class TestSendMessage extends SendMessage {
        private String oauthTokenUrl;

        @Override
        protected String getOAuthUrl() {
            return oauthTokenUrl;
        }
    }

    @Test
    void shouldRejectWhenBothChannelAndToContactAreProvided() {
        RunContext runContext = runContextFactory.of(Map.of());

        SendMessage task = SendMessage.builder()
            .userId(Property.of("test-user"))
            .channel(Property.of("test-channel"))
            .toContact(Property.of("test@example.com"))
            .message(Property.of("Hello"))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void shouldFailWhenNoDestinationIsProvided() {
        RunContext runContext = runContextFactory.of(Map.of());

        SendMessage task = SendMessage.builder()
            .userId(Property.of("test-user"))
            .message(Property.of("Hello"))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void shouldSendMessageToChannel() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());
        embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        String baseUrl = embeddedServer.getURL().toString() + "/";
        FakeZoomController.lastAuthorizationHeader = null;
        FakeZoomController.lastMessageBody = null;

        SendMessage task = TestSendMessage.builder()
            .userId(Property.of("test-user"))
            .channel(Property.of("test-channel"))
            .message(Property.of("Hello"))
            .accountId(Property.of("test-account"))
            .clientId(Property.of("test-client"))
            .clientSecret(Property.of("test-secret"))
            .baseUrl(Property.of(baseUrl))
            .oauthTokenUrl(baseUrl + "oauth/token")
            .build();

        task.run(runContext);

        assertThat(FakeZoomController.lastAuthorizationHeader, equalTo("Bearer test-access-token"));
        assertThat(FakeZoomController.lastMessageBody.get("to_channel"), equalTo("test-channel"));
        assertThat(FakeZoomController.lastMessageBody.get("message"), equalTo("Hello"));
    }

    @Test
    void shouldSendMessageToContact() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());
        embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        String baseUrl = embeddedServer.getURL().toString() + "/";
        FakeZoomController.lastAuthorizationHeader = null;
        FakeZoomController.lastMessageBody = null;

        SendMessage task = TestSendMessage.builder()
            .userId(Property.of("test-user"))
            .toContact(Property.of("test@example.com"))
            .message(Property.of("Hello"))
            .accountId(Property.of("test-account"))
            .clientId(Property.of("test-client"))
            .clientSecret(Property.of("test-secret"))
            .baseUrl(Property.of(baseUrl))
            .oauthTokenUrl(baseUrl + "oauth/token")
            .build();

        task.run(runContext);

        assertThat(FakeZoomController.lastAuthorizationHeader, equalTo("Bearer test-access-token"));
        assertThat(FakeZoomController.lastMessageBody.get("to_contact"), equalTo("test@example.com"));
        assertThat(FakeZoomController.lastMessageBody.get("message"), equalTo("Hello"));
    }
}