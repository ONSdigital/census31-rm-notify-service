package uk.gov.ons.census.notifysvc.model.dto.api;

import java.util.UUID;
import lombok.Data;

@Data
public class RequestHeaderDTO {
  private String source;

  private String channel;

  private UUID correlationId;

  private String originatingUser;
}
