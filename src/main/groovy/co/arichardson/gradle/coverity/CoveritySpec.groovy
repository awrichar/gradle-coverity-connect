package co.arichardson.gradle.coverity

import org.gradle.model.Managed
import org.gradle.model.ModelMap

@Managed
interface CoveritySpec {
    File getPath()
    void setPath(File path)

    String getHost()
    void setHost(String host)

    String getPort()
    void setPort(String port)

    File getAuthKeyFile()
    void setAuthKeyFile(File key)

    List<String> getArgs()

    ModelMap<CoverityStream> getStreams()
}
