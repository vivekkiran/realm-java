/*
 * Copyright 2016 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import io.realm.transformer.RealmTransformer
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.UnknownConfigurationException

class Realm implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // Make sure the project is either an Android application or library
        def isAndroidApp = project.plugins.withType(AppPlugin)
        def isAndroidLib = project.plugins.withType(LibraryPlugin)
        if (!isAndroidApp && !isAndroidLib) {
            throw new GradleException("'com.android.application' or 'com.android.library' plugin required.")
        }

        if (!isTransformAvailable()) {
            throw new GradleException('Realm gradle plugin only supports android gradle plugin 1.5.0 or later.')
        }

        def syncEnabledDefault = false
        def dependencyConfigurationName = getDependencyConfigurationName(project)
        def isKotlinProject = project.plugins.findPlugin('kotlin-android') != null
        def useKotlinExtensionsDefault = isKotlinProject
        def hasAnnotationProcessorConfiguration = project.getConfigurations().findByName('annotationProcessor') != null

        project.extensions.create('realm', RealmPluginExtension, project, syncEnabledDefault, useKotlinExtensionsDefault, dependencyConfigurationName)

        project.android.registerTransform(new RealmTransformer(project))

        project.repositories.add(project.getRepositories().jcenter())
        project.dependencies.add(dependencyConfigurationName, "io.realm:realm-annotations:${Version.VERSION}")
       if (isKotlinProject) {
            project.dependencies.add("kapt", "io.realm:realm-annotations-processor:${Version.VERSION}")
            project.dependencies.add("kaptAndroidTest", "io.realm:realm-annotations-processor:${Version.VERSION}")
        } else {
            assert hasAnnotationProcessorConfiguration
            project.dependencies.add("annotationProcessor", "io.realm:realm-annotations-processor:${Version.VERSION}")
            project.dependencies.add("androidTestAnnotationProcessor", "io.realm:realm-annotations-processor:${Version.VERSION}")
        }
    }

    private static boolean isTransformAvailable() {
        try {
            Class.forName('com.android.build.api.transform.Transform')
            return true
        } catch (Exception ignored) {
            return false
        }
    }

    private static String getDependencyConfigurationName(Project project) {
        /*
         * Dependency configuration name for android gradle plugin 3.0.0-*.
         * We need to use 'api' instead of 'implementation' since user's model class
         * might be using Realm's classes and annotations.
         */
        def newDependencyName = "api"
        def oldDependencyName = "compile"
        try {
            project.getConfigurations().getByName(newDependencyName)
            return newDependencyName
        } catch (UnknownConfigurationException ignored) {
            oldDependencyName
        }
    }

    private static boolean shouldApplyAndroidAptPlugin(boolean isKotlinProject,
                                                       boolean hasAnnotationProcessorConfiguration,
                                                       boolean preferAptOnKotlinProject) {

        if (isKotlinProject) {
            // for any Kotlin projects where user did not apply 'kapt' manually.
            return preferAptOnKotlinProject && !hasAnnotationProcessorConfiguration
        }
        // for any Java Projects where user did not apply 'AnnotationProcessor' plugin manually.
        return !hasAnnotationProcessorConfiguration
    }
}
