package me.ahoo.cosky.examples.service.provider.controller;

import me.ahoo.cosky.examples.service.provider.api.HelloApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ahoo wang
 */
@RestController
@RequestMapping(HelloApi.PATH)
public class HelloController implements HelloApi {

    @Override
    public String hi(String name) {
        return "hello " + name;
    }
}
