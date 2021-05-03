package io.sentry;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import org.jetbrains.annotations.NotNull;

final class NoOpSentryExecutorService implements ISentryExecutorService {
  private static final NoOpSentryExecutorService instance = new NoOpSentryExecutorService();

  private NoOpSentryExecutorService() {}

  public static ISentryExecutorService getInstance() {
    return instance;
  }

  @Override
  public @NotNull Future<?> submit(final @NotNull Runnable runnable) {
    return new FutureTask<>(() -> null);
  }

  @Override
  public void close(long timeoutMillis) {}
}
