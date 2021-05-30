package me.ahoo.cosky.config;

/**
 * @author ahoo wang
 */
public interface ConfigChangedListener {
    String OP_SET = "set";
    String OP_ROLLBACK = "rollback";
    String OP_REMOVE = "remove";

    void onChange(NamespacedConfigId namespacedConfigId, String op);

}
