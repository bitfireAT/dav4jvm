package at.bitfire.dav4android;

import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import junit.framework.TestCase;

import java.io.IOException;
import java.net.Proxy;
import java.nio.charset.Charset;
import java.util.Collection;

import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.exception.InvalidDavResponseException;
import at.bitfire.dav4android.property.CalendarColor;
import at.bitfire.dav4android.property.DisplayName;
import at.bitfire.dav4android.property.ResourceType;

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
        HttpUrl url = mockServer.url("/dav/");
        DavResource dav = new DavResource(httpClient, url);

        /*** NEGATIVE TESTS ***/

        // test for non-multi-status responses:
        // * 500 Internal Server Error
        mockServer.enqueue(new MockResponse().setResponseCode(500));
        try {
            dav.propfind(0, ResourceType.NAME);
            fail("Expected HttpException");
        } catch(HttpException e) {
        }
        // * 200 OK (instead of 207 Multi-Status)
        mockServer.enqueue(new MockResponse().setResponseCode(200));
        try {
            dav.propfind(0, ResourceType.NAME);
            fail("Expected InvalidDavResponseException");
        } catch(InvalidDavResponseException e) {
        }

        // test for invalid multi-status responses:
        // * non-XML response
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "text/html")
                .setBody("<html></html>"));
        try {
            dav.propfind(0, ResourceType.NAME);
            fail("Expected InvalidDavResponseException");
        } catch(InvalidDavResponseException e) {
        }

        // * malformed XML response
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<malformed-xml>"));
        try {
            dav.propfind(0, ResourceType.NAME);
            fail("Expected InvalidDavResponseException");
        } catch(InvalidDavResponseException e) {
        }

        // * response without <multistatus> root element
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<test></test>"));
        try {
            dav.propfind(0, ResourceType.NAME);
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
            dav.propfind(0, ResourceType.NAME);
            fail("Expected HttpException");
        } catch(HttpException e) {
        }

        /*** POSITIVE TESTS ***/

        // multi-status response without <response> elements
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'></multistatus>"));
        dav.propfind(0, ResourceType.NAME);
        assertEquals(0, dav.properties.size());
        assertEquals(0, dav.members.size());

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
        dav.propfind(0, ResourceType.NAME);
        assertEquals(0, dav.properties.size());
        assertEquals(0, dav.members.size());

        // multi-status response with <response>/<propstat> element
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                         "  <response>" +
                         "    <href>/dav</href>" +
                         "    <propstat>" +
                         "      <prop>" +
                         "        <resourcetype>" +
                         "        </resourcetype>" +
                         "        <displayname>My DAV Collection</displayname>" +
                         "      </prop>" +
                         "      <status>HTTP/1.1 200 OK</status>" +
                         "    </propstat>" +
                         "  </response>" +
                         "</multistatus>"));
        dav.propfind(0, ResourceType.NAME, DisplayName.NAME);
        assertEquals("My DAV Collection", ((DisplayName) dav.properties.get(DisplayName.NAME)).displayName);
        assertEquals(0, dav.members.size());

        // multi-status response for collection with several members; incomplete (not all <resourcetype>s listed)
        // TODO hrefs with :, @ etc.
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav/</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <resourcetype><collection/></resourcetype>" +
                        "        <displayname>My DAV Collection</displayname>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "  <response>" +
                        "    <href>/dav/subcollection</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <resourcetype><collection/></resourcetype>" +
                        "        <displayname>A Subfolder</displayname>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "  <response>" +
                        "    <href>/dav/uid@host:file</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <displayname>Absolute path with @ and :</displayname>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "  <response>" +
                        "    <href>relative-uid@host.file</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <displayname>Relative path with @</displayname>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "  <response>" +
                        "    <href>relative:colon.vcf</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <displayname>Relative path with colon</displayname>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "  <response>" +
                        "    <href>/something-very/else</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <displayname>Not requested</displayname>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"));
        dav.propfind(1, ResourceType.NAME, DisplayName.NAME);
        assertEquals(4, dav.members.size());
        boolean ok[] = new boolean[4];
        for (DavResource member : dav.members) {
            if (url.resolve("/dav/subcollection/").equals(member.location)) {
                assertTrue(((ResourceType) member.properties.get(ResourceType.NAME)).types.contains(ResourceType.WEBDAV_COLLECTION));
                assertEquals("A Subfolder", ((DisplayName) member.properties.get(DisplayName.NAME)).displayName);
                ok[0] = true;
            } else if (url.resolve("/dav/uid@host:file").equals(member.location)) {
                assertEquals("Absolute path with @ and :", ((DisplayName)member.properties.get(DisplayName.NAME)).displayName);
                ok[1] = true;
            } else if (url.resolve("/dav/relative-uid@host.file").equals(member.location)) {
                assertEquals("Relative path with @", ((DisplayName)member.properties.get(DisplayName.NAME)).displayName);
                ok[2] = true;
            } else if (url.resolve("/dav/relative:colon.vcf").equals(member.location)) {
                assertEquals("Relative path with colon", ((DisplayName)member.properties.get(DisplayName.NAME)).displayName);
                ok[3] = true;
            }
        }
        for (boolean singleOK : ok)
            assertTrue(singleOK);
    }

    /*public void testPublicPropfind() throws IOException, HttpException, DavException {
        httpClient.setAuthenticator(new Authenticator() {
            @Override
            public Request authenticate(Proxy proxy, Response response) throws IOException {
                String credential = Credentials.basic("test", "test");
                return response.request().newBuilder()
                        .header("Authorization", credential)
                        .build();
            }

            @Override
            public Request authenticateProxy(Proxy proxy, Response response) throws IOException {
                return null;
            }
        });
        DavResource dav = new DavResource(httpClient, HttpUrl.parse("https://demo.owncloud.org/remote.php/caldav/calendars/test/personal"));
        dav.propfind(0, ResourceType.NAME, DisplayName.NAME, CalendarColor.NAME);
    }*/

}
