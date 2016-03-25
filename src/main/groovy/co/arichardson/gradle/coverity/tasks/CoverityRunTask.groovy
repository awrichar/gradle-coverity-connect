package co.arichardson.gradle.coverity.tasks

import co.arichardson.gradle.coverity.Utils
import org.gradle.api.GradleException

class CoverityRunTask extends AbstractCoverityIntermediatesTask {
    private static final int COVERITY_AUTH_KEY_NOT_FOUND = 4

    final List<File> sourceFiles

    public CoverityRunTask() {
        super()
        sourceFiles = new ArrayList<File>()

        executable Utils.findCoverityTool('cov-run-desktop', coverity.path)
        args '--auth-key-file', coverity.authKeyFile
        addHostConfig()
        args(*coverity.args)
    }

    private String findGitRoot() {
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        project.exec {
            commandLine 'git', 'rev-parse', '--show-toplevel'
            standardOutput = output
        }
        return output.toString().trim()
    }

    @Override
    protected void preExec() {
        super.preExec()

        List<String> extraArgs = [
                '--stream', stream.stream, '--text-output', resultsFile.path
        ]

        if (coverity.scm) {
            extraArgs << '--scm' << coverity.scm

            if (coverity.scm == 'git') {
                extraArgs << '--scm-project-root' << findGitRoot()
            }
        }

        if (coverity.analyzeScmModified) {
            extraArgs << '--analyze-scm-modified'
            extraArgs << '--restrict-modified-file-regex'
            extraArgs << sourceFiles.join('|')
        } else {
            args += sourceFiles
        }

        resultsFile.parentFile.mkdirs()

        args = extraArgs + args
    }

    @Override
    protected void exec() {
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
