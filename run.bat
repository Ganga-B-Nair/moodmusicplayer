@echo off
rem Initialize SQLite database path
if not exist "data" mkdir data

rem Configure and compile with all required modules
"C:\jdk 25\bin\javac.exe" --module-path "C:\javafx-sdk-25.0.1\lib" --add-modules javafx.controls,javafx.media,javafx.fxml -cp ".;lib\sqlite-jdbc-3.50.3.0.jar" MoodMusicPlayer.java

rem Run with all required modules and SQLite configuration
"C:\jdk 25\bin\java.exe" --enable-native-access=javafx.graphics --enable-native-access=ALL-UNNAMED --module-path "C:\javafx-sdk-25.0.1\lib" --add-modules javafx.controls,javafx.media,javafx.fxml -cp ".;lib\sqlite-jdbc-3.50.3.0.jar" MoodMusicPlayer