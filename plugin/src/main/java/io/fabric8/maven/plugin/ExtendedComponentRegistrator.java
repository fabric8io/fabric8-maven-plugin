package io.fabric8.maven.plugin;
/*
 *
 * Copyright 2016 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Map;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.configurator.*;
import org.codehaus.plexus.component.configurator.converters.ConfigurationConverter;
import org.codehaus.plexus.component.configurator.converters.composite.MapConverter;
import org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;

/**
 * @author roland
 * @since 28/07/16
 */
//@Component(role = ComponentConfigurator.class, hint = "basic")
public class ExtendedComponentRegistrator extends BasicComponentConfigurator implements Initializable {
    @Override
    public void initialize() throws InitializationException {
        converterLookup.registerConverter(new MapHandlerConverter());
    }

    private static class MapHandlerConverter extends MapConverter {

        @Override
        public boolean canConvert(Class type) {
            return MapHandler.class.isAssignableFrom(type);
        }

        @Override
        public Object fromConfiguration(ConverterLookup converterLookup, PlexusConfiguration configuration, Class type, Class baseType, ClassLoader classLoader, ExpressionEvaluator expressionEvaluator) throws ComponentConfigurationException {
            return (Map) super.fromConfiguration(converterLookup, configuration, Map.class, baseType, classLoader, expressionEvaluator);
        }

        @Override
        public Object fromConfiguration(ConverterLookup converterLookup, PlexusConfiguration configuration, Class type, Class baseType, ClassLoader classLoader, ExpressionEvaluator expressionEvaluator, ConfigurationListener listener) throws ComponentConfigurationException {
            return (Map) super.fromConfiguration(converterLookup, configuration, Map.class, baseType, classLoader, expressionEvaluator, listener);
        }

    }
}

