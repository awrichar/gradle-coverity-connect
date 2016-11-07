package com.cisco.gradle.coverity.tasks

import com.cisco.gradle.coverity.Utils
import org.gradle.api.GradleException

class CoverityAuthTask extends AbstractCoverityTask {
    CoverityAuthTask() {
        super()
        executable Utils.findCoverityTool('cov-manage-im', coverity.path)
        args '--mode', 'auth-key', '--create'
        args '--output-file', coverity.authKeyFile
        addHostConfig()
    }

    @Override
    protected void preExec() {
        super.preExec()

        def console = System.console()
        if (!console) {
            throw new GradleException('Could not open an interactive console - if you are running in daemon mode, please try using --no-daemon instead.')
        }

        def prefix = 'Please enter your credentials for Coverity Connect.'
        args '--user', console.readLine("\n${prefix}\nUsername: ")
        args '--password', new String(console.readPassword("Password: "))
    }
}
