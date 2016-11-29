package com.cisco.gradle.coverity

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

    String getAuthUser()
    void setAuthUser(String user)

    String getAuthPassword()
    void setAuthPassword(String password)

    String getScm()
    void setScm(String scm)

    boolean getAnalyzeScmModified()
    void setAnalyzeScmModified(boolean analyzeScmModified)

    File getIntermediatesDir()
    void setIntermediatesDir(File dir)

    boolean getIgnoreFailures()
    void setIgnoreFailures(boolean ignore)

    List<String> getArgs()

    ModelMap<CoverityStream> getStreams()
}
