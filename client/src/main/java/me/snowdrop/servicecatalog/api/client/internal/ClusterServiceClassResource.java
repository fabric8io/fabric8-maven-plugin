/*
 * Copyright (C) 2018 Red Hat inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.snowdrop.servicecatalog.api.client.internal;

import io.fabric8.kubernetes.client.dsl.Resource;
import me.snowdrop.servicecatalog.api.model.ClusterServiceClass;
import me.snowdrop.servicecatalog.api.model.ClusterServicePlanList;
import me.snowdrop.servicecatalog.api.model.DoneableClusterServiceClass;
import me.snowdrop.servicecatalog.api.model.ServiceInstance;

public interface ClusterServiceClassResource extends Resource<ClusterServiceClass, DoneableClusterServiceClass> {

    ClusterServicePlanList listPlans();

    /**
     * Switch to the {@link ClusterServicePlanResource} with the specified external name.
     * @param externalName
     * @return the resource.
     * @throws IllegalArgumentException if no unique resource with externalName is found.
     */
    ClusterServicePlanResource usePlan(String externalName);

    ServiceInstance instantiate(String instanceName, String plan);
}
