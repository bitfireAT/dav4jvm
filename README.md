
# dav4jvm

dav4jvm is a WebDAV/CalDAV/CardDAV library for JVM (Java/Kotlin). It has
been developed for [DAVx⁵](https://www.davx5.com) initially.

Repository: https://gitlab.com/bitfireAT/dav4jvm/

Discussion: https://forums.bitfire.at/category/18/libraries

Generated KDoc: https://bitfireAT.gitlab.io/dav4jvm/dokka/dav4jvm/


## How to use

You can use [jitpack.io to include dav4jvm](https://jitpack.io/#com.gitlab.bitfireAT/dav4jvm):

    allprojects {
        repositories {
            maven { url 'https://jitpack.io' }
        }
    }
    dependencies {
        implementation 'com.gitlab.bitfireAT:dav4jvm:<version>'  // see tags for latest version, like 1.0
        //implementation 'com.gitlab.bitfireAT:dav4jvm:master-SNAPSHOT'  // alternative
    }

dav4jvm needs a working XmlPullParser (XPP). On Android, the system already comes with
XPP and you don't need to include one; on other systems, you may need to
import for instance `org.ogce:xpp3` to get dav4jvm to work.

## Custom properties

If you use custom WebDAV properties, register the corresponding factories with `PropertyRegistery.register()`
before calling other dav4jvm methods.


## Contact / License

dav4jvm is licensed under [Mozilla Public License, v. 2.0](LICENSE).

For questions, suggestions etc. use this forum:
https://forums.bitfire.at/category/18/libraries

If you want to contribute, please work in your own repository and then
send a merge request.


## Contributors

  * Ricki Hirner (initial contributor)
  * David González Verdugo (dgonzalez@owncloud.com)
  * Matt Jacobsen (https://gitlab.com/mattjacobsen)

