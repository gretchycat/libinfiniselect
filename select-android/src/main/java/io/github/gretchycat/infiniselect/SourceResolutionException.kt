package io.github.gretchycat.infiniselect

/** Access could not be established; this does not mean the resource's contents are invalid. */
class SourceResolutionException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)
