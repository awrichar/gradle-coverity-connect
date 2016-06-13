package co.arichardson.gradle.coverity

import org.gradle.api.Task
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask
import org.gradle.model.ModelMap
import org.gradle.nativeplatform.toolchain.Gcc
import org.gradle.nativeplatform.toolchain.GccCommandLineToolConfiguration
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain
import org.gradle.nativeplatform.toolchain.NativePlatformToolChain
import org.gradle.nativeplatform.toolchain.NativeToolChain

class Utils {
    public static Task addTask(ModelMap<Task> tasks, String name, Class<? extends Task> type) {
        tasks.create(name, type)
        return tasks.get(name)
    }

    public static String findCoverityTool(String toolName, File coverityPath) {
        if (coverityPath != null) {
            return new File(coverityPath, "bin/${toolName}").path
        }

        return toolName
    }

    public static String findGccTool(NativeToolChain toolChain, String toolName) {
        if (toolName == null) return null
        if (!(toolChain in Gcc)) return null
        if (toolChain.path.isEmpty()) return toolName

        def tools = []
        toolChain.path*.eachFile { tools << it }
        return tools.find {
            it.name == toolName || it.name.endsWith("-${toolName}")
        }
    }

    public static GccCommandLineToolConfiguration getPlatformCompiler(NativePlatformToolChain platformToolChain,
                                                                      AbstractNativeCompileTask compileTask) {
        if (!(platformToolChain in GccPlatformToolChain)) return null

        return (compileTask instanceof CppCompile) ?
                platformToolChain.cppCompiler : platformToolChain.cCompiler
    }

    public static List<String> getCompileArgs(AbstractNativeCompileTask compileTask) {
        File optionsFile = new File(compileTask.temporaryDir, 'options.txt')
        return unescapeQuotes(optionsFile.readLines())
    }

    private static List<String> unescapeQuotes(List<String> args) {
        return args.collect { it.replaceAll(/\\"/, '"') }
    }
}
