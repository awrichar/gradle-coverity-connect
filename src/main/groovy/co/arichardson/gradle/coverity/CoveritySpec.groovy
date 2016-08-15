package co.arichardson.gradle.coverity

import org.gradle.model.Managed
import org.gradle.model.ModelMap

@Managed
interface CoveritySpec {
    boolean getEnabled()
    void setEnabled(boolean enabled)

    File getPath()
    void setPath(File path)

    String getHost()
    void setHost(String host)

    String getPort()
    void setPort(String port)

    File getAuthKeyFile()
    void setAuthKeyFile(File key)

    String getScm()
    void setScm(String scm)

    boolean getAnalyzeScmModified()
    void setAnalyzeScmModified(boolean analyzeScmModified)

    File getIntermediatesDir()
    void setIntermediatesDir(File dir)

    List<String> getArgs()

    ModelMap<CoverityStream> getStreams()
}
