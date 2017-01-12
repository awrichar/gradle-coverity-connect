# gradle-coverity-connect
A Gradle plugin for running Coverity static analysis on Java and native code.

Example usage:

    apply plugin: 'com.cisco.coverity'
    import com.cisco.gradle.coverity.CoverityStream

    model {
        coverity {
            host = 'my-coverity-server'
            streams {
                stream1(CoverityStream) {
                    stream = 'stream1'
                }
            }
        }
    }

This will run Coverity static analysis on all binaries in your project, using the specified stream configured on the Coverity Connect server.

You can restrict the static analysis to specific binaries using the `filter` property, which will be evaluated once for every BinarySpec in the project:

    stream1(CoverityStream) {
        stream = 'stream1'
        filter = { binary ->
            binary in NativeBinarySpec &&
            binary.targetPlatform.name == 'x86'
        }
    }

The following properties can be set on the `coverity` block:

* **host:** hostname for the Coverity Connect server (required)
* **port:** port for the Coverity Connect server (default: 8080)
* **path:** path to Coverity tools folder (default: looks in COVERITY_HOME or in PATH)
* **authKeyFile:** path to the authentication key for Coverity Connect
* **authUser:** username to authenticate with Coverity Connect
* **authPassword:** password to authenticate with Coverity Connect
* **enabled:** whether to run Coverity for this project (default: true)
* **scm:** type of SCM in use (git, svn, etc)
* **analyzeScmModified:** if true, analyze only files modified according to the SCM
* **intermediatesDir:** base directory to store Coverity intermediates
* **ignoreFailures:** don't fail the build for static analysis issues (default: false)
