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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import java.util.Map;
import io.kestra.core.models.flows.Flow;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Execution(ExecutionMode.SAME_THREAD)
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

    private RunContext createRunContext() {
        Flow flow = Flow.builder()
            .tenantId("main")
            .namespace("test")
            .id("test-flow")
            .build();

        return runContextFactory.of(flow, Map.of());
    }

    @Test
    void shouldRejectWhenBothChannelAndToContactAreProvided() {
        RunContext runContext = createRunContext();

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
        RunContext runContext = createRunContext();

        SendMessage task = SendMessage.builder()
            .userId(Property.of("test-user"))
            .message(Property.of("Hello"))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void shouldSendMessageToChannel() throws Exception {
        RunContext runContext = createRunContext();
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

        var output = task.run(runContext);

        assertThat(output, nullValue());
        assertThat(FakeZoomController.lastAuthorizationHeader, equalTo("Bearer test-access-token"));
        assertThat(FakeZoomController.lastMessageBody.get("to_channel"), equalTo("test-channel"));
        assertThat(FakeZoomController.lastMessageBody.get("message"), equalTo("Hello"));
    }

    @Test
    void shouldSendMessageToContact() throws Exception {
        RunContext runContext = createRunContext();
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

    @Test
    void shouldFailWhenChannelIsBlank() {
        RunContext runContext = createRunContext();

        SendMessage task = SendMessage.builder()
            .userId(Property.of("test-user"))
            .channel(Property.of("  "))
            .message(Property.of("Hello"))
            .build();

        assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
    }

    @Test
    void shouldFailWhenOAuthResponseHasNoAccessToken() throws Exception {
        RunContext runContext = createRunContext();

        embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        String baseUrl = embeddedServer.getURL().toString() + "/";

        FakeZoomController.simulateMissingAccessToken = true;

        SendMessage task = TestSendMessage.builder()
            .userId(Property.of("test-user"))
            .channel(Property.of("test-channel"))
            .message(Property.of("Hello"))
            .accountId(Property.of("missing-token-account"))
            .clientId(Property.of("missing-token-client"))
            .clientSecret(Property.of("test-secret"))
            .baseUrl(Property.of(baseUrl))
            .oauthTokenUrl(baseUrl + "oauth/token")
            .build();

        try {
            assertThrows(
                IllegalStateException.class,
                () -> task.run(runContext)
            );
        } finally {
            FakeZoomController.simulateMissingAccessToken = false;
        }
    }

    @Test
    void shouldFailWhenZoomApiReturnsError() throws Exception {
        RunContext runContext = createRunContext();
        embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        String baseUrl = embeddedServer.getURL().toString() + "/";

        FakeZoomController.simulateApiFailure = true;

        SendMessage task = TestSendMessage.builder()
            .userId(Property.of("test-user"))
            .channel(Property.of("test-channel"))
            .message(Property.of("Hello"))
            .accountId(Property.of("test-account"))
            .clientId(Property.of("test-client"))
            .clientSecret(Property.of("different-test-secret"))
            .baseUrl(Property.of(baseUrl))
            .oauthTokenUrl(baseUrl + "oauth/token")
            .build();

        try {
            assertThrows(Exception.class, () -> task.run(runContext));
        } finally {
            FakeZoomController.simulateApiFailure = false;
        }
    }

    @Test
    void shouldHandleBaseUrlWithoutTrailingSlash() throws Exception {
        RunContext runContext = createRunContext();

        embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        String baseUrl = embeddedServer.getURL().toString();

        FakeZoomController.lastAuthorizationHeader = null;
        FakeZoomController.lastMessageBody = null;

        SendMessage task = TestSendMessage.builder()
            .userId(Property.of("test-user"))
            .channel(Property.of("test-channel"))
            .message(Property.of("Hello"))
            .accountId(Property.of("different-account"))
            .clientId(Property.of("different-client"))
            .clientSecret(Property.of("different-secret"))
            .baseUrl(Property.of(baseUrl))
            .oauthTokenUrl(baseUrl + "/oauth/token")
            .build();

        task.run(runContext);

        assertThat(FakeZoomController.lastAuthorizationHeader, equalTo("Bearer test-access-token"));
        assertThat(FakeZoomController.lastMessageBody.get("to_channel"), equalTo("test-channel"));
        assertThat(FakeZoomController.lastMessageBody.get("message"), equalTo("Hello"));
    }

    @Test
    void shouldFailWhenZoomApiReturnsEmptyResponse() throws Exception {
        RunContext runContext = createRunContext();

        embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        String baseUrl = embeddedServer.getURL().toString();

        FakeZoomController.simulateEmptyApiResponse = true;

        SendMessage task = TestSendMessage.builder()
            .userId(Property.of("test-user"))
            .channel(Property.of("test-channel"))
            .message(Property.of("Hello"))
            .accountId(Property.of("empty-response-account"))
            .clientId(Property.of("empty-response-client"))
            .clientSecret(Property.of("empty-response-secret"))
            .baseUrl(Property.of(baseUrl))
            .oauthTokenUrl(baseUrl + "/oauth/token")
            .build();

        try {
            assertThrows(
                IllegalStateException.class,
                () -> task.run(runContext)
            );
        } finally {
            FakeZoomController.simulateEmptyApiResponse = false;
        }
    }
}