package com.cisco.gradle.coverity.tasks

import com.cisco.gradle.coverity.CoveritySpec
import org.gradle.api.tasks.Exec

abstract class AbstractCoverityTask extends Exec {
    public static final String CONFIG_DIR = 'coverity-config'
    public static final String CONFIG_FILE = 'config.xml'

    CoveritySpec coverity

    public AbstractCoverityTask() {
        super()
        coverity = project.modelRegistry.find('coverity', CoveritySpec) as CoveritySpec
    }

    protected void addHostConfig() {
        if (coverity.host != null) {
            args '--host', coverity.host
        } else {
            throw new IllegalArgumentException('Coverity Connect host was not specified.')
        }

        if (coverity.port != null) {
            args '--port', coverity.port
        }
    }

    @Override
    protected void exec() {
        preExec();
        super.exec()
    }

    protected void preExec() {
        configDir.mkdirs()
    }

    public File getConfigDir() {
        return new File(project.buildDir, CONFIG_DIR)
    }

    public File getConfigFile() {
        return new File(configDir, CONFIG_FILE)
    }
}
