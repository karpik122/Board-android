package eu.karpik122.board_android.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Zdjęcie na tablicy. Jest niezależnym elementem, więc można je zaznaczać,
 * przesuwać, skalować i usuwać bez zmieniania bloku tekstowego.
 */
public class ImageItem extends BoardItem {
    private final String uri;

    public ImageItem(String uri, float x, float y, float width, float height) {
        super("", x, y, width, height);
        this.uri = uri;
    }

    public String getUri() { return uri; }

    /** Skaluje zdjęcie od szerokości i zachowuje jego bieżące proporcje. */
    public void resizeKeepingAspect(float newWidth) {
        float ratio = getHeight() / getWidth();
        resizeTo(newWidth, newWidth * ratio);
    }

    @Override public JSONObject toJson() throws JSONException {
        JSONObject json = baseJson("image");
        json.put("uri", uri);
        return json;
    }
}
