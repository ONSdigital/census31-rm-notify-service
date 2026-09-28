package uk.gov.ons.census.notifysvc.model.dto.api;

import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class SmsFulfilment {
  private UUID caseId;

  private String phoneNumber;

  private String packCode;

  private Object uacMetadata;

  private Map<String, String> personalisation;
}
