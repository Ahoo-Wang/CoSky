package me.ahoo.cosky.core;

/**
 * @author ahoo wang
 */
public interface NamespacedContext extends Namespaced {

    /**
     * 全局命名空间上下文
     */
    NamespacedContext GLOBAL = of(Consts.COSKY);

    /**
     * 设置当前上下文的命名空间
     *
     * @param namespace
     */
    void setCurrentContextNamespace(String namespace);

    static NamespacedContext of(String namespace) {
        return new Default(namespace);
    }

    class Default implements NamespacedContext {
        private volatile String namespace;

        public Default(String namespace) {
            this.namespace = namespace;
        }

        /**
         * 设置当前上下文的命名空间
         *
         * @param namespace
         */
        @Override
        public void setCurrentContextNamespace(String namespace) {
            this.namespace = namespace;
        }

        /**
         * 获取当前上下文的命名空间
         *
         * @return
         */
        @Override
        public String getNamespace() {
            return namespace;
        }
    }
}
