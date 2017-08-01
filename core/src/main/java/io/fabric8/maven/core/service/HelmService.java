/*
 * Copyright 2017 Amdocs, Inc.
 *
 * Amdocs licenses this file to you under the Apache License, version
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

package io.fabric8.maven.core.service;

import java.io.*;
import java.util.Collections;

import io.fabric8.maven.core.util.ProcessUtil;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.utils.IOHelpers;
import org.apache.maven.shared.utils.io.IOUtil;


/**
 * Created by NIKHILY on 7/24/2017.
 */
public class HelmService {
    /**
     * Evaluate given Helm charts templates as input & generate corresponding yaml
     * by connnecting to tiller server using helm cli
     */

    private File helmEvalFileDir;
    private File helmWorkDir;
    private File defaultHelmBinDir;

    private Logger log;
    private Process process;

    public HelmService(File helmEvalFiles, File helmWorkDir, File defaultHelmBinDir, Logger log) {
        this.helmEvalFileDir = helmEvalFiles;
        this.helmWorkDir = helmWorkDir;
        this.defaultHelmBinDir = defaultHelmBinDir;
        this.log = log;
    }

    public File[] initHelmResources(String chartName) throws Fabric8ServiceException {
        if (helmEvalFileDir != null && helmEvalFileDir.isDirectory()) {
            File kubeFragments = invokeHelm(chartName);
            return kubeFragments.listFiles();
        }
        return new File[0];

    }

    private File invokeHelm(String chartName) throws Fabric8ServiceException {
        File tempHelmDir = new File(helmWorkDir, "kubernetes/" + chartName);
        tempHelmDir.mkdirs();

        try {
            String executableName = "helm";
            File helmBinaryFile = ProcessUtil.findExecutable(log, executableName);
            if (helmBinaryFile == null && this.defaultHelmBinDir != null) {
                // Looking at the default location for helm
                helmBinaryFile = ProcessUtil.findExecutable(log, executableName, Collections.singletonList(this.defaultHelmBinDir));
            }
            if (helmBinaryFile == null) {
                log.error("[[B]]helm[[B]] utility doesn't exist, please execute [[B]]mvn fabric8:install[[B]] command to make it work");
                log.error("or");
                log.error("to install it manually, please log on to [[B]]http://helm.io/installation/[[B]]");
                throw new Fabric8ServiceException("Cannot find the helm binary in PATH or default install location");
            }

            String helmInstallCmd = executableName + " install --dry-run --debug " + helmEvalFileDir;
            process = Runtime.getRuntime().exec(new String[] {helmBinaryFile.getAbsolutePath(), "install", "--dry-run", "--debug", helmEvalFileDir.toString()});
            log.info("Using helm templates from %s", helmEvalFileDir);

            int exitCode = 0;
            BufferedReader buf = new BufferedReader(new InputStreamReader(process.getInputStream()));
            parseOutput(buf, tempHelmDir);

            exitCode = waitForProcess(process);

            if (exitCode != 0) {
                StringWriter stringWriter = new StringWriter();
                IOUtil.copy(process.getErrorStream(), stringWriter);
                log.error("Helm command returned a non-zero exit code : " + stringWriter.toString());
                throw new Fabric8ServiceException(stringWriter.toString());
            }

        } catch (IOException e) {
            throw new Fabric8ServiceException("Failed to run Helm command: ", e);
        }

        return tempHelmDir;
    }



    public void parseOutput (BufferedReader buf, File targetDir) throws Fabric8ServiceException{
        String line = "";
        int flag = 0;
        try {
            while ((line = buf.readLine()) != null) {
                if (line.startsWith("MANIFEST:")) {
                    flag = 1;
                }

                if (flag == 1 && line.startsWith("# Source:")) {

                    String[] tempStr = line.split("/");
                    File fileName = new File(targetDir, tempStr[tempStr.length - 1]);
                    String innerline = "";
                    StringBuilder fileContent = new StringBuilder();
                    while ((innerline = buf.readLine()) != null) {

                        if (innerline.startsWith("---")) {
                            break;
                        }

                        if (innerline != "") {
                            fileContent = fileContent.append("\n" + innerline);
                        }
                    }

                    IOHelpers.writeFully(fileName, fileContent.toString());
                }
            }
        } catch (IOException e) {
            throw new Fabric8ServiceException("Failed to run parse Output: ", e);
        }
    }

    private int waitForProcess (Process pr) throws Fabric8ServiceException {
        int exitCode = 0;
        try {
            pr.waitFor();
            exitCode = pr.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Fabric8ServiceException("Failed to run Helm command: ", e);
        }

        return exitCode;
    }

}
