package me.ahoo.govern.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * @author ahoo wang
 */
@EnableSwagger2
@EnableScheduling
@SpringBootApplication
public class RestApiServer {

    public static void main(String[] args) {
        SpringApplication.run(RestApiServer.class, args);
    }

}
