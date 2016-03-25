package co.arichardson.gradle.coverity

import co.arichardson.gradle.coverity.tasks.CoverityAuthTask
import co.arichardson.gradle.coverity.tasks.CoverityEmitJavaTask
import co.arichardson.gradle.coverity.tasks.CoverityRunTask
import co.arichardson.gradle.coverity.tasks.CoverityTranslateTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.JarBinarySpec
import org.gradle.language.java.JavaSourceSet
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
    public static final String COVERITY_KEY_FILE = '.coverity_key'

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
        coverity.authKeyFile = new File(System.getProperty('user.home'), COVERITY_KEY_FILE)
        coverity.streams.beforeEach {
            filter = { true }
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

        Task mainTask = Utils.addTask(tasks, 'coverity', Task)

        // Create one "run" task per stream
        coverity.streams.each { CoverityStream stream ->
            String taskName = "coverity${stream.name.capitalize()}"
            CoverityRunTask task = Utils.addTask(tasks, taskName, CoverityRunTask) as CoverityRunTask

            mainTask.dependsOn task
            task.stream = stream

            // Create sub-tasks for each binary on this stream
            binaries.findAll(stream.filter).each {
                if (it in NativeBinarySpec) {
                    createNativeCoverityTask(it, task, stream)
                } else if (it in JarBinarySpec) {
                    createJavaCoverityTask(it, task, stream)
                }
            }
        }
    }

    /**
     * Create static analysis tasks for native code
     */
    private static void createNativeCoverityTask(NativeBinarySpec binary, Exec coverityTask, CoverityStream stream) {
        // Add all input files to the main Coverity task
        binary.inputs.each { sourceSet ->
            sourceSet.source.files.each { File sourceFile ->
                coverityTask.args sourceFile.path
            }
        }

        // Add tasks for each compile step in the binary
        binary.tasks.withType(AbstractNativeCompileTask) { AbstractNativeCompileTask compileTask ->
            // Locate the compiler binary
            def compiler = Utils.findGccCompiler(compileTask.toolChain, compileTask)
            if (compiler == null) {
                coverityTask.doFirst {
                    throw new GradleException("Could not infer GCC compiler for: ${compileTask}")
                }
                return
            }

            // Locate the options file generated by the compile task
            def optionsFile = new File(compileTask.temporaryDir, 'options.txt')

            // Create a cov-translate task for each source file
            compileTask.source.files.each { File sourceFile ->
                def taskName = binary.tasks.taskName('coverity', sourceFile.name)
                binary.tasks.create(taskName, CoverityTranslateTask) { CoverityTranslateTask task ->
                    task.stream = stream
                    task.compileTask = compileTask
                    task.sourceFile sourceFile

                    task.dependsOn compileTask
                    coverityTask.dependsOn task
                }
            }
        }
    }

    /**
     * Create static analysis tasks for Java code
     */
    private static void createJavaCoverityTask(JarBinarySpec binary, Exec coverityTask, CoverityStream stream) {
        // Add all input files to the main Coverity task
        binary.inputs.findAll{ it in JavaSourceSet }.each { sourceSet ->
            sourceSet.source.files.each { File sourceFile ->
                coverityTask.args sourceFile.path
            }
        }

        binary.tasks.withType(JavaCompile) { compileTask ->
            // Create a cov-emit-java task for each compile task
            def taskName = binary.tasks.taskName('coverity')
            binary.tasks.create(taskName, CoverityEmitJavaTask) { CoverityEmitJavaTask task ->
                task.stream = stream
                task.compileTask = compileTask

                task.dependsOn compileTask
                coverityTask.dependsOn task
            }
        }
    }
}
