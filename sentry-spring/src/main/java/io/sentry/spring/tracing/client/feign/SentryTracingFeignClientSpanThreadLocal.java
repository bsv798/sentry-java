package io.sentry.spring.tracing.client.feign;

import io.sentry.ISpan;
import org.jetbrains.annotations.NotNull;

/**
 * Stores child span from {@link SentryTracingFeignClientRequestInterceptor} to be used in
 * {@link SentryTracingFeignClientWrapper}.
 */
public final class SentryTracingFeignClientSpanThreadLocal {

  private static ThreadLocal<ISpan> spanHolder = new ThreadLocal<>();

  private SentryTracingFeignClientSpanThreadLocal() {

  }

  public static void setSpan(final @NotNull ISpan span) {
    spanHolder.set(span);
  }

  public static ISpan getSpan() {
    return spanHolder.get();
  }

  public static ISpan clearSpan() {
    ISpan span = spanHolder.get();

    spanHolder.remove();

    return span;
  }
}
