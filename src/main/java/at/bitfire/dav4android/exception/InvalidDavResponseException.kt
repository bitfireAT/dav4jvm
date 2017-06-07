package at.bitfire.dav4android.exception

class InvalidDavResponseException: DavException {

    constructor(message: String?): super(message)
    constructor(message: String?, throwable: Throwable): super(message, throwable)

}
