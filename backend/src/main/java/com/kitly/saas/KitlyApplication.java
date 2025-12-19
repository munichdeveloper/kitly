package com.kitly.saas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KitlyApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(KitlyApplication.class, args);
    }
}
