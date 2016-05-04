package co.arichardson.gradle.coverity.tasks

import co.arichardson.gradle.coverity.Utils
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask

class CoverityConfigureTask extends AbstractCoverityIntermediatesTask {
    String compiler
    AbstractNativeCompileTask compileTask

    CoverityConfigureTask() {
        super()
        executable Utils.findCoverityTool('cov-configure', coverity.path)
    }

    @Override
    protected void preExec() {
        super.preExec()

        args '--config', getConfigFile(compileTask.name).path
        args '--compiler', compiler, '--'
        args Utils.getCompileArgs(compileTask)
    }
}
