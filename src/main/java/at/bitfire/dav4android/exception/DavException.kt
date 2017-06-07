package at.bitfire.dav4android.exception;

open class DavException: Exception {

    constructor(message: String?): super(message)
    constructor(message: String?, throwable: Throwable): super(message, throwable)

}
