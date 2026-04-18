package com.notex;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.notex.mapper")
@EnableAsync
public class NotexApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotexApplication.class, args);
    }
}
