package io.kestra.plugin.zoom;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.models.tasks.VoidOutput;
import io.swagger.v3.oas.annotations.media.Schema;
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
            title = "Notify a Zoom channel when a flow completes successfully.",
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
                """
        )
    }
)
public class SendMessage extends AbstractZoomConnection implements RunnableTask<VoidOutput> {
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
    public VoidOutput run(RunContext runContext) throws Exception{
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

        execute(
            runContext,
            request,
            Map.class
        );

        return null;
    }
}