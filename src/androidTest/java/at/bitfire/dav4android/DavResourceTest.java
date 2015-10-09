package at.bitfire.dav4android;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import junit.framework.TestCase;

import java.io.IOException;

import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.exception.InvalidDavResponseException;
import at.bitfire.dav4android.property.DisplayName;

public class DavResourceTest extends TestCase {

    OkHttpClient httpClient = new HttpClient();
    MockWebServer mockServer = new MockWebServer();

    @Override
    public void setUp() throws IOException {
        mockServer.start(2540);
    }

    @Override
    public void tearDown() throws IOException {
        mockServer.shutdown();
    }


    public void testPropfindAndMultiStatus() throws IOException, HttpException, DavException {
        HttpUrl url = mockServer.url("/dav");
        DavResource dav = new DavResource(httpClient, url);

        /*** NEGATIVE TESTS ***/

        // test for non-Multi-Status responses:
        // * 500 Internal Server Error
        mockServer.enqueue(new MockResponse().setResponseCode(500));
        try {
            dav.propfind(DisplayName.NAME);
            fail("Expected HttpException");
        } catch(HttpException e) {
        }
        // * 200 OK (instead of 207 Multi-Status)
        mockServer.enqueue(new MockResponse().setResponseCode(200));
        try {
            dav.propfind(DisplayName.NAME);
            fail("Expected InvalidDavResponseException");
        } catch(InvalidDavResponseException e) {
        }

        // test for invalid Multi-Status responses:
        // * non-XML response
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "text/html")
                .setBody("<html></html>"));
        try {
            dav.propfind(DisplayName.NAME);
            fail("Expected InvalidDavResponseException");
        } catch(InvalidDavResponseException e) {
        }

        // * malformed XML response
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<malformed-xml>"));
        try {
            dav.propfind(DisplayName.NAME);
            fail("Expected InvalidDavResponseException");
        } catch(InvalidDavResponseException e) {
        }

        // * response without <multistatus> root element
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<test></test>"));
        try {
            dav.propfind(DisplayName.NAME);
            fail("Expected InvalidDavResponseException");
        } catch(InvalidDavResponseException e) {
        }

        // multi-status response with <response>/<status> element indicating failure
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                         "  <response>" +
                         "    <href>/dav</href>" +
                         "    <status>HTTP/1.1 403 Forbidden</status>" +
                         "  </response>" +
                         "</multistatus>"));
        try {
            dav.propfind(DisplayName.NAME);
            fail("Expected HttpException");
        } catch(HttpException e) {
        }

        /*** POSITIVE TESTS ***/

        // multi-status response without <response> elements
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'></multistatus>"));
        dav.propfind(DisplayName.NAME);
        // TODO assert zero members and no changed properties

        // multi-status response with <response>/<status> element indicating success
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                         "  <response>" +
                         "    <href>/dav</href>" +
                         "    <status>HTTP/1.1 200 OK</status>" +
                         "  </response>" +
                         "</multistatus>"));
        dav.propfind(DisplayName.NAME);
        // TODO assert zero members and no changed properties

        // multi-status response with <response>/<propstat> element
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                         "  <response>" +
                         "    <href>/dav</href>" +
                         "    <propstat>" +
                         "      <prop>" +
                         "        <displayname>My DAV Collection</displayname>" +
                         "      </prop>" +
                         "      <status>HTTP/1.1 200 OK</status>" +
                         "    </propstat>" +
                         "  </response>" +
                         "</multistatus>"));
        dav.propfind(DisplayName.NAME);
        // TODO assert zero members, but changed properties

        // TODO hrefs with :, @ etc.
    }

}
