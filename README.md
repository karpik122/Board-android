# Wirtualna Tablica

Prosta aplikacja Android do organizowania notatek, bloków, zdjęć i połączeń na dużej, lokalnej tablicy.

## Co zawiera pierwsza wersja

- płynne przesuwanie tablicy i powiększanie gestem dwóch palców,
- dodawanie karteczek i bloków z tekstem,
- edycję, przeciąganie, usuwanie oraz cofanie ostatniej zmiany,
- dodawanie zdjęć jako niezależnych elementów tablicy,
- przesuwanie i skalowanie zdjęć bez obniżania ich jakości,
- łączenie bloków liniami,
- wiele niezależnych tablic,
- automatyczny zapis na urządzeniu (bez konta i bez internetu),
- ciemny interfejs po polsku.

## Struktura projektu

Kod jest podzielony według odpowiedzialności, aby rozwijanie aplikacji nie wymagało pracy w jednym dużym pliku:

```text
app/src/main/java/eu/karpik122/board_android/
├── MainActivity.java              # ekran, przyciski, okna dialogowe i wybór zdjęć
├── data/
│   └── BoardRepository.java       # lokalny zapis oraz odczyt danych z telefonu
├── model/
│   ├── BoardData.java             # komplet danych jednej tablicy
│   ├── BoardItem.java             # wspólna baza elementów na tablicy
│   ├── NoteItem.java              # model żółtej karteczki
│   ├── BlockItem.java             # model bloku tekstowego do schematów
│   ├── ImageItem.java             # niezależne zdjęcie: pozycja, rozmiar i źródło pliku
│   └── Connection.java            # połączenie dwóch bloków
└── ui/
    └── BoardView.java             # rysowanie tablicy oraz obsługa gestów
```

Każda klasa ma komentarz nad deklaracją oraz przy ważniejszych metodach. Komentarze opisują *dlaczego* dana część istnieje i za co odpowiada, a nie tylko powtarzają nazwę kodu.

### Przepływ danych

1. `MainActivity` odbiera kliknięcie użytkownika.
2. `BoardView` zmienia model w katalogu `model` i odświeża rysunek.
3. `BoardRepository` automatycznie zapisuje całą tablicę lokalnie jako JSON.
4. Przy kolejnym uruchomieniu aplikacji `BoardRepository` odtwarza tablice.

## Uruchomienie w Android Studio

1. Otwórz w Android Studio folder `\Boardandroid`.
2. Przy pierwszym otwarciu zaakceptuj pobranie komponentów Gradle i Android SDK, jeśli program o to poprosi.
3. Wybierz emulator lub telefon z Androidem 13 (API 33) albo nowszym.
4. Kliknij **Run**.

## Wygenerowanie APK

W Android Studio wybierz `Build` → `Build APK(s)`. Plik debug APK znajdziesz potem w:

`app/build/outputs/apk/debug/app-debug.apk`

## Obsługa

- Dotknij elementu, aby go zaznaczyć, a następnie przeciągnij, aby go przesunąć.
- Dotknij pustego miejsca i przeciągnij, aby przesunąć widok tablicy.
- Użyj dwóch palców do przybliżania i oddalania.
- Aby dodać zdjęcie, wybierz **Zdjęcie** i wskaż plik. Obraz pojawi się jako osobny element tablicy.
- Przeciągnij zdjęcie, aby zmienić jego pozycję. Zaznacz je i przeciągnij biały uchwyt w prawym dolnym rogu, aby zmienić rozmiar z zachowaniem proporcji.
- Aby narysować relację, zaznacz pierwszy blok, wybierz **Połącz**, a następnie dotknij drugiego bloku.
