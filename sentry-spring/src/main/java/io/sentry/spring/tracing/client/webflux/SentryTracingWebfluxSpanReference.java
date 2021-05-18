package io.sentry.spring.tracing.client.webflux;

import io.sentry.ISpan;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Stores span between {@link SentryTracingWebfluxHttpClientBeanPostProcessor} events invocations.
 */
public final class SentryTracingWebfluxSpanReference extends AtomicReference<ISpan> {

  private static final long serialVersionUID = -8423413834657610406L;

}
