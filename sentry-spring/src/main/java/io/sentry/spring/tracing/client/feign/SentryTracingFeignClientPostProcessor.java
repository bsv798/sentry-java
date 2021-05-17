package io.sentry.spring.tracing.client.feign;

import com.jakewharton.nopen.annotation.Open;
import feign.Client;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Wraps configured feign client in {@link SentryTracingFeignClientWrapper}.
 */
@Open
public class SentryTracingFeignClientPostProcessor implements BeanPostProcessor {

  @Override
  public Object postProcessAfterInitialization(final @NotNull Object bean, final @NotNull String beanName)
      throws BeansException {
    if (bean instanceof Client) {
      return new SentryTracingFeignClientWrapper((Client) bean);
    }

    return bean;
  }
}
