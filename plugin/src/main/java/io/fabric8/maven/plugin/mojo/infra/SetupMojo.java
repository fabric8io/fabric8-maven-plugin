/*
 * Copyright 2005-2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.plugin.mojo.infra;

import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.plugin.mojo.AbstractFabric8Mojo;
import io.fabric8.utils.DomHelper;
import io.fabric8.utils.Strings;
import io.fabric8.utils.XmlUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import static io.fabric8.utils.DomHelper.firstChild;

/**
 * Sets up the current maven project so that the fabric8 maven plugin is defined and setup correctly in the projects pom.xml
 */
@Mojo(name = "setup", requiresProject = true)
public class SetupMojo extends AbstractFabric8Mojo {

    public static final String PLUGIN_GROUPID = "io.fabric8";
    public static final String PLUGIN_ARTIFACTID = "fabric8-maven-plugin";
    public static final String FABRIC8_MAVEN_PLUGIN_VERSION_PROPERTY = "fabric8.maven.plugin.version";

    /**
     * Should we update the version if the fabric8-maven-plugin is already used on a project?
     */
    @Parameter(property = "updateVersion", defaultValue = "true")
    private boolean updateVersion;

    /**
     * Whether we should use a version property for the plugin
     */
    @Parameter(property = "useVersionProperty")
    private boolean useVersionProperty;

    /**
     * Controls whether a backup pom should be created (default is true).
     */
    @Parameter(property = "generateBackupPoms")
    private boolean generateBackupPoms;

    @Parameter(name = "backupPomFileName", defaultValue = "${basedir}/pom.xml-backup")
    private File backupPomFileName;

