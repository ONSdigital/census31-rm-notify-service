package uk.gov.ons.census.notifysvc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.notifysvc.messaging.ManagedMessageRecoverer;

@ExtendWith(MockitoExtension.class)
class MessageConsumerConfigTest {

  @Mock private ManagedMessageRecoverer managedMessageRecoverer;

  @Mock private PubSubTemplate pubSubTemplate;

  @Test
  void shouldCreateRetryAdviceUsingRecovererCallback() {
    MessageConsumerConfig underTest =
        new MessageConsumerConfig(managedMessageRecoverer, pubSubTemplate);

    RequestHandlerRetryAdvice retryAdvice = underTest.retryAdvice();

    assertThat(
            org.springframework.test.util.ReflectionTestUtils.getField(
                retryAdvice, "recoveryCallback"))
        .isEqualTo(managedMessageRecoverer);
  }

  @Test
  void shouldRetryThreeTotalInvocations() {
    MessageConsumerConfig underTest =
        new MessageConsumerConfig(managedMessageRecoverer, pubSubTemplate);

    RequestHandlerRetryAdvice retryAdvice = underTest.retryAdvice();
    RetryTemplate retryTemplate =
        (RetryTemplate)
            java.util.Objects.requireNonNull(
                ReflectionTestUtils.getField(retryAdvice, "retryTemplate"));
    AtomicInteger attempts = new AtomicInteger();

    assertThrows(
        RetryException.class,
        () ->
            retryTemplate.execute(
                () -> {
                  attempts.incrementAndGet();
                  throw new IllegalStateException("test");
                }));

    assertThat(attempts).hasValue(3);
  }
}
