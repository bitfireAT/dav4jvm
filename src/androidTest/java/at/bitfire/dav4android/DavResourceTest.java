package at.bitfire.dav4android;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import junit.framework.TestCase;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import at.bitfire.dav4android.properties.DisplayName;

public class DavResourceTest extends TestCase {

    OkHttpClient httpClient = new HttpClient();
    MockWebServer mockServer = new MockWebServer();

    @Override
    public void setUp() throws IOException {
        mockServer.start(Constants.PORT);
    }

    @Override
    public void tearDown() throws IOException {
        mockServer.shutdown();
    }


    public void testPropfind() throws XmlPullParserException, IOException, HttpException {
        HttpUrl url = mockServer.url("/dav");
        mockServer.enqueue(new MockResponse()
                .setResponseCode(404));

        DavResource dav = new DavResource(httpClient, url);
        dav.propfind(DisplayName.NAME);
    }

}
