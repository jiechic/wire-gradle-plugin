package com.jiechic.proto.wire

import org.gradle.api.*
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.tasks.SourceSet

import javax.inject.Inject

/**
 * Plugin which adds a Wire proto generation step to a project.
 * <p>
 * For the simplest use case of a single folder of protos, just apply the plugin and place the
 * protos in src/main/proto. If multiple folders or other compiler flags need to be specified, use
 * the "proto" extension on any sourceSet in the project. See {@link WireExtension} for
 * supported configuration settings.
 */

class WirePlugin implements Plugin<Project> {

    // any one of these plugins should be sufficient to proceed with applying this plugin
    private static final List<String> prerequisitePluginOptions = [
            'java',
            'org.gradle.java',
            'com.android.application',
            'com.android.library',
            'android',
            'android-library'
    ]

    private final FileResolver fileResolver;
    private boolean wasApplied = false;

    @Inject
    public WirePlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    void apply(Project project) {

        def gv = project.gradle.gradleVersion =~ "(\\d*)\\.(\\d*).*"
        if (!gv || !gv.matches() || gv.group(1).toInteger() < 2 || gv.group(2).toInteger() < 12) {
            println("You are using Gradle ${project.gradle.gradleVersion}: "
                    + " This version of the protobuf plugin works with Gradle version 2.12+")
        }

        // At least one of the prerequisite plugins must by applied before this plugin can be applied, so
        // we will use the PluginManager.withPlugin() callback mechanism to delay applying this plugin until
        // after that has been achieved. If project evaluation completes before one of the prerequisite plugins
        // has been applied then we will assume that none of prerequisite plugins were specified and we will
        // throw an Exception to alert the user of this configuration issue.
        Action<? super AppliedPlugin> applyWithPrerequisitePlugin = { prerequisitePlugin ->
            if (wasApplied) {
                project.logger.warn('The com.jiechic.proto.wire plugin was already applied to the project: ' + project.path
                        + ' and will not be applied again after plugin: ' + prerequisitePlugin.id)

            } else {
                wasApplied = true

                doApply(project)
            }
        }

        prerequisitePluginOptions.each { pluginName ->
            project.pluginManager.withPlugin(pluginName, applyWithPrerequisitePlugin)
        }

        project.afterEvaluate {
            if (!wasApplied) {
                throw new GradleException('The com.squareup.wire plugin could not be applied during project evaluation.'
                        + ' The Java plugin or one of the Android plugins must be applied to the project first.')
            }
        }
    }


    private void doApply(Project project) {

        project.convention.plugins.wireConfig = new WirebufConvention(project);

        project.extensions.create("wire", WireExtension)

        project.wire.android = WireUtils.isAndroidProject(project)

        addSourceSetExtensions(project)

        project.afterEvaluate {
            addWireTasks(project)
            linkGenerateProtoTasksToJavaCompile(project)
        }
    }

    /**
     * Adds the proto extension to all SourceSets, e.g., it creates
     * sourceSets.main.proto and sourceSets.test.proto.
     */
    private void addSourceSetExtensions(Project project) {
        getSourceSets(project).each { sourceSet ->
            sourceSet.extensions.create('wire', WireSourceDirectorySet, sourceSet.name, fileResolver, project.convention.plugins.wireConfig)
        }
        getSourceSets(project).each { sourceSet ->
            createConfiguration(project, sourceSet.name)
        }
    }

    /**
     * Returns the sourceSets container of a Java or an Android project.
     */
    private Object getSourceSets(Project project) {
        if (WireUtils.isAndroidProject(project)) {
            return project.android.sourceSets
        } else {
            return project.sourceSets
        }
    }

    /**
     * Creates a configuration if necessary for a source set so that the build
     * author can configure dependencies for it.
     */
    private createConfiguration(Project project, String sourceSetName) {
        String configName = WireUtils.getConfigName(sourceSetName, 'wire')
        if (project.configurations.findByName(configName) == null) {
            project.configurations.create(configName) {
                visible = false
                transitive = false
                extendsFrom = []
            }
        }
    }

    /**
     * Adds Protobuf-related tasks to the project.
     */
    private addWireTasks(Project project) {
        if (WireUtils.isAndroidProject(project)) {
            getNonTestVariants(project).each { variant ->
                addTasksForVariant(project, variant, false)
            }
            project.android.testVariants.each { testVariant ->
                addTasksForVariant(project, testVariant, true)
            }
        } else {
            getSourceSets(project).each { sourceSet ->
                addTasksForSourceSet(project, sourceSet)
            }
        }
    }

