package co.arichardson.gradle.coverity.tasks

import co.arichardson.gradle.coverity.Utils
import org.gradle.api.GradleException

class CoverityRunTask extends AbstractCoverityIntermediatesTask {
    private static final int COVERITY_AUTH_KEY_NOT_FOUND = 4

    public CoverityRunTask() {
        super()

        executable Utils.findCoverityTool('cov-run-desktop', coverity.path)
        args '--auth-key-file', coverity.authKeyFile
        addHostConfig()
        args(*coverity.args)
    }

    @Override
    protected void exec() {
        args = ['--stream', stream.stream, '--text-output', resultsFile.path] + args

        resultsFile.parentFile.mkdirs()

        ignoreExitValue = true
        super.exec()

        if (execResult.exitValue == COVERITY_AUTH_KEY_NOT_FOUND) {
            throw new GradleException("Authentication key was not found - please run 'gradle coverity-auth --no-daemon' to generate.")
        }

        execResult.assertNormalExitValue()

        logger.lifecycle("Static analysis complete. Results saved to ${resultsFile}")
    }

    public File getResultsFile() {
        File mainResults = new File(project.buildDir, RESULTS_DIR)
        return new File(mainResults, "${stream.name}/${RESULTS_FILE}")
    }
}
