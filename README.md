
[![Tests](https://github.com/bitfireAT/dav4jvm/actions/workflows/test.yml/badge.svg)](https://github.com/bitfireAT/dav4jvm/actions/workflows/test.yml)
[![License](https://img.shields.io/github/license/bitfireAT/dav4jvm)](https://github.com/bitfireAT/dav4jvm/blob/main/LICENSE)
[![JitPack](https://img.shields.io/jitpack/v/github/bitfireAT/dav4jvm)](https://jitpack.io/#bitfireAT/dav4jvm)


# dav4jvm

dav4jvm is a WebDAV/CalDAV/CardDAV library for JVM (Java/Kotlin). It has
been developed for [DAVx⁵](https://www.davx5.com) initially.

Repository: https://github.com/bitfireAT/dav4jvm/
(~~was: https://gitlab.com/bitfireAT/dav4jvm/~~)

Generated KDoc: https://bitfireat.github.io/dav4jvm/

For questions, suggestions etc. use [Github discussions](https://github.com/bitfireAT/dav4jvm/discussions).
We're happy about contributions, but please let us know in the discussions before. Then make the changes
in your own repository and send a pull request.


## How to use

You can use jitpack.io to include dav4jvm:

    allprojects {
        repositories {
            maven { url 'https://jitpack.io' }
        }
    }
    dependencies {
        implementation 'com.github.bitfireAT:dav4jvm:<version or commit>'  // see tags for latest version, like 1.0, or use the latest commit ID from main branch
        //implementation 'com.github.bitfireAT:dav4jvm:main-SNAPSHOT'      // use it only for testing because it doesn't generate reproducible builds
    }

dav4jvm needs a working XmlPullParser (XPP). On Android, the system already comes with
XPP and you don't need to include one; on other systems, you may need to
import for instance `org.ogce:xpp3` to get dav4jvm to work.


## Custom properties

If you use custom WebDAV properties, register the corresponding factories with `PropertyRegistry.register()`
before calling other dav4jvm methods.


## Contact / License

dav4jvm is licensed under [Mozilla Public License, v. 2.0](LICENSE).


