package eu.karpik122.board_android.data;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import eu.karpik122.board_android.model.BoardData;

/** Jedno miejsce odpowiedzialne za lokalny, trwały zapis tablic w SharedPreferences. */
public class BoardRepository {
    private static final String PREFERENCES_NAME = "virtual_board";
    private static final String BOARDS_KEY = "boards";
    private final SharedPreferences preferences;
    public BoardRepository(Context context) { preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE); }
    /** Odczytuje zapisane tablice; tworzy domyślną przy pierwszym uruchomieniu. */
    public List<BoardData> load() {
        List<BoardData> result = new ArrayList<>();
        try { JSONArray array = new JSONArray(preferences.getString(BOARDS_KEY, "[]")); for (int i = 0; i < array.length(); i++) result.add(BoardData.fromJson(array.getJSONObject(i))); }
        catch (Exception ignored) { /* Uszkodzone dane nie blokują startu aplikacji. */ }
        if (result.isEmpty()) result.add(new BoardData("Moja pierwsza tablica"));
        return result;
    }
    /** Serializuje wszystkie tablice do JSON i zapisuje je asynchronicznie. */
    public void save(List<BoardData> boards) {
        try { JSONArray array = new JSONArray(); for (BoardData board : boards) array.put(board.toJson()); preferences.edit().putString(BOARDS_KEY, array.toString()).apply(); }
        catch (Exception ignored) { }
    }
}
