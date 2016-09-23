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

import java.io.*;
import java.net.URL;
import java.util.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.maven.core.config.ProcessorConfig;
import io.fabric8.maven.core.config.Profile;

/**
 * Helper class for dealing with profiles.
 *
 * @author roland
 * @since 25/07/16
 */
public class ProfileUtil {

    // Alowed profile names
    public static final String[] PROFILE_FILENAMES = {"profiles.yml", "profiles.yaml", "profiles"};

    // Mapper for handling YAML formats
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    /**
     * Find a profile. Profiles are looked up at various locations:
     *
     * <ul>
     *     <li>A given directory with the name profiles.yml (and variations, {@link #findProfile(String, File)}</li>
     * </ul>
     * @param profile the profile's name
     * @param resourceDir a directory to check for profiles.
     * @return the profile found or null if none of this name is given
     * @throws IOException
     */
    public static Profile findProfile(String profile, File resourceDir) throws IOException {
        try {
            if (profile != null) {
                Profile profileFound = lookup(profile, resourceDir);
                if (profileFound != null) {
                    return profileFound;
                } else {
                    throw new IllegalArgumentException("No profile " + profile + " defined");
                }
            }
        } catch (IOException e) {
            throw new IOException("Error while looking up profile " + profile + ": " + e.getMessage(),e);
        }
        return null;
    }

    /**
     * Find an enricher or generator config, possibly via a profile
     *
     * @param extractor how to extract the config from a profile when found
     * @param profile the profile name (can be null, then no profile is used)
     * @param resourceDir resource directory where to lookup the profile (in addition to a classpath lookup)
     * @return the configuration found or <code>null</code> if none could be found
     * @throws MojoExecutionException
     */
    public static ProcessorConfig extractProcesssorConfiguration(ProcessorConfigurationExtractor extractor,
                                                                 String profile,
                                                                 File resourceDir) throws IOException {
        Profile profileFound = findProfile(profile, resourceDir);
        if (profileFound != null) {
            return extractor.extract(profileFound);
        }
        return ProcessorConfig.EMPTY;
    }

    /**
     * Read all profiles found in the classpath.
     *
     * @return map of profiles, keyed by their names
     *
     * @throws IOException if reading of a profile fails
     */
    public static Map<String,Profile> readAllFromClasspath() throws IOException {
        Map<String,Profile> ret = new HashMap<>();
        for (String location : getMetaInfProfilePaths()) {
            for (String url : ClassUtil.getResources(location)) {
                for (Profile profile : fromYaml(new URL(url).openStream())) {
                    ret.put(profile.getName(), profile);
                }
            }
        }
        return ret;
    }

    /**
     * Lookup profiles from a given directory
     *
     * @param name name of the profile to lookup
     * @param directory directory to lookup
     * @return Profile found or null
     * @throws IOException if somethings fails during lookup
     */
    public static Profile lookup(String name, File directory) throws IOException {
        File profileFile = findProfileYaml(directory);
        if (profileFile != null) {
            List<Profile> profiles = fromYaml(new FileInputStream(profileFile));
            for (Profile profile : profiles) {
                if (profile.getName().equals(name)) {
                    return profile;
                }
            }
        }
        return readAllFromClasspath().get(name);
    }

    // ================================================================================

    // check for various variations of profile files
    private static File findProfileYaml(File directory) {
        for (String profileFile : PROFILE_FILENAMES) {
            File ret = new File(directory, profileFile);
            if (ret.exists()) {
                return ret;
            }
        }
        return null;
    }

    // prepend meta-inf location
    private static List<String> getMetaInfProfilePaths() {
        List<String> ret = new ArrayList<>(PROFILE_FILENAMES.length);
        for (String p : PROFILE_FILENAMES) {
            ret.add("META-INF/fabric8/" + p);
        }
        return ret;
    }

    /**
     * Load a profile from an input stream. This must be in YAML format
     *
     * @param is inputstream to read the profile from
     * @return the de-serialized profile
     * @throws IOException if deserialization fails
     */
    public static List<Profile> fromYaml(InputStream is) throws IOException {
        TypeReference<List<Profile>> typeRef = new TypeReference<List<Profile>>() {};
        return mapper.readValue(is, typeRef);
    }

    // ================================================================================

    // Use to select either a generator or enricher config
    public interface ProcessorConfigurationExtractor {
        ProcessorConfig extract(Profile profile);
    }

    /**
     * Get the generator configuration
     */
    public final static ProcessorConfigurationExtractor GENERATOR_CONFIG = new ProcessorConfigurationExtractor() {
        @Override
        public ProcessorConfig extract(Profile profile) {
            return profile.getGeneratorConfig();
        }
    };

    /**
     * Get the enricher configuration
     */
    public final static ProcessorConfigurationExtractor ENRICHER_CONFIG = new ProcessorConfigurationExtractor() {
        @Override
        public ProcessorConfig extract(Profile profile) {
            return profile.getEnricherConfig();
        }
    };

}
