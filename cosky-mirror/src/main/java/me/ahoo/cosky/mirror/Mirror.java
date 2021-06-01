package me.ahoo.cosky.mirror;

import java.util.Map;

/**
 * @author ahoo wang
 */
public interface Mirror {
    String MIRROR_SOURCE = "cosky.mirror.source";
    String MIRROR_SOURCE_NACOS = "nacos";
    String MIRROR_SOURCE_COSKY = "cosky";

    String getSource();

    String getTarget();

    default void markRegisterSource(Map<String, String> metadata) {
        metadata.put(MIRROR_SOURCE, getSource());
    }

    default boolean shouldRegister(Map<String, String> metadata) {
        return !getTarget().equals(metadata.get(MIRROR_SOURCE));
    }
}
