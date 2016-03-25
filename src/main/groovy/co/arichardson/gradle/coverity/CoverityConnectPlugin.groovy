package co.arichardson.gradle.coverity

import co.arichardson.gradle.coverity.tasks.CoverityAuthTask
import co.arichardson.gradle.coverity.tasks.CoverityEmitJavaTask
import co.arichardson.gradle.coverity.tasks.CoverityRunTask
import co.arichardson.gradle.coverity.tasks.CoverityTranslateTask
import org.gradle.api.Task
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.JarBinarySpec
import org.gradle.language.base.LanguageSourceSet
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

        coverity.enabled = true
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

        if (!coverity.enabled) return

        Task mainTask = Utils.addTask(tasks, 'coverity', Task)
        Task mainCleanTask = Utils.addTask(tasks, 'cleanCoverity', Task)

        // Create one "run" task per stream
        coverity.streams.each { CoverityStream stream ->
            // Ensure there are some sources matching this stream
            List<BinarySpec> matchedBinaries = binaries.findAll(stream.filter)
            boolean hasSources = matchedBinaries.find { BinarySpec binary ->
                binary.inputs.find { LanguageSourceSet sourceSet ->
                    sourceSet.source.files.size() > 0
                }
            }
            if (!hasSources) return

            String taskName = "coverity${stream.name.capitalize()}"
            String cleanTaskName = "clean${taskName}"
            CoverityRunTask runTask = Utils.addTask(tasks, taskName, CoverityRunTask) as CoverityRunTask
            Delete cleanTask = Utils.addTask(tasks, cleanTaskName, Delete) as Delete

            mainTask.dependsOn runTask
            mainCleanTask.dependsOn cleanTask

            runTask.stream = stream
            cleanTask.delete runTask.intermediatesDir
            cleanTask.delete runTask.resultsFile

            // Create sub-tasks for each binary on this stream
            matchedBinaries.each { BinarySpec binary ->
                if (binary in NativeBinarySpec) {
                    createNativeCoverityTask(binary, runTask, stream)
                } else if (binary in JarBinarySpec) {
                    createJavaCoverityTask(binary, runTask, stream)
                }
            }
        }
    }

    /**
     * Create static analysis tasks for native code
     */
    private static void createNativeCoverityTask(NativeBinarySpec binary, Exec coverityTask, CoverityStream stream) {
        // Add all input files to the main Coverity task
        binary.inputs.each { LanguageSourceSet sourceSet ->
            sourceSet.source.files.each { File sourceFile ->
                coverityTask.args sourceFile.path
            }
        }

        // Create a cov-translate task for each source file in each compile task
        binary.tasks.withType(AbstractNativeCompileTask) { AbstractNativeCompileTask compileTask ->
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
        binary.inputs.findAll{ it in JavaSourceSet }.each { JavaSourceSet sourceSet ->
            sourceSet.source.files.each { File sourceFile ->
                coverityTask.args sourceFile.path
            }
        }

        // Create a cov-emit-java task for each compile task
        binary.tasks.withType(JavaCompile) { compileTask ->
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
