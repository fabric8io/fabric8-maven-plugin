/**
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
package io.fabric8.maven.core.util;

/**
 */
// TODO-F8SPEC : Should be move the to AppCatalog mojo and must not be in the general available util package
// Also consider whether the Contstants class pattern makes (should probably change to real enums ???)
public class Constants {
    public static final String RESOURCE_SOURCE_URL_ANNOTATION = "maven.fabric8.io/source-url";
    public static final String RESOURCE_APP_CATALOG_ANNOTATION = "maven.fabric8.io/app-catalog";
}
