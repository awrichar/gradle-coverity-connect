package co.arichardson.gradle.coverity

import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.JarBinarySpec
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask
import org.gradle.model.Defaults
import org.gradle.model.Model
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.platform.base.BinarySpec

class CoverityConnectPlugin extends RuleSource {
    private static final int COVERITY_AUTH_KEY_NOT_FOUND = 4

    private static final String INTERMEDIATES_DIR = 'coverity-intermediates'
    private static final String RESULTS_DIR = 'coverity-results'
    private static final String RESULTS_FILE = 'results.txt'

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

        coverity.port = '8080'
        coverity.authKeyFile = new File(System.getProperty('user.home'), '.coverity_key')
        coverity.streams.beforeEach {
            filter = { true }
        }
    }

    /**
     * Create the coverity-auth task
     */
    @Mutate
    void createCoverityAuthTask(ModelMap<Task> tasks, CoveritySpec coverity) {
        tasks.create('coverity-auth', Exec) {
            executable Utils.findCoverityTool('cov-manage-im', coverity.path)
            args '--mode', 'auth-key', '--create'
            args '--output-file', coverity.authKeyFile
            addHostConfig(it, coverity)

            doFirst {
                def console = System.console()
                if (!console) {
                    throw new GradleException('Could not open an interactive console - if you are running in daemon mode, please try using --no-daemon instead.')
                }

                def prefix = 'Please enter your Coverity credentials.'
                args '--user', console.readLine("\n${prefix}\nUsername: ")
                args '--password', new String(console.readPassword("Password: "))
            }
        }
    }

    /**
     * Create the main coverity task for static analysis
     */
    @Mutate
    void createCoverityTasks(ModelMap<Task> tasks,
            @Path('binaries') ModelMap<BinarySpec> binaries,
            CoveritySpec coverity,
            @Path('buildDir') File buildDir) {

        tasks.create('coverity', Task)
        def mainTask = tasks.get('coverity')

        def covRun = Utils.findCoverityTool('cov-run-desktop', coverity.path)
        def mainIntermediates = new File(buildDir, INTERMEDIATES_DIR)
        def mainResults = new File(buildDir, RESULTS_DIR)

        // Create one "run" task per stream
        coverity.streams.each { stream ->
            def taskName = "coverity${stream.name.capitalize()}"
            def intermediates = new File(mainIntermediates, stream.name)
            def results = new File(mainResults, "${stream.name}/${RESULTS_FILE}")

            tasks.create(taskName, Exec)
            def task = tasks.get(taskName)

            mainTask.dependsOn task
            task.executable covRun
            task.args '--dir', intermediates.path
            task.args '--stream', stream.stream
            task.args '--auth-key-file', coverity.authKeyFile
            task.args '--text-output', results
            addHostConfig(task, coverity)
            task.args(*coverity.args)

            task.doFirst {
                intermediates.mkdirs()
                results.parentFile.mkdirs()
            }

            task.ignoreExitValue = true
            task.doLast {
                if (execResult.exitValue == COVERITY_AUTH_KEY_NOT_FOUND) {
                    throw new GradleException("Authentication key was not found - please run 'gradle coverity-auth' to generate.")
                }

                execResult.assertNormalExitValue()

                logger.lifecycle("Static analysis complete. Results saved to ${results}")
            }

            // Create sub-tasks for each binary on this stream
            binaries.findAll(stream.filter).each {
                if (it in NativeBinarySpec) {
                    createNativeCoverityTask(it, coverity, task, intermediates)
                } else if (it in JarBinarySpec) {
                    createJavaCoverityTask(it, coverity, task, intermediates)
                }
            }
        }
    }

    /**
     * Create static analysis tasks for native code
     */
    private void createNativeCoverityTask(NativeBinarySpec binary,
            CoveritySpec coverity, Exec coverityTask, File intermediates) {

        def covTranslate = Utils.findCoverityTool('cov-translate', coverity.path)

        binary.tasks.withType(AbstractNativeCompileTask) { compileTask ->
            // Locate the compiler binary
            def compiler = Utils.findGccCompiler(binary.toolChain, compileTask)
            if (compiler == null) {
                coverityTask.doFirst {
                    throw new GradleException("Could not infer GCC compiler for: ${compileTask}")
                }
                return
            }

            // Locate the options file generated by the compile task
            def optionsFile = new File(temporaryDir, 'options.txt')

            compileTask.source.files.each { sourceFile ->
                // Add this file to the list for the cov-run-desktop task
                coverityTask.args sourceFile

                // Create a cov-translate task for each source file
                def taskName = binary.tasks.taskName('coverity', sourceFile.name)
                binary.tasks.create(taskName, Exec) { task ->
                    task.dependsOn compileTask
                    coverityTask.dependsOn task

                    task.executable covTranslate
                    task.args '--dir', intermediates.path
                    task.args compiler, "@${optionsFile}"
                    task.args sourceFile.path
                }
            }
        }
    }

    /**
     * Create static analysis tasks for Java code
     */
    private void createJavaCoverityTask(JarBinarySpec binary,
            CoveritySpec coverity, Exec coverityTask, File intermediates) {

        def covEmitJava = Utils.findCoverityTool('cov-emit-java', coverity.path)

        binary.tasks.withType(JavaCompile) { compileTask ->
            // Create a cov-emit-java task for each compile task
            def taskName = binary.tasks.taskName('coverity')
            binary.tasks.create(taskName, Exec) { task ->
                task.dependsOn compileTask
                coverityTask.dependsOn task

                task.executable covEmitJava
                task.args '--dir', intermediates.path
                task.args '--compiler-outputs', compileTask.destinationDir.path
                task.args '--classpath', compileTask.classpath.asPath
                task.args '--bootclasspath', compileTask.options.bootClasspath
                task.args '--encoding', compileTask.options.encoding
                task.args '--source', compileTask.sourceCompatibility

                compileTask.source.files.each { sourceFile ->
                    // Add this file to the list for the cov-run-desktop task
                    coverityTask.args sourceFile
                    task.args sourceFile
                }
            }
        }
    }

    private static void addHostConfig(Exec task, CoveritySpec coverity) {
        if (coverity.host != null) {
            task.args '--host', coverity.host
        } else {
            throw new IllegalArgumentException('Coverity Connect host was not specified.')
        }

        if (coverity.port != null) {
            task.args '--port', coverity.port
        }
    }
}
