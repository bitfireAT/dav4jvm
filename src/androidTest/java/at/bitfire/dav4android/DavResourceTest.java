package at.bitfire.dav4android;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import junit.framework.TestCase;

import java.io.IOException;
import java.net.HttpURLConnection;

import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.exception.InvalidDavResponseException;
import at.bitfire.dav4android.exception.PreconditionFailedException;
import at.bitfire.dav4android.property.DisplayName;
import at.bitfire.dav4android.property.GetCTag;
import at.bitfire.dav4android.property.GetContentType;
import at.bitfire.dav4android.property.GetETag;
import at.bitfire.dav4android.property.ResourceType;

public class DavResourceTest extends TestCase {

    private static final String
            sampleText = "SAMPLE RESPONSE";

    private OkHttpClient httpClient = new OkHttpClient();
    private MockWebServer mockServer = new MockWebServer();


    @Override
    public void setUp() throws IOException {
        mockServer.start();
    }

    @Override
    public void tearDown() throws IOException {
        mockServer.shutdown();
    }

    private HttpUrl sampleUrl() {
        return mockServer.url("/dav/");
    }


    public void testOptions() throws InterruptedException, IOException, HttpException, DavException {
        HttpUrl url = sampleUrl();
        DavResource dav = new DavResource(null, httpClient, url);

        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setHeader("DAV", "1,2,3, hyperactive-access"));
        dav.options();
        assertTrue(dav.capabilities.contains("1"));
        assertTrue(dav.capabilities.contains("2"));
        assertTrue(dav.capabilities.contains("3"));
        assertTrue(dav.capabilities.contains("hyperactive-access"));

        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK));
        dav.options();
        assertTrue(dav.capabilities.isEmpty());
    }

    public void testGet() throws InterruptedException, IOException, HttpException, DavException {
        HttpUrl url = sampleUrl();
        DavResource dav = new DavResource(null, httpClient, url);

        /* POSITIVE TEST CASES */

        // 200 OK
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setHeader("ETag", "W/\"My Weak ETag\"")
                .setHeader("Content-Type", "application/x-test-result")
                .setBody(sampleText));
        ResponseBody body = dav.get("*/*");
        assertEquals(sampleText, body.string());
        assertEquals("My Weak ETag", ((GetETag)dav.properties.get(GetETag.NAME)).eTag);
        assertEquals("application/x-test-result", ((GetContentType) dav.properties.get(GetContentType.NAME)).type);

        RecordedRequest rq = mockServer.takeRequest();
        assertEquals("GET", rq.getMethod());
        assertEquals(url.encodedPath(), rq.getPath());
        assertEquals("*/*", rq.getHeader("Accept"));

        // 302 Moved Temporarily + 200 OK
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_MOVED_TEMP)
                .setHeader("Location", "/target")
                .setBody("This resource was moved."));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setHeader("ETag", "\"StrongETag\"")
                .setBody(sampleText));
        body = dav.get("*/*");
        assertEquals(sampleText, body.string());
        assertEquals("StrongETag", ((GetETag) dav.properties.get(GetETag.NAME)).eTag);

        rq = mockServer.takeRequest();
        rq = mockServer.takeRequest();
        assertEquals("GET", rq.getMethod());
        assertEquals("/target", rq.getPath());

        /* NEGATIVE TEST CASES */

        // 200 OK without ETag in response
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(sampleText));
        try {
            body = dav.get("*/*");
            fail();
        } catch (DavException e) {
        }
    }

    public void testPut() throws InterruptedException, IOException, HttpException, DavException {
        HttpUrl url = sampleUrl();
        DavResource dav = new DavResource(null, httpClient, url);

        /* POSITIVE TEST CASES */

        // no preconditions, 201 Created
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_CREATED)
                .setHeader("ETag", "W/\"Weak PUT ETag\""));
        assertFalse(dav.put(RequestBody.create(MediaType.parse("text/plain"), sampleText), null, false));
        assertEquals("Weak PUT ETag", ((GetETag)dav.properties.get(GetETag.NAME)).eTag);

        RecordedRequest rq = mockServer.takeRequest();
        assertEquals("PUT", rq.getMethod());
        assertEquals(url.encodedPath(), rq.getPath());
        assertNull(rq.getHeader("If-Match"));
        assertNull(rq.getHeader("If-None-Match"));

        // precondition: If-None-Match, 301 Moved Permanently + 204 No Content, no ETag in response
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_MOVED_PERM)
                .setHeader("Location", "/target"));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_NO_CONTENT));
        assertTrue(dav.put(RequestBody.create(MediaType.parse("text/plain"), sampleText), null, true));
        assertEquals(url.resolve("/target"), dav.location);
        assertNull(dav.properties.get(GetETag.NAME));

        rq = mockServer.takeRequest();
        rq = mockServer.takeRequest();
        assertEquals("PUT", rq.getMethod());
        assertEquals("*", rq.getHeader("If-None-Match"));

        // precondition: If-Match, 412 Precondition Failed
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_PRECON_FAILED));
        try {
            dav.put(RequestBody.create(MediaType.parse("text/plain"), sampleText), "ExistingETag", false);
            fail();
        } catch(PreconditionFailedException e) {
        }
        rq = mockServer.takeRequest();
        assertEquals("\"ExistingETag\"", rq.getHeader("If-Match"));
        assertNull(rq.getHeader("If-None-Match"));
    }

    public void testDelete() throws InterruptedException, IOException, HttpException {
        HttpUrl url = sampleUrl();
        DavResource dav = new DavResource(null, httpClient, url);

        /* POSITIVE TEST CASES */

        // no preconditions, 204 No Content
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_NO_CONTENT));
        dav.delete(null);

        RecordedRequest rq = mockServer.takeRequest();
        assertEquals("DELETE", rq.getMethod());
        assertEquals(url.encodedPath(), rq.getPath());
        assertNull(rq.getHeader("If-Match"));

        // precondition: If-Match, 200 OK
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody("Resource has been deleted."));
        dav.delete("DeleteOnlyThisETag");

        rq = mockServer.takeRequest();
        assertEquals("\"DeleteOnlyThisETag\"", rq.getHeader("If-Match"));

        /* NEGATIVE TEST CASES */

        // 302 Moved Temporarily
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_MOVED_TEMP));
        try {
            dav.delete(null);
            fail();
        } catch(HttpException e) {
            // we don't follow redirects on DELETE
        }
    }

    public void testPropfindAndMultiStatus() throws IOException, HttpException, DavException {
        HttpUrl url = sampleUrl();
        DavResource dav = new DavResource(null, httpClient, url);

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

        // * multi-status response with invalid <status> in <response>
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                         "  <response>" +
                         "    <href>/dav</href>" +
                         "    <status>Invalid Status Line</status>" +
                         "  </response>" +
                         "</multistatus>"));
        try {
            dav.propfind(0, ResourceType.NAME);
            fail("Expected HttpException");
        } catch(HttpException e) {
        }

        // * multi-status response with <response>/<status> element indicating failure
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

        // * multi-status response with invalid <status> in <propstat>
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <resourcetype><collection/></resourcetype>" +
                        "      </prop>" +
                        "      <status>Invalid Status Line</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"));
        dav.propfind(0, ResourceType.NAME);
        assertNull(dav.properties.get(ResourceType.NAME));


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
        assertEquals("My DAV Collection", ((DisplayName)dav.properties.get(DisplayName.NAME)).displayName);
        assertEquals(0, dav.members.size());

        // multi-status response for collection with several members; incomplete (not all <resourcetype>s listed)
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>" + url.toString() + "</href>" +
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
                assertTrue(((ResourceType) member.properties.get(ResourceType.NAME)).types.contains(ResourceType.COLLECTION));
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


        /*** SPECIAL CASES ***/

        // same property is sent as 200 OK and 404 Not Found in same <response> (seen in iCloud)
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>" + url.toString() + "</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <resourcetype><collection/></resourcetype>" +
                        "        <displayname>My DAV Collection</displayname>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <resourcetype/>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 404 Not Found</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"));
        dav.propfind(0, ResourceType.NAME, DisplayName.NAME);
        assertTrue(((ResourceType) dav.properties.get(ResourceType.NAME)).types.contains(ResourceType.COLLECTION));
        assertEquals("My DAV Collection", ((DisplayName) dav.properties.get(DisplayName.NAME)).displayName);

        // multi-status response with <propstat> that doesn't contain <status> (=> assume 200 OK)
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <displayname>Without Status</displayname>" +
                        "      </prop>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"));
        dav.propfind(0, DisplayName.NAME);
        assertEquals("Without Status", ((DisplayName) dav.properties.get(DisplayName.NAME)).displayName);
    }

    public void testPropfindUpdateProperties() throws IOException, HttpException, DavException {
        HttpUrl url = sampleUrl();
        DavResource dav = new DavResource(null, httpClient, url);

        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                         "  <response>" +
                         "    <href>/dav</href>" +
                         "    <propstat>" +
                         "      <prop>" +
                         "        <displayname>DisplayName 1</displayname>" +
                         "        <getetag>ETag 1</getetag>" +
                         "        <getctag xmlns=\"http://calendarserver.org/ns/\">CTag 1</getctag>" +
                         "      </prop>" +
                         "      <status>HTTP/1.1 200 OK</status>" +
                         "    </propstat>" +
                         "  </response>" +
                         "</multistatus>"));
        dav.propfind(0, DisplayName.NAME, GetETag.NAME, GetCTag.NAME);
        assertEquals("DisplayName 1", ((DisplayName) dav.properties.get(DisplayName.NAME)).displayName);
        assertEquals("ETag 1", ((GetETag)dav.properties.get(GetETag.NAME)).eTag);
        assertEquals("CTag 1", ((GetCTag) dav.properties.get(GetCTag.NAME)).cTag);

        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <displayname>DisplayName 2</displayname>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <getetag/>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 404 Not Found</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"));
        dav.propfind(0, ResourceType.NAME, DisplayName.NAME);
        assertEquals("DisplayName 2", ((DisplayName)dav.properties.get(DisplayName.NAME)).displayName);
        assertNull(dav.properties.get(GetETag.NAME));
        assertEquals("CTag 1", ((GetCTag)dav.properties.get(GetCTag.NAME)).cTag);
    }

}
