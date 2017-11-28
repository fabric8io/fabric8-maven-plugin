package io.fabric8.maven.rt;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.arquillian.smart.testing.rules.git.GitCloner;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.lib.Repository;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.BuiltProject;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.EmbeddedMaven;

import java.io.*;
import java.util.List;
import java.util.Map;

public class Core
{

    private final String fabric8PluginGroupId = "io.fabric8";

    private final String fabric8PluginArtifactId = "fabric8-maven-plugin";

    private GitCloner gitCloner;

    private Model getCurrentProjectModel() throws Exception {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(new FileReader("pom.xml"));
        return model;
    }

    private Repository cloneRepositoryUsingHttp(String repositoryUrl) throws Exception {
        gitCloner = new GitCloner(repositoryUrl);
        return gitCloner.cloneRepositoryToTempFolder();
    }

    private void modifyPomFileToProjectVersion(Repository aRepository) throws Exception {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        String baseDir = aRepository.getWorkTree().getAbsolutePath();
        Model model = reader.read(new FileInputStream(new File(baseDir, "/pom.xml")));

        Map<String, Plugin> aStringToPluginMap = model.getBuild().getPluginsAsMap();
        List<Profile> profiles =  model.getProfiles();
        if(aStringToPluginMap.get(fabric8PluginGroupId + ":" + fabric8PluginArtifactId) != null) {
            aStringToPluginMap.get(fabric8PluginGroupId + ":" + fabric8PluginArtifactId).setVersion(getCurrentProjectModel().getVersion());
        } else {
            for (Profile profile:profiles) {
                if(profile.getBuild() != null && profile.getBuild().getPluginsAsMap().get(fabric8PluginGroupId + ":" + fabric8PluginArtifactId) != null) {
                    profile.getBuild().getPluginsAsMap()
                            .get(fabric8PluginGroupId + ":" + fabric8PluginArtifactId)
                            .setVersion(getCurrentProjectModel().getVersion());
                }
            }
        }

        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(new FileOutputStream(new File(baseDir, "/pom.xml")), model);
        model.getArtifactId();
    }

    protected void updateSourceCode(Repository repository) throws Exception {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        String baseDir = repository.getWorkTree().getAbsolutePath();
        Model model = reader.read(new FileInputStream(new File(baseDir, "/pom.xml")));

        Dependency dependency = new Dependency();
        dependency.setGroupId("org.apache.commons");
        dependency.setArtifactId("commons-lang3");
        dependency.setVersion("3.5");
        model.getDependencies().add(dependency);

        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(new FileOutputStream(new File(baseDir, "/pom.xml")), model);
        model.getArtifactId();
    }

    protected Repository setupSampleTestRepository(String repositoryUrl) throws Exception {
        Repository repository = cloneRepositoryUsingHttp(repositoryUrl);
        modifyPomFileToProjectVersion(repository);
        return repository;
    }

    protected void runEmbeddedMavenBuild(Repository sampleRepository, String goals, String profiles) {
        String baseDir = sampleRepository.getWorkTree().getAbsolutePath();
        BuiltProject builtProject = EmbeddedMaven.forProject(baseDir + "/pom.xml")
                .setGoals(goals)
                .setProfiles(profiles)
                .build();

        assert builtProject.getDefaultBuiltArchive() != null;
    }

    protected void cleanSampleTestRepository() throws Exception {
        gitCloner.removeClone();
    }
}