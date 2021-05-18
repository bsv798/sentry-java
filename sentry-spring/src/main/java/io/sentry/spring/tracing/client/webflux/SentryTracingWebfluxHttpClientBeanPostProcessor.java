package io.sentry.spring.tracing.client.webflux;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

import java.util.function.BiConsumer;

/**
 * Adds tracing listeners to {@link HttpClient}.
 */
@Open
public class SentryTracingWebfluxHttpClientBeanPostProcessor implements BeanPostProcessor {

  @Override
  public Object postProcessAfterInitialization(final @NotNull Object bean, final @NotNull String beanName)
      throws BeansException {
    if (bean instanceof HttpClient) {
      return instrumentBean((HttpClient) bean);
    }

    return bean;
  }

  public HttpClient instrumentBean(final @NotNull HttpClient bean) {
    return bean.doOnRequest(doOnRequestConsumer())
      .doOnResponse(doOnResponseConsumer());
  }

  @SuppressWarnings("UnnecessaryLambda")
  private BiConsumer<HttpClientRequest, Connection> doOnRequestConsumer() {
    return (req, conn) -> {
      final ISpan activeSpan = req.currentContextView().get(ITransaction.class);

      if (activeSpan != null) {
        final ISpan span = activeSpan.startChild("http.client");

        span.setDescription(req.method() + " " + req.uri());

        final SentryTraceHeader sentryTraceHeader = span.toSentryTrace();

        req.header(sentryTraceHeader.getName(), sentryTraceHeader.getValue());
        req.currentContextView().get(SentryTracingWebfluxSpanReference.class).set(span);
      }
    };
  }

  @SuppressWarnings("UnnecessaryLambda")
  private BiConsumer<HttpClientResponse, Connection> doOnResponseConsumer() {
    return (resp, conn) -> {
      final ISpan span = resp.currentContextView().get(SentryTracingWebfluxSpanReference.class).getAndSet(null);

      if (span != null) {
        try {
          span.setStatus(SpanStatus.fromHttpStatusCode(resp.status().code()));
        } finally {
          span.finish();
        }
      }
    };
  }
}
