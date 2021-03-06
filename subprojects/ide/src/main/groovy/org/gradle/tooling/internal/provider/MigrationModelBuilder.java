/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import com.google.common.collect.Lists;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.GradleInternal;
import org.gradle.tooling.internal.migration.DefaultProjectOutcomes;
import org.gradle.tooling.internal.protocol.InternalProjectOutput;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.gradle.tooling.model.internal.migration.FileBuildOutcome;
import org.gradle.tooling.model.internal.migration.ProjectOutcomes;

import java.util.List;

public class MigrationModelBuilder implements BuildsModel {

    private final Transformer<FileBuildOutcome, PublishArtifact> artifactTransformer = new PublishArtifactToFileBuildOutcomeTransformer();

    public boolean canBuild(Class<?> type) {
        return type == InternalProjectOutput.class;
    }

    public ProjectVersion3 buildAll(GradleInternal gradle) {
        return buildProjectOutput(gradle.getRootProject(), null);
    }

    private DefaultProjectOutcomes buildProjectOutput(Project project, ProjectOutcomes parent) {
        DefaultProjectOutcomes projectOutput = new DefaultProjectOutcomes(project.getName(), project.getPath(),
                project.getDescription(), project.getProjectDir(), getFileOutcomes(project), parent);
        for (Project child : project.getChildProjects().values()) {
            projectOutput.addChild(buildProjectOutput(child, projectOutput));
        }
        return projectOutput;
    }

    private DomainObjectSet<FileBuildOutcome> getFileOutcomes(Project project) {
        List<FileBuildOutcome> fileBuildOutcomes = Lists.newArrayList();
        addArtifacts(project, fileBuildOutcomes);
        return new ImmutableDomainObjectSet<FileBuildOutcome>(fileBuildOutcomes);
    }

    private void addArtifacts(Project project, List<FileBuildOutcome> outcomes) {
        Configuration configuration = project.getConfigurations().findByName("archives");
        if (configuration != null) {
            for (PublishArtifact artifact : configuration.getArtifacts()) {
                FileBuildOutcome outcome = artifactTransformer.transform(artifact);
                outcomes.add(outcome);
            }
        }
    }

}
