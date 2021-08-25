/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosky.core;

import com.google.common.base.Strings;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * @author ahoo wang
 */
public class ReactorTests {
    @Test
    public void test() {
        Mono<String> source = Mono.<String>create(monoSink -> {
            String uuidStr = UUID.randomUUID().toString();
            System.out.println(Strings.lenientFormat("%s - monoSink.success:%s", Thread.currentThread().getName(), uuidStr));
            monoSink.success(uuidStr);
        }).subscribeOn(Schedulers.boundedElastic()).cache().publishOn(Schedulers.boundedElastic());

        source.subscribe(s -> {
            System.out.println(Strings.lenientFormat("1 - %s - monoSink.success:%s", Thread.currentThread().getName(), s));
        });
        source.subscribe(s -> {
            System.out.println(Strings.lenientFormat("2 - %s - monoSink.success:%s", Thread.currentThread().getName(), s));
        });

//        Mono.deferContextual((contextView -> {
//                    System.out.println("deferContextual:before:" + contextView.getOrDefault("trx", ""));
//                    return Mono.just("yes");
//                }))
//                .contextWrite((context) -> {
//                    System.out.println("trx commit-:before:" + context.getOrDefault("trx", ""));
//                    return context.put("trx", "commit");
//                })
//                .transformDeferredContextual((original, context) -> {
//                    System.out.println("trx commit-:before:" + context.getOrDefault("trx", ""));
//                    return original;
//                })
//                .doOnNext((msg) -> {
//                    System.out.println("do biz1");
//                })
//                .doOnNext((msg) -> {
//                    System.out.println("do biz2");
//                })
//                .contextWrite(context -> {
//                    System.out.println("trx start-:before:" + context.getOrDefault("trx", ""));
//                    return context.put("trx", "start");
//                })
//                .block();
    }

}
