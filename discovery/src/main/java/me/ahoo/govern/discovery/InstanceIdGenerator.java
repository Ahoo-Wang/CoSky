package me.ahoo.govern.discovery;

import com.google.common.base.Strings;
import lombok.var;

/**
 * @author ahoo wang
 */
public interface InstanceIdGenerator {

    Default DEFAULT = new Default();

    String generate(Instance instance);

    class Default implements InstanceIdGenerator {
        /**
         * {@link  ServiceInstance#getServiceId()}@{@link ServiceInstance#getSchema()}#{@link ServiceInstance#getIp()}#{@link ServiceInstance#getPort()}}
         * order_service@http#127.0.0.1#8088
         */
        public final static String ID_FORMAT = "%s@%s#%s#%s";

        public String generate(Instance instance) {
            return Strings.lenientFormat(ID_FORMAT,
                    instance.getServiceId(),
                    instance.getSchema(),
                    instance.getIp(),
                    instance.getPort()
            );
        }

        public Instance of(String instanceId) {
            var instance = new Instance();
            instance.setInstanceId(instanceId);
            var serviceSpits = instanceId.split("@");
            if (serviceSpits.length != 2) {
                throw new IllegalArgumentException(Strings.lenientFormat("instanceId:[%s] format error.", instanceId));
            }
            instance.setServiceId(serviceSpits[0]);
            var instanceSpits = serviceSpits[1].split("#");
            if (instanceSpits.length != 3) {
                throw new IllegalArgumentException(Strings.lenientFormat("instanceId:[%s] format error.", instanceId));
            }
            instance.setSchema(instanceSpits[0]);
            instance.setIp(instanceSpits[1]);
            instance.setPort(Integer.parseInt(instanceSpits[2]));
            return instance;
        }


    }

}
