package uk.gov.ons.census.notifysvc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.Retryable;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;

class DefaultListenerSupportTest {
  private final DefaultListenerSupport underTest = new DefaultListenerSupport();

  @Test
  void shouldSupportLegacyRetryableCallbacks() {
    RetryContext retryContext = mock(RetryContext.class);
    @SuppressWarnings("unchecked")
    RetryCallback<Object, RuntimeException> retryCallback =
        (RetryCallback<Object, RuntimeException>) mock(RetryCallback.class);

    assertThat(underTest.open(retryContext, retryCallback)).isTrue();
    assertThatCode(
            () -> {
              underTest.onError(retryContext, retryCallback, new RuntimeException("failure"));
              underTest.close(retryContext, retryCallback, new RuntimeException("failure"));
            })
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAllowCoreRetryListenerDefaultsToBeCalled() {
    RetryListener coreRetryListener = underTest;
    RetryPolicy retryPolicy = mock(RetryPolicy.class);
    @SuppressWarnings("unchecked")
    Retryable<Object> retryable = (Retryable<Object>) mock(Retryable.class);

    assertThatCode(
            () -> {
              coreRetryListener.beforeRetry(retryPolicy, retryable);
              coreRetryListener.onRetryFailure(
                  retryPolicy, retryable, new RuntimeException("failure"));
            })
        .doesNotThrowAnyException();
  }

  @Test
  void shouldImplementBothRetryListenerContracts() {
    assertThat(underTest)
        .isInstanceOf(RetryListener.class)
        .isInstanceOf(org.springframework.retry.RetryListener.class);
  }
}
