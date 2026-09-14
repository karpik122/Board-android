package eu.karpik122.board_android.model;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/** Wspólna baza elementów rysowanych na tablicy: tekst, identyfikator i pozycja. */
public abstract class BoardItem {
    private String id = UUID.randomUUID().toString();
    private String text;
    private float x, y, width, height;
    protected BoardItem(String text, float x, float y, float width, float height) { this.text = text; this.x = x; this.y = y; this.width = width; this.height = height; }
    public String getId() { return id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    /** Przesuwa element o podaną odległość w układzie współrzędnych tablicy. */
    public void moveBy(float dx, float dy) { x += dx; y += dy; }
    /** Zmienia rozmiar elementu; chroni przed utworzeniem niewidocznego elementu. */
    public void resizeTo(float newWidth, float newHeight) { width = Math.max(80, newWidth); height = Math.max(60, newHeight); }
    /** Sprawdza, czy punkt dotyku należy do prostokąta elementu. */
    public boolean contains(float pointX, float pointY) { return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height; }
    protected JSONObject baseJson(String type) throws JSONException {
        JSONObject json = new JSONObject(); json.put("type", type); json.put("id", id); json.put("text", text);
        json.put("x", x); json.put("y", y); json.put("width", width); json.put("height", height); return json;
    }
    protected void restoreCommonFields(JSONObject json) {
        id = json.optString("id", id); text = json.optString("text", text); x = (float) json.optDouble("x", x); y = (float) json.optDouble("y", y);
        width = (float) json.optDouble("width", width); height = (float) json.optDouble("height", height);
    }
    public abstract JSONObject toJson() throws JSONException;
    /** Odtwarza odpowiedni typ elementu na podstawie pola `type` w JSON. */
    public static BoardItem fromJson(JSONObject json) throws JSONException {
        String type = json.optString("type");
        BoardItem item;
        if ("note".equals(type)) item = new NoteItem("", 0, 0);
        else if ("image".equals(type)) item = new ImageItem(json.optString("uri"), 0, 0, 240, 180);
        else item = new BlockItem("", 0, 0);
        item.restoreCommonFields(json);
        if (item instanceof BlockItem) ((BlockItem) item).restoreLegacyImages(json);
        return item;
    }
}
