package io.k2dv.garden;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GardenApplication {

  public static void main(String[] args) {
    SpringApplication.run(GardenApplication.class, args);
  }

}
