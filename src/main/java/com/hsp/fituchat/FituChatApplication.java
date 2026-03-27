package com.hsp.fituchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FituChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(FituChatApplication.class, args);
    }
}
