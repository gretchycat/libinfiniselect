# libinfiniselect

`libinfiniselect` is an Android library for viewer apps that need an open-file
flow without depending on InfiniFolder's UI or storage implementation.

## Use

```kotlin
InfiniSelect.open(
    activity,
    OpenRequest(
        clientId = "infiniscribe",
        displayName = "InfiniScribe",
        mimeTypes = listOf("*/*"),
        allowMultiple = false,
    ),
    REQUEST_OPEN
)
```

Handle the Activity result as a normal Android document result. The returned
intent carries a readable `content://` URI, a display name, MIME type, and URI
read grant. InfiniFolder's URI is a short-lived read-only stream relay, so
remote, archive, and provider-backed files can be opened without exposing
storage credentials or private VFS schemes to the viewer.

When InfiniFolder is installed, the library launches its floating selector at
Virtual root. Otherwise it shows the requesting app's icon/name, installed
source buttons, Android's Storage Access Framework picker, and a per-client
default starting folder. Optional `FileSourceBackend` implementations can be
registered at runtime or exposed with `ServiceLoader` through
`META-INF/services/io.github.gretchycat.infiniselect.FileSourceBackend`.

The library uses Android/Kotlin APIs only; it contains no native code and can
be built with Android's supported target architectures. It does not depend on
InfiniFolder. The optional InfiniFolder integration is a documented intent
contract in `FileSelectionContract`. Its picker starts at Virtual root as
requested. Multiple selection is supported by the InfiniFolder selector and
the Android SAF fallback.

`InfiniSelect.defaultDirectory(context, clientId)` and
`InfiniSelect.setDefaultDirectory(context, clientId, uri)` store starting
folders in the consuming app's private preferences, partitioned by viewer ID.
The fallback screen also lets a user choose that viewer's default folder.

`FileSourceBackend` is the extension point for additional loading systems. It
provides an availability check, a visible label/order, and an Intent factory.
Backends can be registered with `FileSourceRegistry` or through Java
`ServiceLoader`; unavailable backends are omitted. Viewer apps keep the result
contract (`Activity` result with `data`, MIME type, display name, and URI read
grant) and choose their own rendering path.

Viewers can pass `supportedExtensions` alongside `mimeTypes` and set
`hideUnsupported = true` to hide unsupported files in listing-capable sources.
The InfiniFolder floating picker keeps directories and browsable archive or
filesystem-image containers visible, so users can navigate into them, while
only allowing matching files to be selected. MIME and extension matches are
combined, which lets a viewer accept files whose storage backend reports a
generic MIME type. The Android SAF fallback receives the MIME filters; Android
document providers do not expose a portable extension-filter API.

InfiniFolder streams selected items through a grantable, read-only content URI.
This supports files in VFS backends and archive entries without asking the
receiving app to reuse a remote credential. The stream is sequential; a viewer
that needs positional access must spool the content or use its own seekable
source adapter. Relay references expire after 24 hours.

## Build

From this directory, run `gradle :select-android:assembleRelease`.

See [LICENSE.md](LICENSE.md) for the current license status.