    @Override
    public void executeInternal() throws MojoExecutionException, MojoFailureException {
        File pom = project.getFile();
        Document doc;
        try {
            doc = XmlUtils.parseDoc(pom);
        } catch (Exception e) {
            getLog().error("Failed to parse pom " + pom + ". " + e, e);
            throw new MojoExecutionException(e.getMessage(), e);
        }

        if (createOrUpdateFabric8MavenPlugin(doc)) {
            if (generateBackupPoms) {
                try {
                    pom.renameTo(backupPomFileName);
                    getLog().info("Created backup pom.xml file at: " + backupPomFileName);
                } catch (Exception e) {
                    throw new MojoExecutionException("Failed to create backup: " + backupPomFileName + ". " + e, e);
                }
            }
            getLog().info("Updating the pom " + pom);
            try {
                DomHelper.save(doc, pom);
            } catch (Exception e) {
                getLog().error("Failed to update pom " + pom + ". " + e, e);
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    private boolean createOrUpdateFabric8MavenPlugin(Document doc) throws MojoExecutionException {
        boolean updated = false;
        String latestVersion;
        try {
            latestVersion = MavenUtil.getVersion(PLUGIN_GROUPID, PLUGIN_ARTIFACTID);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        // lets check that there's a property defined
        String currentVersion = project.getProperties().getProperty(FABRIC8_MAVEN_PLUGIN_VERSION_PROPERTY);
        String versionExpression = "${" + FABRIC8_MAVEN_PLUGIN_VERSION_PROPERTY + "}";
        Element fmpPlugin = findPlugin(doc, PLUGIN_GROUPID, PLUGIN_ARTIFACTID);
        boolean pluginMissing = fmpPlugin == null;
        if (updateVersion || pluginMissing) {
            if (useVersionProperty) {
                Element documentElement = doc.getDocumentElement();
                Element properties = firstChild(documentElement, "properties");
                if (properties == null) {
                    properties = addChildAfter(appendAfterLastElement(properties, doc.createTextNode("\n      ")), "properties");
                }
                if (Strings.isNullOrBlank(currentVersion)) {
                    addChildAfter(appendAfterLastElement(properties, doc.createTextNode("\n    ")), FABRIC8_MAVEN_PLUGIN_VERSION_PROPERTY, latestVersion);
                    updated = true;
                } else if (!Objects.equals(currentVersion, latestVersion)) {
                    Element propertyElement = DomHelper.firstChild(properties, FABRIC8_MAVEN_PLUGIN_VERSION_PROPERTY);
                    if (propertyElement != null) {
                        propertyElement.setTextContent(latestVersion);
                        updated = true;
                    }
                }
            } else {
                versionExpression = latestVersion;
            }
        }

        if (pluginMissing) {
            fmpPlugin = findOrAddPlugin(doc, PLUGIN_GROUPID, PLUGIN_ARTIFACTID, versionExpression);
            updated = true;
        }
        if (updateVersion || pluginMissing) {
            String version = DomHelper.firstChildTextContent(fmpPlugin, "version");
            if (version == null || !version.equals(versionExpression)) {
                Element versionElement = DomHelper.firstChild(fmpPlugin, "version");
                if (versionElement == null) {
                    Element artifactId = DomHelper.firstChild(fmpPlugin, "artifactId");
                    Text textNode = doc.createTextNode("\n        ");
                    if (artifactId != null) {
                        addChildAfter(artifactId, textNode);
                    } else {
                        appendAfterLastElement(fmpPlugin, textNode);
                    }
                    addChildAfter(textNode, "version", versionExpression);
                } else {
                    versionElement.setTextContent(versionExpression);
                }
                updated = true;
            }
        }
        if (pluginMissing) {
            Element executions = firstChild(fmpPlugin, "executions");
            if (executions == null) {
                executions = addChildAfter(appendAfterLastElement(fmpPlugin, doc.createTextNode("\n        ")), "executions");
            } else {
                // lets remove all the children to be sure
                DomHelper.removeChildren(executions);
            }
            executions.appendChild(doc.createTextNode("\n          "));
            Element execution = DomHelper.addChildElement(executions, "execution");
            execution.appendChild(doc.createTextNode("\n            "));

            DomHelper.addChildElement(execution, "id", "fmp");
            execution.appendChild(doc.createTextNode("\n            "));

            Element goals = DomHelper.addChildElement(execution, "goals");
            execution.appendChild(doc.createTextNode("\n          "));

            String[] goalNames = {"resource", "helm", "build"};
            for (String goalName : goalNames) {
                goals.appendChild(doc.createTextNode("\n              "));
                DomHelper.addChildElement(goals, "goal", goalName);
            }
            goals.appendChild(doc.createTextNode("\n            "));

            executions.appendChild(doc.createTextNode("\n        "));
            updated = true;
        }
        return updated;
    }

    private Element addChildAfter(Node node, String elementName) {
        Document ownerDocument = node.getOwnerDocument();
        Element newChild = ownerDocument.createElement(elementName);
        return (Element) addChildAfter(node, newChild);
    }

    private Node addChildAfter(Node node, Node newChild) {
        Node element = node.getParentNode();
        Node nextSibling = node.getNextSibling();
        if (nextSibling != null) {
            element.insertBefore(newChild, nextSibling);
        } else {
            element.appendChild(newChild);
        }
        return newChild;
    }

    private Element addChildAfter(Node node, String elementName, String textContent) {
        Element newChild = addChildAfter(node, elementName);
        newChild.setTextContent(textContent);
        return newChild;
    }

    private Node appendAfterLastElement(Element element, Node newChild) {
        // lets find last element child
        Node lastChild = element.getLastChild();
        while (lastChild != null) {
            if (lastChild instanceof Element) {
                break;
            }
            lastChild = lastChild.getPreviousSibling();
        }
        Node nextSibling = null;
        if (lastChild != null) {
            nextSibling = lastChild.getNextSibling();
        }
        if (nextSibling != null) {
            element.insertBefore(newChild, nextSibling);
        } else {
            element.appendChild(newChild);
        }
        return newChild;
    }

    private Element findOrAddPlugin(Document doc, String groupId, String artifactId, String version) {
        Element plugin = findPlugin(doc, groupId, artifactId);
        if (plugin != null) {
            return plugin;
        }
        Element documentElement = doc.getDocumentElement();
        Element build = firstChild(documentElement, "build");
        if (build == null) {
            build = addChildAfter(appendAfterLastElement(documentElement, doc.createTextNode("\n  ")), "build");
        }
        Element plugins = firstChild(build, "plugins");
        if (plugins == null) {
            plugins = addChildAfter(appendAfterLastElement(build, doc.createTextNode("\n    ")), "plugins");
        }

        plugin = addChildAfter(appendAfterLastElement(plugins, doc.createTextNode("\n      ")), "plugin");
        plugin.appendChild(doc.createTextNode("\n        "));
        DomHelper.addChildElement(plugin, "groupId", groupId);
        plugin.appendChild(doc.createTextNode("\n        "));
        DomHelper.addChildElement(plugin, "artifactId", artifactId);
        plugin.appendChild(doc.createTextNode("\n        "));
        DomHelper.addChildElement(plugin, "version", version);
        plugin.appendChild(doc.createTextNode("\n      "));
        return plugin;
    }

    private Element findPlugin(Document doc, String groupId, String artifactId) {
        Element build = firstChild(doc.getDocumentElement(), "build");
        if (build != null) {
            Element plugins = firstChild(build, "plugins");
            if (plugins != null) {
                NodeList childNodes = plugins.getChildNodes();
                if (childNodes != null) {
                    for (int i = 0; i < childNodes.getLength(); i++) {
                        Node item = childNodes.item(i);
                        if (item instanceof Element) {
                            Element plugin = (Element) item;
                            if (Objects.equals(DomHelper.firstChildTextContent(plugin, "groupId"), groupId) &&
                                    Objects.equals(DomHelper.firstChildTextContent(plugin, "artifactId"), artifactId)) {
                                return plugin;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
