package io.fabric8.maven.core.service.kubernetes;

import io.fabric8.maven.docker.config.RegistryAuthConfiguration;
import io.fabric8.maven.docker.util.DeepCopy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class JibBuildConfigurationUtil {

    private Map<String, String> envMap;

    private Map<String, String> credMap;

    private List<String> ports;

    private List<Path> dependencyList;

    private String from;

    private String to;

    public JibBuildConfigurationUtil() {
    }

    public Map<String, String> getEnvMap() {
        return envMap;
    }

    public Map<String, String> getCredMap() {return credMap; }

    public List<String> getPorts() {return ports;}

    public String getFrom() {return from;}

    public String getTo() {return to;}

    public List<Path> getDependencyList() {return dependencyList;}

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

        public Builder depList(List<Path> dependencyList) {
            configutil.dependencyList = dependencyList;
            return this;
        }

        public Builder from(String from) {
            configutil.from = from;
            return this;
        }

        public Builder target(String to) {
            configutil.to = to;
            return  this;
        }

        public JibBuildConfigurationUtil build() {
            return configutil;
        }

    }


}
