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

package org.gradle.api.plugins.migration.model.render.internal.html

import org.gradle.api.plugins.migration.fixtures.outcome.StringBuildOutcome
import org.gradle.api.plugins.migration.fixtures.outcome.StringBuildOutcomeComparisonResult
import org.gradle.api.plugins.migration.fixtures.outcome.StringBuildOutcomeComparisonResultHtmlRenderer
import org.gradle.api.plugins.migration.model.compare.BuildComparisonResult
import org.gradle.api.plugins.migration.model.compare.BuildOutcomeComparisonResult
import org.gradle.api.plugins.migration.model.compare.internal.DefaultBuildComparisonSpecBuilder
import org.gradle.api.plugins.migration.model.outcome.BuildOutcome
import org.gradle.api.plugins.migration.model.outcome.internal.DefaultBuildOutcomeAssociation
import org.gradle.api.plugins.migration.model.render.internal.BuildComparisonResultRenderer
import org.gradle.api.plugins.migration.model.render.internal.DefaultBuildOutcomeComparisonResultRendererFactory
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import spock.lang.Specification

import static org.gradle.api.plugins.migration.model.render.internal.html.HtmlBuildComparisonResultRenderer.*

class HtmlBuildComparisonResultRendererTest extends Specification {

    def headPart = null
    def headerPart = null
    def footerPart = null

    def renderers = new DefaultBuildOutcomeComparisonResultRendererFactory(HtmlRenderContext)

    def unassociatedFrom = new HashSet<BuildOutcome>()
    def unassociatedTo = new HashSet<BuildOutcome>()
    def comparisons = new LinkedList<BuildOutcomeComparisonResult>()

    def comparisonSpecBuilder = new DefaultBuildComparisonSpecBuilder()

    def writer = new StringWriter()

    BuildComparisonResultRenderer makeRenderer(renderers = this.renderers, headPart = this.headPart, headerPart = this.headerPart, footerPart = this.footerPart) {
        new HtmlBuildComparisonResultRenderer(renderers, headPart, headerPart, footerPart)
    }

    BuildComparisonResult makeResult(
            Set<BuildOutcome> unassociatedFrom = this.unassociatedFrom,
            Set<BuildOutcome> unassociatedTo = this.unassociatedTo,
            List<BuildOutcomeComparisonResult<?>> comparisons = this.comparisons
    ) {
        new BuildComparisonResult(unassociatedFrom, unassociatedTo, comparisons)
    }

    StringBuildOutcome str(name, value = name) {
        new StringBuildOutcome(name, value)
    }

    Set<StringBuildOutcome> strs(String... strings) {
        strings.collect { str(it) } as Set
    }

    BuildOutcomeComparisonResult strcmp(String from, String to) {
        new StringBuildOutcomeComparisonResult(
                new DefaultBuildOutcomeAssociation(str(from), str(to), StringBuildOutcome)
        )
    }

    Document render() {
        makeRenderer().render(makeResult(), writer)
        Jsoup.parse(writer.toString())
    }

    def "empty render"() {
        given:
        def html = render()

        expect:
        writer.toString().startsWith("<html>")
        !html.children().empty
        html.head().children().empty
        html.body().children().empty
    }

    def "render some results"() {
        given:
        renderers.registerRenderer(new StringBuildOutcomeComparisonResultHtmlRenderer())
        comparisons << strcmp("a", "a")
        comparisons << strcmp("a", "b")
        comparisons << strcmp("a", "c")

        when:
        def html = render()

        then:
        // Just need to test that the renderers were called correctly, not the renderers themselves
        def tables = html.select("table")
        tables.size() == 3
        tables[0].select("th").text() == "From To Distance"
        tables[0].select("td")[0].text() == "a"
        tables[2].select("td")[2].text() == comparisons.last.distance.toString()
    }

    def "parts"() {
        given:
        headPart = partRenderer { title "a" }
        headerPart = partRenderer { header("b") }
        footerPart = partRenderer { footer("c") }

        when:
        def result = render()

        then:
        result.head().select("title").text() == "a"
        result.select("header").text() == "b"
        result.select("footer").text() == "c"
    }

    PartRenderer partRenderer(Closure c) {
        new PartRenderer() {
            void render(BuildComparisonResult result, HtmlRenderContext context) {
                context.render(c)
            }
        }
    }
}
