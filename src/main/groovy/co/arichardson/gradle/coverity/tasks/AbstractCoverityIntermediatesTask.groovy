package co.arichardson.gradle.coverity.tasks

import co.arichardson.gradle.coverity.CoverityStream

abstract class AbstractCoverityIntermediatesTask extends AbstractCoverityTask {
    public static final String INTERMEDIATES_DIR = 'coverity-intermediates'
    public static final String RESULTS_DIR = 'coverity-results'
    public static final String RESULTS_FILE = 'results.txt'

    CoverityStream stream

    AbstractCoverityIntermediatesTask() {
        super()
    }

    @Override
    protected void exec() {
        args = ['--dir', intermediatesDir.path] + args
        intermediatesDir.mkdirs()
        super.exec()
    }

    public File getIntermediatesDir() {
        File mainIntermediates = new File(project.buildDir, INTERMEDIATES_DIR)
        return new File(mainIntermediates, stream.name)
    }
}
