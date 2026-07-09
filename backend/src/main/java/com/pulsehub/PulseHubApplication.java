package com.pulsehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PulseHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(PulseHubApplication.class, args);
    }

}
