package eu.karpik122.board_android;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import java.util.List;
import eu.karpik122.board_android.data.BoardRepository;
import eu.karpik122.board_android.model.BoardData;
import eu.karpik122.board_android.model.BoardItem;
import eu.karpik122.board_android.ui.BoardView;

/** Ekran główny: buduje paski narzędzi i przekazuje działania do widoku tablicy. */
public class MainActivity extends AppCompatActivity implements BoardView.Listener {
    private BoardRepository repository;
    private List<BoardData> boards;
    private int currentBoardIndex;
    private BoardView boardView;
    private TextView boardTitle;
    private ActivityResultLauncher<String[]> imagePicker;

    @Override protected void onCreate(Bundle savedInstanceState) {
        // Aplikacja ma zawsze zachować zaprojektowany ciemny wygląd.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        // Kontekst aktywności jest w pełni gotowy dopiero w onCreate.
        repository = new BoardRepository(this);
        boards = repository.load();
        registerImagePicker();
        setContentView(createScreen());
        updateTitle();
    }

    /** Rejestruje systemowy wybór pliku i dodaje zdjęcie jako niezależny element tablicy. */
    private void registerImagePicker() {
        imagePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { /* Nie każdy dostawca umożliwia trwały dostęp. */ }
            boardView.addImage(uri.toString());
        });
    }

    /** Tworzy ekran z nagłówkiem, obszarem roboczym i dolnym paskiem narzędzi. */
    private View createScreen() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(16, 18, 24));
        root.addView(createHeader());
        boardView = new BoardView(this, boards.get(currentBoardIndex), this);
        root.addView(boardView, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(createToolbar());
        return root;
    }

    /** Górny pasek: nazwa tablicy, przełącznik tablic oraz cofanie. */
    private View createHeader() {
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(10), dp(8), dp(10), dp(8));
        boardTitle = new TextView(this); boardTitle.setTextColor(Color.WHITE); boardTitle.setTextSize(18); boardTitle.setTypeface(Typeface.DEFAULT_BOLD); boardTitle.setSingleLine();
        header.addView(boardTitle, new LinearLayout.LayoutParams(0, dp(44), 1));
        addButton(header, "Tablice", ignored -> showBoardChooser());
        addButton(header, "↶", ignored -> boardView.undo());
        return header;
    }

    /** Dolny pasek: operacje na elementach aktualnie otwartej tablicy. */
    private View createToolbar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        LinearLayout toolbar = new LinearLayout(this); toolbar.setPadding(dp(8), dp(8), dp(8), dp(10));
        addButton(toolbar, "+ Karteczka", ignored -> boardView.addNote());
        addButton(toolbar, "+ Blok", ignored -> boardView.addBlock());
        addButton(toolbar, "Zdjęcie", ignored -> imagePicker.launch(new String[]{"image/*"}));
        addButton(toolbar, "Połącz", ignored -> boardView.beginConnection());
        addButton(toolbar, "Edytuj", ignored -> editSelectedItem());
        addButton(toolbar, "Usuń", ignored -> boardView.deleteSelected());
        scroll.addView(toolbar); return scroll;
    }

    /** Dodaje jednolicie ostylowany przycisk do jednego z pasków. */
    private void addButton(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(this); button.setText(label); button.setTextSize(12); button.setTextColor(Color.WHITE); button.setAllCaps(false); button.setOnClickListener(listener);
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.rgb(39, 45, 59)); background.setCornerRadius(dp(10)); button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(42)); params.setMargins(dp(3), 0, dp(3), 0); parent.addView(button, params);
    }

    /** Pozwala otworzyć istniejącą tablicę albo przejść do tworzenia nowej. */
    private void showBoardChooser() {
        String[] names = new String[boards.size() + 1];
        for (int index = 0; index < boards.size(); index++) names[index] = boards.get(index).getName();
        names[boards.size()] = "＋ Nowa tablica";
        new AlertDialog.Builder(this).setTitle("Twoje tablice").setItems(names, (dialog, selected) -> {
            if (selected == boards.size()) showNewBoardDialog(); else switchBoard(selected);
        }).show();
    }

    /** Tworzy nową tablicę i od razu otwiera ją w widoku. */
    private void showNewBoardDialog() {
        EditText input = new EditText(this); input.setHint("np. Pomysły na projekt"); input.setSingleLine(); input.setPadding(dp(24), 0, dp(24), 0);
        new AlertDialog.Builder(this).setTitle("Nowa tablica").setView(input).setNegativeButton("Anuluj", null)
                .setPositiveButton("Utwórz", (dialog, ignored) -> {
                    String name = input.getText().toString().trim(); boards.add(new BoardData(name.isEmpty() ? "Nowa tablica" : name));
                    switchBoard(boards.size() - 1); saveBoards();
                }).show();
    }

    /** Edytuje tekst aktualnie zaznaczonego elementu. */
    private void editSelectedItem() {
        BoardItem item = boardView.getSelectedItem();
        if (item == null) { showMessage("Dotknij elementu, który chcesz edytować."); return; }
        if (item instanceof eu.karpik122.board_android.model.ImageItem) { showMessage("Zdjęcie możesz przesuwać i skalować uchwytem w prawym dolnym rogu."); return; }
        EditText input = new EditText(this); input.setText(item.getText()); input.setPadding(dp(24), 0, dp(24), 0);
        new AlertDialog.Builder(this).setTitle("Edytuj tekst").setView(input).setNegativeButton("Anuluj", null)
                .setPositiveButton("Zapisz", (dialog, ignored) -> boardView.updateSelectedText(input.getText().toString())).show();
    }

    /** Podmienia model widoku przy wyborze innej tablicy. */
    private void switchBoard(int index) { currentBoardIndex = index; boardView.setBoard(boards.get(index)); updateTitle(); }
    private void updateTitle() { boardTitle.setText(boards.get(currentBoardIndex).getName() + "  •  przesuwaj palcem"); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    /** Widok powiadamia aktywność o zmianie, która ma zostać automatycznie zapisana. */
    @Override public void onBoardChanged(BoardData board) { boards.set(currentBoardIndex, board); saveBoards(); }
    @Override public void showMessage(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    @Override protected void onPause() { super.onPause(); saveBoards(); }
    private void saveBoards() { repository.save(boards); }
}
