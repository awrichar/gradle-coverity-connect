package co.arichardson.gradle.coverity

import org.gradle.api.Task
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask
import org.gradle.model.ModelMap

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

    public static List<String> getCompileArgs(AbstractNativeCompileTask compileTask) {
        File optionsFile = new File(compileTask.temporaryDir, 'options.txt')
        return unescapeQuotes(optionsFile.readLines())
    }

    private static List<String> unescapeQuotes(List<String> args) {
        return args.collect { it.replaceAll(/\\"/, '"') }
    }
}
