package com.lisa.curriculum;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CurriculumApplication {
    public static void main(String[] args) {
        SpringApplication.run(CurriculumApplication.class, args);
    }
}
