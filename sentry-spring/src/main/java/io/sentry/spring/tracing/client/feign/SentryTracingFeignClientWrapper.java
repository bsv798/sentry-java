package io.sentry.spring.tracing.client.feign;

import com.jakewharton.nopen.annotation.Open;
import feign.Client;
import feign.Request;
import feign.Response;
import io.sentry.ISpan;
import io.sentry.SpanStatus;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Finishes child span created at {@link SentryTracingFeignClientRequestInterceptor}.
 */
@Open
public class SentryTracingFeignClientWrapper implements Client {

  private final @NotNull Client client;

  public SentryTracingFeignClientWrapper(final @NotNull Client client) {
    this.client = Objects.requireNonNull(client, "feign client is required");
  }

  @Override
  public Response execute(final @NotNull Request request, final @NotNull Request.Options options)
      throws IOException {
    final ISpan span = SentryTracingFeignClientSpanThreadLocal.clearSpan();

    if (span == null) {
      return client.execute(request, options);
    }

    try {
      final Response response = client.execute(request, options);

      span.setStatus(SpanStatus.fromHttpStatusCode(response.status()));

      return response;
    } finally {
      span.finish();
    }
  }
}
