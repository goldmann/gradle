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

package org.gradle.api.plugins.migration;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.filestore.PathNormalisingKeyFileStore;
import org.gradle.api.plugins.migration.gradle.internal.GradleBuildOutcomeSetTransformer;
import org.gradle.api.plugins.migration.model.compare.BuildComparator;
import org.gradle.api.plugins.migration.model.compare.BuildComparisonResult;
import org.gradle.api.plugins.migration.model.compare.BuildComparisonSpec;
import org.gradle.api.plugins.migration.model.compare.internal.BuildComparisonSpecFactory;
import org.gradle.api.plugins.migration.model.compare.internal.DefaultBuildComparator;
import org.gradle.api.plugins.migration.model.compare.internal.DefaultBuildOutcomeComparatorFactory;
import org.gradle.api.plugins.migration.model.outcome.BuildOutcome;
import org.gradle.api.plugins.migration.model.outcome.internal.BuildOutcomeAssociator;
import org.gradle.api.plugins.migration.model.outcome.internal.ByTypeAndNameBuildOutcomeAssociator;
import org.gradle.api.plugins.migration.model.outcome.internal.archive.GeneratedArchiveBuildOutcome;
import org.gradle.api.plugins.migration.model.outcome.internal.archive.GeneratedArchiveBuildOutcomeComparator;
import org.gradle.api.plugins.migration.model.outcome.internal.archive.entry.GeneratedArchiveBuildOutcomeComparisonResultHtmlRenderer;
import org.gradle.api.plugins.migration.model.render.internal.BuildComparisonResultRenderer;
import org.gradle.api.plugins.migration.model.render.internal.DefaultBuildOutcomeComparisonResultRendererFactory;
import org.gradle.api.plugins.migration.model.render.internal.html.*;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.internal.migration.ProjectOutcomes;
import org.gradle.util.GradleVersion;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Set;

/**
 * Executes two Gradle builds (that can be the same build) with specified versions and compares the outputs.
 */
public class CompareGradleBuilds extends DefaultTask {

    private String sourceVersion = GradleVersion.current().getVersion();
    private String targetVersion = sourceVersion;

    private Object sourceProjectDir = getProject().getRootDir();
    private Object targetProjectDir = getProject().getRootDir();

    private Object reportDir;

    private final FileResolver fileResolver;

    public CompareGradleBuilds(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public void setSourceVersion(String sourceVersion) {
        this.sourceVersion = sourceVersion;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }

    public File getSourceProjectDir() {
        return fileResolver.resolve(sourceProjectDir);
    }

    public void setSourceProjectDir(Object sourceProjectDir) {
        this.sourceProjectDir = sourceProjectDir;
    }

    public File getTargetProjectDir() {
        return fileResolver.resolve(targetProjectDir);
    }

    public void setTargetProjectDir(Object targetProjectDir) {
        this.targetProjectDir = targetProjectDir;
    }

    @OutputDirectory
    public File getReportDir() {
        return fileResolver.resolve(reportDir);
    }

    public void setReportDir(Object reportDir) {
        this.reportDir = reportDir;
    }

    public File getReportFile() {
        return new File(getReportDir(), "index.html");
    }

    public File getFileStoreDir() {
        return new File(getReportDir(), "files");
    }

    @TaskAction
    void compare() {
        // Build the outcome model and outcomes

        GradleBuildOutcomeSetTransformer fromOutcomeTransformer = createOutcomeSetTransformer("source");
        ProjectOutcomes fromOutput = generateBuildOutput(sourceVersion, getSourceProjectDir());
        Set<BuildOutcome> fromOutcomes = fromOutcomeTransformer.transform(fromOutput);

        GradleBuildOutcomeSetTransformer toOutcomeTransformer = createOutcomeSetTransformer("target");
        ProjectOutcomes toOutput = generateBuildOutput(targetVersion, getTargetProjectDir());
        Set<BuildOutcome> toOutcomes = toOutcomeTransformer.transform(toOutput);

        // Associate from each side (create spec)

        BuildOutcomeAssociator outcomeAssociator = new ByTypeAndNameBuildOutcomeAssociator<BuildOutcome>(GeneratedArchiveBuildOutcome.class);
        BuildComparisonSpecFactory specFactory = new BuildComparisonSpecFactory(outcomeAssociator);
        BuildComparisonSpec comparisonSpec = specFactory.createSpec(fromOutcomes, toOutcomes);

        DefaultBuildOutcomeComparatorFactory comparatorFactory = new DefaultBuildOutcomeComparatorFactory();
        comparatorFactory.registerComparator(new GeneratedArchiveBuildOutcomeComparator());

        // Compare

        BuildComparator buildComparator = new DefaultBuildComparator(comparatorFactory);
        BuildComparisonResult result = buildComparator.compareBuilds(comparisonSpec);

        writeReport(result);
    }

    private GradleBuildOutcomeSetTransformer createOutcomeSetTransformer(String filesPath) {
        return new GradleBuildOutcomeSetTransformer(new PathNormalisingKeyFileStore(new File(getFileStoreDir(), filesPath)));
    }

    private ProjectOutcomes generateBuildOutput(String gradleVersionString, File other) {
        GradleVersion gradleVersion = GradleVersion.version(gradleVersionString);
        GradleConnector connector = GradleConnector.newConnector().forProjectDirectory(other);
        connector.useGradleUserHomeDir(getProject().getGradle().getStartParameter().getGradleUserHomeDir());
        if (gradleVersion.equals(GradleVersion.current())) {
            connector.useInstallation(getProject().getGradle().getGradleHomeDir());
        } else {
            connector.useGradleVersion(gradleVersion.getVersion());
        }
        ProjectConnection connection = connector.connect();
        try {
            ProjectOutcomes buildOutcomes = connection.getModel(ProjectOutcomes.class);
            connection.newBuild().forTasks("assemble").run();
            return buildOutcomes;
        } finally {
            connection.close();
        }
    }

    private void writeReport(BuildComparisonResult result) {
        File destination = getReportFile();

        OutputStream outputStream;
        Writer writer;

        try {
            outputStream = FileUtils.openOutputStream(destination);
            writer = new OutputStreamWriter(outputStream, Charset.defaultCharset());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
            createResultRenderer().render(result, writer);
        } finally {
            IOUtils.closeQuietly(writer);
            IOUtils.closeQuietly(outputStream);
        }
    }

    private BuildComparisonResultRenderer<Writer> createResultRenderer() {
        DefaultBuildOutcomeComparisonResultRendererFactory<HtmlRenderContext> renderers = new DefaultBuildOutcomeComparisonResultRendererFactory<HtmlRenderContext>(HtmlRenderContext.class);
        renderers.registerRenderer(new GeneratedArchiveBuildOutcomeComparisonResultHtmlRenderer("Source Build", "Target Build"));

        PartRenderer headRenderer = new HeadRenderer("Gradle Build Comparison", Charset.defaultCharset().name());

        PartRenderer headingRenderer = new GradleComparisonHeadingRenderer(
                getSourceProjectDir().getAbsolutePath(), getSourceVersion(), getTargetProjectDir().getAbsolutePath(), getTargetVersion()
        );

        return new HtmlBuildComparisonResultRenderer(renderers, headRenderer, headingRenderer, null);
    }

}
