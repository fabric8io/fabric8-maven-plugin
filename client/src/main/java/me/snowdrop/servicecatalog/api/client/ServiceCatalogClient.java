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
package me.snowdrop.servicecatalog.api.client;


import io.fabric8.kubernetes.client.Client;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import me.snowdrop.servicecatalog.api.client.internal.ClusterServiceBrokerResource;
import me.snowdrop.servicecatalog.api.client.internal.ClusterServiceClassResource;
import me.snowdrop.servicecatalog.api.client.internal.ClusterServicePlanResource;
import me.snowdrop.servicecatalog.api.client.internal.ServiceBindingResource;
import me.snowdrop.servicecatalog.api.client.internal.ServiceInstanceResource;
import me.snowdrop.servicecatalog.api.model.ClusterServiceBroker;
import me.snowdrop.servicecatalog.api.model.ClusterServiceBrokerList;
import me.snowdrop.servicecatalog.api.model.ClusterServiceClass;
import me.snowdrop.servicecatalog.api.model.ClusterServiceClassList;
import me.snowdrop.servicecatalog.api.model.ClusterServicePlan;
import me.snowdrop.servicecatalog.api.model.ClusterServicePlanList;
import me.snowdrop.servicecatalog.api.model.DoneableClusterServiceBroker;
import me.snowdrop.servicecatalog.api.model.DoneableClusterServiceClass;
import me.snowdrop.servicecatalog.api.model.DoneableClusterServicePlan;
import me.snowdrop.servicecatalog.api.model.DoneableServiceBinding;
import me.snowdrop.servicecatalog.api.model.DoneableServiceInstance;
import me.snowdrop.servicecatalog.api.model.ServiceBinding;
import me.snowdrop.servicecatalog.api.model.ServiceBindingList;
import me.snowdrop.servicecatalog.api.model.ServiceInstance;
import me.snowdrop.servicecatalog.api.model.ServiceInstanceList;

public interface ServiceCatalogClient extends Client {

    NonNamespaceOperation<ClusterServiceBroker, ClusterServiceBrokerList, DoneableClusterServiceBroker, ClusterServiceBrokerResource> clusterServiceBrokers();

    NonNamespaceOperation<ClusterServiceClass, ClusterServiceClassList, DoneableClusterServiceClass, ClusterServiceClassResource> clusterServiceClasses();

    NonNamespaceOperation<ClusterServicePlan, ClusterServicePlanList, DoneableClusterServicePlan, ClusterServicePlanResource> clusterServicePlans();

    MixedOperation<ServiceInstance, ServiceInstanceList, DoneableServiceInstance, ServiceInstanceResource> serviceInstances();

    MixedOperation<ServiceBinding, ServiceBindingList, DoneableServiceBinding, ServiceBindingResource> serviceBindings();
}
