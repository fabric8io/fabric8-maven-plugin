package io.fabric8.maven.enricher.api.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class MavenConfigurationExtractor {

    public static Map<String, Object> extract(Xpp3Dom root) {
        if (root == null) {
            return new HashMap<>();
        }

        return getElement(root);
    }

    private static Map<String, Object> getElement(Xpp3Dom element) {

        final Map<String, Object> conf = new HashMap<>();

        final Xpp3Dom[] currentElements = element.getChildren();

        for (Xpp3Dom currentElement: currentElements) {
            if (isSimpleType(currentElement)) {

                if (isAListOfElements(conf, currentElement)) {
                    addAsList(conf, currentElement);
                } else {
                    conf.put(currentElement.getName(), currentElement.getValue());
                }
            } else {
                conf.put(currentElement.getName(), getElement(currentElement));
            }
        }

        return conf;

    }

    private static void addAsList(Map<String, Object> conf, Xpp3Dom currentElement) {
        final Object insertedValue = conf.get(currentElement.getName());
        if (insertedValue instanceof List) {
            ((List) insertedValue).add(currentElement.getValue());
        } else {
            final List<String> list = new ArrayList<>();
            list.add((String) insertedValue);
            list.add(currentElement.getValue());
            conf.put(currentElement.getName(), list);
        }
    }

    private static boolean isAListOfElements(Map<String, Object> conf, Xpp3Dom currentElement) {
        return conf.containsKey(currentElement.getName());
    }

    private static boolean isSimpleType(Xpp3Dom currentElement) {
        return currentElement.getChildCount() == 0;
    }
}
