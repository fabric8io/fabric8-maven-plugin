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
package io.fabric8.generator.openliberty;

import static io.fabric8.maven.core.util.FileUtil.getRelativePath;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.assembly.model.Assembly;
import org.apache.maven.plugins.assembly.model.DependencySet;
import org.apache.maven.plugins.assembly.model.FileSet;
import org.apache.maven.project.MavenProject;

import io.fabric8.maven.generator.javaexec.FatJarDetector;
import io.fabric8.maven.core.util.MavenUtil;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.generator.api.GeneratorContext;
import io.fabric8.maven.generator.javaexec.JavaExecGenerator;

public class OpenLibertyGenerator extends JavaExecGenerator {

	protected static final String LIBERTY_SELF_EXTRACTOR = "wlp.lib.extract.SelfExtractRun";
	protected static final String LIBERTY_RUNNABLE_JAR = "LIBERTY_RUNNABLE_JAR";
	protected static final String JAVA_APP_JAR = "JAVA_APP_JAR";
	
	private String runnableJarName = null;

	public OpenLibertyGenerator(GeneratorContext context) {
        super(context, "openliberty");
    }

    // Override so that the generator kicks in when the liberty-maven-plugin is used
    @Override
    public boolean isApplicable(List<ImageConfiguration> configs) {
        return shouldAddImageConfiguration(configs)
                && MavenUtil.hasPlugin(getProject(), "io.openliberty.tools", "liberty-maven-plugin");
                
    }

    // Override extractPorts so that we default to 9080 rather than 8080 for the web port. 
    @Override
    protected List<String> extractPorts() {
        List<String> ret = new ArrayList<>();
        addPortIfValid(ret, getConfig(JavaExecGenerator.Config.webPort, "9080"));
        addPortIfValid(ret, getConfig(JavaExecGenerator.Config.jolokiaPort));
        addPortIfValid(ret, getConfig(JavaExecGenerator.Config.prometheusPort));
        return ret;
    }

  
    @Override
    protected Map<String, String> getEnv(boolean prePackagePhase) throws MojoExecutionException {
    	Map<String,String> ret = super.getEnv(prePackagePhase);
    	if ( runnableJarName != null) {
    		ret.put(LIBERTY_RUNNABLE_JAR, runnableJarName);
    		ret.put(JAVA_APP_JAR, runnableJarName);
    	}
    	return ret;
    }
    @Override
    protected AssemblyConfiguration createAssembly() throws MojoExecutionException {
        AssemblyConfiguration.Builder builder = new AssemblyConfiguration.Builder().targetDir(getConfig(Config.targetDir));
        addAssembly(builder);
        return builder.build();
    }
    
    @Override
    protected void addAssembly(AssemblyConfiguration.Builder builder) throws MojoExecutionException {
        String assemblyRef = getConfig(Config.assemblyRef);
        if (assemblyRef != null) {
            builder.descriptorRef(assemblyRef);
        } else {
            Assembly assembly = new Assembly();
            addAdditionalFiles(assembly);
            if (isFatJar()) {
                FatJarDetector.Result fatJar = detectFatJar();
                MavenProject project = getProject();
                if (fatJar == null) {
                    DependencySet dependencySet = new DependencySet();
                    dependencySet.addInclude(project.getGroupId() + ":" + project.getArtifactId());
                    assembly.addDependencySet(dependencySet);
                } else {
                    FileSet fileSet = getOutputDirectoryFileSet(fatJar, project);
                    if ( LIBERTY_SELF_EXTRACTOR.equals(fatJar.getMainClass())) {
                    	this.runnableJarName = fatJar.getArchiveFile().getName();
                    }
                    assembly.addFileSet(fileSet);
                }
            } else {
                builder.descriptorRef("artifact-with-dependencies");
            }
            builder.assemblyDef(assembly);
        }
    }

    private void addAdditionalFiles(Assembly assembly) {
        assembly.addFileSet(createFileSet("src/main/fabric8-includes/bin","bin","0755","0755"));
        assembly.addFileSet(createFileSet("src/main/fabric8-includes",".","0644","0755"));
        // Add server.xml file
        assembly.addFileSet(createFileSet("src/main/liberty/config","src/wlp/config","0644", "0755"));
      
    }
    
    private FileSet getOutputDirectoryFileSet(FatJarDetector.Result fatJar, MavenProject project) {
        FileSet fileSet = new FileSet();
        File buildDir = new File(project.getBuild().getDirectory());
        fileSet.setDirectory(getRelativePath(project.getBasedir(), buildDir).getPath());
        fileSet.addInclude(getRelativePath(buildDir, fatJar.getArchiveFile()).getPath());
        fileSet.setOutputDirectory(".");
        fileSet.setFileMode("0640");
        return fileSet;
    }

    private FileSet createFileSet(String sourceDir, String outputDir, String fileMode, String directoryMode) {
        FileSet fileSet = new FileSet();
        fileSet.setDirectory(sourceDir);
        fileSet.setOutputDirectory(outputDir);
        fileSet.setFileMode(fileMode);
        fileSet.setDirectoryMode(directoryMode);
        return fileSet;
    }
    
  
  
}