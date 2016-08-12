package co.arichardson.gradle.coverity

import co.arichardson.gradle.coverity.tasks.CoverityAuthTask
import co.arichardson.gradle.coverity.tasks.CoverityConfigureTask
import co.arichardson.gradle.coverity.tasks.CoverityEmitJavaTask
import co.arichardson.gradle.coverity.tasks.CoverityRunTask
import co.arichardson.gradle.coverity.tasks.CoverityTranslateTask
import org.gradle.api.Task
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.JarBinarySpec
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.c.tasks.CCompile
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.language.jvm.JvmResourceSet
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask
import org.gradle.model.Defaults
import org.gradle.model.Finalize
import org.gradle.model.Model
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.nativeplatform.toolchain.internal.gcc.AbstractGccCompatibleToolChain
import org.gradle.nativeplatform.toolchain.internal.gcc.GccToolChain
import org.gradle.platform.base.BinarySpec

class CoverityConnectPlugin extends RuleSource {
    public static final String COVERITY_KEY_FILE = '.coverity_key'
    private static final PlatformToolchainMap platformToolchains = [:]

    /**
     * Create the model.coverity block
     */
    @Model
    void coverity(CoveritySpec spec) {}

    /**
     * Set defaults for the coverity block
     */
    @Defaults
    void setCoverityDefaults(CoveritySpec coverity) {
        if ('COVERITY_HOME' in System.env) {
            coverity.path = new File(System.env.COVERITY_HOME)
        }

        coverity.enabled = true
        coverity.port = '8080'
        coverity.authKeyFile = new File(System.getProperty('user.home'), COVERITY_KEY_FILE)
        coverity.streams.beforeEach {
            it.enabled = true
            it.filter = { true }
        }
    }

    /**
     * Save a map of compiler names for later use
     * TODO: This seems hacky; is there a better way?
     */
    @Finalize
    void mapToolChains(NativeToolChainRegistry toolChains) {
        toolChains.each { NativeToolChain toolChain ->
            if (!(toolChain in AbstractGccCompatibleToolChain)) return

            toolChain.eachPlatform { GccPlatformToolChain platformToolChain ->
                platformToolchains.setCCompiler(platformToolChain.platform, toolChain,
                        platformToolChain.cCompiler.executable)
                platformToolchains.setCppCompiler(platformToolChain.platform, toolChain,
                        platformToolChain.cppCompiler.executable)
            }
        }
    }

    /**
     * Create the coverity-auth task
     */
    @Mutate
    void createCoverityAuthTask(ModelMap<Task> tasks) {
        tasks.create('coverity-auth', CoverityAuthTask)
    }

    /**
     * Create the main coverity task for static analysis
     */
    @Mutate
    void createCoverityTasks(ModelMap<Task> tasks,
                             @Path('binaries') ModelMap<BinarySpec> binaries,
                             CoveritySpec coverity) {

        if (!coverity.enabled) return

        Task mainTask = Utils.addTask(tasks, 'coverity', Task)
        Task mainCleanTask = Utils.addTask(tasks, 'cleanCoverity', Task)

        // Create one "run" task per stream
        coverity.streams.each { CoverityStream stream ->
            if (!stream.enabled) return

            List<BinarySpec> matchedBinaries = binaries.findAll(stream.filter)

            // Configure the run and clean tasks
            String taskName = "coverity${stream.name.capitalize()}"
            String cleanTaskName = "clean${taskName}"
            CoverityRunTask runTask = Utils.addTask(tasks, taskName, CoverityRunTask) as CoverityRunTask
            Delete cleanTask = Utils.addTask(tasks, cleanTaskName, Delete) as Delete

            runTask.stream = stream
            cleanTask.delete runTask.configDir
            cleanTask.delete runTask.intermediatesDir
            cleanTask.delete runTask.resultsFile

            // Create sub-tasks for each binary on this stream
            boolean hasSources = false
            matchedBinaries.each { BinarySpec binary ->
                Set<LanguageSourceSet> sourceSets = binary.inputs.findAll { !(it in JvmResourceSet) }
                List<File> sources = sourceSets*.source*.files.flatten() as List<File>

                if (!sources.isEmpty()) {
                    hasSources = true
                    runTask.sourceFiles.addAll(sources)

                    if (binary in NativeBinarySpec) {
                        createNativeCoverityTask(binary, runTask, stream)
                    } else if (binary in JarBinarySpec) {
                        createJavaCoverityTask(binary, runTask, stream)
                    }
                }
            }

            if (hasSources) mainTask.dependsOn runTask
            mainCleanTask.dependsOn cleanTask
        }
    }

    /**
     * Generate a unique name for a cov-configure task
     */
    private static String getConfigTaskName(NativeToolChain toolChain, NativePlatform platform) {
        'configureCoverity' + toolChain.name.capitalize() + platform.name.capitalize()
    }

    /**
     * Create a cov-configure task for each GCC toolchain
     */
    @Finalize
    void createConfigureTasks(ModelMap<Task> tasks, NativeToolChainRegistry toolChains) {
        platformToolchains.cCompilers.each { PlatformToolchainMap.PlatformId platformId,
                                             String cCompiler ->

            String configTaskName = getConfigTaskName(platformId.toolChain, platformId.platform)
            tasks.create(configTaskName, CoverityConfigureTask) { CoverityConfigureTask task ->
                task.compiler = cCompiler
                task.compilerType = 'gcc'
            }
        }
    }

    /**
     * Create static analysis tasks for native code
     */
    private static void createNativeCoverityTask(NativeBinarySpec binary,
                                                 CoverityRunTask coverityTask,
                                                 CoverityStream stream) {

        // Create a cov-translate task for each compile task
        binary.tasks.withType(AbstractNativeCompileTask) { AbstractNativeCompileTask compileTask ->
            if (!(binary.toolChain in GccToolChain)) return
            GccToolChain toolChain = binary.toolChain as GccToolChain

            String compiler
            if (compileTask in CCompile) {
                compiler = platformToolchains.getCCompiler(binary.targetPlatform, toolChain)
            } else if (compileTask in CppCompile) {
                compiler = platformToolchains.getCppCompiler(binary.targetPlatform, toolChain)
            } else {
                return
            }

            String configTaskName = getConfigTaskName(toolChain, binary.targetPlatform)
            String translateTaskName = coverityTask.name + compileTask.name.capitalize()

            String path = System.env.PATH
            if (toolChain && toolChain.path) {
                path = [toolChain.path.join(File.pathSeparator), path].join(File.pathSeparator)
            }

            binary.tasks.create(translateTaskName, CoverityTranslateTask) { CoverityTranslateTask task ->
                task.stream = stream
                task.compiler = compiler
                task.compileTask = compileTask
                task.environment['PATH'] = path

                task.dependsOn configTaskName, compileTask
                coverityTask.dependsOn task
            }
        }
    }

    /**
     * Create static analysis tasks for Java code
     */
    private static void createJavaCoverityTask(JarBinarySpec binary,
                                               CoverityRunTask coverityTask,
                                               CoverityStream stream) {

        // Create a cov-emit-java task for each compile task
        binary.tasks.withType(JavaCompile) { compileTask ->
            def taskName = coverityTask.name + compileTask.name.capitalize()
            binary.tasks.create(taskName, CoverityEmitJavaTask) { CoverityEmitJavaTask task ->
                task.stream = stream
                task.compileTask = compileTask

                task.dependsOn compileTask
                coverityTask.dependsOn task
            }
        }
    }
}
