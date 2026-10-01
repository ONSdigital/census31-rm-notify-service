package uk.gov.ons.census.notifysvc.healthcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

public class HealthCheckTest {
  private final HealthCheck underTest = new HealthCheck();

  @TempDir Path tempDir;

  @Test
  public void testHappyPath() throws Exception {
    // Given
    Path healthFile = tempDir.resolve("health.txt");
    ReflectionTestUtils.setField(underTest, "fileName", healthFile.toString());

    // When
    underTest.updateFileWithCurrentTimestamp();

    // Then
    String fileContent = Files.readString(healthFile);
    assertThat(fileContent).isNotBlank();
    assertThat(OffsetDateTime.parse(fileContent)).isNotNull();
  }

  @Test
  public void testWriteFailureIsSurfaced() {
    // Given
    Path invalidPath = tempDir.resolve("missing-directory").resolve("health.txt");
    ReflectionTestUtils.setField(underTest, "fileName", invalidPath.toString());

    // When / Then
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> underTest.updateFileWithCurrentTimestamp());
    assertThat(exception)
        .hasMessageContaining("Failed to update health-check file")
        .hasCauseInstanceOf(java.io.IOException.class);
  }
}
