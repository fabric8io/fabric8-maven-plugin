package io.fabric8.maven.plugin.converter;

import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.utils.io.FileUtils;
import org.apache.maven.shared.utils.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class ComposeToKubeConverter {

    public static final String KOMPOSE_RESOURCES_DIRECTORY = "kompose_resources";

    private Path komposeResourcesPath;
    private Path composeFilePath;
    private Logger log;
    private Process process;

    public ComposeToKubeConverter(Path composeFilePath, Logger log) {
        this.composeFilePath = composeFilePath;
        this.log = log;
    }

    public File[] listComposeConvertedFragments() throws IOException, MojoExecutionException {
        File[] komposeResourceFiles = {};

        if(composeFilePath != null) {
            log.info("converting docker compose file "+ composeFilePath +" to kubernetes resource descriptors");
            initializeKompose();
            invokeKompose();
            komposeResourceFiles = handelKomposeResult();
            log.info("conversion completed successfully : %s resource descriptors generated", komposeResourceFiles.length);
        }

        return komposeResourceFiles;
    }

    private void initializeKompose() throws IOException {
        komposeResourcesPath = Files.createTempDirectory(KOMPOSE_RESOURCES_DIRECTORY);
    }

    private void invokeKompose() throws IOException {
        process = Runtime.getRuntime().exec("kompose convert -o "+ komposeResourcesPath +" -f "+ composeFilePath);
        waitForConversion();
    }

    private File[] handelKomposeResult() throws IOException, MojoExecutionException {
        if(process.exitValue() != 0) {
            StringWriter stringWriter = new StringWriter();
            IOUtil.copy(process.getErrorStream(), stringWriter);
            log.error("conversion failed : " + stringWriter.toString());
            throw new MojoExecutionException(stringWriter.toString());
        }

        return komposeResourcesPath.toFile().listFiles();
    }

    public void cleanComposeRresources() {
        if(komposeResourcesPath == null) {
            return;
        }

        try {
            FileUtils.deleteDirectory(komposeResourcesPath.toFile());
        } catch (IOException e) {
            log.warn("kompose clean up failed: %s", e.getMessage());
        }
    }

    private void waitForConversion() {
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            log.error("kompose process interrupted: %s", e.getMessage());
        }
    }

    public Path getPath() {
        return komposeResourcesPath;
    }
}
