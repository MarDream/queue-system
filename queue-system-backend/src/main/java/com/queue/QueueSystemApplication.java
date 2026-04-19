package com.queue;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
@MapperScan("com.queue.mapper")
@EnableScheduling
public class QueueSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueueSystemApplication.class, args);
    }
}
