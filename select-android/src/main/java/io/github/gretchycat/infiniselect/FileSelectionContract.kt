package io.github.gretchycat.infiniselect

/** Cross-app contract implemented by InfiniFolder's optional floating selector. */
object FileSelectionContract {
    const val ACTION_SELECT = "io.github.gretchycat.infiniselect.action.SELECT_FILE"

    const val EXTRA_CLIENT_ID = "io.github.gretchycat.infiniselect.CLIENT_ID"
    const val EXTRA_DISPLAY_NAME = "io.github.gretchycat.infiniselect.DISPLAY_NAME"
    const val EXTRA_MIME_TYPES = "io.github.gretchycat.infiniselect.MIME_TYPES"
    const val EXTRA_SUPPORTED_EXTENSIONS = "io.github.gretchycat.infiniselect.SUPPORTED_EXTENSIONS"
    const val EXTRA_HIDE_UNSUPPORTED = "io.github.gretchycat.infiniselect.HIDE_UNSUPPORTED"
    const val EXTRA_ALLOW_MULTIPLE = "io.github.gretchycat.infiniselect.ALLOW_MULTIPLE"
    const val EXTRA_INITIAL_DIRECTORY = "io.github.gretchycat.infiniselect.INITIAL_DIRECTORY"
    const val EXTRA_INITIAL_SCROLL_Y = "io.github.gretchycat.infiniselect.INITIAL_SCROLL_Y"

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
    const val EXTRA_FALLBACK_TO_SOURCES = "io.github.gretchycat.infiniselect.FALLBACK_TO_SOURCES"
    const val EXTRA_FAILURE_MESSAGE = "io.github.gretchycat.infiniselect.FAILURE_MESSAGE"

    const val EXTRA_SELECTION_MODE = "io.github.gretchycat.infiniselect.SELECTION_MODE"
    const val EXTRA_SELECTION_RECEIVER = "io.github.gretchycat.infiniselect.SELECTION_RECEIVER"
    const val EXTRA_SELECTION_MIME_TYPES = "io.github.gretchycat.infiniselect.SELECTION_MIME_TYPES"
    const val EXTRA_MULTIPLE_DELIVERY = "io.github.gretchycat.infiniselect.MULTIPLE_DELIVERY"
    const val EXTRA_METADATA_FORMAT = "io.github.gretchycat.infiniselect.METADATA_FORMAT"
    const val EXTRA_ALLOW_RECURSIVE_AUDIO_DIRECTORIES = "io.github.gretchycat.infiniselect.ALLOW_RECURSIVE_AUDIO_DIRECTORIES"
    const val EXTRA_RESULT_PLAYLIST_URI = "io.github.gretchycat.infiniselect.RESULT_PLAYLIST_URI"
    const val EXTRA_RESULT_METADATA_URI = "io.github.gretchycat.infiniselect.RESULT_METADATA_URI"

    const val RESULT_SELECTED = 1
    const val RESULT_CANCELLED = 0
    const val RESULT_FALLBACK = -1
}
