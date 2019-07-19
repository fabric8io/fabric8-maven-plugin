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
package me.snowdrop.servicecatalog.api.examples;

import me.snowdrop.servicecatalog.api.model.ClusterServicePlanList;
import me.snowdrop.servicecatalog.api.model.ClusterServicePlan;
import me.snowdrop.servicecatalog.api.client.ServiceCatalogClient;


public class ListServicePlans {

  public static void main(String[] args) {
      ServiceCatalogClient client = ClientFactory.newClient(args);
      ClusterServicePlanList list = client.clusterServicePlans().list();
      ClusterServicePlan c;
      System.out.println("Listing Cluster Service Plans:");
      list.getItems().stream()
          .forEach(b -> {
                  System.out.println(b.getSpec().getClusterServiceBrokerName() + "\t\t\t" + b.getSpec().getClusterServiceClassRef() + "\t\t\t" + b.getSpec().getExternalName());
              });
      System.out.println("Done");
  }
}
