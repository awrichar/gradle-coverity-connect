package co.arichardson.gradle.coverity

import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.c.CSourceSet
import org.gradle.language.cpp.CppSourceSet
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask
import org.gradle.model.Defaults
import org.gradle.model.Managed
import org.gradle.model.Model
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.toolchain.Gcc
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.platform.base.BinarySpec

@Managed
interface CoveritySpec {
    File getPath()
    void setPath(File path)

    String getHost()
    void setHost(String host)

    String getPort()
    void setPort(String port)

    File getAuthKeyFile()
    void setAuthKeyFile(File key)

    File getIntermediatesDir()
    void setIntermediatesDir(File dir)

    File getResultsFile()
    void setResultsFile(File results)

    List<String> getArgs()

    ModelMap<CoverityStream> getStreams()
}

@Managed
interface CoverityStream extends Named {
    String getPlatform()
    void setPlatform(String platform)

    String getStream()
    void setStream(String stream)
}

class CoverityPlugin extends RuleSource {
    private static final int COVERITY_AUTH_KEY_NOT_FOUND = 4

    @Model
    void coverity(CoveritySpec spec) {}

    @Defaults
    void setCoverityDefaults(CoveritySpec coverity,
            @Path('buildDir') File buildDir) {

        if ('COVERITY_HOME' in System.env) {
            coverity.path = new File(System.env.COVERITY_HOME)
        }

        coverity.port = '8080'
        coverity.authKeyFile = new File(System.getProperty('user.home'), '.coverity_key')
        coverity.intermediatesDir = new File(buildDir, 'coverity-intermediates')
        coverity.resultsFile = new File(buildDir, 'coverity-results/results.txt')
    }

    @Mutate
    void createCoverityAuthTask(ModelMap<Task> tasks, CoveritySpec coverity) {
        tasks.create('coverity-auth', Exec) {
            executable findCoverityTool('cov-manage-im', coverity.path)
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

    @Mutate
    void createCoverityTasks(ModelMap<Task> tasks, CoveritySpec coverity,
            @Path('binaries') ModelMap<BinarySpec> binaries) {

        tasks.create('coverity', Exec) { task ->
            doFirst {
                coverity.resultsFile.parentFile.mkdirs()
            }

            executable findCoverityTool('cov-run-desktop', coverity.path)
            args '--dir', coverity.intermediatesDir
            args '--stream', 'sl_master_BE'
            args '--auth-key-file', coverity.authKeyFile
            args '--text-output', coverity.resultsFile
            addHostConfig(task, coverity)
            args(*coverity.args)

            binaries.each {
                if (it in NativeBinarySpec) {
                    createNativeCoverityTask(it, coverity, task)
                }
            }

            ignoreExitValue = true
            doLast {
                if (execResult.exitValue == COVERITY_AUTH_KEY_NOT_FOUND) {
                    throw new GradleException("Authentication key was not found - please run 'gradle coverity-auth' to generate.")
                } else {
                    execResult.assertNormalExitValue()
                }
            }
        }
    }

    private void createNativeCoverityTask(NativeBinarySpec binary,
            CoveritySpec coverity, Exec coverityTask) {

        def stream = coverity.streams.find {
            it.platform == binary.targetPlatform.name
        }?.stream
        if (!stream) return

        def covTranslate = findCoverityTool('cov-translate', coverity.path)

        binary.inputs.each { sourceSet ->
            binary.tasks.withType(AbstractNativeCompileTask) { compileTask ->
                // Locate the compiler binary
                def compiler = findGccCompiler(binary.toolChain, sourceSet)
                if (compiler == null) {
                    throw new GradleException("Could not find GCC compiler for: ${sourceSet.class}")
                }

                // Locate the options file generated by the compile task
                def optionsFile = new File(temporaryDir, 'options.txt')
                if (!optionsFile.exists()) {
                    return
                }

                sourceSet.source.files.each { sourceFile ->
                    // Add this file to the list for the cov-run-desktop task
                    coverityTask.args sourceFile

                    // Create a cov-translate task for each source file
                    def taskName = binary.tasks.taskName('coverity', sourceFile.name)
                    binary.tasks.create(taskName, Exec) { task ->
                        task.dependsOn compileTask
                        coverityTask.dependsOn task

                        task.executable covTranslate
                        task.args '--dir', coverity.intermediatesDir
                        task.args compiler, "@${optionsFile}"
                        task.args sourceFile.path
                    }
                }
            }
        }
    }

    private void addHostConfig(Exec task, CoveritySpec coverity) {
        if (coverity.host != null) {
            task.args '--host', coverity.host
        } else {
            throw new IllegalArgumentException('Coverity Connect host was not specified.')
        }

        if (coverity.port != null) {
            task.args '--port', coverity.port
        }
    }

    private String findCoverityTool(String toolName, File coverityPath) {
        if (coverityPath != null) {
            return new File(coverityPath, "bin/${toolName}").path
        }

        return toolName
    }

    private File findGccTool(NativeToolChain toolChain, String toolName) {
        if (!(toolChain in Gcc)) return null

        def tools = []
        toolChain.path*.eachFile { tools << it }
        return tools.find {
            it.name == toolName || it.name.endsWith("-${toolName}")
        }
    }

    // TODO: Find a way to identify the compilers actually configured by Gradle
    private File findGccCompiler(NativeToolChain toolChain, LanguageSourceSet sourceSet) {
        if (sourceSet in CSourceSet) {
            return findGccTool(toolChain, 'gcc')
        } else if (sourceSet in CppSourceSet) {
            return findGccTool(toolChain, 'g++')
        }

        return null
    }
}