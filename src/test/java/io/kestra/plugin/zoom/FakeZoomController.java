package io.kestra.plugin.zoom;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;

import java.util.Map;

@Controller
public class FakeZoomController {

    public static String lastAuthorizationHeader;
    public static Map<String, Object> lastMessageBody;
    public static boolean simulateApiFailure = false;
    public static boolean simulateMissingAccessToken = false;
    public static boolean simulateEmptyApiResponse = false;

    @Post("/oauth/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public HttpResponse<Map<String, Object>> token() {
        if(simulateMissingAccessToken) {
            return HttpResponse.ok(Map.of("token_type", "bearer"));
        }
        return HttpResponse.ok(Map.of("access_token", "test-access-token"));
    }

    @Post("/chat/users/{userId}/messages")
    public HttpResponse<Map<String, Object>> sendMessage(
        String userId,
        @Body Map<String, Object> body,
        @Header("Authorization") String authorization
    ) {

        if(simulateApiFailure) {
            return HttpResponse.badRequest(Map.of("message", "invalid request"));
        }

        if (simulateEmptyApiResponse) {
            return HttpResponse.ok();
        }

        lastAuthorizationHeader = authorization;
        lastMessageBody = body;
        return HttpResponse.ok(Map.of());
    }
}