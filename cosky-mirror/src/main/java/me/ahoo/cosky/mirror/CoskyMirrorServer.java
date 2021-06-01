package me.ahoo.cosky.mirror;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author ahoo wang
 */
@EnableScheduling
@SpringBootApplication
public class CoskyMirrorServer {

    public static void main(String[] args) {
        SpringApplication.run(CoskyMirrorServer.class, args);
    }

}
