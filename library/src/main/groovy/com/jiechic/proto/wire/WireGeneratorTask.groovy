package com.jiechic.proto.wire

import com.squareup.wire.WireCompiler
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.api.tasks.incremental.InputFileDetails

/** Task to generate Java for a set of .proto files with the Wire compiler. */
class WireGeneratorTask extends DefaultTask {

    private final List<File> sourceDirs = new ArrayList()
    private final List<String> fileNames = new ArrayList<>()

    @OutputDirectory
    def File outputDir

    private boolean noOptions
    private boolean android
    private List<String> enumOptions = new ArrayList<>()

    private SourceSet sourceSet
    private Object variant
    private List<String> flavors
    private String buildType
    private boolean isTestVariant

    /**
     * Add a directory to protoc's include path.
     */
    void addSourceDir(File dir) {
        if (dir instanceof File) {
            sourceDirs.add(dir)
        }
    }

    void addFileName(String fileName) {
        fileNames.add(fileName)
    }

    void setInputDir(String filePath) {
        this.inputDir = new File(filePath)
    }

    void setOutputDir(String file) {
        this.outputDir = new File(file + "/java");
    }

    File getOutputDir() {
        return this.outputDir
    }

    void setNoOptions(boolean noOptions) {
        this.noOptions = noOptions
    }

    void setAndroid(boolean android) {
        this.android = android;
    }

    boolean getAndroid() {
        return this.android
    }

    void setEnumOptions(List<String> enumOptions) {
        if (enumOptions != null)
            this.enumOptions = enumOptions
    }

    List<String> getEnumOptions() {
        return this.enumOptions
    }

    void setSourceSet(SourceSet sourceSet) {
        WireUtils.checkState(!WireUtils.isAndroidProject(project),
                'sourceSet should not be set in an Android project')
        this.sourceSet = sourceSet
    }

    void setVariant(Object variant, boolean isTestVariant) {
        WireUtils.checkState(WireUtils.isAndroidProject(project),
                'variant should not be set in a Java project')
        this.variant = variant
        this.isTestVariant = isTestVariant
    }

    void setFlavors(List<String> flavors) {
        WireUtils.checkState(WireUtils.isAndroidProject(project),
                'flavors should not be set in a Java project')
        this.flavors = flavors
    }

    void setBuildType(String buildType) {
        WireUtils.checkState(WireUtils.isAndroidProject(project),
                'buildType should not be set in a Java project')
        this.buildType = buildType
    }

    SourceSet getSourceSet() {
        WireUtils.checkState(!WireUtils.isAndroidProject(project),
                'sourceSet should not be used in an Android project')
        WireUtils.checkNotNull(sourceSet, 'sourceSet is not set')
        return sourceSet
    }

    Object getVariant() {
        WireUtils.checkState(WireUtils.isAndroidProject(project),
                'variant should not be used in a Java project')
        WireUtils.checkNotNull(variant, 'variant is not set')
        return variant
    }

    boolean getIsTestVariant() {
        WireUtils.checkState(WireUtils.isAndroidProject(project),
                'isTestVariant should not be used in a Java project')
        WireUtils.checkNotNull(variant, 'variant is not set')
        return isTestVariant
    }

    List<String> getFlavors() {
        WireUtils.checkState(WireUtils.isAndroidProject(project),
                'flavors should not be used in a Java project')
        WireUtils.checkNotNull(flavors, 'flavors is not set')
        return flavors
    }

    String getBuildType() {
        WireUtils.checkState(WireUtils.isAndroidProject(project),
                'buildType should not be used in a Java project')
        WireUtils.checkNotNull(buildType, 'buildType is not set')
        return buildType
    }

    @TaskAction
    void execute(IncrementalTaskInputs incrementalTaskInputs) {
        println incrementalTaskInputs.incremental ? "CHANGED inputs considered out of date" : "ALL inputs considered out of date"

        if (!incrementalTaskInputs.incremental) {
            project.delete(outputDir.listFiles())
        }

        incrementalTaskInputs.outOfDate(new Action<InputFileDetails>() {
            @Override
            void execute(InputFileDetails inputFileDetails) {

                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }


                List<String> args = new ArrayList<>()

                Set<File> protoFiles = inputs.files.files


                for (File file : protoFiles) {

                    args.add("--proto_path=" + file.getParentFile().canonicalPath)
                }

                enumOptions.each { option ->
                    args.add("--enum_options=" + option)
                }

                if (android) {
                    args.add("--android")
                }

                args.add("--java_out=" + outputDir)

                for (File file : protoFiles) {
                    args.add(file.getName())
                }

                try {
                    WireCompiler.main(args.toArray(new String[args.size()]))
                } catch (Exception e) {
                    throw new RuntimeException(
                            "${e.getClass().getSimpleName()} generating Wire Java source for "
                                    + "$outputDir: ${e.getMessage()}", e);
                }

            }
        })


    }
}
