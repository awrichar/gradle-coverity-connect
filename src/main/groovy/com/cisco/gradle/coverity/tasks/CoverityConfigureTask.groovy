package com.cisco.gradle.coverity.tasks

import com.cisco.gradle.coverity.Utils

class CoverityConfigureTask extends AbstractCoverityTask {
    String compiler
    String compilerType

    CoverityConfigureTask() {
        super()
        executable Utils.findCoverityTool('cov-configure', coverity.path)
    }

    @Override
    protected void preExec() {
        super.preExec()

        args '--config', configFile.path
        if (compilerType) {
            args '--comptype', compilerType
        }
        args '--template'
        args '--compiler', compiler, '--'
    }
}
