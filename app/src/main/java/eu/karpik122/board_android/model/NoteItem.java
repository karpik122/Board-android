package eu.karpik122.board_android.model;
import org.json.JSONException;
import org.json.JSONObject;
/** Żółta karteczka z tekstem. */
public class NoteItem extends BoardItem {
    public NoteItem(String text, float x, float y) { super(text, x, y, 180, 110); }
    @Override public JSONObject toJson() throws JSONException { return baseJson("note"); }
}
