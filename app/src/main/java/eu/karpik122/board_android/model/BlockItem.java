package eu.karpik122.board_android.model;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
/** Niebieski blok schematu z tekstem. Lista URI istnieje tylko dla migracji danych starej wersji. */
public class BlockItem extends BoardItem {
    private final List<String> imageUris = new ArrayList<>();
    public BlockItem(String text, float x, float y) { super(text, x, y, 210, 125); }
    /** Zwraca stare przypięte zdjęcia, aby przy odczycie przekształcić je w osobne elementy. */
    public List<String> getLegacyImageUris() { return imageUris; }
    void restoreLegacyImages(JSONObject json) throws JSONException { JSONArray images = json.optJSONArray("images"); if (images != null) for (int i = 0; i < images.length(); i++) imageUris.add(images.getString(i)); }
    /** Nowy zapis bloku nie zawiera zdjęć: są one samodzielnymi elementami ImageItem. */
    @Override public JSONObject toJson() throws JSONException { return baseJson("block"); }
}
