package com.cardpool.backend;

import java.lang.management.ManagementFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@Slf4j
public class CardPoolGeneratorApplication {
    public static void main(String[] args) {
        ManagementFactory.getRuntimeMXBean()
                .getInputArguments()
                .forEach(arg -> log.info("VM ARG: {}", arg));
        ManagementFactory.getGarbageCollectorMXBeans()
                .forEach(gc -> log.info("GC={}", gc.getName()));
        SpringApplication.run(CardPoolGeneratorApplication.class, args);
    }
}