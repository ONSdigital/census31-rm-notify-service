package uk.gov.ons.census.notifysvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.notifysvc.utils.Constants.RATE_LIMITER_EXCEPTION_MESSAGE;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.retry.RetryException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.retry.RetryContext;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.notifysvc.client.ExceptionManagerClient;
import uk.gov.ons.census.notifysvc.model.dto.api.ExceptionReportResponse;
import uk.gov.ons.census.notifysvc.model.dto.api.SkippedMessage;

@ExtendWith(MockitoExtension.class)
class ManagedMessageRecovererTest {
  private static final String TEST_MESSAGE_HASH =
      "90f56b5b3ffe9558a546af25a7256da4b2761864575f9d59c81b70629023465b";
  private static final String TEST_SERVICE = "Notify Service";

  @Mock private BasicAcknowledgeablePubsubMessage originalMessage;

  @Mock private ExceptionManagerClient exceptionManagerClient;

  @InjectMocks private ManagedMessageRecoverer underTest;

  @Test
  public void testRecover() {
    // Given
    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    RetryContext retryContext = testSetupTestRecover(exceptionReportResponse);

    // When
    MessageHandlingException thrownException =
        assertThrows(MessageHandlingException.class, () -> underTest.recover(retryContext));

    // Then
    verify(exceptionManagerClient)
        .reportException(
            eq(TEST_MESSAGE_HASH),
            eq(TEST_SERVICE),
            eq("TEST SUBSCRIPTION"),
            any(Throwable.class),
            anyString());
    verify(originalMessage, never()).nack();
    verify(originalMessage, never()).ack();
    assertThat(thrownException.getMessage())
        .isEqualTo("Cannot process this message at this time, but it will be retried");
  }

  @Test
  public void testRecoverLogIt() {
    // Given
    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    exceptionReportResponse.setLogIt(true);
    RetryContext retryContext = testSetupTestRecover(exceptionReportResponse);

    // When
    MessageHandlingException thrownException =
        assertThrows(MessageHandlingException.class, () -> underTest.recover(retryContext));

    // Then
    verify(exceptionManagerClient)
        .reportException(
            eq(TEST_MESSAGE_HASH),
            eq(TEST_SERVICE),
            eq("TEST SUBSCRIPTION"),
            any(RuntimeException.class),
            contains(
                "uk.gov.ons.census.notifysvc.messaging.ManagedMessageRecovererTest.testSetupTestRecover"));
    verify(originalMessage, never()).nack();
    verify(originalMessage, never()).ack();
    assertThat(thrownException.getMessage())
        .isEqualTo("Cannot process this message at this time, but it will be retried");
  }

  @Test
  public void testRecoverSkip() {
    // Given
    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    exceptionReportResponse.setSkipIt(true);
    RetryContext retryContext = testSetupTestRecover(exceptionReportResponse);

    // When
    underTest.recover(retryContext);

    // Then
    verify(exceptionManagerClient)
        .reportException(
            eq(TEST_MESSAGE_HASH),
            eq(TEST_SERVICE),
            eq("TEST SUBSCRIPTION"),
            any(RuntimeException.class),
            contains(
                "uk.gov.ons.census.notifysvc.messaging.ManagedMessageRecovererTest.testSetupTestRecover"));
    verify(originalMessage, never()).nack();
    verify(originalMessage, never()).ack();

    ArgumentCaptor<SkippedMessage> skippedMessageArgCapt =
        ArgumentCaptor.forClass(SkippedMessage.class);
    verify(exceptionManagerClient).storeMessageBeforeSkipping(skippedMessageArgCapt.capture());
    SkippedMessage skippedMessage = skippedMessageArgCapt.getValue();
    assertThat(skippedMessage.getMessageHash()).isEqualTo(TEST_MESSAGE_HASH);
    assertThat(skippedMessage.getMessagePayload()).isEqualTo("TEST PAYLOAD".getBytes());
    assertThat(skippedMessage.getSubscription()).isEqualTo("TEST SUBSCRIPTION");
    assertThat(skippedMessage.getService()).isEqualTo(TEST_SERVICE);
  }

