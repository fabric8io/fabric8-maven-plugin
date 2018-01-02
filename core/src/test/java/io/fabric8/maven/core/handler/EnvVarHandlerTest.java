/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.core.handler;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.maven.core.extenvvar.ExternalEnvVarHandler;
import org.apache.maven.project.MavenProject;

import mockit.Expectations;
import mockit.Mocked;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

public class EnvVarHandlerTest {

    @Mocked
    private ExternalEnvVarHandler externalEnvVarHandler;

    final MavenProject project = new MavenProject();

    Map<String, String> ret = new HashMap<>();

    Map env = new HashMap();

    //it will be present in all
    EnvVar var4 = new EnvVarBuilder().withName("KUBERNETES_NAMESPACE").
            withNewValueFrom()
            .withNewFieldRef().withFieldPath("metadata.namespace")
            .endFieldRef()
            .endValueFrom()
            .build();

    @Test
    public void emptyEnvVarHandlerTest(){
        //Empty Environment Variable in Config
        EnvVarHandler envVarHandler = new EnvVarHandler(project);

        env.clear();

        new Expectations(){{
            externalEnvVarHandler.getExportedEnvironmentVariables(project,env);
            ret.putAll(env);
            result = ret;
        }};

        List<EnvVar> envVars = envVarHandler.getEnvironmentVariables(env);

        assertNotNull(envVars);
        assertEquals(1,envVars.size());
        assertEquals("KUBERNETES_NAMESPACE", envVars.get(0).getName());
        assertTrue(envVars.contains(var4));
    }

    @Test
    public void envVarHandlerTest(){
        //Some Environment Variable in Config
        EnvVar var1 = new EnvVarBuilder().withName("TEST1").withValue("OK").build();
        EnvVar var2 = new EnvVarBuilder().withName("TEST2").withValue("DONE").build();
        EnvVar var3 = new EnvVarBuilder().withName("TEST3").withValue("").build();

        env.clear();
        env.put(var1.getName(), var1.getValue());
        env.put(var2.getName(), var2.getValue());
        env.put(var3.getName(), var3.getValue());

        EnvVarHandler envVarHandler = new EnvVarHandler(project);

        new Expectations(){{
            externalEnvVarHandler.getExportedEnvironmentVariables(project,env);
            ret.putAll(env);
            result = ret;
        }};

        List<EnvVar> envVars = envVarHandler.getEnvironmentVariables(env);

        assertNotNull(envVars);
        assertEquals(4,envVars.size());
        assertTrue(envVars.contains(var1));
        assertTrue(envVars.contains(var2));
        assertTrue(envVars.contains(var3));
        assertTrue(envVars.contains(var4));
    }

    @Test
    public void envVarHandlerWithoutNameTest(){
        //Environment Variable without name in Config
        EnvVar var1 = new EnvVarBuilder().withName(null).withValue("OK").build();

        env.clear();
        env.put(null, var1.getValue());

        EnvVarHandler envVarHandler = new EnvVarHandler(project);

        new Expectations(){{
            externalEnvVarHandler.getExportedEnvironmentVariables(project,env);
            ret.put(null,"OK");
            result = ret;
        }};

        List<EnvVar> envVars = envVarHandler.getEnvironmentVariables(env);

        assertNotNull(envVars);
        assertEquals(1,envVars.size());
        assertTrue(!envVars.contains(var1));
        assertEquals("KUBERNETES_NAMESPACE", envVars.get(0).getName());
        assertTrue(envVars.contains(var4));
    }
}