    /**
     * Creates Protobuf tasks for a variant in an Android project.
     */
    private addTasksForVariant(Project project, final Object variant, boolean isTestVariant) {
//        // The collection of sourceSets that will be compiled for this variant
//        def sourceSetNames = new ArrayList()
//        def sourceSets = new ArrayList()
//        if (isTestVariant) {
//            // All test variants will include the androidTest sourceSet
//            sourceSetNames.add 'androidTest'
//        } else {
//            // All non-test variants will include the main sourceSet
//            sourceSetNames.add 'main'
//        }
//        sourceSetNames.add variant.name
//        sourceSetNames.add variant.buildType.name
        List<String> flavorListBuilder = new ArrayList<String>()
        if (variant.hasProperty('productFlavors')) {
            variant.productFlavors.each { flavor ->
                flavorListBuilder.add flavor.name
            }
        }
//        sourceSetNames.each { sourceSetName ->
//            def object = project.android.sourceSets.maybeCreate(sourceSetName)
//            if (object.wire == null) {
//                sourceSet.extensions.create('wire', WireSourceDirectorySet, sourceSet.name, fileResolver, project.convention.plugins.wireConfig)
//            }
//            sourceSets.add object
//        }
        for (Object sourceSet : variant.properties.sourceSets) {
            if (!sourceSet.hasProperty("wire")) {
                sourceSet.extensions.create('wire', WireSourceDirectorySet, sourceSet.name, fileResolver, project.convention.plugins.wireConfig)

            }
        }

        def generateProtoTask = addGenerateProtoTask(project, variant.name, variant.properties.sourceSets)
        generateProtoTask.setVariant(variant, isTestVariant)
        generateProtoTask.flavors = flavorListBuilder
        generateProtoTask.buildType = variant.buildType.name

    }

    /**
     * Creates Protobuf tasks for a sourceSet in a Java project.
     */
    private addTasksForSourceSet(Project project, SourceSet sourceSet) {
        def generateProtoTask = addGenerateProtoTask(project, sourceSet.name, [sourceSet])
        generateProtoTask.sourceSet = sourceSet
    }

