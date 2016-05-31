package com.squareup.wire.gradle

import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.squareup.wire.WireCompiler
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
/** Task to generate Java for a set of .proto files with the Wire compiler. */
class WireGeneratorTask extends DefaultTask {

//    public static final String PROTO_PATH_FLAG = "--proto_path=";
//    public static final String JAVA_OUT_FLAG = "--java_out=";
//    public static final String FILES_FLAG = "--files=";
//    public static final String INCLUDES_FLAG = "--includes=";
//    public static final String EXCLUDES_FLAG = "--excludes=";
//    public static final String QUIET_FLAG = "--quiet";
//    public static final String DRY_RUN_FLAG = "--dry_run";
//    public static final String NAMED_FILES_ONLY = "--named_files_only";
//    public static final String ANDROID = "--android";
//    public static final String COMPACT = "--compact";

    private final List includeDirs = new ArrayList()

    // accidentally modifying them.
    private String outputBaseDir
    // Tags for selectors inside protobuf.generateProtoTasks
    private SourceSet sourceSet
    private Object variant
    private ImmutableList<String> flavors
    private String buildType
    private boolean isTestVariant

    private State state = State.INIT

    private static enum State {
        INIT, CONFIG, FINALIZED
    }

    private void checkInitializing() {
        Utils.checkState(state == State.INIT, 'Should not be called after initilization has finished')
    }

    private void checkCanConfig() {
        Utils.checkState(state == State.CONFIG || state == State.INIT,
                'Should not be called after configuration has finished')
    }

    void setSourceSet(SourceSet sourceSet) {
        checkInitializing()
        Utils.checkState(!Utils.isAndroidProject(project),
                'sourceSet should not be set in an Android project')
        this.sourceSet = sourceSet
    }

    void setVariant(Object variant, boolean isTestVariant) {
        checkInitializing()
        Utils.checkState(Utils.isAndroidProject(project),
                'variant should not be set in a Java project')
        this.variant = variant
        this.isTestVariant = isTestVariant
    }

    void setFlavors(ImmutableList<String> flavors) {
        checkInitializing()
        Utils.checkState(Utils.isAndroidProject(project),
                'flavors should not be set in a Java project')
        this.flavors = flavors
    }

    void setBuildType(String buildType) {
        checkInitializing()
        Utils.checkState(Utils.isAndroidProject(project),
                'buildType should not be set in a Java project')
        this.buildType = buildType
    }

    SourceSet getSourceSet() {
        Utils.checkState(!Utils.isAndroidProject(project),
                'sourceSet should not be used in an Android project')
        Utils.checkNotNull(sourceSet, 'sourceSet is not set')
        return sourceSet
    }

    Object getVariant() {
        Utils.checkState(Utils.isAndroidProject(project),
                'variant should not be used in a Java project')
        Utils.checkNotNull(variant, 'variant is not set')
        return variant
    }

    boolean getIsTestVariant() {
        Utils.checkState(Utils.isAndroidProject(project),
                'isTestVariant should not be used in a Java project')
        Utils.checkNotNull(variant, 'variant is not set')
        return isTestVariant
    }

    ImmutableList<String> getFlavors() {
        Utils.checkState(Utils.isAndroidProject(project),
                'flavors should not be used in a Java project')
        Utils.checkNotNull(flavors, 'flavors is not set')
        return flavors
    }

    String getBuildType() {
        Utils.checkState(Utils.isAndroidProject(project),
                'buildType should not be used in a Java project')
        Utils.checkNotNull(buildType, 'buildType is not set')
        return buildType
    }

    void doneInitializing() {
        Utils.checkState(state == State.INIT, "Invalid state: ${state}")
        state = State.CONFIG
    }

    void doneConfig() {
        Utils.checkState(state == State.CONFIG, "Invalid state: ${state}")
        state = State.FINALIZED
    }













    String getOutputBaseDir(){
        return outputBaseDir
    }
    void setOutputBaseDir(String outputBaseDir) {
        this.outputBaseDir = outputBaseDir+"/java"
    }

    /**
     * Add a directory to protoc's include path.
     */
    void include(Object dir) {
        if (dir instanceof File) {
            includeDirs.add(dir)
        } else {
            includeDirs.add(project.file(dir))
        }
    }

    Collection<File> getAllOutputDirs() {
        ImmutableList.Builder<File> dirs = ImmutableList.builder()
//        builtins.each { builtin ->
//            dirs.add(new File(getOutputDir(builtin)))
//        }
//        plugins.each { plugin ->
//            dirs.add(new File(getOutputDir(plugin)))
//        }
        dirs.add(new File(outputBaseDir))
        return dirs.build()
    }

