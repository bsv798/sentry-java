package io.sentry.spring.tracing.client.feign;

import com.jakewharton.nopen.annotation.Open;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.sentry.IHub;
import io.sentry.ISpan;
import io.sentry.SentryTraceHeader;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * Creates child span and sets tracing header.
 */
@Open
public class SentryTracingFeignClientRequestInterceptor implements RequestInterceptor {

  private final @NotNull IHub hub;

  public SentryTracingFeignClientRequestInterceptor(final @NotNull IHub hub) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
  }

  @Override
  public void apply(final @NotNull RequestTemplate template) {
    final ISpan activeSpan = hub.getSpan();

    if (activeSpan != null) {
      final ISpan span = activeSpan.startChild("http.client");
      span.setDescription(template.method() + " " + template.url());

      final SentryTraceHeader sentryTraceHeader = span.toSentryTrace();

      template.header(sentryTraceHeader.getName(), Collections.singleton(sentryTraceHeader.getValue()));

      SentryTracingFeignClientSpanThreadLocal.setSpan(span);
    }
  }
}