  @Test
  public void testRecoverSkipFailureDoesNotAck() {
    // Given
    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    exceptionReportResponse.setSkipIt(true);
    RetryContext retryContext = testSetupTestRecover(exceptionReportResponse);

    doThrow(RuntimeException.class)
        .when(exceptionManagerClient)
        .storeMessageBeforeSkipping(any(SkippedMessage.class));

    // When
    MessageHandlingException thrownException =
        assertThrows(MessageHandlingException.class, () -> underTest.recover(retryContext));

    // Then
    verify(exceptionManagerClient)
        .reportException(
            eq(TEST_MESSAGE_HASH),
            eq(TEST_SERVICE),
            eq("TEST SUBSCRIPTION"),
            any(RuntimeException.class),
            contains(
                "uk.gov.ons.census.notifysvc.messaging.ManagedMessageRecovererTest.testSetupTestRecover"));
    verify(originalMessage, never()).nack();
    verify(originalMessage, never()).ack();
    assertThat(thrownException.getMessage())
        .isEqualTo("Cannot process this message at this time, but it will be retried");
  }

  @Test
  public void testRecoverPeek() {
    // Given
    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    exceptionReportResponse.setPeek(true);
    RetryContext retryContext = testSetupTestRecover(exceptionReportResponse);

    // When
    MessageHandlingException thrownException =
        assertThrows(MessageHandlingException.class, () -> underTest.recover(retryContext));

    // Then
    verify(exceptionManagerClient)
        .reportException(
            eq(TEST_MESSAGE_HASH),
            eq(TEST_SERVICE),
            eq("TEST SUBSCRIPTION"),
            any(RuntimeException.class),
            contains(
                "uk.gov.ons.census.notifysvc.messaging.ManagedMessageRecovererTest.testSetupTestRecover"));
    verify(originalMessage, never()).nack();
    verify(originalMessage, never()).ack();
    assertThat(thrownException.getMessage())
        .isEqualTo("Cannot process this message at this time, but it will be retried");

    verify(exceptionManagerClient).respondToPeek(TEST_MESSAGE_HASH, "TEST PAYLOAD".getBytes());
  }

  @Test
  void testRecoverUnwrapsRetryExceptionAndReportsUnderlyingCause() {
    RetryContext retryContext = mock(RetryContext.class);

    ProjectSubscriptionName projectSubscriptionName = mock(ProjectSubscriptionName.class);
    when(originalMessage.getProjectSubscriptionName()).thenReturn(projectSubscriptionName);
    when(projectSubscriptionName.getSubscription()).thenReturn("TEST SUBSCRIPTION");

    ByteString byteString = ByteString.copyFrom("TEST PAYLOAD".getBytes());
    PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(byteString).build();
    when(originalMessage.getPubsubMessage()).thenReturn(pubsubMessage);

    Message<?> message =
        MessageBuilder.withPayload("TEST PAYLOAD".getBytes())
            .setHeader("gcp_pubsub_original_message", originalMessage)
            .build();
    MessagingException messagingException =
        new MessageHandlingException(message, new RuntimeException("qid '555555' not found!"));

    when(retryContext.getLastThrowable())
        .thenReturn(new RetryException("retry exhausted", messagingException));

    when(exceptionManagerClient.reportException(
            anyString(), anyString(), anyString(), any(Throwable.class), anyString()))
        .thenReturn(new ExceptionReportResponse());

    MessageHandlingException thrownException =
        assertThrows(MessageHandlingException.class, () -> underTest.recover(retryContext));

    ArgumentCaptor<Throwable> causeCaptor = ArgumentCaptor.forClass(Throwable.class);
    verify(exceptionManagerClient)
        .reportException(
            eq(TEST_MESSAGE_HASH),
            eq(TEST_SERVICE),
            eq("TEST SUBSCRIPTION"),
            causeCaptor.capture(),
            anyString());

    assertThat(causeCaptor.getValue()).isInstanceOf(RuntimeException.class);
    assertThat(causeCaptor.getValue().getMessage()).isEqualTo("qid '555555' not found!");
    assertThat(thrownException.getMessage())
        .isEqualTo("Cannot process this message at this time, but it will be retried");
  }

