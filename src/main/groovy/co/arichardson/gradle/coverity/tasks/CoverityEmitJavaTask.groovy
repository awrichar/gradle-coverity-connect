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
        args '--bootclasspath', compileTask.options.bootClasspath
        args '--encoding', compileTask.options.encoding
        args '--source', compileTask.sourceCompatibility
        compileTask.source.files.each { args it.path }
    }
}
