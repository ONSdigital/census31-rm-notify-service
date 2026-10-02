package uk.gov.ons.census.notifysvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@EnableConfigurationProperties
@EntityScan("uk.gov.ons.census.common.model.entity")
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
