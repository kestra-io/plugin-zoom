package io.kestra.plugin.zoom;

import io.kestra.core.http.HttpRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import jakarta.validation.constraints.NotNull;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import io.kestra.core.models.annotations.PluginProperty;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send a Zoom Team Chat message",
    description = "Posts a message to a Zoom channel or a Zoom user via the Zoom Team Chat API."
)
@Plugin(
    examples = {
        @Example(
            title = "Notify a Zoom channel when a flow fails.",
            full = true,
            code = """
                id: unreliable_flow
                namespace: company.team

                tasks:
                  - id: fail
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - exit 1

                errors:
                  - id: alert_on_failure
                    type: io.kestra.plugin.zoom.SendMessage
                    accountId: "{{ secret('ZOOM_ACCOUNT_ID') }}"
                    clientId: "{{ secret('ZOOM_CLIENT_ID') }}"
                    clientSecret: "{{ secret('ZOOM_CLIENT_SECRET') }}"
                    userId: "{{ secret('ZOOM_BOT_USER_ID') }}"
                    channel: "{{ secret('ZOOM_CHANNEL_ID') }}"
                    message: "Flow {{ flow.namespace }}.{{ flow.id }} failed."
                """
        ),
        @Example(
            title = "Notify a Zoom channel when a flow completes successfully and store the message ID in the KV store.",
            full = true,
            code = """
                id: notify_zoom_on_success
                namespace: company.team

                tasks:
                  - id: run_job
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - echo "job done"

                  - id: send_notification
                    type: io.kestra.plugin.zoom.SendMessage
                    accountId: "{{ secret('ZOOM_ACCOUNT_ID') }}"
                    clientId: "{{ secret('ZOOM_CLIENT_ID') }}"
                    clientSecret: "{{ secret('ZOOM_CLIENT_SECRET') }}"
                    userId: "{{ secret('ZOOM_BOT_USER_ID') }}"
                    channel: "{{ secret('ZOOM_CHANNEL_ID') }}"
                    message: "Flow {{ flow.namespace }}.{{ flow.id }} completed successfully."

                  - id: store_message_id
                    type: io.kestra.plugin.core.kv.Set
                    key: last_zoom_message_id
                    value: "{{ outputs.send_notification.messageId }}"
                """
        )
    }
)
public class SendMessage extends AbstractZoomConnection implements RunnableTask<SendMessage.Output> {
    @Schema(
        title = "Zoom User ID",
        description = "The Zoom user ID associated with the chat"
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> userId;

    @Schema(
        title = "Channel",
        description = "The Zoom channel ID to send the message to"
    )
    @PluginProperty(group = "main")
    private Property<String> channel;

    @Schema(
        title = "Recipient Contact",
        description = "The email address of the user to send the message to"
    )
    @PluginProperty(group = "main")
    private Property<String> toContact;

    @Schema(
        title = "Message",
        description = "The message to send"
    )
    @PluginProperty(group = "main")
    @NotNull
    private Property<String> message;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String userId = runContext.render(this.userId)
            .as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("'userId' is required"));
        String channel = runContext.render(this.channel)
            .as(String.class)
            .orElse(null);
        String toContact = runContext.render(this.toContact)
            .as(String.class)
            .orElse(null);
        String message = runContext.render(this.message)
            .as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("'message' is required"));

        if((channel == null || channel.isBlank()) == (toContact == null || toContact.isBlank())){
            throw new IllegalArgumentException(
                "Exactly one of 'channel' or 'toContact' must be provided"
            );
        }

        Map<String,Object> body;

        if(channel != null && !channel.isBlank()){
            body = Map.of(
                "message", message,
                "to_channel", channel
            );
        }else {
            body = Map.of(
                "message", message,
                "to_contact", toContact
            );
        }

        String baseUrl = getBaseUrl(runContext);
        String url = baseUrl + "chat/users/" +  URLEncoder.encode(userId, StandardCharsets.UTF_8) + "/messages";

        HttpRequest request = createAuthenticatedRequest(
            runContext,
            "POST",
            url ,
            HttpRequest.JsonRequestBody.of(body)
        );

        var response = execute(
            runContext,
            request,
            SendMessageResponse.class
        );

        var responseBody = response.getBody();
        var messageId = responseBody != null ? responseBody.id() : null;
        if (messageId == null) {
            runContext.logger().warn("Zoom accepted the message but its response did not contain a message id; 'messageId' output will be empty");
        }

        return Output.builder()
            .messageId(messageId)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The ID of the sent message",
            description = "UUID of the Zoom Team Chat message, as returned by the Zoom API. Can be used by downstream tasks to reply to, update, or delete the message."
        )
        private String messageId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SendMessageResponse(String id) {}
}