    @TaskAction
    void compile() {

        File outputDir = new File(outputBaseDir)
        outputDir.mkdirs()

        Set<File> protoFiles = inputs.sourceFiles.files
        List<String> args = Lists.newArrayList()

//        Collection<String> enumOptions = configuration.getEnumOptions()
//        Collection<String> roots = configuration.getRoots()
//        String serviceWriter = configuration.getServiceWriter()
//        String registryClass = configuration.getRegistryClass()
//        Collection<String> serviceWriterOpts = configuration.getServiceWriterOpts()

        for (String string : includeDirs) {
            args.add("--proto_path=" + string)
        }

//        if (noOptions) {
//        args.add("--no_options")
//        }
//        enumOptions.each { option ->
//            args.add("--enum_options=" + option)
//        }
//        roots.each { root ->
//            args.add("--roots=" + root)
//        }
//        if (!Strings.isNullOrEmpty(serviceWriter)) {
//            args.add("--service_writer=" + serviceWriter)
//        }
//        if (!Strings.isNullOrEmpty(registryClass)) {
//            args.add("--registry_class=" + registryClass)
//        }
//        serviceWriterOpts.each { serviceWriterOpt ->
//            args.add("--service_writer_opt=" + serviceWriterOpt)
//        }

        args.add("--java_out=" + outputDir.getAbsolutePath())

        for (File protoFile : protoFiles) {
            args.add(protoFile.getAbsoluteFile().getName())
        }

        try {
            WireCompiler.main(args.toArray(new String[args.size()]))
        } catch (Exception e) {
            throw new RuntimeException(
                    "${e.getClass().getSimpleName()} generating Wire Java source for "
                            + "$outputDir: ${e.getMessage()}", e);
        }
    }

//    @OutputDirectory
//    File outputDir
//
//    @InputFiles
//    Collection<File> getInputFiles() {
//        Set<File> inputFiles = Sets.newHashSet()
//        for (WireSourceSetExtension configuration : getConfigurations()) {
//            for (Map.Entry<String, Collection<String>> entry : configuration.getFiles().asMap().entrySet()) {
//                for (String file : entry.value) {
//                    inputFiles.add(new File(entry.key, file))
//                }
//            }
//        }
//        return inputFiles
//    }
//
//    @TaskAction
//    void generate() {
//
//        Set<File> protoFiles = inputs.sourceFiles.files
//        List<String> args = Lists.newArrayList()
//        for (File protoFile : protoFiles) {
//            args.add("--proto_path=" + protoFile.getAbsolutePath())
//        }
//
//        // Clear the output directory.
//        File outDir = getOutputDir()
//        outDir.deleteDir()
//        outDir.mkdirs()
//
//        for (WireSourceSetExtension configuration : getConfigurations()) {
//            boolean noOptions = configuration.getNoOptions()
//            Collection<String> enumOptions = configuration.getEnumOptions()
//            Collection<String> roots = configuration.getRoots()
//            String serviceWriter = configuration.getServiceWriter()
//            String registryClass = configuration.getRegistryClass()
//            Collection<String> serviceWriterOpts = configuration.getServiceWriterOpts()
//
//            Multimap<String, String> files = configuration.getFiles()
//            for (Map.Entry<String, Collection<String>> entry : files.asMap().entrySet()) {
//                String protoDir = entry.key
//                List<String> args = Lists.newArrayList()
//                args.add("--proto_path=" + project.file(protoDir).getAbsolutePath())
//
//                for (String path : configuration.protoPaths) {
//                    args.add("--proto_path=" + project.file(path).getAbsolutePath())
//                }
//
//                if (noOptions) {
//                    args.add("--no_options")
//                }
//                enumOptions.each { option ->
//                    args.add("--enum_options=" + option)
//                }
//                roots.each { root ->
//                    args.add("--roots=" + root)
//                }
//                if (!Strings.isNullOrEmpty(serviceWriter)) {
//                    args.add("--service_writer=" + serviceWriter)
//                }
//                if (!Strings.isNullOrEmpty(registryClass)) {
//                    args.add("--registry_class=" + registryClass)
//                }
//                serviceWriterOpts.each { serviceWriterOpt ->
//                    args.add("--service_writer_opt=" + serviceWriterOpt)
//                }
//
//                args.add("--java_out=" + outputDir.absolutePath)
//                args.addAll(entry.value)
//
//                try {
//                    WireCompiler.main(args.toArray(new String[args.size()]))
//                } catch (Exception e) {
//                    throw new RuntimeException(
//                            "${e.getClass().getSimpleName()} generating Wire Java source for "
//                                    + "$protoDir: ${e.getMessage()}", e);
//                }
//            }
//        }
//    }
}
