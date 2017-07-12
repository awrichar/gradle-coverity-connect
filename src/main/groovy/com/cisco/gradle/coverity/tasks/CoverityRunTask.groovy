package com.cisco.gradle.coverity.tasks

import com.cisco.gradle.coverity.Utils
import groovy.json.JsonSlurper

import java.util.regex.Pattern

class CoverityRunTask extends AbstractCoverityIntermediatesTask {
    final List<File> sourceFiles

    CoverityRunTask() {
        super()
        sourceFiles = new ArrayList<File>()

        executable Utils.findCoverityTool('cov-run-desktop', coverity.path)
        addHostConfig()
        if (coverity.args) {
            args(*coverity.args)
        }
    }

    private String findScmRoot() {
        if (coverity.scm == 'git') {
            ByteArrayOutputStream output = new ByteArrayOutputStream()
            project.exec {
                commandLine 'git', 'rev-parse', '--show-toplevel'
                standardOutput = output
            }
            return output.toString().trim()
        }

        return '.'
    }

    private List<String> findScmModifiedFiles() {
        List<String> modifiedFiles = null
        String scmRoot = findScmRoot()

        File.createTempFile("cov", ".tmp").with { File tmp ->
            project.exec {
                executable Utils.findCoverityTool('cov-extract-scm', coverity.path)
                args '--get-modified-files'
                args '--scm', coverity.scm, '--project-root', scmRoot, '--output', tmp.path
            }

            Map scmDetails = new JsonSlurper().parse(tmp) as Map
            modifiedFiles = (scmDetails['modified_files'] as List).collect { String filename ->
                [scmRoot, filename].join(File.separator)
            }

            tmp.deleteOnExit()
        }

        return modifiedFiles
    }

    private Collection<String> normalizePaths(Collection<Object> paths) {
        return paths.collect { project.file(it).canonicalPath }
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
            extraArgs << '--scm-project-root' << findScmRoot()
        }

        if (coverity.analyzeScmModified) {
            extraArgs << '--analyze-scm-modified'
            extraArgs << '--restrict-modified-file-regex'

            Collection<String> modifiedSources = normalizePaths(sourceFiles)
                    .intersect(normalizePaths(findScmModifiedFiles()))

            if (modifiedSources) {
                extraArgs << modifiedSources.collect { Pattern.quote(it) }.join('|')
            } else {
                extraArgs << '""'
            }
        } else {
            args += sourceFiles
        }

        if (!coverity.ignoreFailures) {
            extraArgs << '--exit1-if-defects' << 'true'
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
