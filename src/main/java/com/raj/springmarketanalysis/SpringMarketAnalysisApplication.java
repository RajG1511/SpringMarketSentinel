package com.raj.springmarketanalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableScheduling
@SpringBootApplication
public class SpringMarketAnalysisApplication {

    public static void main(String[] args) {

        SpringApplication.run(SpringMarketAnalysisApplication.class, args);
    }

}
