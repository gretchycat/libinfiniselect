package io.github.gretchycat.infiniselect

/** Cross-app contract implemented by InfiniFolder's optional floating selector. */
object FileSelectionContract {
    const val ACTION_SELECT = "io.github.gretchycat.infiniselect.action.SELECT_FILE"
    const val ACTION_SAVE = "io.github.gretchycat.infiniselect.action.SAVE_FILE"

    const val EXTRA_CLIENT_ID = "io.github.gretchycat.infiniselect.CLIENT_ID"
    const val EXTRA_DISPLAY_NAME = "io.github.gretchycat.infiniselect.DISPLAY_NAME"
    const val EXTRA_MIME_TYPES = "io.github.gretchycat.infiniselect.MIME_TYPES"
    const val EXTRA_SUPPORTED_EXTENSIONS = "io.github.gretchycat.infiniselect.SUPPORTED_EXTENSIONS"
    const val EXTRA_HIDE_UNSUPPORTED = "io.github.gretchycat.infiniselect.HIDE_UNSUPPORTED"
    const val EXTRA_ALLOW_MULTIPLE = "io.github.gretchycat.infiniselect.ALLOW_MULTIPLE"
    const val EXTRA_INITIAL_DIRECTORY = "io.github.gretchycat.infiniselect.INITIAL_DIRECTORY"
    const val EXTRA_INITIAL_SCROLL_Y = "io.github.gretchycat.infiniselect.INITIAL_SCROLL_Y"
    /** Explicit recipient for URI grants returned through the floating selector's ResultReceiver. */
    const val EXTRA_REQUESTING_PACKAGE = "io.github.gretchycat.infiniselect.REQUESTING_PACKAGE"

    const val EXTRA_RESULT_URI = "io.github.gretchycat.infiniselect.RESULT_URI"
    const val EXTRA_RESULT_NAME = "io.github.gretchycat.infiniselect.RESULT_NAME"
    const val EXTRA_RESULT_MIME = "io.github.gretchycat.infiniselect.RESULT_MIME"
    const val EXTRA_RESULT_SIZE = "io.github.gretchycat.infiniselect.RESULT_SIZE"
    const val EXTRA_RESULT_DIRECTORY = "io.github.gretchycat.infiniselect.RESULT_DIRECTORY"
    const val EXTRA_RESULT_SCROLL_Y = "io.github.gretchycat.infiniselect.RESULT_SCROLL_Y"
    const val EXTRA_RESULT_URIS = "io.github.gretchycat.infiniselect.RESULT_URIS"
    const val EXTRA_RESULT_NAMES = "io.github.gretchycat.infiniselect.RESULT_NAMES"
    const val EXTRA_RESULT_MIMES = "io.github.gretchycat.infiniselect.RESULT_MIMES"
    const val EXTRA_RESULT_SIZES = "io.github.gretchycat.infiniselect.RESULT_SIZES"
    const val EXTRA_ITEM_DIRECTORY_URIS = "io.github.gretchycat.infiniselect.ITEM_DIRECTORY_URIS"
    const val EXTRA_ITEM_DIRECTORY_NAMES = "io.github.gretchycat.infiniselect.ITEM_DIRECTORY_NAMES"
    const val EXTRA_ITEM_DIRECTORY_COVER_URIS = "io.github.gretchycat.infiniselect.ITEM_DIRECTORY_COVER_URIS"
    const val EXTRA_FALLBACK_TO_SOURCES = "io.github.gretchycat.infiniselect.FALLBACK_TO_SOURCES"
    const val EXTRA_FAILURE_MESSAGE = "io.github.gretchycat.infiniselect.FAILURE_MESSAGE"

    const val EXTRA_SELECTION_MODE = "io.github.gretchycat.infiniselect.SELECTION_MODE"
    const val EXTRA_SELECTION_RECEIVER = "io.github.gretchycat.infiniselect.SELECTION_RECEIVER"
    const val EXTRA_SELECTION_MIME_TYPES = "io.github.gretchycat.infiniselect.SELECTION_MIME_TYPES"
    const val EXTRA_MULTIPLE_DELIVERY = "io.github.gretchycat.infiniselect.MULTIPLE_DELIVERY"
    const val EXTRA_METADATA_FORMAT = "io.github.gretchycat.infiniselect.METADATA_FORMAT"
    const val EXTRA_ALLOW_RECURSIVE_AUDIO_DIRECTORIES = "io.github.gretchycat.infiniselect.ALLOW_RECURSIVE_AUDIO_DIRECTORIES"
    const val EXTRA_SAVE_MODE = "io.github.gretchycat.infiniselect.SAVE_MODE"
    const val EXTRA_SAVE_FILE_NAME = "io.github.gretchycat.infiniselect.SAVE_FILE_NAME"
    const val EXTRA_SAVE_MIME_TYPE = "io.github.gretchycat.infiniselect.SAVE_MIME_TYPE"
    const val EXTRA_SAVE_SOURCE_URI = "io.github.gretchycat.infiniselect.SAVE_SOURCE_URI"
    const val EXTRA_SAVE_SOURCE_SIZE = "io.github.gretchycat.infiniselect.SAVE_SOURCE_SIZE"
    const val EXTRA_SAVE_DIRECTORY = "io.github.gretchycat.infiniselect.SAVE_DIRECTORY"
    const val EXTRA_SAVE_SUCCESS = "io.github.gretchycat.infiniselect.SAVE_SUCCESS"
    const val EXTRA_SAVE_RESULT_NAME = "io.github.gretchycat.infiniselect.SAVE_RESULT_NAME"
    const val EXTRA_SAVE_ERROR = "io.github.gretchycat.infiniselect.SAVE_ERROR"
    const val EXTRA_RESULT_PLAYLIST_URI = "io.github.gretchycat.infiniselect.RESULT_PLAYLIST_URI"
    const val EXTRA_RESULT_METADATA_URI = "io.github.gretchycat.infiniselect.RESULT_METADATA_URI"
    const val EXTRA_PROGRESS_RECEIVER = "io.github.gretchycat.infiniselect.PROGRESS_RECEIVER"
    const val EXTRA_PROGRESS_LABEL = "io.github.gretchycat.infiniselect.PROGRESS_LABEL"
    const val EXTRA_PROGRESS_FOLDERS = "io.github.gretchycat.infiniselect.PROGRESS_FOLDERS"
    const val EXTRA_PROGRESS_TRACKS = "io.github.gretchycat.infiniselect.PROGRESS_TRACKS"

    const val RESULT_SELECTED = 1
    const val RESULT_CANCELLED = 0
    const val RESULT_FALLBACK = -1
    const val RESULT_LOADING_STARTED = 2
    const val RESULT_LOADING_PROGRESS = 3
    const val RESULT_LOADING_FAILED = 4
}
