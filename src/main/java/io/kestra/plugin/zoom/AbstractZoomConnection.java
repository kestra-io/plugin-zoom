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
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.runners.RunContext;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.storages.kv.KVMetadata;
import java.time.Duration;
import java.util.Optional;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kestra.core.models.annotations.PluginProperty;

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
    @PluginProperty(group = "connection")
    private Property<String> accountId;

    @Schema(
        title = "Zoom Client ID",
        description = "Client ID obtained from Zoom App Marketplace"
    )
    @NotNull
    @PluginProperty(group = "connection")
    private Property<String> clientId;

    @Schema(
        title = "Zoom Client Secret",
        description = "Client Secret obtained from Zoom App Marketplace"
    )
    @NotNull
    @PluginProperty(group = "connection" , secret = true)
    @ToString.Exclude
    private Property<String> clientSecret;

    @Schema(
        title =  "Custom Base URL",
        description = "Override Zoom API base URL. Defaults to https://api.zoom.us/v2/"
    )
    @PluginProperty(group = "connection")
    private Property<String> baseUrl;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OAuthTokenResponse(String access_token, Integer expires_in) {}

    protected HttpClient createHttpClient(RunContext runContext) throws Exception{
        return HttpClient.builder()
            .runContext(runContext)
            .configuration(HttpConfiguration.builder().build())
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
        String accountId = runContext.render(this.accountId).as(String.class).orElseThrow(() -> new IllegalArgumentException("Zoom 'accountId' is required"));
        String clientId = runContext.render(this.clientId).as(String.class).orElseThrow(() -> new IllegalArgumentException("Zoom 'clientId' is required"));
        String clientSecret = runContext.render(this.clientSecret).as(String.class).orElseThrow(() -> new IllegalArgumentException("Zoom 'clientSecret' is required"));

        KVStore kv = runContext.namespaceKv(runContext.flowInfo().namespace());
        String cacheKey = "zoom-token" + Integer.toHexString((accountId + ":" + clientId).hashCode());

        Optional<io.kestra.core.storages.kv.KVValue> cached = kv.getValue(cacheKey);
        if(cached.isPresent()) {
            return cached.get().value().toString();
        }

        String credentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.of(
            URI.create(getOAuthUrl()),
            "POST",
            HttpRequest.UrlEncodedRequestBody.of(
                Map.of("grant_type", "account_credentials", "account_id", accountId)
            ),
            Map.of("Authorization", List.of("Basic " + credentials))
        );

        try (HttpClient httpClient = createHttpClient(runContext)) {
            HttpResponse<OAuthTokenResponse> response;

            try{
                response = httpClient.request(request, OAuthTokenResponse.class);
            } catch (Exception e){
                throw new IllegalStateException( "Failed to authenticate with Zoom OAuth API: " + e.getMessage(),
                    e);
            }

            if (response.getBody() == null || response.getBody().access_token() == null) {
                throw new IllegalStateException("Zoom OAuth response did not contain an access token");
            }

            String token = response.getBody().access_token();
            int expiresIn = Optional.ofNullable(response.getBody().expires_in()).orElse(3600);
            long safeTtlSeconds = Math.max(expiresIn - 60, 30);

            kv.put(cacheKey, new KVValueAndMetadata(new KVMetadata(null, Duration.ofSeconds(safeTtlSeconds)), token),true);

            return token;
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
            try {
                return httpClient.request(request, responseType);
            } catch (Exception e) {
                throw new IllegalStateException("Empty or invalid response from Zoom API: " + e.getMessage(), e);
            }
        }
    }
}
