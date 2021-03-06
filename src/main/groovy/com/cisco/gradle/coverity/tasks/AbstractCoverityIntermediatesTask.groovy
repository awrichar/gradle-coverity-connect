package com.cisco.gradle.coverity.tasks

import com.cisco.gradle.coverity.CoverityStream

abstract class AbstractCoverityIntermediatesTask extends AbstractCoverityTask {
    public static final String INTERMEDIATES_DIR = 'coverity-intermediates'
    public static final String RESULTS_DIR = 'coverity-results'
    public static final String RESULTS_FILE = 'results.txt'

    CoverityStream stream

    AbstractCoverityIntermediatesTask() {
        super()
    }

    @Override
    protected void preExec() {
        super.preExec()
        intermediatesDir.mkdirs()
    }

    public File getIntermediatesDir() {
        File mainIntermediates
        if (coverity.intermediatesDir) {
            mainIntermediates = coverity.intermediatesDir
        } else {
            mainIntermediates = new File(project.buildDir, INTERMEDIATES_DIR)
        }
        return new File(mainIntermediates, stream.name)
    }
}
