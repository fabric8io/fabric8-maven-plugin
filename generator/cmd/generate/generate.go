/**
 * Copyright (C) 2011 Red Hat, Inc.
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
package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/kubernetes-incubator/service-catalog/pkg/apis/servicecatalog/v1beta1"
	"github.com/snowdrop/service-catalog-java-api/generator/pkg/schemagen"
	"log"
	"os"
	"reflect"
	"strings"
	"time"
)

//A Schema with the core types of the Service Catalog
type Schema struct {
	ClusterServiceBroker     v1beta1.ClusterServiceBroker
	ClusterServiceBrokerList v1beta1.ClusterServiceBrokerList
	ClusterServiceClass      v1beta1.ClusterServiceClass
	ClusterServiceClassList  v1beta1.ClusterServiceClassList
	ClusterServicePlan       v1beta1.ClusterServicePlan
	ClusterServicePlanList   v1beta1.ClusterServicePlanList
	ServiceInstance          v1beta1.ServiceInstance
	ServiceInstanceList      v1beta1.ServiceInstanceList
	ServiceBinding           v1beta1.ServiceBinding
	ServiceBindingList       v1beta1.ServiceBindingList
	ServiceBroker            v1beta1.ServiceBroker
	ServiceBrokerList        v1beta1.ServiceBrokerList
}

func main() {
	packages := []schemagen.PackageDescriptor{
		{"github.com/kubernetes-incubator/service-catalog/pkg/apis/servicecatalog/v1beta1", "servicecatalog.k8s.io", "me.snowdrop.servicecatalog.api.model", "servicecatalog_"},
	}

	typeMap := map[reflect.Type]reflect.Type{
		reflect.TypeOf(time.Time{}): reflect.TypeOf(""),
		reflect.TypeOf(struct{}{}):  reflect.TypeOf(""),
	}
	schema, err := schemagen.GenerateSchema(reflect.TypeOf(Schema{}), packages, typeMap)
	if err != nil {
		log.Fatal(err)
	}

	args := os.Args[1:]
	if len(args) < 1 || args[0] != "validation" {
		schema.Resources = nil
	}

	b, err := json.Marshal(&schema)
	if err != nil {
		log.Fatal(err)
	}
	result := string(b)
	result = strings.Replace(result, "\"additionalProperty\":", "\"additionalProperties\":", -1)
	var out bytes.Buffer
	err = json.Indent(&out, []byte(result), "", "  ")
	if err != nil {
		log.Fatal(err)
	}

	fmt.Println(out.String())
}
