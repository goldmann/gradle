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

package org.gradle.api.internal.artifacts.result

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ModuleVersionSelector
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.util.Matchers.strictlyEqual

/**
 * by Szczepan Faber, created at: 8/21/12
 */
class DefaultResolvedDependencyResultSpec extends Specification {

    def "object methods"() {
        def dependency = newDependency(newSelector("a", "b", "1"), newId("a", "b", "1"), ['conf'])
        def same =   newDependency(newSelector("a", "b", "1"), newId("a", "b", "1"), ['conf'])

        def differentRequested =      newDependency(newSelector("X", "b", "1"), newId("a", "b", "1"), ['conf'])
        def differentSelected =       newDependency(newSelector("a", "b", "1"), newId("a", "X", "1"), ['conf'])
        def differentConfigurations = newDependency(newSelector("a", "b", "1"), newId("a", "b", "1"), ['XXXX'])

        expect:
        dependency strictlyEqual(same)
        dependency != differentRequested
        dependency != differentSelected
        dependency != differentConfigurations

        dependency.hashCode() != differentRequested.hashCode()
        dependency.hashCode() != differentSelected.hashCode()
        dependency.hashCode() != differentConfigurations.hashCode()
    }

    private newDependency(ModuleVersionSelector requested, ModuleVersionIdentifier selected, Collection<String> configurations) {
        new DefaultResolvedDependencyResult(requested, selected, configurations)
    }
}
