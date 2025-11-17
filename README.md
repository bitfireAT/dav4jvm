
[![License](https://img.shields.io/github/license/bitfireAT/dav4jvm)](https://github.com/bitfireAT/dav4jvm/blob/main/LICENSE)
[![Tests](https://github.com/bitfireAT/dav4jvm/actions/workflows/test.yml/badge.svg)](https://github.com/bitfireAT/dav4jvm/actions/workflows/test.yml)
[![JitPack](https://img.shields.io/jitpack/v/github/bitfireAT/dav4jvm)](https://jitpack.io/#bitfireAT/dav4jvm)
[![KDoc](https://img.shields.io/badge/documentation-KDoc-informational)](https://bitfireat.github.io/dav4jvm/)


# dav4jvm

dav4jvm is a WebDAV/CalDAV/CardDAV library for JVM (Java/Kotlin). It has
been developed for [DAVx⁵](https://www.davx5.com) initially.

Repository: https://github.com/bitfireAT/dav4jvm/

Generated KDoc: https://bitfireat.github.io/dav4jvm/

For questions, suggestions etc. use [Github discussions](https://github.com/bitfireAT/dav4jvm/discussions).
We're happy about contributions, but please let us know in the discussions before. Then make the changes
in your own repository and send a pull request.

> [!NOTE]
> dav4jvm is currently being rewritten to use ktor instead of OkHttp to allow Kotlin Multiplatform support, and other engines.
> 
> In the mean time, there are two packages available:
> - `at.bitfire.dav4jvm.okhttp` (the current one, using OkHttp, JVM only)
> - `at.bitfire.dav4jvm.ktor` (new package, uses ktor, supports Kotlin Multiplatform)
> 
> There's some common code shared between both packages. This code may contain references to ktor, so do not exclude the dependency, even if you are only using okhttp.


## Installation

You can use jitpack.io to include dav4jvm:

    allprojects {
        repositories {
            maven { url 'https://jitpack.io' }
        }
    }
    dependencies {
        implementation 'com.github.bitfireAT:dav4jvm:<tag or commit>'  // usually the latest commit ID from main branch
        //implementation 'com.github.bitfireAT:dav4jvm:main-SNAPSHOT'  // use it only for testing because it doesn't generate reproducible builds
    }

dav4jvm needs a working XmlPullParser (XPP). On Android, the system already comes with
XPP and you don't need to include one; on other systems, you may need to
import for instance `org.ogce:xpp3` to get dav4jvm to work.


## Usage

First, you'll need to set up an OkHttp instance. Use `BasicDigestAuthHandler` to configure the credentials:

    val authHandler = BasicDigestAuthHandler(
        domain = null, // Optional, to only authenticate against hosts with this domain.
        username = "user1",
        password = "pass1"
    )
    val okHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .authenticator(authHandler)
        .addNetworkInterceptor(authHandler)
        .build()


### Files

Here's an example to create and download a file:

    val location = "https://example.com/webdav/hello.txt".toHttpUrl()
    val davCollection = DavCollection(account.requireClient(), location)

    // Create a text file
    davCollection.put("World".toRequestBody(contentType = "text/plain".toMediaType()) { response ->
        // Upload successful!
    }

    // Download a text file
    davCollection.get(accept = "", headers = null) { response ->
        response.body?.string()
        // Download successful!
    }

To list a folder's contents, you need to pass in which properties to fetch:

    val location = "https://example.com/webdav/".toHttpUrl()
    val davCollection = DavCollection(account.requireClient(), location)

    davCollection.propfind(depth = 1, DisplayName.NAME, GetLastModified.NAME) { response, relation ->
        // This callback will be called for every file in the folder.
        // Use `response.properties` to access the successfully retrieved properties.
    }

## Custom properties

If you use custom WebDAV properties, register the corresponding factories with `PropertyRegistry.register()`
before calling other dav4jvm methods.


# Useful forks

For specific use-cases, we have a list of forks that cover them:
- Kotlin Multiplatform (maintained by [McDjuady](https://github.com/McDjuady)): https://github.com/McDjuady/dav4jvm


## Contact / License

dav4jvm is licensed under [Mozilla Public License, v. 2.0](LICENSE).


