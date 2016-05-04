package co.arichardson.gradle.coverity.tasks

import co.arichardson.gradle.coverity.CoverityStream

abstract class AbstractCoverityIntermediatesTask extends AbstractCoverityTask {
    public static final String CONFIG_DIR = 'coverity-config'
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
        configDir.mkdirs()
        intermediatesDir.mkdirs()
    }

    public File getConfigDir() {
        File mainConfig = new File(project.buildDir, CONFIG_DIR)
        return new File(mainConfig, stream.name)
    }

    public File getConfigFile(String id) {
        return new File(configDir, "${id}_config.xml")
    }

    public File getIntermediatesDir() {
        File mainIntermediates = new File(project.buildDir, INTERMEDIATES_DIR)
        return new File(mainIntermediates, stream.name)
    }
}
