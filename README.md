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
The fallback screen also lets a user choose that viewer's default folder. A
successful selection updates the viewer's starting folder to the directory it
came from; dismissing the InfiniFolder picker after navigating saves the last
directory reached. Saving uses the same per-viewer folder and updates it after
choosing a destination, so load and save resume in the most recently used
location across launches.

## Saving documents

Use `InfiniSelect.save(activity, SaveRequest(...), requestCode)` to let the user
choose a destination for a document the viewer has generated. Publish its bytes
with `InfiniSelect.createDocument(...)` and pass the resulting source URI and
size in the request. When InfiniFolder is installed, it opens its floating
browser and writes into the selected storage location. Existing files are kept;
a numbered filename is selected if the requested name already exists. If
InfiniFolder is unavailable or cannot open its picker, the library falls back
to Android's `ACTION_CREATE_DOCUMENT` flow. A successful result includes
`FileSelectionContract.EXTRA_SAVE_SUCCESS` and the actual saved name.

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

## Multiple items and playlists

Multiple selection keeps each item as its own read-only `content://` URI. A
client can request selections of two or more items as JSPF, with the content
URIs in each track's `location`:

```kotlin
InfiniSelect.open(activity, OpenRequest(
    clientId = "infinimedia",
    displayName = "InfiniMedia",
    mimeTypes = listOf("audio/*", "application/xspf+xml", "application/jspf+json"),
    allowMultiple = true,
    multipleSelectionDelivery = MultipleSelectionDelivery.JSPF,
    metadataFormat = SelectionMetadataFormat.XML,
    allowRecursiveAudioDirectories = true,
), REQUEST_OPEN)
```

For one selected item, the result remains that file's content URI. For several
items, `Intent.data` and `EXTRA_RESULT_PLAYLIST_URI` identify the generated
JSPF document; `EXTRA_RESULT_URIS` and `ClipData` retain the individual item
URIs. `EXTRA_RESULT_METADATA_URI` optionally identifies a companion XML
document with each item's URI, display name, MIME type, and known size. Parse
either result shape with `InfiniSelect.readResult(context, data)`. Generated
JSPF and XML documents are served from the consuming app's cache through a
read-only provider and expire after 24 hours.

`allowRecursiveAudioDirectories` makes a long press on a directory in
InfiniFolder recursively enumerate supported audio files through its storage
provider and return them through the same JSPF flow. Symlink directories are
not followed; unreadable folders and scan limits are reported. An optional
`OpenRequest.progressReceiver` receives `RESULT_LOADING_STARTED`,
`RESULT_LOADING_PROGRESS`, and `RESULT_LOADING_FAILED` while the selector
closes and the enumeration continues. Each result includes a human-readable
progress label and folder/track counts. Selected items also include their
parent directory URI and display name in `SelectionItem`, the parallel
`EXTRA_ITEM_DIRECTORY_URIS` / `EXTRA_ITEM_DIRECTORY_NAMES` arrays, and the
generated JSPF InfiniSelect extension fields. This lets clients retain
directory grouping and artwork context without rescanning the hierarchy.
For recursive selections, InfiniFolder also attaches a read-granted URI to the
first readable `cover.*` image in each directory. It passes the URI through
`SelectionItem.directoryCoverUri` and the JSPF `directoryCoverUri` extension,
along with `directoryArtworkMetadataResolved`. The playlist stores the cover as
a URI only, never image bytes. Viewers can show a folder icon immediately and
load the image during their background metadata pass; directories without a
cover keep the folder icon without repeating the lookup for every track.

## Build

Install JDK 17 or 21, Python 3 and Android SDK platform 36/build tools 35.0.0.
Set `ANDROID_HOME` or this checkout's `local.properties`, then run:

```sh
python3 scripts/fetch-dependencies.py
./gradlew :select-android:assembleDebug :select-android:testDebugUnitTest
```

There are no InfiniSuite dependencies. No parent or sibling checkout is read.

See [LICENSE.md](LICENSE.md) for the current license status.

## Android storage contract

`StorageAccess.selectDirectory` requests a standard `ACTION_OPEN_DOCUMENT_TREE`
result, preferring Infinifolder's DocumentsProvider when available. `OpenRequest`
with `writable = true` requests a standard editable document. Read-only requests
retain the floating picker for compatibility. Both routes work with Android SAF
when Infinifolder is absent or unavailable. The provider authority is
`com.infinifolder.documents`; document IDs are opaque and must not be decoded.

Use `StorageAccess.openRead`, `openWrite`, or `openDescriptor`. The caller owns
and closes every handle. `seekable(descriptor)` probes the handle without copying
its contents; unsupported seeking remains explicit. `capabilities` returns
unknown where Android metadata cannot promise a feature. An advertised capability
does not replace the access check performed by the actual open operation.

`StorageAccess.persist(context, result)` persists only flags Android actually
granted. Temporary floating-picker relay URIs expire after 24 hours and cannot
be persisted. SAF document/tree references last until revoked by the user or
provider; network credentials remain private to Infinifolder. Neither route
materializes a complete file merely to provide a URI. A decoder that requires a
local file must explicitly choose and own its cache/staging policy.

Android DocumentsContract/ContentResolver APIs support tree traversal, child
creation and other provider operations. Unsupported operations fail explicitly.
The compatibility JSPF/XML result serializers encode selection references only;
media decoding and metadata extraction are outside this library.

Saved-document results also use Android content references and preserve the actual
read/write/persist flags. Infinifolder's save adapter verifies the framework caller
and its source access before reading. On Android 8–14, publish generated content
from a provider owned by the caller or pass a document with an explicit URI grant;
provider-wide access alone is insufficient for a cross-application permission
check. Newer Android versions additionally expose a full content permission check.
Capability metadata distinguishes an already persisted grant (`persisted`) from
seek support, which is probed on the actual open descriptor.
