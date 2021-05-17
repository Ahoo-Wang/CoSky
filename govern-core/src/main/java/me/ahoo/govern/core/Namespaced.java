package me.ahoo.govern.core;

/**
 * @author ahoo wang
 */
public interface Namespaced {
    String DEFAULT = "govern-{default}";
    String SYSTEM = "govern-{system}";

    /**
     * 获取当前上下文的命名空间
     *
     * @return
     */
    String getNamespace();
}
