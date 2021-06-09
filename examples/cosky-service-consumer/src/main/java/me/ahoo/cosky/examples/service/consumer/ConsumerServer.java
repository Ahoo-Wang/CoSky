package me.ahoo.cosky.examples.service.consumer;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.cosky.examples.service.provider.client.HelloClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @author ahoo wang
 */
@Slf4j
@SpringBootApplication
@EnableFeignClients(basePackages = {"me.ahoo.cosky.examples.service.provider.client"})
public class ConsumerServer implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.run(ConsumerServer.class, args);
    }

    @Autowired
    private HelloClient helloClient;

    /**
     * Callback used to run the bean.
     *
     * @param args incoming main method arguments
     * @throws Exception on error
     */
    @Override
    public void run(String... args) throws Exception {
        var rpcResponse = helloClient.hi("consumer");
        log.warn(rpcResponse);
    }
}
