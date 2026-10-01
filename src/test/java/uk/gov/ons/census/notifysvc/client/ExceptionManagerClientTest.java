package uk.gov.ons.census.notifysvc.client;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.notifysvc.model.dto.api.ExceptionReportResponse;
import uk.gov.ons.census.notifysvc.model.dto.api.SkippedMessage;

class ExceptionManagerClientTest {

  private WireMockServer wireMockServer;
  private ExceptionManagerClient exceptionManagerClient;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    configureFor("localhost", wireMockServer.port());

    exceptionManagerClient = new ExceptionManagerClient();
    ReflectionTestUtils.setField(exceptionManagerClient, "scheme", "http");
    ReflectionTestUtils.setField(exceptionManagerClient, "host", "localhost");
    ReflectionTestUtils.setField(
        exceptionManagerClient, "port", String.valueOf(wireMockServer.port()));
  }

  @AfterEach
  void tearDown() {
    wireMockServer.stop();
  }

  @Test
  void reportExceptionPostsExpectedPayloadAndReturnsResponse() {
    stubFor(
        post(urlEqualTo("/reportexception"))
            .willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"peek\":true,\"logIt\":true,\"skipIt\":false}")));

    ExceptionReportResponse response =
        exceptionManagerClient.reportException(
            "hash-123", "notify-service", "sub-1", new RuntimeException("test"), "root-cause");

    assertThat(response.isPeek()).isTrue();
    assertThat(response.isLogIt()).isTrue();
    assertThat(response.isSkipIt()).isFalse();

    verify(
        postRequestedFor(urlEqualTo("/reportexception"))
            .withRequestBody(containing("\"messageHash\":\"hash-123\""))
            .withRequestBody(containing("\"service\":\"notify-service\""))
            .withRequestBody(containing("\"subscription\":\"sub-1\""))
            .withRequestBody(containing("\"exceptionClass\":\"java.lang.RuntimeException\""))
            .withRequestBody(containing("\"exceptionMessage\":\"test\""))
            .withRequestBody(containing("\"exceptionRootCause\":\"root-cause\"")));
  }

  @Test
  void respondToPeekPostsPeekReplyPayload() {
    stubFor(
        post(urlEqualTo("/peekreply"))
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.ok()));

    exceptionManagerClient.respondToPeek("hash-abc", "PAYLOAD".getBytes(StandardCharsets.UTF_8));

    verify(
        postRequestedFor(urlEqualTo("/peekreply"))
            .withRequestBody(containing("\"messageHash\":\"hash-abc\""))
            .withRequestBody(containing("\"messagePayload\":\"UEFZTE9BRA==\"")));
  }

  @Test
  void storeMessageBeforeSkippingPostsMessage() {
    stubFor(
        post(urlEqualTo("/storeskippedmessage"))
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.ok()));

    SkippedMessage skippedMessage = new SkippedMessage();
    skippedMessage.setMessageHash("hash-skip");
    skippedMessage.setService("notify-service");
    skippedMessage.setSubscription("subscription-name");
    skippedMessage.setRoutingKey("routing.key");
    skippedMessage.setContentType("application/json");
    skippedMessage.setHeaders(Map.of("x-header", "header-value"));

    exceptionManagerClient.storeMessageBeforeSkipping(skippedMessage);

    verify(
        postRequestedFor(urlEqualTo("/storeskippedmessage"))
            .withRequestBody(containing("\"messageHash\":\"hash-skip\""))
            .withRequestBody(containing("\"service\":\"notify-service\""))
            .withRequestBody(containing("\"subscription\":\"subscription-name\""))
            .withRequestBody(containing("\"routingKey\":\"routing.key\""))
            .withRequestBody(containing("\"contentType\":\"application/json\""))
            .withRequestBody(containing("\"x-header\":\"header-value\"")));
  }
}
