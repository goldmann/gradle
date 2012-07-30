/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.mvnsettings;

import java.io.File;
import java.util.Map;

/**
 * @author Steve Ebersole
 */
public class DefaultLocalMavenRepositoryLocator implements LocalMavenRepositoryLocator {
    private static final String SETTINGS_LOCATION_OVERRIDE = "maven.settings";

    public DefaultLocalMavenRepositoryLocator(MavenFileLocations mavenFileLocations, Map<String, String> systemProperties, Map<String, String> environmentVariables) {
    }

    public File getLocalMavenRepository() {
        return determineSettingsFileLocation();
    }

    private static String normalizePath(String path) {
        if ( path.startsWith( "~" ) ) {
            path = System.getProperty( "user.home" ) + path.substring( 1 );
         }
        return path;
    }

    private File determineSettingsFileLocation() {
        final String defaultLocation = "~/.m2/settings.xml";
        final String location = System.getProperty( SETTINGS_LOCATION_OVERRIDE, defaultLocation );
        return new File( normalizePath( location ) );
    }
}
