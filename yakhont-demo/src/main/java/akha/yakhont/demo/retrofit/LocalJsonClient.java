package akha.yakhont.demo.retrofit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import retrofit.client.Client;
import retrofit.client.Header;
import retrofit.client.Request;
import retrofit.client.Response;
import retrofit.mime.TypedInput;

/**
 * from https://gist.github.com/swanson/7dee3f3474e30fe8f15c
 
 * @author Matt Swanson
 */
@SuppressLint("DefaultLocale")
public class LocalJsonClient implements Client {

    private final Context context;

    private String scenario = null;

    private int delay;

    public LocalJsonClient(Context ctx) {
        this.context = ctx;
    }

    @SuppressWarnings("unused")
    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    @SuppressWarnings("SameParameterValue")
    public void setDelay(int delay) {
        this.delay = delay;
    }

    @Override
    public Response execute(Request request) throws IOException {
        URL requestedUrl = new URL(request.getUrl());
        String requestedMethod = request.getMethod();

        String prefix = "";
        if (this.scenario != null) {
            prefix = scenario + "_";
        }

        String fileName = (prefix + requestedMethod + requestedUrl.getPath()).replace('/', '_');
        fileName = fileName.toLowerCase();

        int resourceId = context.getResources().getIdentifier(fileName, "raw", context.getPackageName());

        if (resourceId == 0) {
            Log.wtf("YourTag", "Could not find res/raw/" + fileName + ".json");
            throw new IOException("Could not find res/raw/" + fileName + ".json");
        }

        InputStream inputStream = context.getResources().openRawResource(resourceId);

        String mimeType = URLConnection.guessContentTypeFromStream(inputStream);
        if (mimeType == null)
            mimeType = "application/json";

        if (delay > 0) SystemClock.sleep(delay);

        TypedInput body = new TypedInputStream(mimeType, inputStream.available(), inputStream);

        return new Response(request.getUrl(), 200, "Content from res/raw/" + fileName, new ArrayList<Header>(), body);
    }

    private static class TypedInputStream implements TypedInput {
        private final String mimeType;
        private final long length;
        private final InputStream stream;

        private TypedInputStream(String mimeType, long length, InputStream stream) {
            this.mimeType = mimeType;
            this.length = length;
            this.stream = stream;
        }

        @Override
        public String mimeType() {
            return mimeType;
        }

        @Override
        public long length() {
            return length;
        }

        @Override
        public InputStream in() throws IOException {
            return stream;
        }
    }
}
