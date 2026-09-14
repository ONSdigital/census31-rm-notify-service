package uk.gov.ons.census.notifysvc.utils;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class ObjectMapperFactory {

  private ObjectMapperFactory() {}

  public static ObjectMapper objectMapper() {
    return JsonMapper.builder()
        // Jackson 3 enables SORT_PROPERTIES_ALPHABETICALLY by default. Disabled
        // so every published event keeps its declaration order and this release
        // is contract-neutral. MapperFeature is builder-only in Jackson 3.
        .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        // Unchanged behaviour, new package.
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        // Jackson 3 turns FAIL_ON_TRAILING_TOKENS ON by default.
        .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();
  }
}
