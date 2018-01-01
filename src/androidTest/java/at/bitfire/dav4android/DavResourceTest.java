package at.bitfire.dav4android;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DavResourceTest {

    private static final String
            sampleText = "SAMPLE RESPONSE";

    private OkHttpClient httpClient = new OkHttpClient.Builder()
            .followRedirects(false)
            .build();
    private MockWebServer mockServer = new MockWebServer();


    @Before
    public void startServer() throws IOException {
        mockServer.start();
    }

    @After
    public void stopServer() throws IOException {
        mockServer.shutdown();
    }

    private HttpUrl sampleUrl() {
        return mockServer.url("/dav/");
    }


    @Test
    public void testOptions() throws InterruptedException, IOException, HttpException, DavException {
        HttpUrl url = sampleUrl();
        DavResource dav = new DavResource(httpClient, url);

        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setHeader("DAV", "1,2,3, hyperactive-access"));
        dav.options();
        assertTrue(dav.getCapabilities().contains("1"));
        assertTrue(dav.getCapabilities().contains("2"));
        assertTrue(dav.getCapabilities().contains("3"));
        assertTrue(dav.getCapabilities().contains("hyperactive-access"));

        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK));
        dav.options();
        assertTrue(dav.getCapabilities().isEmpty());
    }

    @Test
    public void testGet() throws InterruptedException, IOException, HttpException, DavException {
        HttpUrl url = sampleUrl();
        DavResource dav = new DavResource(httpClient, url);

        /* POSITIVE TEST CASES */

        // 200 OK
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setHeader("ETag", "W/\"My Weak ETag\"")
                .setHeader("Content-Type", "application/x-test-result")
                .setBody(sampleText));
        ResponseBody body = dav.get("*/*");
        assertEquals(sampleText, body.string());
        assertEquals("My Weak ETag", dav.getProperties().get(GetETag.class).getETag());
        assertEquals("application/x-test-result", dav.getProperties().get(GetContentType.class).getType());

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
        assertEquals("StrongETag", dav.getProperties().get(GetETag.class).getETag());

        mockServer.takeRequest();
        rq = mockServer.takeRequest();
        assertEquals("GET", rq.getMethod());
        assertEquals("/target", rq.getPath());

        // 200 OK without ETag in response
        dav.getProperties().set(GetETag.NAME, new GetETag("test"));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(sampleText));
        dav.get("*/*");
        assertNull(dav.getProperties().get(GetETag.class));
    }

    @Test
    public void testPut() throws InterruptedException, IOException, HttpException, DavException {
        HttpUrl url = sampleUrl();
        DavResource dav = new DavResource(httpClient, url);

        /* POSITIVE TEST CASES */

        // no preconditions, 201 Created
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_CREATED)
                .setHeader("ETag", "W/\"Weak PUT ETag\""));
        assertFalse(dav.put(RequestBody.create(MediaType.parse("text/plain"), sampleText), null, false));
        assertEquals("Weak PUT ETag", dav.getProperties().get(GetETag.class).getETag());

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
        assertEquals(url.resolve("/target"), dav.getLocation());
        assertNull(dav.getProperties().get(GetETag.class));

        mockServer.takeRequest();
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

    @Test
    public void testDelete() throws InterruptedException, IOException, HttpException {
        HttpUrl url = sampleUrl();
        DavResource dav = new DavResource(httpClient, url);

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

        // 302 Moved Temporarily
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_MOVED_TEMP)
                .setHeader("Location", "/new-location")
        );
        mockServer.enqueue(new MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK));
        dav.delete(null);

        /* NEGATIVE TEST CASES */

        // 207 multi-status (e.g. single resource couldn't be deleted when DELETEing a collection)
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207));
        try {
            dav.delete(null);
            fail();
        } catch(HttpException e) {
            // treat 207 as an error
        }
    }

    @Test
    public void testPropfindAndMultiStatus() throws IOException, HttpException, DavException {
        HttpUrl url = sampleUrl();
        DavResource dav = new DavResource(httpClient, url);

        /*** NEGATIVE TESTS ***/

        // test for non-multi-status responses:
        // * 500 Internal Server Error
        mockServer.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR));
        try {
            dav.propfind(0, ResourceType.NAME);
            fail("Expected HttpException");
        } catch(HttpException e) {
        }
        // * 200 OK (instead of 207 Multi-Status)
        mockServer.enqueue(new MockResponse().setResponseCode(HttpURLConnection.HTTP_OK));
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
        assertNull(dav.getProperties().get(ResourceType.class));


        /*** POSITIVE TESTS ***/

        // multi-status response without <response> elements
        mockServer.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'></multistatus>"));
        dav.propfind(0, ResourceType.NAME);
        assertEquals(0, dav.getProperties().size());
        assertEquals(0, dav.getMembers().size());

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
        assertEquals(0, dav.getProperties().size());
        assertEquals(0, dav.getMembers().size());

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
        assertEquals("My DAV Collection", dav.getProperties().get(DisplayName.class).getDisplayName());
        assertEquals(0, dav.getMembers().size());

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
        assertEquals(4, dav.getMembers().size());
        boolean ok[] = new boolean[4];
        for (DavResource member : dav.getMembers()) {
            if (url.resolve("/dav/subcollection/").equals(member.getLocation())) {
                assertTrue(member.getProperties().get(ResourceType.class).getTypes().contains(ResourceType.COLLECTION));
                assertEquals("A Subfolder", member.getProperties().get(DisplayName.class).getDisplayName());
                ok[0] = true;
            } else if (url.resolve("/dav/uid@host:file").equals(member.getLocation())) {
                assertEquals("Absolute path with @ and :", member.getProperties().get(DisplayName.class).getDisplayName());
                ok[1] = true;
            } else if (url.resolve("/dav/relative-uid@host.file").equals(member.getLocation())) {
                assertEquals("Relative path with @", member.getProperties().get(DisplayName.class).getDisplayName());
                ok[2] = true;
            } else if (url.resolve("/dav/relative:colon.vcf").equals(member.getLocation())) {
                assertEquals("Relative path with colon", member.getProperties().get(DisplayName.class).getDisplayName());
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
        assertTrue(dav.getProperties().get(ResourceType.class).getTypes().contains(ResourceType.COLLECTION));
        assertEquals("My DAV Collection", dav.getProperties().get(DisplayName.class).getDisplayName());

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
        assertEquals("Without Status", dav.getProperties().get(DisplayName.class).getDisplayName());
    }

    @Test
    public void testPropfindUpdateProperties() throws IOException, HttpException, DavException {
        HttpUrl url = sampleUrl();
        DavResource dav = new DavResource(httpClient, url);

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
        assertEquals("DisplayName 1", dav.getProperties().get(DisplayName.class).getDisplayName());
        assertEquals("ETag 1", dav.getProperties().get(GetETag.class).getETag());
        assertEquals("CTag 1", dav.getProperties().get(GetCTag.class).getCTag());

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
        assertEquals("DisplayName 2", dav.getProperties().get(DisplayName.class).getDisplayName());
        assertNull(dav.getProperties().get(GetETag.class));
        assertEquals("CTag 1", dav.getProperties().get(GetCTag.class).getCTag());
    }

}
