package io.sentry.spring.tracing.client.webflux;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.CustomSamplingContext;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.SentryLevel;
import io.sentry.SentryTraceHeader;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.exception.InvalidSentryTraceHeaderException;
import io.sentry.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Creates {@link ITransaction} around reactive HTTP request executions.
 */
@Open
public class SentryTracingWebfluxFilter implements WebFilter {

  private static final String TRANSACTION_OP = "http.server";

  private final @NotNull IHub hub;
  private final @NotNull SentryTracingWebfluxRequestResolver requestResolver;
  private final @NotNull SentryTracingWebfluxTransactionNameProvider transactionNameProvider;

  public SentryTracingWebfluxFilter(final @NotNull IHub hub) {
    this(hub, new SentryTracingWebfluxRequestResolver(hub), new SentryTracingWebfluxTransactionNameProvider());
  }

  public SentryTracingWebfluxFilter(final @NotNull IHub hub,
      final @NotNull SentryTracingWebfluxRequestResolver requestResolver,
      final @NotNull SentryTracingWebfluxTransactionNameProvider transactionNameProvider) {
    this.hub = Objects.requireNonNull(hub, "hub is required");
    this.requestResolver = Objects.requireNonNull(requestResolver, "request resolver is required");
    this.transactionNameProvider = Objects.requireNonNull(transactionNameProvider,
      "transaction name provider is required");
  }

  @Override
  public Mono<Void> filter(final @NotNull ServerWebExchange exchange,
      final @NotNull WebFilterChain chain) {
    if (hub.isEnabled()) {
      final ServerHttpRequest request = exchange.getRequest();
      final String sentryTraceHeader = request.getHeaders().getFirst(SentryTraceHeader.SENTRY_TRACE_HEADER);

      // at this stage we are not able to get real transaction name
      final ITransaction transaction = startTransaction(request, sentryTraceHeader);

      try {
        return chain.filter(exchange)
          .doOnSuccess(doOnSuccessConsumer(exchange, transaction))
          .doOnError(doOnErrorConsumer(exchange, transaction))
          .contextWrite(contextWriteFunction(transaction));
      } catch (Throwable e) {
        doOnResponse(exchange, transaction);

        throw e;
      }
    } else {
      return chain.filter(exchange);
    }
  }

  private ITransaction startTransaction(final @NotNull ServerHttpRequest request,
      final @NotNull String sentryTraceHeader) {
    final String name = request.getMethod() + " " + request.getPath();
    final CustomSamplingContext customSamplingContext = new CustomSamplingContext();

    customSamplingContext.set("request", request);

    if (sentryTraceHeader != null) {
      try {
        final TransactionContext contexts =
          TransactionContext.fromSentryTrace(
            name, TRANSACTION_OP, new SentryTraceHeader(sentryTraceHeader));
        final ITransaction transaction = hub.startTransaction(contexts, customSamplingContext);

        hub.configureScope(scope -> scope.setTransaction(transaction));

        return transaction;
      } catch (InvalidSentryTraceHeaderException e) {
        hub.getOptions()
          .getLogger()
          .log(SentryLevel.DEBUG, "Failed to parse Sentry trace header: %s", e.getMessage());
      }
    }

    final ITransaction transaction =
      hub.startTransaction(name, TRANSACTION_OP, customSamplingContext);

    hub.configureScope(scope -> scope.setTransaction(transaction));

    return transaction;
  }

  private Consumer<Void> doOnSuccessConsumer(final @NotNull ServerWebExchange exchange,
      final @NotNull ITransaction transaction) {
    return (x) -> doOnResponse(exchange, transaction);
  }

  private Consumer<Throwable> doOnErrorConsumer(final @NotNull ServerWebExchange exchange,
      final @NotNull ITransaction transaction) {
    return (x) -> doOnResponse(exchange, transaction);
  }

  private void doOnResponse(final @NotNull ServerWebExchange exchange,
      final @NotNull ITransaction transaction) {
    // after all filters run, templated path pattern is available in request attribute
    final ServerHttpRequest request = exchange.getRequest();
    final String transactionName = transactionNameProvider.provideTransactionName(request, exchange);
    // if transaction name is not resolved, the request has not been processed by a controller
    // and we should not report it to Sentry
    if (transactionName != null) {
      transaction.setName(transactionName);
      transaction.setOperation(TRANSACTION_OP);
      transaction.setRequest(requestResolver.resolveSentryRequest(request));
      transaction.setStatus(SpanStatus.fromHttpStatusCode(exchange.getResponse().getRawStatusCode()));
      transaction.finish();
    }
  }

  private Function<Context, Context> contextWriteFunction(final @NotNull ITransaction transaction) {
    return (context) -> context.put(ITransaction.class, transaction)
      .put(SentryTracingWebfluxSpanReference.class, new SentryTracingWebfluxSpanReference());
  }
}
