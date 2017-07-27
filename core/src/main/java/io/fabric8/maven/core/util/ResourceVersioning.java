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
package io.fabric8.maven.core.util;

/**
 * @author nicola
 * @since 24/05/2017
 */
public class ResourceVersioning {

    private String coreVersion;

    private String extensionsVersion;

    private String appsVersion;

    private String jobVersion;

    public ResourceVersioning() {
    }

    public ResourceVersioning(String coreVersion, String extensionsVersion, String appsVersion, String jobVersion) {
        this.coreVersion = coreVersion;
        this.extensionsVersion = extensionsVersion;
        this.appsVersion = appsVersion;
        this.jobVersion = jobVersion;
    }

    public String getCoreVersion() {
        return coreVersion;
    }

    public void setCoreVersion(String coreVersion) {
        this.coreVersion = coreVersion;
    }

    public String getExtensionsVersion() {
        return extensionsVersion;
    }

    public void setExtensionsVersion(String extensionsVersion) {
        this.extensionsVersion = extensionsVersion;
    }

    public String getAppsVersion() {
        return appsVersion;
    }

    public void setAppsVersion(String appsVersion) {
        this.appsVersion = appsVersion;
    }

    public String getJobVersion() {
        return jobVersion;
    }

    public void setJobVersion(String jobVersion) {
        this.jobVersion = jobVersion;
    }

    public ResourceVersioning withCoreVersion(String coreVersion) {
        ResourceVersioning c = copy();
        c.setCoreVersion(coreVersion);
        return c;
    }

    public ResourceVersioning withExtensionsVersion(String extensionsVersion) {
        ResourceVersioning c = copy();
        c.setExtensionsVersion(extensionsVersion);
        return c;
    }

    public ResourceVersioning withAppsVersion(String appsVersion) {
        ResourceVersioning c = copy();
        c.setAppsVersion(appsVersion);
        return c;
    }

    public ResourceVersioning withJobVersion(String jobVersion) {
        ResourceVersioning c = copy();
        c.setJobVersion(jobVersion);
        return c;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ResourceVersioning{");
        sb.append("coreVersion='").append(coreVersion).append('\'');
        sb.append(", extensionsVersion='").append(extensionsVersion).append('\'');
        sb.append(", appsVersion='").append(appsVersion).append('\'');
        sb.append(", jobVersion='").append(jobVersion).append('\'');
        sb.append('}');
        return sb.toString();
    }

    protected ResourceVersioning copy() {
        return new ResourceVersioning(coreVersion, extensionsVersion, appsVersion, jobVersion);
    }
}
