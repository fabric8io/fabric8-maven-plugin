package io.fabric8.maven.core.config;

import java.util.ArrayList;
import java.util.List;

public class ConfigMap {

    private List<ConfigMapEntry> elements = new ArrayList<>();

    public void addElement(ConfigMapEntry configMapEntry) {
        this.elements.add(configMapEntry);
    }

    public List<ConfigMapEntry> getElements() {
        return elements;
    }
}
