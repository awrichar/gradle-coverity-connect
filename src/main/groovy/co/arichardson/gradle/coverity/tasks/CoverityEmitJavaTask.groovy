package co.arichardson.gradle.coverity.tasks

import co.arichardson.gradle.coverity.Utils
import org.gradle.api.tasks.compile.JavaCompile

class CoverityEmitJavaTask extends AbstractCoverityIntermediatesTask {
    JavaCompile compileTask

    CoverityEmitJavaTask() {
        super()
        executable Utils.findCoverityTool('cov-emit-java', coverity.path)
    }

    @Override
    protected void preExec() {
        super.preExec()
        args '--dir', intermediatesDir.path
        args '--compiler-outputs', compileTask.destinationDir.path
        args '--classpath', compileTask.classpath.asPath
        args '--source', compileTask.sourceCompatibility

        if (compileTask.options.bootClasspath) {
            args '--bootclasspath', compileTask.options.bootClasspath
        }
        if (compileTask.options.encoding) {
            args '--encoding', compileTask.options.encoding
        }

        sourceFiles.each { args it.path }
    }

    public Set<File> getSourceFiles() {
        compileTask.source.files
    }
}
