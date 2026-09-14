package eu.karpik122.board_android.model;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
/** Model jednej tablicy: jej nazwa, elementy oraz linie łączące bloki. */
public class BoardData {
    private final String name;
    private final List<BoardItem> items = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();
    public BoardData(String name) { this.name = name; }
    public String getName() { return name; }
    public List<BoardItem> getItems() { return items; }
    public List<Connection> getConnections() { return connections; }
    /** Szuka elementu po identyfikatorze, np. aby narysować połączenie. */
    public BoardItem findItem(String id) { for (BoardItem item : items) if (item.getId().equals(id)) return item; return null; }
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject().put("name", name); JSONArray savedItems = new JSONArray(); for (BoardItem item : items) savedItems.put(item.toJson()); json.put("items", savedItems);
        JSONArray savedConnections = new JSONArray(); for (Connection connection : connections) savedConnections.put(connection.toJson()); json.put("connections", savedConnections); return json;
    }
    /** Odtwarza całą tablicę z lokalnego zapisu JSON. */
    public static BoardData fromJson(JSONObject json) throws JSONException {
        BoardData board = new BoardData(json.optString("name", "Tablica"));
        JSONArray savedItems = json.optJSONArray("items"); if (savedItems != null) for (int i = 0; i < savedItems.length(); i++) board.items.add(BoardItem.fromJson(savedItems.getJSONObject(i)));
        // `lines` zachowuje kompatybilność z pierwszym lokalnym formatem aplikacji.
        JSONArray savedConnections = json.optJSONArray("connections");
        if (savedConnections == null) savedConnections = json.optJSONArray("lines");
        if (savedConnections != null) for (int i = 0; i < savedConnections.length(); i++) board.connections.add(Connection.fromJson(savedConnections.getJSONObject(i)));
        board.migrateLegacyImages();
        return board;
    }

    /** Zamienia zdjęcia przypięte do bloków z wersji 1 na niezależne zdjęcia na tablicy. */
    private void migrateLegacyImages() {
        List<ImageItem> migrated = new ArrayList<>();
        for (BoardItem item : items) {
            if (!(item instanceof BlockItem)) continue;
            float imageY = item.getY() + item.getHeight() + 18;
            for (String uri : ((BlockItem) item).getLegacyImageUris()) {
                migrated.add(new ImageItem(uri, item.getX(), imageY, 220, 165));
                imageY += 180;
            }
        }
        items.addAll(migrated);
    }
}
