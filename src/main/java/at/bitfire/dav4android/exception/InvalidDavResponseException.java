package at.bitfire.dav4android.exception;

public class InvalidDavResponseException extends DavException {

    public InvalidDavResponseException(String message) {
        super(message);
    }

    public InvalidDavResponseException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
