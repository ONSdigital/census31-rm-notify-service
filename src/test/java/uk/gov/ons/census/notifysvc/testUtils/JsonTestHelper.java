package uk.gov.ons.census.notifysvc.testUtils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.ons.census.notifysvc.utils.ObjectMapperFactory;

public class JsonTestHelper {
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();

  public static String convertObjectToJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JacksonException e) {
      throw new RuntimeException("Failed converting Object To Json", e);
    }
  }
}
