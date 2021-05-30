package me.ahoo.cosky.config;

import com.google.common.base.Objects;

/**
 * @author ahoo wang
 */
public class ConfigHistory extends Config {
    /**
     * set
     * remove
     * rollback
     */
    private String op;
    private Long opTime;

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public Long getOpTime() {
        return opTime;
    }

    public void setOpTime(Long opTime) {
        this.opTime = opTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfigHistory)) return false;
        if (!super.equals(o)) return false;
        ConfigHistory that = (ConfigHistory) o;
        return Objects.equal(op, that.op) && Objects.equal(opTime, that.opTime);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), op, opTime);
    }
}
