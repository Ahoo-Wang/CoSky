package me.ahoo.cosky.examples.service.provider.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @author ahoo wang
 */
public interface HelloApi {
    String PATH = "hello";

    @GetMapping("hi/{name}")
    String hi(@PathVariable("name") String name);

}
