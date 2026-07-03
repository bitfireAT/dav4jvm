# dav4jvm – Project Structure

dav4jvm is a WebDAV/CalDAV/CardDAV client library for the JVM, originally written for DAVx⁵. It handles HTTP communication, XML parsing, and the full set of WebDAV/CalDAV/CardDAV operations and properties.

The library uses **Ktor** as its HTTP client: the Ktor-based implementation lives in `ktor/` and is designed to eventually support Kotlin Multiplatform.

> **Maintenance note for agents:** Keep this file up to date. When making structural changes — adding/removing packages, introducing new major dependencies, changing the HTTP backend — update the relevant sections here as part of the same change. Only reflect genuinely significant structural changes; don't update for routine additions like new property classes.

## Module layout

dav4jvm is a **single Gradle module** (no sub-projects). All sources live under `src/`.

## Package map

All packages are under `at.bitfire.dav4jvm`.

```
property/
  webdav/     Standard WebDAV properties (RFC 4918): ResourceType, GetETag, SyncToken, …
  caldav/     CalDAV properties (RFC 4791): CalendarData, CalendarHomeSet, …
  carddav/    CardDAV properties (RFC 6352): AddressData, AddressbookHomeSet, …
  common/     Shared base types (HrefListProperty)
  push/       WebDAV-Push properties (draft): PushRegister, Subscription, …

ktor/         Ktor backend (Multiplatform-ready): DavResource/Collection/Calendar/AddressBook,
              multistatus+response+propstat parsers, response/propstat types, URL+Ktor helpers, callbacks
  exception/  HTTP and DAV exception hierarchy (HttpException, DavException, …)
```

Root level: DAV error parsing, HTTP header helpers, `Property` interface and `Name` type, `PropertyRegistry` (QName → factory), quoted-string utilities, streaming XML helper (`XmlReader`), static XML utilities.

## Key dependencies

| Dependency | Purpose |
|---|---|
| Ktor Client Core | HTTP client for the `ktor/` backend |
| Ktor Client Encoding | Content-encoding support for Ktor |
| XPP3 | XML Pull Parser (provided by Android; explicit dep on plain JVM) |

Test-only: Ktor Client Mock, Ktor Client Auth, JUnit 4, kotlinx-coroutines-test.

## Testing

Tests live in `src/test/kotlin/at/bitfire/dav4jvm/` and mirror the main source layout:

- `ktor/` — Ktor backend tests (use Ktor MockEngine)
- Root-level — shared utilities (HttpUtils, XmlReader, Property parsing)

Run all tests with `./gradlew test`.

## Build and publishing

- **Build system:** Gradle (single `build.gradle.kts` at root)
- **Publishing:** Maven via JitPack (`com.github.bitfireAT:dav4jvm:<tag>`)
- **Docs:** Dokka KDoc, published to GitHub Pages via CI
