package net.emberhold.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** EmberWeb — read-only admin dashboard + analytics (spec 07 §B, 09 §A). */
@SpringBootApplication
public class EmberWebApp {

    public static void main(String[] args) {
        SpringApplication.run(EmberWebApp.class, args);
    }
}
