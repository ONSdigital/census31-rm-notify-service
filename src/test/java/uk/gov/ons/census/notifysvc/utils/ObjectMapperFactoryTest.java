package uk.gov.ons.census.notifysvc.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.notifysvc.model.dto.api.RequestPayloadDTO;
import uk.gov.ons.census.notifysvc.model.dto.event.EmailRequest;
import uk.gov.ons.census.notifysvc.model.dto.event.EventDTO;
import uk.gov.ons.census.notifysvc.model.dto.event.EventHeaderDTO;
import uk.gov.ons.census.notifysvc.model.dto.event.PayloadDTO;

class ObjectMapperFactoryTest {
  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.objectMapper();
  private static final String CASE_ID = "10000000001";
  private static final OffsetDateTime EVENT_TIME = OffsetDateTime.parse("2024-06-01T10:15:30Z");
  private static final String ACTION = "CREATE";

  @Test
  void shouldPreserveFieldOrderAndWriteDatesAsIsoStrings() {
    CaseEvent caseEvent = new CaseEvent(CASE_ID, EVENT_TIME, ACTION);

    assertThat(OBJECT_MAPPER.writeValueAsString(caseEvent))
        .isEqualTo(
            "{\"caseId\":\"10000000001\",\"eventTime\":\"2024-06-01T10:15:30Z\",\"action\":\"CREATE\"}");
  }

  @Test
  void shouldIgnoreUnknownPropertiesWhenReadingJson() {
    CaseEvent caseEvent =
        OBJECT_MAPPER.readValue(
            "{\"caseId\":\"10000000001\",\"eventTime\":\"2024-06-01T10:15:30Z\","
                + "\"action\":\"CREATE\",\"extraField\":\"ignored\"}",
            CaseEvent.class);

    assertThat(caseEvent).isEqualTo(new CaseEvent(CASE_ID, EVENT_TIME, ACTION));
  }

  @Test
  void shouldIgnoreTrailingTokensWhenReadingJson() {
    CaseEvent caseEvent =
        OBJECT_MAPPER.readValue(
            "{\"caseId\":\"10000000001\",\"eventTime\":\"2024-06-01T10:15:30Z\","
                + "\"action\":\"CREATE\"} true",
            CaseEvent.class);

    assertThat(caseEvent).isEqualTo(new CaseEvent(CASE_ID, EVENT_TIME, ACTION));
  }

  @Test
  void shouldRoundTripEventDtosContainingOffsetDateTime() {
    EventHeaderDTO eventHeaderDTO = new EventHeaderDTO();
    eventHeaderDTO.setVersion(Constants.OUTBOUND_EVENT_SCHEMA_VERSION);
    eventHeaderDTO.setTopic("event_email-request");
    eventHeaderDTO.setSource("NOTIFY_SERVICE");
    eventHeaderDTO.setChannel("RM");
    eventHeaderDTO.setDateTime(EVENT_TIME);
    eventHeaderDTO.setMessageId(UUID.fromString("926cdd2b-1454-4da9-8203-669618f81e33"));
    eventHeaderDTO.setCorrelationId(UUID.fromString("58004677-17ce-4bd7-ac7d-433e421dbcd5"));
    eventHeaderDTO.setOriginatingUser("notify-service@test.ons.gov.uk");
    eventHeaderDTO.setMessageType(EventType.ACTION_RULE_EMAIL_REQUEST);

    EmailRequest emailRequest = new EmailRequest();
    emailRequest.setCaseId(UUID.fromString("57f6195f-f830-478c-b021-3d797b70087d"));
    emailRequest.setEmail("notify.service@test.ons.gov.uk");
    emailRequest.setPackCode("P_A");
    emailRequest.setScheduled(false);
    emailRequest.setPersonalisation(Map.of("firstName", "Taylor"));

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setEmailRequest(emailRequest);

    EventDTO eventDTO = new EventDTO(eventHeaderDTO, payloadDTO);

    assertThat(OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsBytes(eventDTO), EventDTO.class))
        .usingRecursiveComparison()
        .isEqualTo(eventDTO);
  }

  @Test
  void shouldOmitNullFieldsFromJsonIncludeDtos() {
    assertThat(OBJECT_MAPPER.writeValueAsString(new PayloadDTO())).isEqualTo("{}");
    assertThat(OBJECT_MAPPER.writeValueAsString(new RequestPayloadDTO())).isEqualTo("{}");
  }

  private record CaseEvent(String caseId, OffsetDateTime eventTime, String action) {}
}
