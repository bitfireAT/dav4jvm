package at.bitfire.dav4android.exception;

public class DavException extends Exception {

    public DavException(String message) {
        super(message);
    }

    public DavException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