  @Test
  void testRecoverWithAttributeAccessorUnwrapsRetryExceptionAndReportsUnderlyingCause() {
    AttributeAccessor context = mock(AttributeAccessor.class);

    ProjectSubscriptionName projectSubscriptionName = mock(ProjectSubscriptionName.class);
    when(originalMessage.getProjectSubscriptionName()).thenReturn(projectSubscriptionName);
    when(projectSubscriptionName.getSubscription()).thenReturn("TEST SUBSCRIPTION");

    ByteString byteString = ByteString.copyFrom("TEST PAYLOAD".getBytes());
    PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(byteString).build();
    when(originalMessage.getPubsubMessage()).thenReturn(pubsubMessage);

    Message<?> message =
        MessageBuilder.withPayload("TEST PAYLOAD".getBytes())
            .setHeader("gcp_pubsub_original_message", originalMessage)
            .build();
    MessagingException messagingException =
        new MessageHandlingException(message, new RuntimeException("qid '555555' not found!"));

    Throwable wrappedFailure = new RetryException("retry exhausted", messagingException);

    when(exceptionManagerClient.reportException(
            anyString(), anyString(), anyString(), any(Throwable.class), anyString()))
        .thenReturn(new ExceptionReportResponse());

    MessageHandlingException thrownException =
        assertThrows(
            MessageHandlingException.class, () -> underTest.recover(context, wrappedFailure));

    ArgumentCaptor<Throwable> causeCaptor = ArgumentCaptor.forClass(Throwable.class);
    verify(exceptionManagerClient)
        .reportException(
            eq(TEST_MESSAGE_HASH),
            eq(TEST_SERVICE),
            eq("TEST SUBSCRIPTION"),
            causeCaptor.capture(),
            anyString());

    assertThat(causeCaptor.getValue()).isInstanceOf(RuntimeException.class);
    assertThat(causeCaptor.getValue().getMessage()).isEqualTo("qid '555555' not found!");
    assertThat(thrownException.getMessage())
        .isEqualTo("Cannot process this message at this time, but it will be retried");
  }

  @Test
  void testRecoverPreservesDedicatedRateLimitedLogMessage() {
    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    exceptionReportResponse.setLogIt(true);
    RetryContext retryContext =
        testSetupTestRecover(
            exceptionReportResponse,
            new RuntimeException(
                RATE_LIMITER_EXCEPTION_MESSAGE + " email (from enriched email request event)",
                new RuntimeException("429")));

    ReflectionTestUtils.setField(underTest, "logStackTraces", false);

    Logger logger = (Logger) LoggerFactory.getLogger(ManagedMessageRecoverer.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);

    try {
      assertThrows(MessageHandlingException.class, () -> underTest.recover(retryContext));
    } finally {
      logger.detachAppender(listAppender);
      listAppender.stop();
    }

    assertThat(listAppender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getFormattedMessage())
                  .contains("Could not process message - rate limited");
            });
  }

  private RetryContext testSetupTestRecover(ExceptionReportResponse exceptionReportResponse) {
    return testSetupTestRecover(
        exceptionReportResponse, new RuntimeException(new RuntimeException("TEST EXCEPTION")));
  }

  private RetryContext testSetupTestRecover(
      ExceptionReportResponse exceptionReportResponse, Throwable reportedCause) {
    MessagingException messagingException = mock(MessagingException.class);
    when(messagingException.getCause()).thenReturn(reportedCause);

    RetryContext retryContext = mock(RetryContext.class);
    when(retryContext.getLastThrowable()).thenReturn(messagingException);

    @SuppressWarnings("unchecked")
    Message<?> message = (Message<?>) mock(Message.class);
    when(messagingException.getFailedMessage()).thenReturn((Message) message);

    MessageHeaders messageHeaders = mock(MessageHeaders.class);
    when(messageHeaders.get("gcp_pubsub_original_message")).thenReturn(originalMessage);
    when(message.getHeaders()).thenReturn(messageHeaders);

    ProjectSubscriptionName projectSubscriptionName = mock(ProjectSubscriptionName.class);
    when(originalMessage.getProjectSubscriptionName()).thenReturn(projectSubscriptionName);
    when(projectSubscriptionName.getSubscription()).thenReturn("TEST SUBSCRIPTION");

    ByteString byteString = ByteString.copyFrom("TEST PAYLOAD".getBytes());
    PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(byteString).build();
    when(originalMessage.getPubsubMessage()).thenReturn(pubsubMessage);

    when(exceptionManagerClient.reportException(
            anyString(), anyString(), anyString(), any(Throwable.class), anyString()))
        .thenReturn(exceptionReportResponse);
    return retryContext;
  }
}
