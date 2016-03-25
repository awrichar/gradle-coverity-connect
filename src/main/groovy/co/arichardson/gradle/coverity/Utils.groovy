package co.arichardson.gradle.coverity

import org.gradle.api.Task
import org.gradle.language.c.tasks.CCompile
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask
import org.gradle.model.ModelMap
import org.gradle.nativeplatform.toolchain.Gcc
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

    public static File findGccTool(NativeToolChain toolChain, String toolName) {
        if (!(toolChain in Gcc)) return null

        def tools = []
        toolChain.path*.eachFile { tools << it }
        return tools.find {
            it.name == toolName || it.name.endsWith("-${toolName}")
        }
    }

    /**
     * TODO: Find a way to identify the compilers actually configured by Gradle
     */
    public static File findGccCompiler(NativeToolChain toolChain, AbstractNativeCompileTask compileTask) {
        if (compileTask in CCompile) {
            return findGccTool(toolChain, 'gcc')
        } else if (compileTask in CppCompile) {
            return findGccTool(toolChain, 'g++')
        }

        return null
    }
}
