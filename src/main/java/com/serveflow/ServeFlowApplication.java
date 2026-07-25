package com.serveflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServeFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServeFlowApplication.class, args);
    }
}
