package co.arichardson.gradle.coverity

import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.NativePlatformToolChain
import org.gradle.nativeplatform.toolchain.NativeToolChain

class PlatformToolchainMap extends HashMap<PlatformId, NativePlatformToolChain> {
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

    NativePlatformToolChain get(NativePlatform platform, NativeToolChain toolChain) {
        get(new PlatformId(platform, toolChain))
    }

    NativePlatformToolChain put(NativePlatform platform, NativeToolChain toolChain, NativePlatformToolChain value) {
        put(new PlatformId(platform, toolChain), value)
    }
}