    private Task addGenerateProtoTask(Project project, String sourceSetOrVariantName, Collection<Object> sourceSets) {
        def generateProtoTaskName = 'generate' +
                WireUtils.getSourceSetSubstringForTaskNames(sourceSetOrVariantName) + 'WireProto'
//        return project.tasks.create(generateProtoTaskName, WireGeneratorTask) {
//            description = "Compiles Wire Proto source for '${sourceSetOrVariantName}'"
//            outputDir = "${project.wireConfig.generatedFilesBaseDir}/${sourceSetOrVariantName}"
//            enumOptions = project.wire.enumOptions;
//            android = project.wire.android;
//            noOptions = project.wire.noOptions;
//            sourceSets.each { sourceSet ->
//                // Include sources
//                inputs.source sourceSet.wire
//
////                // Include extracted sources
////                ConfigurableFileTree extractedProtoSources =
////                        project.fileTree(getExtractedProtosDir(project, sourceSet.name)) {
////                            include "**/*.proto"
////                        }
//////                inputs.source extractedProtoSources
////                include extractedProtoSources.dir
////
////                // Register extracted include protos
////                ConfigurableFileTree extractedIncludeProtoSources =
////                        project.fileTree(getExtractedIncludeProtosDir(project, sourceSet.name)) {
////                            include "**/*.proto"
////                        }
////                // Register them as input, but not as "source".
////                // Inputs are checked in incremental builds, but only "source" files are compiled.
//////                inputs.dir extractedIncludeProtoSources
////                // Add the extracted include dir to the --proto_path include paths.
////                include extractedIncludeProtoSources.dir
//            }
//        }
        return project.tasks.create(generateProtoTaskName, WireGeneratorTask, new Action<WireGeneratorTask>() {
            @Override
            void execute(WireGeneratorTask wireGeneratorTask) {
                wireGeneratorTask.description = "Compiles Wire Proto source for '${sourceSetOrVariantName}'"
//                wireGeneratorTask.outputs.dir(project.fileTree("${project.wireConfig.generatedFilesBaseDir}/${sourceSetOrVariantName}"))
                wireGeneratorTask.outputDir = "${project.wireConfig.generatedFilesBaseDir}/${sourceSetOrVariantName}"
//                wireGeneratorTask.outputDir = "${project.wireConfig.generatedFilesBaseDir}/${sourceSetOrVariantName}"
//                wireGeneratorTask.outputs.file
                wireGeneratorTask.enumOptions = project.wire.enumOptions;
                wireGeneratorTask.android = project.wire.android;
                wireGeneratorTask.noOptions = project.wire.noOptions;


                sourceSets.each { sourceSet ->
                    WireSourceDirectorySet protoSrcDirSet = sourceSet.wire
                    if (protoSrcDirSet != null) {
                        for (File file : protoSrcDirSet.srcDirs) {
                            wireGeneratorTask.inputs.dir(protoSrcDirSet.getSourceDirectories())
                        }
                    }
                }
//                for (SourceSet sourceSet : sourceSets) {
//
//                    for (File file : protoSrcDirSet.srcDirs) {
//                        if (file.list() != null) {
//                            wireGeneratorTask.addSourceDir(file)
//                            for (String string : file.list()) {
//                                wireGeneratorTask.addFileName(string)
//                            }
//                        }
//
//                    }

//                    // Include extracted sources
//                    ConfigurableFileTree extractedProtoSources =
//                            project.fileTree(getExtractedProtosDir(project, sourceSet.name))
//                    for (File file : protoSrcDirSet.srcDirs) {
//                        wireGeneratorTask.inputs.dir(file.canonicalPath)
//                    }
////                    if (extractedProtoSources.dir.list() != null) {
////                        wireGeneratorTask.addSourceDir(extractedProtoSources.dir)
////                        for (String string : extractedProtoSources.dir.list()) {
////                            wireGeneratorTask.addFileName(string)
////                        }
////                    }
//                    // Register extracted include protos
//                    ConfigurableFileTree extractedIncludeProtoSources =
//                            project.fileTree(getExtractedIncludeProtosDir(project, sourceSet.name))
//                    for (File file : extractedIncludeProtoSources.srcDirs) {
//                        wireGeneratorTask.inputs.dir(file.canonicalPath)
//
//                    }

//                    if (extractedIncludeProtoSources.dir.list() != null) {
//                        wireGeneratorTask.addSourceDir(extractedIncludeProtoSources.dir)
//                        for (String string : extractedIncludeProtoSources.dir.list()) {
//                            wireGeneratorTask.addFileName(string)
//                        }
//                    }
//                }
            }
        })
    }

    private linkGenerateProtoTasksToJavaCompile(Project project) {
        if (WireUtils.isAndroidProject(project)) {
            (getNonTestVariants(project) + project.android.testVariants).each { variant ->
                project.wireConfig.generateProtoTasks.ofVariant(variant.name).each { generateProtoTask ->
                    // This cannot be called once task execution has started
                    generateProtoTask.outputs.files.each { dir ->
                        variant.registerJavaGeneratingTask(generateProtoTask, dir)
                    }
                }
            }
        } else {
            for (SourceSet sourceSet : project.sourceSets) {
                def javaCompileTask = project.tasks.getByName(sourceSet.getCompileTaskName("java"))
                project.wireConfig.generateProtoTasks.ofSourceSet(sourceSet.name).each { generateProtoTask ->
                    javaCompileTask.dependsOn(generateProtoTask)
                    generateProtoTask.outputs.files.each { dir ->
                        javaCompileTask.source += project.fileTree(dir: dir)
                    }
                }
//                for (WireGeneratorTask generateProtoTask : project.wireConfig.generateProtoTasks.ofSourceSet(sourceSet.name)) {
//                    javaCompileTask.dependsOn(generateProtoTask)
//                    javaCompileTask.inputs.dir(generateProtoTask.outputs.files)
//                    project.fileTree()
//                }
            }
        }
    }

    private Object getNonTestVariants(Project project) {
        return project.android.hasProperty('libraryVariants') ?
                project.android.libraryVariants : project.android.applicationVariants
    }


    private String getExtractedIncludeProtosDir(Project project, String sourceSetName) {
        return "${project.buildDir}/extracted-include-protos/${sourceSetName}"
    }

    private String getExtractedProtosDir(Project project, String sourceSetName) {
        return "${project.buildDir}/extracted-protos/${sourceSetName}"
    }
}