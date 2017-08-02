/*
 * Copyright (c) 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.jiechic.proto.wire

import org.gradle.api.Project

/**
 * The main configuration block exposed as {@code protobuf} in the build script.
 */
public class WireConfigurator {

    private final Project project;
    private final GenerateProtoTaskCollection generateProtoTasks
    private final ArrayList<Closure> taskConfigClosures
    /**
     * The base directory of generated files. The default is
     * "${project.buildDir}/generated/source/proto".
     */
    public String generatedFilesBaseDir

    public WireConfigurator(Project project) {
        this.project = project
        if (WireUtils.isAndroidProject(project)) {
            generateProtoTasks = new AndroidGenerateProtoTaskCollection()
        } else {
            generateProtoTasks = new JavaGenerateProtoTaskCollection()
        }
        taskConfigClosures = new ArrayList()
        generatedFilesBaseDir = "${project.buildDir}/generated/source/wire"
    }


    /**
     * Returns the collection of generateProto tasks. Note the tasks are
     * available only after project evaluation.
     *
     * <p>Do not try to change the tasks other than in the closure provided
     * to {@link #WireGeneratorTask(Closure)}. The reason is explained
     * in the comments for the linked method.
     */
    public GenerateProtoTaskCollection getGenerateProtoTasks() {
        return generateProtoTasks
    }


    public class GenerateProtoTaskCollection {
        public Collection<WireGeneratorTask> all() {
            return project.tasks.findAll { task ->
                task instanceof WireGeneratorTask
            }
        }
    }

    public class AndroidGenerateProtoTaskCollection extends GenerateProtoTaskCollection {
        public Collection<WireGeneratorTask> ofFlavor(String flavor) {
            return all().findAll { task ->
                task.flavors.contains(flavor)
            }
        }

        public Collection<WireGeneratorTask> ofBuildType(String buildType) {
            return all().findAll { task ->
                task.buildType == buildType
            }
        }

        public Collection<WireGeneratorTask> ofVariant(String variant) {
            return all().findAll { task ->
                task.variant.name == variant
            }
        }

        public Collection<WireGeneratorTask> ofNonTest() {
            return all().findAll { task -> !task.isTestVariant }
        }

        public Collection<WireGeneratorTask> ofTest() {
            return all().findAll { task -> task.isTestVariant }
        }
    }

    public class JavaGenerateProtoTaskCollection extends GenerateProtoTaskCollection {
        public Collection<WireGeneratorTask> ofSourceSet(String sourceSet) {
            return all().findAll { task ->
                task.sourceSet.name == sourceSet
            }
        }
    }
}
