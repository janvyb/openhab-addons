package org.openhab.binding.nuki.internal.dataexchange;

import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.WWWAuthenticationProtocolHandler;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.nuki.api.model.Smartlock;
import org.openhab.binding.nuki.api.model.SmartlockAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

@NonNullByDefault
public class NukiWebHttpClient {

    private static final String BASE_URL = "https://api.nuki.io";
    private static final String PATH_SMARTLOCKS = "/smartlock";
    private static final String PATH_SMARTLOCK = "/smartlock/%s";
    private static final String PATH_SMARTLOCK_ACTION = "/smartlock/%s/action";

    private static final URI URI_SMARTLOCKS = getUri(PATH_SMARTLOCKS);

    private static final Type TYPE_SMARTLOCK_LIST = new TypeToken<ArrayList<Smartlock>>() {
    }.getType();
    private static final Type TYPE_SMARTLOCK = new TypeToken<Smartlock>() {
    }.getType();

    private final Logger logger = LoggerFactory.getLogger(NukiWebHttpClient.class);
    private final HttpClient httpClient;
    private final String authHeader;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeDeserializer()).create();

    public NukiWebHttpClient(HttpClient httpClient, String bearerToken) {
        this.httpClient = httpClient;
        httpClient.getProtocolHandlers().remove(WWWAuthenticationProtocolHandler.NAME);
        this.authHeader = "Bearer: " + bearerToken;
    }

    public List<Smartlock> getSmartlocks() throws CommunicationErrorException, UnauthorizedException {
        return performGet(URI_SMARTLOCKS, TYPE_SMARTLOCK_LIST);
    }

    public Smartlock getSmartlock(String smartlockId) throws CommunicationErrorException, UnauthorizedException {
        return performGet(getUri(PATH_SMARTLOCK, smartlockId), TYPE_SMARTLOCK);
    }

    public void sendAction(String smartLockId, SmartlockAction update)
            throws CommunicationErrorException, UnauthorizedException {
        performPost(getUri(PATH_SMARTLOCK_ACTION, smartLockId), update);
    }

    private void performPost(URI uri, Object body) throws CommunicationErrorException, UnauthorizedException {
        String bodyString = gson.toJson(body);
        logger.debug("POST to '{}' with body: '{}'", uri, bodyString);
        sendRequest(httpPost(uri, body));
    }

    private ContentResponse sendRequest(Request request) throws CommunicationErrorException, UnauthorizedException {
        ContentResponse response;
        try {
            response = request.send();
        } catch (Exception e) {
            throw new CommunicationErrorException(
                    "Exception when sending request - " + e.getClass() + ": " + e.getMessage(), e);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Response {}: {}", response.getStatus(), response.getContentAsString());
        }
        if (response.getStatus() == HttpStatus.UNAUTHORIZED_401 || response.getStatus() == HttpStatus.FORBIDDEN_403) {
            throw new UnauthorizedException(getErrorMessage(response));
        } else if (response.getStatus() >= 300) {
            throw new CommunicationErrorException(getErrorMessage(response));
        } else {
            return response;
        }
    }

    private String getErrorMessage(ContentResponse response) {
        String content = response.getContentAsString();
        if (content == null) {
            return "Server returned status " + response.getStatus() + ": " + response.getReason();
        } else if (response.getMediaType().equals("application/json")) {
            NukiApiError error = gson.fromJson(content, NukiApiError.class);
            if (error.getDetailMessage() != null) {
                return error.getDetailMessage();
            }
        }
        return "Server returned status " + response.getStatus() + ": " + content;
    }

    private <T> T performGet(URI uri, Type responseType) throws CommunicationErrorException, UnauthorizedException {
        logger.debug("GET to: '{}'", uri);
        ContentResponse response = sendRequest(httpGet(uri));
        return gson.fromJson(response.getContentAsString(), responseType);
    }

    private Request httpGet(URI uri) {
        return request(uri, HttpMethod.GET, null);
    }

    private Request httpPost(URI uri, Object body) {
        String bodyString = gson.toJson(body);
        return request(uri, HttpMethod.POST, bodyString);
    }

    private Request request(URI uri, HttpMethod method, @Nullable String jsonBody) {
        Request req = this.httpClient.newRequest(uri).method(method).header(HttpHeader.AUTHORIZATION, this.authHeader)
                .accept("application/json");
        if (jsonBody != null) {
            return req.content(new StringContentProvider("application/json", jsonBody, StandardCharsets.UTF_8));
        } else {
            return req;
        }
    }

    private static URI getUri(String path, @Nullable String... params) {
        if (params == null) {
            return URI.create(BASE_URL + path);
        } else {
            return URI.create(BASE_URL + String.format(path, params));
        }
    }

    public static class UnauthorizedException extends Exception {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    public static class CommunicationErrorException extends Exception {
        public CommunicationErrorException(String message) {
            super(message);
        }

        public CommunicationErrorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class OffsetDateTimeDeserializer implements JsonDeserializer<OffsetDateTime> {

        @Override
        @Nullable
        public OffsetDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (json.isJsonNull()) {
                return null;
            } else if (json.isJsonPrimitive()) {
                return OffsetDateTime.parse(json.getAsString());
            } else {
                throw new JsonParseException("Cannot deserialize OffsetDateTime from " + json);
            }
        }
    }
}
