package me.ahoo.cosky.core;

/**
 * @author ahoo wang
 */
public interface Namespaced {
    String DEFAULT = Consts.COSKY + "-{default}";
    String SYSTEM = Consts.COSKY + "-{system}";

    /**
     * 获取当前上下文的命名空间
     *
     * @return
     */
    String getNamespace();
}
