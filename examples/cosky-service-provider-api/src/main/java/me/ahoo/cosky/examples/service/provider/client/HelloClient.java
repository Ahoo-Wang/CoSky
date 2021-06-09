package me.ahoo.cosky.examples.service.provider.client;

import me.ahoo.cosky.examples.service.provider.Constants;
import me.ahoo.cosky.examples.service.provider.api.HelloApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author ahoo wang
 */
@FeignClient(name = Constants.SERVICE_NAME, contextId = Constants.SERVICE_NAME_PREFIX + "HelloClient", path = HelloApi.PATH)
public interface HelloClient extends HelloApi {
}
