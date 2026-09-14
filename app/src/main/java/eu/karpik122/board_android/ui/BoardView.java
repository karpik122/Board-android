package eu.karpik122.board_android.ui;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;
import java.io.InputStream;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import eu.karpik122.board_android.model.*;

/**
 * Widok odpowiedzialny wyłącznie za tablicę: rysuje elementy, interpretuje gesty
 * oraz modyfikuje przekazany model. O zmianach informuje ekran przez Listener.
 */
public class BoardView extends View {
    /** Most między widokiem a aktywnością: zapis danych i komunikaty pozostają poza widokiem. */
    public interface Listener { void onBoardChanged(BoardData board); void showMessage(String text); }

    private static final int MAX_UNDO_STATES = 30;
    private static final int IMAGE_CACHE_KB = 32 * 1024;
    private static final int MAX_DECODED_IMAGE_EDGE = 2048;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Pamięć podręczna ogranicza liczbę pełnych odczytów obrazu przy każdym odrysowaniu tablicy. */
    private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(IMAGE_CACHE_KB) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount() / 1024; }
    };
    private final List<String> undoSnapshots = new ArrayList<>();
    private final Listener listener;
    private BoardData board;
    private BoardItem selectedItem;
    private float offsetX, offsetY, zoom = 1f;
    private float lastTouchX, lastTouchY, pinchDistance, pinchStartZoom;
    private boolean panning;
    private boolean resizing;
    private boolean dragUndoSaved;
    private boolean connecting;
    private String connectionSourceId;

    public BoardView(Context context, BoardData board, Listener listener) {
        super(context);
        this.board = board;
        this.listener = listener;
        paint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        setBackgroundColor(Color.rgb(22, 25, 32));
    }

    /** Ustawia inną tablicę i resetuje widok, ale nie zmienia danych pozostałych tablic. */
    public void setBoard(BoardData board) {
        this.board = board; selectedItem = null; offsetX = 0; offsetY = 0; zoom = 1f;
        undoSnapshots.clear(); connecting = false; invalidate();
    }

    public BoardItem getSelectedItem() { return selectedItem; }

    /** Dodaje notatkę pośrodku obecnie widocznego fragmentu tablicy. */
    public void addNote() { saveUndoState(); selectedItem = new NoteItem("Nowa karteczka", visibleCenterX(), visibleCenterY()); board.getItems().add(selectedItem); notifyChanged(); }
    /** Dodaje blok schematu pośrodku obecnie widocznego fragmentu tablicy. */
    public void addBlock() { saveUndoState(); selectedItem = new BlockItem("Nowy blok", visibleCenterX(), visibleCenterY()); board.getItems().add(selectedItem); notifyChanged(); }

    /** Dodaje zdjęcie jako osobny element; jego wymiary są dostosowane do oryginalnych proporcji. */
    public void addImage(String uri) {
        saveUndoState();
        Bitmap bitmap = loadImage(uri);
        float width = 280, height = 210;
        if (bitmap != null && bitmap.getWidth() > 0) height = width * bitmap.getHeight() / bitmap.getWidth();
        selectedItem = new ImageItem(uri, visibleCenterX(), visibleCenterY(), width, height);
        board.getItems().add(selectedItem);
        notifyChanged();
    }

    /** Zapisuje zmianę tekstu aktualnie wybranego elementu. */
    public void updateSelectedText(String text) {
        if (selectedItem == null) return;
        saveUndoState(); selectedItem.setText(text); notifyChanged();
    }

    /** Usuwa wybrany element oraz wszystkie linie prowadzące do tego elementu. */
    public void deleteSelected() {
        if (selectedItem == null) { listener.showMessage("Zaznacz element do usunięcia."); return; }
        saveUndoState(); String removedId = selectedItem.getId(); board.getItems().remove(selectedItem);
        board.getConnections().removeIf(connection -> connection.getFromId().equals(removedId) || connection.getToId().equals(removedId));
        selectedItem = null; notifyChanged();
    }

    /** Rozpoczyna tworzenie linii; drugi blok użytkownik wybiera dotknięciem. */
    public void beginConnection() {
        if (!(selectedItem instanceof BlockItem)) { listener.showMessage("Zaznacz pierwszy blok, potem wybierz „Połącz”."); return; }
        connecting = true; connectionSourceId = selectedItem.getId(); listener.showMessage("Dotknij drugiego bloku, aby utworzyć połączenie.");
    }

    /** Przywraca stan modelu sprzed ostatniej mutacji. */
    public void undo() {
        if (undoSnapshots.isEmpty()) { listener.showMessage("Nie ma już zmian do cofnięcia."); return; }
        try {
            board = BoardData.fromJson(new JSONObject(undoSnapshots.remove(undoSnapshots.size() - 1)));
            selectedItem = null; listener.onBoardChanged(board); invalidate();
        } catch (Exception ignored) { listener.showMessage("Nie udało się cofnąć zmiany."); }
    }

    /** Rysuje siatkę, połączenia, a na końcu elementy (dzięki temu są nad liniami). */
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.save(); canvas.translate(offsetX, offsetY); canvas.scale(zoom, zoom);
        drawGrid(canvas);
        for (Connection connection : board.getConnections()) drawConnection(canvas, connection);
        for (BoardItem item : board.getItems()) drawItem(canvas, item);
        canvas.restore();
    }

    /** Subtelna siatka ułatwia wizualne rozmieszczanie elementów. */
    private void drawGrid(Canvas canvas) {
        paint.setColor(Color.rgb(35, 40, 50)); paint.setStrokeWidth(1f / zoom);
        for (int x = -2000; x < 4000; x += 80) canvas.drawLine(x, -2000, x, 4000, paint);
        for (int y = -2000; y < 4000; y += 80) canvas.drawLine(-2000, y, 4000, y, paint);
    }

    /** Rysuje linię między środkami dwóch bloków powiązanych połączeniem. */
    private void drawConnection(Canvas canvas, Connection connection) {
        BoardItem from = board.findItem(connection.getFromId()), to = board.findItem(connection.getToId());
        if (from == null || to == null) return;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(Color.rgb(126, 145, 190));
        canvas.drawLine(centerX(from), centerY(from), centerX(to), centerY(to), paint); paint.setStyle(Paint.Style.FILL);
    }

    /** Rysuje odpowiednią reprezentację elementu: tekstowy blok albo pełnej jakości obraz. */
    private void drawItem(Canvas canvas, BoardItem item) {
        RectF rect = new RectF(item.getX(), item.getY(), item.getX() + item.getWidth(), item.getY() + item.getHeight());
        if (item instanceof ImageItem) {
            drawImageItem(canvas, (ImageItem) item, rect);
            if (item == selectedItem) drawSelection(canvas, rect);
            return;
        }
        paint.setColor(item instanceof NoteItem ? Color.rgb(255, 196, 79) : Color.rgb(48, 97, 174)); canvas.drawRoundRect(rect, 18, 18, paint);
        if (item == selectedItem) drawSelection(canvas, rect);
        paint.setColor(Color.WHITE); paint.setTextSize(17); paint.setTypeface(Typeface.create("sans", Typeface.BOLD));
        drawMultilineText(canvas, item.getText(), item.getX() + 14, item.getY() + 29, item.getWidth() - 28, 21);
    }

    /** Rysuje obraz z filtrowaniem; nie używa miniatur systemowych, więc zachowuje szczegóły. */
    private void drawImageItem(Canvas canvas, ImageItem item, RectF rect) {
        Bitmap bitmap = loadImage(item.getUri());
        if (bitmap == null) {
            paint.setColor(Color.rgb(67, 72, 84)); canvas.drawRoundRect(rect, 12, 12, paint);
            paint.setColor(Color.WHITE); paint.setTextSize(14); canvas.drawText("Nie można wczytać zdjęcia", item.getX() + 14, item.getY() + 30, paint);
            return;
        }
        paint.setFilterBitmap(true); paint.setDither(true);
        canvas.drawBitmap(bitmap, null, rect, paint);
    }

    /** Zaznaczenie pokazuje ramkę i uchwyt, za który można przeciągać w celu skalowania. */
    private void drawSelection(Canvas canvas, RectF rect) {
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(Color.WHITE); canvas.drawRoundRect(rect, 12, 12, paint); paint.setStyle(Paint.Style.FILL);
        float size = 14; paint.setColor(Color.WHITE); canvas.drawRect(rect.right - size, rect.bottom - size, rect.right + 2, rect.bottom + 2, paint);
    }

    /** Łamie tekst na wiersze, aby mieścił się w szerokości elementu. */
    private void drawMultilineText(Canvas canvas, String text, float x, float y, float maxWidth, float lineHeight) {
        StringBuilder line = new StringBuilder(); int row = 0;
        for (String word : text.split("\\s+")) {
            if (paint.measureText(line + word) > maxWidth && line.length() > 0) { canvas.drawText(line.toString(), x, y + row++ * lineHeight, paint); line.setLength(0); }
            line.append(word).append(' ');
        }
        if (line.length() > 0) canvas.drawText(line.toString(), x, y + row * lineHeight, paint);
    }

    /** Obsługuje przeciąganie elementu, panoramę pustej przestrzeni i gest szczypania. */
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            lastTouchX = event.getX(); lastTouchY = event.getY(); BoardItem hit = itemAt(boardX(lastTouchX), boardY(lastTouchY));
            if (connecting && hit instanceof BlockItem && !hit.getId().equals(connectionSourceId)) {
                saveUndoState(); board.getConnections().add(new Connection(connectionSourceId, hit.getId())); connecting = false; selectedItem = hit; notifyChanged(); return true;
            }
            selectedItem = hit; panning = hit == null;
            resizing = hit != null && isOnResizeHandle(hit, boardX(lastTouchX), boardY(lastTouchY));
            invalidate(); return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() == 2) { pinchDistance = fingerDistance(event); pinchStartZoom = zoom; return true; }
        if (action == MotionEvent.ACTION_MOVE) {
            if (event.getPointerCount() == 2) { zoomAroundFingers(event); return true; }
            float dx = event.getX() - lastTouchX, dy = event.getY() - lastTouchY;
            if (selectedItem != null && resizing) {
                if (!dragUndoSaved) { saveUndoState(); dragUndoSaved = true; }
                resizeSelected(dx / zoom, dy / zoom);
            } else if (selectedItem != null && !panning) {
                // Jedna seria ruchu stanowi jedną zmianę możliwą do cofnięcia.
                if (!dragUndoSaved) { saveUndoState(); dragUndoSaved = true; }
                selectedItem.moveBy(dx / zoom, dy / zoom);
            } else { offsetX += dx; offsetY += dy; }
            lastTouchX = event.getX(); lastTouchY = event.getY(); invalidate(); return true;
        }
        if (action == MotionEvent.ACTION_UP) { if (selectedItem != null && !panning) listener.onBoardChanged(board); pinchDistance = 0; dragUndoSaved = false; resizing = false; return true; }
        return true;
    }

    /** Utrzymuje punkt między palcami w miejscu podczas przybliżania. */
    private void zoomAroundFingers(MotionEvent event) {
        if (pinchDistance <= 0) return;
        float newZoom = Math.max(.35f, Math.min(3f, pinchStartZoom * fingerDistance(event) / pinchDistance));
        float factor = newZoom / zoom; offsetX = event.getX(0) - (event.getX(0) - offsetX) * factor; offsetY = event.getY(0) - (event.getY(0) - offsetY) * factor;
        zoom = newZoom; invalidate();
    }

    /** Zapisuje JSON bieżącej tablicy przed zmianą, aby umożliwić cofnięcie. */
    private void saveUndoState() {
        try { undoSnapshots.add(board.toJson().toString()); if (undoSnapshots.size() > MAX_UNDO_STATES) undoSnapshots.remove(0); } catch (Exception ignored) { }
    }
    private void notifyChanged() { listener.onBoardChanged(board); invalidate(); }
    /** W przypadku zdjęcia zachowuje proporcje, a dla tekstowych elementów zmienia oba wymiary. */
    private void resizeSelected(float dx, float dy) {
        if (selectedItem instanceof ImageItem) ((ImageItem) selectedItem).resizeKeepingAspect(selectedItem.getWidth() + dx);
        else selectedItem.resizeTo(selectedItem.getWidth() + dx, selectedItem.getHeight() + dy);
        invalidate();
    }
    /** Uchwyt ma większy obszar dotyku niż sam kwadrat, aby wygodnie używało się go palcem. */
    private boolean isOnResizeHandle(BoardItem item, float x, float y) {
        float touchArea = 28;
        return x >= item.getX() + item.getWidth() - touchArea && y >= item.getY() + item.getHeight() - touchArea;
    }
    private BoardItem itemAt(float x, float y) { for (int i = board.getItems().size() - 1; i >= 0; i--) if (board.getItems().get(i).contains(x, y)) return board.getItems().get(i); return null; }
    private float visibleCenterX() { return (getWidth() / 2f - offsetX) / zoom - 90; }
    private float visibleCenterY() { return (getHeight() / 2f - offsetY) / zoom - 55; }
    private float boardX(float screenX) { return (screenX - offsetX) / zoom; }
    private float boardY(float screenY) { return (screenY - offsetY) / zoom; }
    private float centerX(BoardItem item) { return item.getX() + item.getWidth() / 2f; }
    private float centerY(BoardItem item) { return item.getY() + item.getHeight() / 2f; }
    private float fingerDistance(MotionEvent event) { float dx = event.getX(0) - event.getX(1), dy = event.getY(0) - event.getY(1); return (float) Math.sqrt(dx * dx + dy * dy); }

    /**
     * Wczytuje obraz w jakości wystarczającej do powiększania, ale ogranicza ekstremalnie
     * duże pliki do 2048 px na dłuższym boku, aby nie wyczerpać pamięci telefonu.
     */
    private Bitmap loadImage(String uri) {
        Bitmap cached = imageCache.get(uri);
        if (cached != null) return cached;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            try (InputStream stream = getContext().getContentResolver().openInputStream(Uri.parse(uri))) { BitmapFactory.decodeStream(stream, null, bounds); }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sampleSize = 1;
            while (Math.max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > MAX_DECODED_IMAGE_EDGE) sampleSize *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sampleSize; options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap;
            try (InputStream stream = getContext().getContentResolver().openInputStream(Uri.parse(uri))) { bitmap = BitmapFactory.decodeStream(stream, null, options); }
            if (bitmap != null) imageCache.put(uri, bitmap);
            return bitmap;
        } catch (Exception ignored) { return null; }
    }
}
