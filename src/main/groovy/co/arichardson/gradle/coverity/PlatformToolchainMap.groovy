package co.arichardson.gradle.coverity

import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.NativeToolChain

class PlatformToolchainMap {
    Map<PlatformId, String> cCompilers = [:]
    Map<PlatformId, String> cppCompilers = [:]

    public static class PlatformId {
        NativePlatform platform
        NativeToolChain toolChain

        PlatformId(NativePlatform platform, NativeToolChain toolChain) {
            this.platform = platform
            this.toolChain = toolChain
        }

        boolean equals(o) {
            if (this.is(o)) return true
            if (getClass() != o.class) return false

            PlatformId that = (PlatformId) o

            if (platform.name != that.platform.name) return false
            if (toolChain.name != that.toolChain.name) return false

            return true
        }

        int hashCode() {
            int result
            result = platform.name.hashCode()
            result = 31 * result + toolChain.name.hashCode()
            return result
        }
    }

    String getCCompiler(NativePlatform platform, NativeToolChain toolChain) {
        cCompilers.get(new PlatformId(platform, toolChain))
    }

    String setCCompiler(NativePlatform platform, NativeToolChain toolChain, String executable) {
        cCompilers.put(new PlatformId(platform, toolChain), executable)
    }

    String getCppCompiler(NativePlatform platform, NativeToolChain toolChain) {
        cppCompilers.get(new PlatformId(platform, toolChain))
    }

    String setCppCompiler(NativePlatform platform, NativeToolChain toolChain, String executable) {
        cppCompilers.put(new PlatformId(platform, toolChain), executable)
    }
}
