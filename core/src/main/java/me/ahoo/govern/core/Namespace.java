package me.ahoo.govern.core;

import com.google.common.base.Objects;

/**
 * @author ahoo wang
 */
@Deprecated
public class Namespace {
    private String namespace;
    private Long createTime;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Namespace)) return false;
        Namespace namespace1 = (Namespace) o;
        return Objects.equal(namespace, namespace1.namespace) && Objects.equal(createTime, namespace1.createTime);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(namespace, createTime);
    }
}
