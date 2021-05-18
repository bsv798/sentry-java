package io.sentry.spring.tracing.client.webflux;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.IHub;
import io.sentry.protocol.Request;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Enriches {@link Request} with HTTP data.
 */
@Open
public class SentryTracingWebfluxRequestResolver {

  private static final List<String> SENSITIVE_HEADERS =
    Arrays.asList("X-FORWARDED-FOR", "AUTHORIZATION", "COOKIE");

  private final @NotNull IHub hub;

  public SentryTracingWebfluxRequestResolver(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  // httpRequest.getRequestURL() returns StringBuffer which is considered an obsolete class.
  public Request resolveSentryRequest(final @NotNull ServerHttpRequest httpRequest) {
    final Request sentryRequest = new Request();

    sentryRequest.setMethod(httpRequest.getMethodValue());
    sentryRequest.setQueryString(httpRequest.getURI().getQuery());
    sentryRequest.setUrl(httpRequest.getURI().toString());
    sentryRequest.setHeaders(resolveHeadersMap(httpRequest));

    if (hub.getOptions().isSendDefaultPii()) {
      sentryRequest.setCookies(toString(httpRequest.getHeaders().get("Cookie")));
    }

    return sentryRequest;
  }

  Map<String, String> resolveHeadersMap(final @NotNull ServerHttpRequest request) {
    final Map<String, String> headersMap = new HashMap<>();

    for (String headerName : request.getHeaders().keySet()) {
      // do not copy personal information identifiable headers
      if (hub.getOptions().isSendDefaultPii()
        || !SENSITIVE_HEADERS.contains(headerName.toUpperCase(Locale.ROOT))) {
        headersMap.put(headerName, toString(request.getHeaders().get(headerName)));
      }
    }

    return headersMap;
  }

  private static String toString(final @NotNull List<String> list) {
    return list != null ? String.join(",", list) : null;
  }
}
