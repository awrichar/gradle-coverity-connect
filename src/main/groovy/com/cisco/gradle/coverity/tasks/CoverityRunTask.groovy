package com.cisco.gradle.coverity.tasks

import com.cisco.gradle.coverity.Utils
import org.gradle.api.GradleException

import java.util.regex.Pattern

class CoverityRunTask extends AbstractCoverityIntermediatesTask {
    final List<File> sourceFiles

    public CoverityRunTask() {
        super()
        sourceFiles = new ArrayList<File>()

        executable Utils.findCoverityTool('cov-run-desktop', coverity.path)
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
                '--dir', intermediatesDir.path,
                '--stream', stream.stream,
                '--text-output', resultsFile.path
        ]

        if (!(coverity.authKeyFile || (coverity.authUser && coverity.authPassword))) {
            throw new IllegalArgumentException('Either authKeyFile or authUser/authPassword must be specified.')
        }

        if (coverity.authKeyFile) {
            extraArgs << '--auth-key-file' << coverity.authKeyFile.path
        }

        if (coverity.authUser && coverity.authPassword) {
            extraArgs << '--user' << coverity.authUser << '--password' << coverity.authPassword
            if (coverity.authKeyFile) {
                extraArgs << '--create-auth-key'
            }
        }

        if (coverity.scm) {
            extraArgs << '--scm' << coverity.scm

            if (coverity.scm == 'git') {
                extraArgs << '--scm-project-root' << findGitRoot()
            }
        }

        if (coverity.analyzeScmModified) {
            extraArgs << '--analyze-scm-modified'
            extraArgs << '--restrict-modified-file-regex'
            extraArgs << sourceFiles.collect{ Pattern.quote(it.path) }.join('|')
        } else {
            args += sourceFiles
        }

        resultsFile.parentFile.mkdirs()

        args = extraArgs + args
    }

    @Override
    protected void exec() {
        super.exec()
        logger.lifecycle("Static analysis complete. Results saved to ${resultsFile}")
    }

    public File getResultsFile() {
        File mainResults = new File(project.buildDir, RESULTS_DIR)
        return new File(mainResults, "${stream.name}/${RESULTS_FILE}")
    }
}
