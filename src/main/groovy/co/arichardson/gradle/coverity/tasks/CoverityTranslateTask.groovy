package co.arichardson.gradle.coverity.tasks

import co.arichardson.gradle.coverity.Utils
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask
import org.gradle.process.internal.ExecAction

class CoverityTranslateTask extends AbstractCoverityIntermediatesTask {
    String compiler
    AbstractNativeCompileTask compileTask

    CoverityTranslateTask() {
        super()
        executable Utils.findCoverityTool('cov-translate', coverity.path)
    }

    @Override
    protected void preExec() {
        super.preExec()

        args '--config', configFile.path
        args '--dir', intermediatesDir.path
        args compiler
        args Utils.getCompileArgs(compileTask)
    }

    @Override
    protected void exec() {
        preExec()

        // Perform one exec per source file
        sourceFiles.each { File sourceFile ->
            ExecAction subAction = getExecActionFactory().newExecAction()
            subAction.commandLine = this.commandLine
            subAction.environment = this.environment
            subAction.workingDir = this.workingDir
            subAction.args sourceFile.path
            subAction.execute()
        }
    }

    public Set<File> getSourceFiles() {
        compileTask.source.files
    }
}
