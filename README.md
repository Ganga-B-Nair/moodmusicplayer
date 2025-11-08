# 🎵 Mood Music Player

[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=java&logoColor=white)](https://www.oracle.com/java/)
[![JavaFX](https://img.shields.io/badge/JavaFX-UI_Framework-blue?logo=openjdk&logoColor=white)](https://openjfx.io/)
[![SQLite](https://img.shields.io/badge/SQLite-Database-003B57?logo=sqlite&logoColor=white)](https://sqlite.org)
[![Made with Love](https://img.shields.io/badge/Made%20with-❤️-ff69b4.svg)]()
[![License](https://img.shields.io/badge/License-Free-brightgreen.svg)]()

---

A **JavaFX desktop application** that creates and manages **mood-based music playlists** with a clean, glass-style interface inspired by Spotify and Notion.

---

## 🌟 Overview

The **Mood Music Player** helps users organize and explore music according to their current mood.  
It combines a **modern aesthetic UI** with persistent local storage powered by **SQLite**.  
Users can add songs, assign moods, and auto-generate playlists for specific emotions like *Happy*, *Sad*, *Calm*, *Energetic*, or *Focus*.

---

## ✨ Features

- 🎧 **Mood-based song management**  
  Add and browse songs tagged by mood, artist, and title.

- 🗂️ **Playlist creation & auto-generation**  
  Manually build playlists or auto-generate one for a specific mood.

- 🪩 **Glassmorphism UI**  
  Smooth translucent interface with color palette:  
  - `#000000` (black background)  
  - `#1B263B` (deep blue base)  
  - `#E0E1DD` (soft white text)  
  Uses the *Poppins* font for a clean and modern appearance.

- 🗃️ **SQLite database**  
  Stores all songs and playlists locally (`mood_music.db`).

- ▶️ **Simulated playback bar**  
  Displays “Now Playing” song with mood and artist.

---

## 🧠 Tech Stack

| Component | Technology |
|------------|-------------|
| Programming Language | Java (JDK 17+) |
| UI Framework | JavaFX 21 |
| Database | SQLite (JDBC driver) |
| Font | Poppins |
| IDE | Visual Studio Code |

---

## ⚙️ Setup & Run

### 🪄 Requirements
- Java JDK 17 or later  
- JavaFX SDK (21+)  
- SQLite JDBC Driver (JAR)

---

### 🧩 Folder Structure

mood-music/
├─ lib/
│ └─ sqlite-jdbc-3.46.0.0.jar
├─ resources/
│ └─ Poppins-Regular.ttf
├─ screenshots/ <-- Add screenshots here
├─ MoodMusicPlayer.java
└─ out/

---

### 🖥️ Compile

Windows:
```bash
mkdir out
javac --module-path "C:\javafx-sdk-21\lib" --add-modules javafx.controls,javafx.fxml ^
-cp "lib\sqlite-jdbc-3.46.0.0.jar" -d out MoodMusicPlayer.java

macOS / Linux:

mkdir out
javac --module-path "/path/to/javafx-sdk-21/lib" --add-modules javafx.controls,javafx.fxml \
-cp "lib/sqlite-jdbc-3.46.0.0.jar" -d out MoodMusicPlayer.java

▶️ Run

Windows:

java --module-path "C:\javafx-sdk-21\lib" --add-modules javafx.controls,javafx.fxml ^
-cp "out;lib\sqlite-jdbc-3.46.0.0.jar" MoodMusicPlayer


macOS / Linux:

java --module-path "/path/to/javafx-sdk-21/lib" --add-modules javafx.controls,javafx.fxml \
-cp "out:lib/sqlite-jdbc-3.46.0.0.jar" MoodMusicPlayer
📦 Database Schema

Tables automatically created on first run:

CREATE TABLE songs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT,
  artist TEXT,
  mood TEXT,
  path TEXT
);

CREATE TABLE playlists (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT UNIQUE
);

CREATE TABLE playlist_songs (
  playlist_id INTEGER,
  song_id INTEGER,
  PRIMARY KEY (playlist_id, song_id),
  FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
  FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE
);

🧑‍💻 Developer

Ganga B. Nair
🎶 Building creative tools that make technology more human.

📜 License

This project is open-source and free to use for educational and personal purposes.
Feel free to fork, modify, and build upon it.

