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
    public static final String COVERITY_TASK_GROUP = "Coverity static analysis tasks"
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
        tasks.create('coverity-auth', CoverityAuthTask) { task ->
            task.group = COVERITY_TASK_GROUP
            task.description = 'Generates a Coverity authentication key file.'
        }
    }

    /**
     * Create the main coverity task for static analysis
     */
    @Mutate
    void createCoverityTasks(ModelMap<Task> tasks,
                             @Path('binaries') ModelMap<BinarySpec> binaries,
                             CoveritySpec coverity) {

        if (!coverity.enabled) return

        List<String> runTasks = []

        // Create one "run" task per stream
        coverity.streams.each { CoverityStream stream ->
            if (!stream.enabled) return

            List<BinarySpec> matchedBinaries = binaries.findAll(stream.filter)

            String runTaskName = "coverityRun${stream.name.capitalize()}"
            String emitTaskName = "coverityEmit${stream.name.capitalize()}"
            runTasks << runTaskName

            List<File> allSources = []
            List<String> emitTasks = []

            // Create emit tasks for each binary on this stream
            matchedBinaries.each { BinarySpec binary ->
                Set<LanguageSourceSet> sourceSets = binary.inputs.findAll { !(it in JvmResourceSet) }
                List<File> sources = sourceSets*.source*.files.flatten() as List<File>

                if (!sources.isEmpty()) {
                    allSources.addAll(sources)

                    if (binary in NativeBinarySpec) {
                        emitTasks.addAll(createNativeCoverityTasks(binary, stream))
                    } else if (binary in JarBinarySpec) {
                        emitTasks.addAll(createJavaCoverityTasks(binary, stream))
                    }
                }
            }

            // Create the emit and run tasks
            tasks.create(emitTaskName) { Task task ->
                task.group = COVERITY_TASK_GROUP
                task.description = "Emits Coverity intermediate data for stream '${stream.name}'."
                task.dependsOn emitTasks
            }

            tasks.create(runTaskName, CoverityRunTask) { CoverityRunTask task ->
                task.group = COVERITY_TASK_GROUP
                task.description = "Runs Coverity static analysis for stream '${stream.name}'."
                task.stream = stream
                task.dependsOn emitTaskName

                if (allSources.isEmpty()) {
                    task.enabled = false
                } else {
                    task.sourceFiles.addAll(allSources)
                }
            }
        }

        tasks.create('coverity') { Task task ->
            task.group = COVERITY_TASK_GROUP
            task.description = "Runs Coverity static analysis."
            task.dependsOn runTasks
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
                task.group = COVERITY_TASK_GROUP
                task.compiler = cCompiler
                task.compilerType = 'gcc'
            }
        }
    }

    /**
     * Create static analysis tasks for native code
     */
    private static List<String> createNativeCoverityTasks(NativeBinarySpec binary, CoverityStream stream) {
        List<String> taskNames = []

        // Create a cov-translate task for each compile task
        binary.tasks.findAll{ it in AbstractNativeCompileTask }.each { Task task ->
            if (!(binary.toolChain in GccToolChain)) return

            AbstractNativeCompileTask compileTask = task as AbstractNativeCompileTask
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
            String translateTaskName = 'coverityTranslate' +
                    stream.name.capitalize() + compileTask.name.capitalize()
            taskNames << translateTaskName

            String path = System.env.PATH
            if (toolChain && toolChain.path) {
                path = [toolChain.path.join(File.pathSeparator), path].join(File.pathSeparator)
            }

            binary.tasks.create(translateTaskName, CoverityTranslateTask) { CoverityTranslateTask translateTask ->
                translateTask.stream = stream
                translateTask.compiler = compiler
                translateTask.compileTask = compileTask
                translateTask.environment['PATH'] = path
                translateTask.dependsOn configTaskName, compileTask
            }
        }

        return taskNames
    }

    /**
     * Create static analysis tasks for Java code
     */
    private static List<String> createJavaCoverityTasks(JarBinarySpec binary, CoverityStream stream) {
        List<String> taskNames = []

        // Create a cov-emit-java task for each compile task
        binary.tasks.findAll{ it in JavaCompile }.each { Task task ->
            JavaCompile compileTask = task as JavaCompile
            String taskName = 'coverityEmitJava' +
                    stream.name.capitalize() + compileTask.name.capitalize()
            taskNames << taskName

            binary.tasks.create(taskName, CoverityEmitJavaTask) { CoverityEmitJavaTask emitJavaTask ->
                emitJavaTask.stream = stream
                emitJavaTask.compileTask = compileTask
                emitJavaTask.dependsOn compileTask
            }
        }

        return taskNames
    }
}
