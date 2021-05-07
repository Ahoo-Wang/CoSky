package me.ahoo.govern.discovery;

/**
 * @author ahoo wang
 */
public class ServiceStat {
    private String serviceId;
    private Integer instanceCount;

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public Integer getInstanceCount() {
        return instanceCount;
    }

    public void setInstanceCount(Integer instanceCount) {
        this.instanceCount = instanceCount;
    }
}
