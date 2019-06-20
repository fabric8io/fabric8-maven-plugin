package io.fabric8.maven.core.service.kubernetes;

import io.fabric8.maven.docker.config.RegistryAuthConfiguration;
import io.fabric8.maven.docker.util.DeepCopy;

import java.util.List;
import java.util.Map;

public class JibBuildConfigurationUtil {

    private Map<String, String> envMap;

    private Map<String, String> credMap;

    private List<String> ports;

    public JibBuildConfigurationUtil() {
    }

    public Map<String, String> getEnvMap() {
        return envMap;
    }

    public Map<String, String> getCredMap() {return credMap; }

    public List<String> getPorts() {return ports;}

    public static class Builder {
        private final JibBuildConfigurationUtil configutil;

        public Builder() {
            this(null);
        }

        public Builder(JibBuildConfigurationUtil that) {
            if (that == null) {
                this.configutil = new JibBuildConfigurationUtil();
            } else {
                this.configutil = DeepCopy.copy(that);
            }
        }

        public Builder envMap(Map<String, String> envMap) {
            configutil.envMap = envMap;
            return this;
        }

        public Builder credMap(Map<String, String> credMap) {
            configutil.credMap = credMap;
            return this;
        }

        public Builder ports(List<String> ports) {
            configutil.ports = ports;
            return this;
        }

    }


}
