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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import io.fabric8.maven.docker.util.Logger;

import org.apache.maven.plugin.MojoExecutionException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 *
 * Utilities for download and more
 * @author roland
 * @since 14/10/16
 */
public class IoUtil {

    /**
     * Download with showing the progress a given URL and store it in a file
     * @param log logger used to track progress
     * @param downloadUrl url to download
     * @param target target file where to store the downloaded data
     * @throws MojoExecutionException
     */
    public static void download(Logger log, URL downloadUrl, File target) throws MojoExecutionException {
        log.progressStart();
        try {
            OkHttpClient client =
                new OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.MINUTES).build();
            Request request = new Request.Builder()
                .url(downloadUrl)
                .build();
            Response response = client.newCall(request).execute();

            try (OutputStream out = new FileOutputStream(target);
                 InputStream im = response.body().byteStream()) {

                long length = response.body().contentLength();
                InputStream in = response.body().byteStream();
                byte[] buffer = new byte[8192];

                long readBytes = 0;
                while (true) {
                    int len = in.read(buffer);
                    readBytes += len;
                    log.progressUpdate(target.getName(), "Downloading", getProgressBar(readBytes, length));
                    if (len <= 0) {
                        out.flush();
                        break;
                    }
                    out.write(buffer, 0, len);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to download URL " + downloadUrl + " to  " + target + ": " + e, e);
        } finally {
            log.progressFinished();
        }

    }

    /**
     * Find a free (on localhost) random port in the range [49152, 65535] after 100 attempts.
     *
     * @return a random port where a server socket can be bound to
     */
    public static int getFreeRandomPort() {
        // 100 attempts should be enough
        return getFreeRandomPort(49152, 65535, 100);
    }

    /**
     * Find a free (on localhost) random port in the specified range after the given number of attempts.
     */
    public static int getFreeRandomPort(int min, int max, int attempts) {
        Random random = new Random();
        for (int i=0; i < attempts; i++) {
            int port = min + random.nextInt(max - min + 1);
            try (Socket socket = new Socket("localhost", port)) {
            } catch (ConnectException e) {
                return port;
            } catch (IOException e) {
                throw new IllegalStateException("Error while trying to check open ports", e);
            }
        }
        throw new IllegalStateException("Cannot find a free random port in the range [" + min + ", " + max + "] after " + attempts + " attempts");
    }

    public static void main(String[] args) {
        System.out.println(getFreeRandomPort());
    }

    // ========================================================================================

    private static int PROGRESS_LENGTH = 50;

    private static String getProgressBar(long bytesRead, long length) {
        StringBuffer ret = new StringBuffer("[");
        if (length > - 1) {
            int bucketSize = (int) (length / PROGRESS_LENGTH + 0.5);
            int index = (int) (bytesRead / bucketSize + 0.5);
            for (int i = 0; i < PROGRESS_LENGTH; i++) {
                ret.append(i < index ? "=" : (i == index ? ">" : " "));
            }
            ret.append(String.format("] %.2f MB/%.2f MB",
                                 ((float) bytesRead / (1024 * 1024)),
                                 ((float) length / (1024 * 1024))));
        } else {
            int bucketSize = 200 * 1024; // 200k
            int index = (int) (bytesRead / bucketSize + 0.5) % PROGRESS_LENGTH;
            for (int i = 0; i < PROGRESS_LENGTH; i++) {
                ret.append(i == index ? "*" : " ");
            }
            ret.append("]");
        }

        return ret.toString();
    }
}
