package io.kestra.plugin.zoom;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import jakarta.validation.constraints.NotNull;

//imports for kestra's HTTP client
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.runners.RunContext;

//imports for OAuth
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor

public abstract class AbstractZoomConnection extends Task{

    @Schema(
        title = "Zoom Account ID",
        description = "Account ID obtained from Zoom App Marketplace"
    )
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "connection")
    private Property<String> accountId;

    @Schema(
        title = "Zoom Client ID",
        description = "Client ID obtained from Zoom App Marketplace"
    )
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "connection")
    private Property<String> clientId;

    @Schema(
        title = "Zoom Client Secret",
        description = "Client Secret obtained from Zoom App Marketplace"
    )
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "connection" , secret = true)
    @ToString.Exclude
    private Property<String> clientSecret;

    @Schema(
        title =  "Custom Base URL",
        description = "Override Zoom API base URL. Defaults to https://api.zoom.us/v2/"
    )
    @io.kestra.core.models.annotations.PluginProperty(group = "connection")
    private Property<String> baseUrl;

    private record OAuthTokenResponse(String access_token) {}

    protected HttpClient createHttpClient(RunContext runContext) throws Exception{
        return HttpClient.builder().
            runContext(runContext).
            configuration(HttpConfiguration.builder().build())
            .build();
    }

    protected String getBaseUrl(RunContext runContext) throws Exception {
        String url = runContext.render(this.baseUrl)
            .as(String.class)
            .map(String::trim)
            .filter(u -> !u.isBlank())
            .orElse("https://api.zoom.us/v2/");

        return url.endsWith("/") ? url : url + "/";
    }

    protected String getOAuthUrl() throws Exception {
        return "https://zoom.us/oauth/token";
    }

    protected String getAccessToken(RunContext runContext) throws Exception {
        String accountId = runContext.render(this.accountId).as(String.class).orElseThrow();
        String clientId = runContext.render(this.clientId).as(String.class).orElseThrow();
        String clientSecret = runContext.render(this.clientSecret).as(String.class).orElseThrow();

        String credentials = Base64.getEncoder()
            .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.of(
            URI.create(getOAuthUrl()),
            "POST",
            HttpRequest.UrlEncodedRequestBody.of(
                Map.of(
                    "grant_type", "account_credentials",
                    "account_id", accountId
                )
            ),
            Map.of(
                "Authorization", List.of("Basic " + credentials)
            )
        );

        try (HttpClient httpClient = createHttpClient(runContext)) {
            HttpResponse<OAuthTokenResponse> response = httpClient.request(request, OAuthTokenResponse.class);

            if (response.getBody() == null || response.getBody().access_token() == null) {
                throw new IllegalStateException("Zoom OAuth response did not contain an access token");
            }

            return response.getBody().access_token();
        }
    }

    protected HttpRequest createAuthenticatedRequest(
        RunContext runContext,
        String method,
        String url,
        HttpRequest.RequestBody body
    ) throws Exception {
        String accessToken = getAccessToken(runContext);

        return HttpRequest.of(
            URI.create(url),
            method,
            body,
            Map.of(
                "Authorization", List.of("Bearer " + accessToken)
            )
        );
    }

    protected <T> HttpResponse<T> execute(
        RunContext runContext,
        HttpRequest request,
        Class<T> responseType
    ) throws Exception {
        try(HttpClient httpClient = createHttpClient(runContext)){
            return httpClient.request(request,responseType);
        }
    }
}
