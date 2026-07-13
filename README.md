
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
We're happy about contributions, but please let us know in the discussions before.
Then make the changes in your own repository and send a pull request.

See [CHANGELOG.md](CHANGELOG.md) for a list of changes and releases.

dav4jvm uses [Ktor](https://ktor.io/) as its HTTP client, which allows Kotlin
Multiplatform support and the use of different HTTP engines. The dav4jvm classes
are located in the `at.bitfire.dav4jvm.ktor` package.


## Roadmap

**4.0:** API change: [Replace callback pattern by suspending functions /
`Flow`s](https://github.com/bitfireAT/dav4jvm/issues/200)

Besides from that, no big changes are currently planned. There's the idea of making XML processing
multiplatform-capable too at some time, but no specific plans. Maybe it will be renamed then
to dav4kmp or something like that.


## Installation

You can use jitpack.io to include dav4jvm:
```kotlin
allprojects {
    repositories {
        maven("https://jitpack.io")
    }
}
dependencies {
    implementation("com.github.bitfireAT:dav4jvm:<version>")       // recommended: use the latest version available
    //implementation("com.github.bitfireAT:dav4jvm:<commit>")      // try the latest changes from the commit ID from main branch
    //implementation("com.github.bitfireAT:dav4jvm:main-SNAPSHOT") // use it only for testing because it doesn't generate reproducible builds
}
```

dav4jvm needs a working XmlPullParser (XPP). On Android, the system already comes with
XPP and you don't need to include one; on other systems, you may need to
import for instance `org.ogce:xpp3` to get dav4jvm to work.


## Usage

First, you'll need to set up a Ktor `HttpClient`. dav4jvm handles redirects itself,
so make sure that the client does **not** follow redirects. Use Ktor's authentication
plugin to configure the credentials:
```kotlin
val httpClient = HttpClient(CIO) {
    followRedirects = false
    install(Auth) {
        basic {
            credentials { BasicAuthCredentials(username = "user1", password = "pass1") }
            sendWithoutRequest { true }
        }
    }
}
```

Most dav4jvm request methods are `suspend` functions, so call them from a coroutine. Methods that
process a WebDAV Multi-Status response (like `propfind`) return a `Flow` instead; the request is
only sent (and errors only thrown) once that `Flow` is collected.


### Files

Here's an example to create and download a file:
```kotlin
val location = Url("https://example.com/webdav/hello.txt")
val davCollection = DavCollection(httpClient, location)

// Create a text file
davCollection.put(TextContent("World", ContentType.Text.Plain)) { response ->
    // Upload successful!
}

// Download a text file
davCollection.get(additionalHeaders = null) { response ->
    val body = response.bodyAsText()
    // Download successful!
}
```

To list a folder's contents, you need to pass in which properties to fetch:
```kotlin
val location = Url("https://example.com/webdav/")
val davCollection = DavCollection(httpClient, location)

davCollection.propfind(depth = 1, DisplayName.NAME, GetLastModified.NAME).collect { item ->
    if (item is MultiStatusItem.Response) {
        // This is called for every file in the folder.
        // Use `item.response.properties` to access the successfully retrieved properties.
    }
}
```


## Custom properties

If you use custom WebDAV properties, register the corresponding factories with `PropertyRegistry.register()`
before calling other dav4jvm methods.


## Who uses dav4jvm?

**If you're using dav4jvm for a public project, please create a PR to add it here** (alphabetical order).
This helps us estimating whether the project is actually used by someone and how proper we want to do
releases etc.

- (add your project/reference here)

There are also noticable forks:

- for Kotlin Multiplatform (maintained by [McDjuady](https://github.com/McDjuady)): https://github.com/McDjuady/dav4jvm


## Contact / License

dav4jvm is licensed under [Mozilla Public License, v. 2.0](LICENSE).


