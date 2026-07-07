
For more detailed changes, see https://github.com/bitfireAT/dav4jvm/compare/. Example: https://github.com/bitfireAT/dav4jvm/compare/2.1.2...2.1.3

### 3.0.0
> [!NOTE]
> This release aims to give the library more stability, and start to provide releases with a more predictable versioning scheme.
> The library is now in a stable state, and we will follow semantic versioning from now on.

This version migrates away from OkHttp to Ktor as the underlying HTTP client.
This allows for better multiplatform support, and more flexibility in terms of configuration.

You are still free to use OkHttp as the underlying engine, but now with the freedom of using other engines as well.
The old OkHttp-based API is no longer available, and you will need to migrate your code to the new Ktor-based API.
  
Migration should be straightforward, as the API is very similar. The main difference is regarding the `HttpClient` setup. Other functions can just be moved from the `okhttp` package to the `ktor` package, and the rest of the code should work as before.
Some utility functions may be missing though, if already provided by Ktor, or not useful anymore.
