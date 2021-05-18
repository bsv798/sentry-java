package io.sentry.spring.tracing.client.webflux;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;

/**
 * Resolves transaction name.
 */
public final class SentryTracingWebfluxTransactionNameProvider {

  public String provideTransactionName(final @NotNull ServerHttpRequest request,
      final @NotNull ServerWebExchange exchange) {
    Object pattern = exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

    pattern = (pattern == null) ? request.getPath() : pattern;

    return pattern != null ? request.getMethod() + " " + pattern : null;
  }
}
