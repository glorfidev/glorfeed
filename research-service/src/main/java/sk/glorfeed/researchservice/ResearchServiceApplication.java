package sk.glorfeed.researchservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ResearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResearchServiceApplication.class, args);
    }
}
