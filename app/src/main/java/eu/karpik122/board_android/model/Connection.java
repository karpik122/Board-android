package eu.karpik122.board_android.model;
import org.json.JSONException;
import org.json.JSONObject;
/** Linia relacji między dwoma blokami, opisana przez ich stabilne identyfikatory. */
public class Connection {
    private final String fromId;
    private final String toId;
    public Connection(String fromId, String toId) { this.fromId = fromId; this.toId = toId; }
    public String getFromId() { return fromId; }
    public String getToId() { return toId; }
    public JSONObject toJson() throws JSONException { return new JSONObject().put("from", fromId).put("to", toId); }
    public static Connection fromJson(JSONObject json) { return new Connection(json.optString("from"), json.optString("to")); }
}
