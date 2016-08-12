package co.arichardson.gradle.coverity

import org.gradle.api.Named
import org.gradle.model.Managed
import org.gradle.model.Unmanaged

@Managed
interface CoverityStream extends Named {
    boolean getEnabled()
    void setEnabled(boolean enabled)

    String getStream()
    void setStream(String stream)

    @Unmanaged
    Closure<Boolean> getFilter()
    void setFilter(Closure<Boolean> filter)
}
