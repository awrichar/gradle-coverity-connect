package co.arichardson.gradle.coverity.tasks

import co.arichardson.gradle.coverity.CoveritySpec
import org.gradle.api.tasks.Exec

abstract class AbstractCoverityTask extends Exec {
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
}
