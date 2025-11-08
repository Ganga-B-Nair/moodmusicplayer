/*
 * MoodMusicPlayer.java
 * A single-file JavaFX application that demonstrates a minimal mood-based music
 * playlist manager with SQLite database connectivity.
 *
 * Notes:
 * - This is a compact single-file example for learning/demo. In a real project
 *   you'd split code across multiple classes/files and resources.
 * - The UI uses JavaFX and a minimal "glassmorphism" look via inline styles.
 * - Database: SQLite (jdbc:sqlite:mood_music.db). The app will create tables
 *   and seed sample data on first run.
 * - The app does NOT play real audio files; instead it simulates playback and
 *   stores file paths for future extension.
 *
 * To compile/run see the instructions that accompany this file.
 */

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import static javafx.beans.binding.Bindings.createBooleanBinding;
import static javafx.beans.binding.Bindings.when;
import javafx.scene.Node;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import javafx.stage.FileChooser;
import java.io.File;
import javafx.stage.Modality;
import javafx.util.Pair;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import javafx.util.Pair;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonBar;
import javafx.scene.layout.GridPane;

public class MoodMusicPlayer extends Application {

    // DB file
    private static final String DB_FILE = "data/moodmusic.db";
    private DBHelper db;
    
    // User credentials
    private boolean isAdmin;
    private MediaPlayer mediaPlayer;
    
    // Mood emojis
    private static final Map<String, String> MOOD_EMOJIS;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("Happy", "😊");
        map.put("Sad", "😢");
        map.put("Energetic", "⚡");
        map.put("Calm", "😌");
        map.put("Focus", "🎯");
        MOOD_EMOJIS = Collections.unmodifiableMap(map);
    }
    
    public void setUserCredentials(String username, boolean isAdmin) {
        this.isAdmin = isAdmin;
    }
    
    private void showLoginDialog(Stage primaryStage) {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Login to Mood Music");
        dialog.setHeaderText("Please enter your credentials");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.getDialogPane().setStyle("-fx-background-color: #1B263B;");

        // Set the button types
        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        // Create the username and password labels and fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        grid.setStyle("-fx-background-color: #1B263B;");

        TextField username = new TextField();
        username.setPromptText("Username");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(username, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(password, 1, 1);

        // Style the text fields and labels
        username.setStyle("-fx-text-fill: white; -fx-background-color: #415A77;");
        password.setStyle("-fx-text-fill: white; -fx-background-color: #415A77;");
        grid.getChildren().filtered(node -> node instanceof Label)
            .forEach(node -> ((Label) node).setTextFill(Color.WHITE));

        // Enable/Disable login button depending on whether a username was entered
        Node loginButton = dialog.getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);

        // Do some validation
        username.textProperty().addListener((observable, oldValue, newValue) -> 
            loginButton.setDisable(newValue.trim().isEmpty() || password.getText().trim().isEmpty()));
        password.textProperty().addListener((observable, oldValue, newValue) -> 
            loginButton.setDisable(username.getText().trim().isEmpty() || newValue.trim().isEmpty()));

        dialog.getDialogPane().setContent(grid);

        // Convert the result to a username-password-pair when the login button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new Pair<>(username.getText(), password.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> result = dialog.showAndWait();

        result.ifPresent(usernamePassword -> {
            try {
                PreparedStatement ps = db.getConnection().prepareStatement(
                    "SELECT username, is_admin FROM users WHERE username = ? AND password = ?");
                ps.setString(1, usernamePassword.getKey());
                ps.setString(2, usernamePassword.getValue());
                
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    setUserCredentials(rs.getString("username"), rs.getBoolean("is_admin"));
                    initializeMainWindow(primaryStage);
                } else {
                    showAlert("Invalid username or password");
                    showLoginDialog(primaryStage); // Show login dialog again
                }
            } catch (SQLException e) {
                e.printStackTrace();
                showAlert("Database error: " + e.getMessage());
            }
        });

        // If the user closes the dialog without logging in, exit the application
        if (!result.isPresent()) {
            Platform.exit();
        }
    }
    
    private void initializeMainWindow(Stage primaryStage) {
        loadFontIfPresent();
        primaryStage.setTitle("Mood Music — Minimal Player");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: linear-gradient(#000000, #1B263B); padding: 20;");

        // Top: Header
        HBox header = buildHeader();
        root.setTop(header);

        // Center: main content split
        HBox center = new HBox(20);
        center.setPadding(new Insets(20));

        VBox left = buildLeftPane();
        VBox right = buildRightPane();

        center.getChildren().addAll(left, right);
        root.setCenter(center);

        // Bottom: now playing bar
        HBox bottom = buildNowPlayingBar();
        root.setBottom(bottom);

        Scene scene = new Scene(root, 1000, 640);
        primaryStage.setScene(scene);
        primaryStage.show();

        refreshSongList();
    }

    // UI elements
    private TableView<Song> songTable;
    private TableView<Song> playlistTable;
    private ComboBox<String> moodFilter;
    private Label nowPlayingLabel;
    private ObservableList<Song> allSongs;
    private ObservableList<Song> playlistSongs;

    // Colors requested: #000000, #1B263B, #E0E1DD

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Ensure data directory exists
        File dbFile = new File(DB_FILE);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        // Initialize database connection
        db = new DBHelper(DB_FILE);
        db.initAndSeed();
        
        // Create default admin user if not exists
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                "CREATE TABLE IF NOT EXISTS users (username TEXT PRIMARY KEY, password TEXT NOT NULL, is_admin INTEGER DEFAULT 0)");
            ps.executeUpdate();
            
            // Check if admin user exists
            ps = db.getConnection().prepareStatement("SELECT COUNT(*) as count FROM users WHERE username = ?");
            ps.setString(1, "admin");
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt("count") == 0) {
                // Create default admin user
                ps = db.getConnection().prepareStatement("INSERT INTO users (username, password, is_admin) VALUES (?, ?, 1)");
                ps.setString(1, "admin");
                ps.setString(2, "admin");
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error creating admin user: " + e.getMessage());
            e.printStackTrace();
        }
        
        showLoginDialog(primaryStage);
        
        try {
            db.initAndSeed();
        } catch (Exception e) {
            System.err.println("Database initialization error: " + e.getMessage());
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("Failed to initialize database");
            alert.setContentText("Please make sure the application has write permissions to: " + DB_FILE);
            alert.showAndWait();
            return;
        }

        loadFontIfPresent();
        primaryStage.setTitle("Mood Music — Minimal Player");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: linear-gradient(#000000, #1B263B); padding: 20;");

        // Top: Header
        HBox header = buildHeader();
        root.setTop(header);

        // Center: main content split
        HBox center = new HBox(20);
        center.setPadding(new Insets(20));

        VBox left = buildLeftPane();
        VBox right = buildRightPane();

        center.getChildren().addAll(left, right);
        root.setCenter(center);

        // Bottom: now playing bar
        HBox bottom = buildNowPlayingBar();
        root.setBottom(bottom);

        Scene scene = new Scene(root, 1000, 640);
        primaryStage.setScene(scene);
        primaryStage.show();

        refreshSongList();
    }

    private void loadFontIfPresent() {
        try {
            File f = new File("resources/Poppins-Regular.ttf");
            if (f.exists()) {
                Font.loadFont(f.toURI().toString(), 12);
            }
        } catch (Exception ignored) {
        }
    }

    private HBox buildHeader() {
        HBox header = new HBox();
        header.setPadding(new Insets(12));
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Mood Music");
        title.setTextFill(Color.web("#E0E1DD"));
        title.setStyle("-fx-font-family: 'Poppins', system; -fx-font-size: 26px; -fx-font-weight: 700;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(title, spacer);
        header.setStyle(makeGlassStyle(12));
        return header;
    }

    private VBox buildLeftPane() {
        VBox left = new VBox(12);
        left.setPrefWidth(560);

        // Controls row
        HBox controls = new HBox(8);
        controls.setAlignment(Pos.CENTER_LEFT);

        moodFilter = new ComboBox<>();
        moodFilter.setPromptText("Filter by mood");
        moodFilter.getItems().addAll("All", "Happy", "Sad", "Energetic", "Calm", "Focus");
        moodFilter.setValue("All");
        moodFilter.valueProperty().addListener((obs, oldv, newv) -> refreshSongList());

        Button load = new Button("Refresh");
        load.setOnAction(e -> refreshSongList());

        Button addSong = new Button("Add Song");
        addSong.setOnAction(e -> showAddSongDialog());

        controls.getChildren().addAll(moodFilter, load, addSong);

        songTable = new TableView<>();
        songTable.setPlaceholder(new Label("No songs found"));
        TableColumn<Song, Integer> idCol = new TableColumn<>("#");
        idCol.setPrefWidth(40);
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<Song, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(220);

        TableColumn<Song, String> artistCol = new TableColumn<>("Artist");
        artistCol.setCellValueFactory(new PropertyValueFactory<>("artist"));
        artistCol.setPrefWidth(160);

        TableColumn<Song, String> moodCol = new TableColumn<>("Mood");
        moodCol.setCellValueFactory(new PropertyValueFactory<>("mood"));
        moodCol.setPrefWidth(120);

        songTable.getColumns().addAll(idCol, titleCol, artistCol, moodCol);
        // Add action column for edit/delete
        TableColumn<Song, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(100);
        actionCol.setCellFactory(col -> new TableCell<Song, Void>() {
            private final Button editBtn = new Button("✏");
            private final Button deleteBtn = new Button("🗑");
            private final HBox buttons = new HBox(5, editBtn, deleteBtn);
            
            {
                // Style buttons
                editBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #E0E1DD;");
                deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #E0E1DD;");
                buttons.setAlignment(Pos.CENTER);
                
                // Add button actions
                editBtn.setOnAction(e -> {
                    Song song = (MoodMusicPlayer.Song) getTableRow().getItem();
                    if (song != null && isAdmin) {
                        showEditSongDialog(song);
                    }
                });
                
                deleteBtn.setOnAction(e -> {
                    Song song = (MoodMusicPlayer.Song) getTableRow().getItem();
                    if (song != null && isAdmin) {
                        if (showConfirmDialog("Delete Song", 
                            "Are you sure you want to delete '" + song.getTitle() + "'?")) {
                            deleteSong(song);
                        }
                    }
                });
                
                // Only show buttons for admin users
                editBtn.visibleProperty().bind(createBooleanBinding(() -> isAdmin));
                deleteBtn.visibleProperty().bind(createBooleanBinding(() -> isAdmin));
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttons);
            }
        });
        
        songTable.getColumns().add(actionCol);
        
        // Add double-click to play and context menu
        songTable.setRowFactory(tv -> {
            TableRow<Song> row = new TableRow<>();
            
            // Context menu for right-click
            ContextMenu contextMenu = new ContextMenu();
            MenuItem playItem = new MenuItem("Play");
            MenuItem editItem = new MenuItem("Edit");
            MenuItem deleteItem = new MenuItem("Delete");
            
            playItem.setOnAction(e -> {
                Song s = row.getItem();
                if (s != null) playSong(s);
            });
            
            editItem.setOnAction(e -> {
                Song s = row.getItem();
                if (s != null && isAdmin) showEditSongDialog(s);
            });
            
            deleteItem.setOnAction(e -> {
                Song s = row.getItem();
                if (s != null && isAdmin && showConfirmDialog("Delete Song", 
                    "Are you sure you want to delete '" + s.getTitle() + "'?")) {
                    deleteSong(s);
                }
            });
            
            contextMenu.getItems().addAll(playItem, new SeparatorMenuItem(), editItem, deleteItem);
            
            // Only show edit/delete for admin
            editItem.visibleProperty().bind(createBooleanBinding(() -> isAdmin));
            deleteItem.visibleProperty().bind(createBooleanBinding(() -> isAdmin));
            
            row.contextMenuProperty().bind(
                when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(contextMenu)
            );
            
            // Double-click to play
            row.setOnMouseClicked(evt -> {
                if (!row.isEmpty() && evt.getClickCount() == 2) {
                    playSong(row.getItem());
                }
            });
            
            return row;
        });

        left.getChildren().addAll(controls, songTable);
        left.setStyle(makeGlassStyle(14));
        left.setPadding(new Insets(16));
        return left;
    }

    private VBox buildRightPane() {
        VBox right = new VBox(12);
        right.setPrefWidth(360);

        // Playlist controls
        HBox pcontrols = new HBox(8);
        pcontrols.setAlignment(Pos.CENTER_LEFT);

        TextField playlistName = new TextField();
        playlistName.setPromptText("New playlist name");
        Button createPlaylist = new Button("Create");
        createPlaylist.setOnAction(e -> {
            String name = playlistName.getText().trim();
            if (!name.isEmpty()) {
                db.createPlaylist(name);
                playlistName.clear();
                refreshPlaylistView(null);
            }
        });

        pcontrols.getChildren().addAll(playlistName, createPlaylist);

        // Playlist table
        playlistTable = new TableView<>();
        TableColumn<Song, Integer> pId = new TableColumn<>("#");
        pId.setCellValueFactory(new PropertyValueFactory<>("id"));
        pId.setPrefWidth(40);

        TableColumn<Song, String> pTitle = new TableColumn<>("Title");
        pTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        pTitle.setPrefWidth(200);

        TableColumn<Song, String> pMood = new TableColumn<>("Mood");
        pMood.setCellValueFactory(new PropertyValueFactory<>("mood"));
        pMood.setPrefWidth(100);

        playlistTable.getColumns().addAll(pId, pTitle, pMood);

        // Playlist selector
        ComboBox<String> playlistSelector = new ComboBox<>();
        playlistSelector.setPromptText("Select playlist");
        refreshPlaylistView(playlistSelector);

        Button addToPlaylist = new Button("Add selected song");
        addToPlaylist.setOnAction(e -> {
            Song s = songTable.getSelectionModel().getSelectedItem();
            String pl = playlistSelector.getValue();
            if (s != null && pl != null) {
                db.addSongToPlaylistByName(pl, s.getId());
                refreshPlaylistView(playlistSelector);
            }
        });

        // Create a mood selector for playlist generation
        ComboBox<String> moodForPlaylist = new ComboBox<>();
        moodForPlaylist.setPromptText("Select mood for playlist");
        moodForPlaylist.getItems().addAll("Happy", "Sad", "Energetic", "Calm", "Focus");
        
        Button generateMood = new Button("Generate mood playlist");
        generateMood.setOnAction(e -> {
            String mood = moodForPlaylist.getValue();
            if (mood == null) {
                showAlert("Please select a mood first");
                return;
            }
            
            // Create a unique name with timestamp
            String timestamp = String.format("%1$tY%1$tm%1$td_%1$tH%1$tM", new java.util.Date());
            String newName = mood + "_playlist_" + timestamp;
            
            // Create playlist and add songs
            int pid = db.createPlaylist(newName);
            List<Integer> ids = db.findSongIdsByMood(mood);
            
            if (ids.isEmpty()) {
                showAlert("No songs found for mood: " + mood);
                return;
            }
            
            // Add all matching songs to playlist
            for (int id: ids) {
                db.addSongToPlaylist(pid, id);
            }
            
            // Refresh view and select new playlist
            refreshPlaylistView(playlistSelector);
            playlistSelector.setValue(newName);
            
            showAlert("Created playlist with " + ids.size() + " " + mood + " songs!");
            moodForPlaylist.setValue(null); // Reset selection
        });
        
        // Layout for mood playlist generation
        HBox moodPlaylistControls = new HBox(8);
        moodPlaylistControls.setAlignment(Pos.CENTER_LEFT);
        moodPlaylistControls.getChildren().addAll(moodForPlaylist, generateMood);

        right.getChildren().addAll(pcontrols, playlistSelector, addToPlaylist, moodPlaylistControls, playlistTable);
        right.setStyle(makeGlassStyle(14));
        right.setPadding(new Insets(16));
        return right;
    }

    private HBox buildNowPlayingBar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(12));
        bar.setAlignment(Pos.CENTER);
        bar.setStyle(makeGlassStyle(8));

        // Left section - Song info and mood
        VBox songInfo = new VBox(4);
        songInfo.setAlignment(Pos.CENTER_LEFT);
        
        nowPlayingLabel = new Label("Not playing");
        nowPlayingLabel.setStyle("-fx-font-family: 'Poppins', system; -fx-font-size: 14px; -fx-text-fill: #E0E1DD;");
        
        Label moodLabel = new Label("");
        moodLabel.setStyle("-fx-font-family: 'Poppins', system; -fx-font-size: 24px;");
        
        songInfo.getChildren().addAll(nowPlayingLabel, moodLabel);

        // Center section - Playback controls
        HBox controls = new HBox(20);
        controls.setAlignment(Pos.CENTER);
        
        Button prevButton = new Button("⏮");
        Button playButton = new Button("▶");
        Button nextButton = new Button("⏭");
        Button stopButton = new Button("⏹");
        
        Slider timeSlider = new Slider();
        timeSlider.setPrefWidth(300);
        timeSlider.setStyle("-fx-control-inner-background: #415A77;");
        
        Label timeLabel = new Label("0:00 / 0:00");
        timeLabel.setTextFill(Color.web("#E0E1DD"));
        
        controls.getChildren().addAll(prevButton, playButton, nextButton, stopButton, timeSlider, timeLabel);

        // Right section - Volume
        HBox volumeBox = new HBox(8);
        volumeBox.setAlignment(Pos.CENTER_RIGHT);
        
        Label volumeIcon = new Label("🔊");
        Slider volumeSlider = new Slider(0, 100, 100);
        volumeSlider.setPrefWidth(100);
        volumeSlider.setStyle("-fx-control-inner-background: #415A77;");
        
        volumeBox.getChildren().addAll(volumeIcon, volumeSlider);

        // Add all sections to the bar
        Region spacerLeft = new Region();
        Region spacerRight = new Region();
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);
        HBox.setHgrow(spacerRight, Priority.ALWAYS);
        
        bar.getChildren().addAll(songInfo, spacerLeft, controls, spacerRight, volumeBox);

        // Player controls setup
        playButton.setOnAction(e -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                    mediaPlayer.pause();
                    playButton.setText("▶");
                } else {
                    mediaPlayer.play();
                    playButton.setText("⏸");
                }
            }
        });

        stopButton.setOnAction(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                playButton.setText("▶");
                nowPlayingLabel.setText("Not playing");
                moodLabel.setText("");
            }
        });

        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newVal.doubleValue() / 100.0);
            }
        });

        return bar;
    }

    private String makeGlassStyle(int radius) {
        // Basic glass effect: semi-transparent background, blur and light border
        return String.join("",
                "-fx-background-color: rgba(224,225,221,0.08);",
                "-fx-background-insets: 0;",
                "-fx-background-radius: ", String.valueOf(radius), ";",
                "-fx-border-radius: ", String.valueOf(radius), ";",
                "-fx-border-color: rgba(224,225,221,0.12);",
                "-fx-border-width: 1;",
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 12, 0.1, 0, 2);"
        );
    }

    private void refreshSongList() {
        String mood = moodFilter.getValue();
        if (mood == null) mood = "All";
        List<Song> songs;
        if (mood.equals("All")) songs = db.getAllSongs();
        else songs = db.getSongsByMood(mood);

        allSongs = FXCollections.observableArrayList(songs);
        songTable.setItems(allSongs);
    }

    private void refreshPlaylistView(ComboBox<String> playlistSelector) {
        List<String> names = db.getAllPlaylistNames();
        if (playlistSelector != null) {
            // Repopulate selector items
            playlistSelector.getItems().clear();
            playlistSelector.getItems().addAll(names);

            // Add the listener only once (guard via properties map)
            if (playlistSelector.getProperties().get("listenerAdded") == null) {
                playlistSelector.valueProperty().addListener((obs, oldv, newv) -> {
                    if (newv != null) {
                        List<Song> songs = db.getSongsForPlaylist(newv);
                        playlistSongs = FXCollections.observableArrayList(songs);
                        playlistTable.setItems(playlistSongs);
                    } else {
                        playlistTable.setItems(FXCollections.observableArrayList());
                    }
                });
                playlistSelector.getProperties().put("listenerAdded", Boolean.TRUE);
            }

            // If there are playlists, select the first by default so the table shows content
            if (!names.isEmpty() && playlistSelector.getValue() == null) {
                playlistSelector.setValue(names.get(0));
            }
        }
        // if there's at least one playlist, populate the table with the first one
        if (!names.isEmpty() && playlistSelector == null) {
            List<Song> songs = db.getSongsForPlaylist(names.get(0));
            playlistSongs = FXCollections.observableArrayList(songs);
            playlistTable.setItems(playlistSongs);
        }
    }

    private void showAddSongDialog() {
        Dialog<Song> dialog = new Dialog<>();
        dialog.setTitle("Add Song");
        dialog.setHeaderText("Add a new song to your library");
        dialog.getDialogPane().setStyle("-fx-background-color: #1B263B;");

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.setStyle("-fx-background-color: #1B263B;");

        TextField title = new TextField();
        title.setPromptText("Song title");
        TextField artist = new TextField();
        artist.setPromptText("Artist");
        ComboBox<String> mood = new ComboBox<>();
        mood.getItems().addAll("Happy", "Sad", "Energetic", "Calm", "Focus");
        mood.setValue("Happy");
        
        TextField path = new TextField();
        path.setPromptText("Media file path or URL (e.g., http://...)");
        
        Button browseButton = new Button("Browse File");
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Media File");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.m4a"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File file = fileChooser.showOpenDialog(dialog.getOwner());
            if (file != null) {
                path.setText(file.getAbsolutePath());
            }
        });

        // Layout for path and browse button
        HBox pathBox = new HBox(5);
        pathBox.getChildren().addAll(path, browseButton);
        HBox.setHgrow(path, Priority.ALWAYS);

        grid.add(new Label("Title:"), 0, 0);
        grid.add(title, 1, 0);
        grid.add(new Label("Artist:"), 0, 1);
        grid.add(artist, 1, 1);
        grid.add(new Label("Mood:"), 0, 2);
        grid.add(mood, 1, 2);
        grid.add(new Label("Media:"), 0, 3);
        grid.add(pathBox, 1, 3);

        dialog.getDialogPane().setContent(grid);
        
        // Enable/disable add button based on input validation
        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(true);
        
        // Enable add button only if title and artist are provided
        title.textProperty().addListener((obs, old, newValue) -> 
            addButton.setDisable(title.getText().trim().isEmpty() || artist.getText().trim().isEmpty()));
        artist.textProperty().addListener((obs, old, newValue) -> 
            addButton.setDisable(title.getText().trim().isEmpty() || artist.getText().trim().isEmpty()));

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                return new Song(0, title.getText().trim(), artist.getText().trim(), mood.getValue(), path.getText().trim());
            }
            return null;
        });

        // Style the dialog inputs
        title.setStyle("-fx-text-fill: white; -fx-background-color: #415A77;");
        artist.setStyle("-fx-text-fill: white; -fx-background-color: #415A77;");
        path.setStyle("-fx-text-fill: white; -fx-background-color: #415A77;");
        mood.setStyle("-fx-text-fill: white; -fx-background-color: #415A77;");
        
        // Style labels
        grid.getChildren().filtered(node -> node instanceof Label)
            .forEach(node -> ((Label) node).setTextFill(Color.WHITE));

        dialog.showAndWait().ifPresent(s -> {
            if (s.getTitle().trim().isEmpty() || s.getArtist().trim().isEmpty()) {
                showAlert("Title and artist are required!");
                return;
            }
            
            try {
                int id = db.insertSong(s.getTitle(), s.getArtist(), s.getMood(), s.getPath());
                if (id != -1) {
                    refreshSongList();
                    showAlert("Song '" + s.getTitle() + "' added successfully!");
                }
            } catch (SQLException e) {
                showAlert("Database error: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                showAlert("Error adding song: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void playSong(Song s) {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        
        String path = s.getPath();
        if (path == null || path.trim().isEmpty()) {
            showAlert("No media file or URL specified for this song");
            return;
        }
        
        try {
            Media media;
            if (path.toLowerCase().startsWith("http")) {
                // Ensure URL is properly formatted
                String encodedUrl = path.replace(" ", "%20");
                media = new Media(encodedUrl);
            } else {
                File file = new File(path);
                if (!file.exists()) {
                    showAlert("File not found: " + path);
                    return;
                }
                media = new Media(file.toURI().toString());
            }
            
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setOnReady(() -> {
                mediaPlayer.play();
                nowPlayingLabel.setText(s.getTitle() + " — " + s.getArtist());
                
                // Update mood emoji
                String emoji = MOOD_EMOJIS.getOrDefault(s.getMood(), "🎵");
                ((Label)((VBox)((HBox)nowPlayingLabel.getParent().getParent()).getChildren().get(0)).getChildren().get(1)).setText(emoji);
            });
            
            mediaPlayer.setOnEndOfMedia(() -> {
                mediaPlayer.stop();
                mediaPlayer.seek(Duration.ZERO);
            });
            
            // Update time slider
            Slider timeSlider = (Slider)((HBox)((HBox)nowPlayingLabel.getParent().getParent()).getChildren().get(2)).getChildren().get(4);
            Label timeLabel = (Label)((HBox)((HBox)nowPlayingLabel.getParent().getParent()).getChildren().get(2)).getChildren().get(5);
            
            mediaPlayer.currentTimeProperty().addListener((obs, oldVal, newVal) -> {
                if (!timeSlider.isValueChanging()) {
                    timeSlider.setValue(newVal.toSeconds() / mediaPlayer.getTotalDuration().toSeconds() * 100.0);
                    timeLabel.setText(String.format("%d:%02d / %d:%02d",
                        (int)newVal.toMinutes(), (int)newVal.toSeconds() % 60,
                        (int)mediaPlayer.getTotalDuration().toMinutes(),
                        (int)mediaPlayer.getTotalDuration().toSeconds() % 60));
                }
            });
            
            timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (timeSlider.isValueChanging()) {
                    mediaPlayer.seek(mediaPlayer.getTotalDuration().multiply(newVal.doubleValue() / 100.0));
                }
            });
            
        } catch (Exception e) {
            showAlert("Error playing media: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, text, ButtonType.OK);
        a.showAndWait();
    }
    
    private boolean showConfirmDialog(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    
    private void deleteSong(Song song) {
        try {
            PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM songs WHERE id = ?");
            ps.setInt(1, song.getId());
            ps.executeUpdate();
            refreshSongList();
            showAlert("Song deleted successfully!");
        } catch (SQLException e) {
            showAlert("Error deleting song: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showEditSongDialog(Song song) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Song");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField title = new TextField(song.getTitle());
        TextField artist = new TextField(song.getArtist());
        ComboBox<String> mood = new ComboBox<>();
        mood.getItems().addAll("Happy", "Sad", "Energetic", "Calm", "Focus");
        mood.setValue(song.getMood());
        TextField path = new TextField(song.getPath());

        Button browseButton = new Button("Browse");
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Media File");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.m4a"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File file = fileChooser.showOpenDialog(dialog.getOwner());
            if (file != null) {
                path.setText(file.getAbsolutePath());
            }
        });

        HBox pathBox = new HBox(5);
        pathBox.getChildren().addAll(path, browseButton);

        grid.add(new Label("Title:"), 0, 0);
        grid.add(title, 1, 0);
        grid.add(new Label("Artist:"), 0, 1);
        grid.add(artist, 1, 1);
        grid.add(new Label("Mood:"), 0, 2);
        grid.add(mood, 1, 2);
        grid.add(new Label("File path/URL:"), 0, 3);
        grid.add(pathBox, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    PreparedStatement ps = db.getConnection().prepareStatement(
                        "UPDATE songs SET title = ?, artist = ?, mood = ?, path = ? WHERE id = ?");
                    ps.setString(1, title.getText());
                    ps.setString(2, artist.getText());
                    ps.setString(3, mood.getValue());
                    ps.setString(4, path.getText());
                    ps.setInt(5, song.getId());
                    ps.executeUpdate();
                    refreshSongList();
                } catch (SQLException e) {
                    e.printStackTrace();
                    showAlert("Error updating song: " + e.getMessage());
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

        @Override
    public void stop() throws Exception {
        super.stop();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
        if (db != null) {
            try {
                // Ensure any pending transactions are committed
                if (db.getConnection() != null && !db.getConnection().isClosed()) {
                    if (!db.getConnection().getAutoCommit()) {
                        db.getConnection().commit();
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error committing final transactions: " + e.getMessage());
            } finally {
                db.close();
            }
        }
    }    public static void main(String[] args) {
        launch(args);
    }

    // -- Models --
    public static class Song {
        private int id;
        private String title;
        private String artist;
        private String mood;
        private String path;

        public Song(int id, String title, String artist, String mood, String path) {
            this.id = id; this.title = title; this.artist = artist; this.mood = mood; this.path = path;
        }

        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getMood() { return mood; }
        public String getPath() { return path; }
    }

    // -- DB helper --
    public static class DBHelper {
        private Connection conn;
        private final String dbfile;
        private final Object lock = new Object();
        private static final int MAX_RETRIES = 3;
        private static final int RETRY_DELAY_MS = 1000;
        public DBHelper(String dbfile) {
            this.dbfile = dbfile;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    System.err.println("Error closing database connection: " + e.getMessage());
                }
            }));
        }

        public void initAndSeed() throws RuntimeException {
            synchronized(lock) {
                for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                    try {
                        // Ensure parent directory exists
                        File dbFile = new File(dbfile);
                        File parentDir = dbFile.getParentFile();
                        if (parentDir != null && !parentDir.exists()) {
                            parentDir.mkdirs();
                        }
                        
                        ensureConnection();
                        
                        // Configure database settings before starting transaction
                        try (Statement st = conn.createStatement()) {
                            // Configure database for better reliability
                            st.execute("PRAGMA journal_mode=WAL");
                            st.execute("PRAGMA synchronous=NORMAL");
                            st.execute("PRAGMA busy_timeout=5000");
                            st.execute("PRAGMA foreign_keys=ON");
                        }

                        // Use a transaction for schema creation
                        try {
                            conn.setAutoCommit(false);
                            try (Statement st = conn.createStatement()) {
                        
                                // Create tables with better indices for performance
                                st.execute("CREATE TABLE IF NOT EXISTS songs (" +
                                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                        "title TEXT NOT NULL, " +
                                        "artist TEXT NOT NULL, " +
                                        "mood TEXT NOT NULL, " +
                                        "path TEXT NOT NULL DEFAULT '');");
                        
                                st.execute("CREATE INDEX IF NOT EXISTS idx_songs_mood ON songs(mood);");
                        
                                st.execute("CREATE TABLE IF NOT EXISTS playlists (" +
                                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                        "name TEXT UNIQUE NOT NULL);");
                                
                                st.execute("CREATE TABLE IF NOT EXISTS playlist_songs (" +
                                        "playlist_id INTEGER NOT NULL, " +
                                        "song_id INTEGER NOT NULL, " +
                                        "FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE, " +
                                        "FOREIGN KEY(song_id) REFERENCES songs(id) ON DELETE CASCADE, " +
                                        "PRIMARY KEY(playlist_id, song_id));");
                                
                                st.execute("CREATE INDEX IF NOT EXISTS idx_playlist_songs_song " +
                                        "ON playlist_songs(song_id);");

                                // Check for existing songs using prepared statement
                                try (PreparedStatement ps = conn.prepareStatement(
                                        "SELECT COUNT(*) AS c FROM songs")) {
                                    ResultSet rs = ps.executeQuery();
                                    int count = rs.next() ? rs.getInt("c") : 0;
                                    
                                    // Seed sample data if empty
                                    if (count == 0) {
                                        String[][] sampleSongs = {
                                            {"Sunshine Drive", "Neon Roads", "Happy", ""},
                                            {"Midnight Thought", "Quiet Hour", "Calm", ""},
                                            {"Run Wild", "Pulse Factory", "Energetic", ""},
                                            {"Rainy Window", "Soft Echo", "Sad", ""},
                                            {"Study Focus", "Ambient Labs", "Focus", ""}
                                        };
                                        
                                        try (PreparedStatement insert = conn.prepareStatement(
                                                "INSERT INTO songs (title, artist, mood, path) VALUES (?, ?, ?, ?)")) {
                                            for (String[] song : sampleSongs) {
                                                insert.setString(1, song[0]);
                                                insert.setString(2, song[1]);
                                                insert.setString(3, song[2]);
                                                insert.setString(4, song[3]);
                                                insert.executeUpdate();
                                            }
                                        }
                                    }
                                }
                            }
                            conn.commit();
                            break; // Successfully initialized, exit the retry loop
                        } catch (SQLException e) {
                            try {
                                conn.rollback();
                            } catch (SQLException rollbackEx) {
                                System.err.println("Error during rollback: " + rollbackEx.getMessage());
                            }
                            if (attempt == MAX_RETRIES - 1) {
                                throw new RuntimeException("Failed to initialize database after " + MAX_RETRIES + " attempts", e);
                            }
                            // Wait before retrying
                            try {
                                Thread.sleep(RETRY_DELAY_MS);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Interrupted during database initialization", ie);
                            }
                        } finally {
                            try {
                                conn.setAutoCommit(true);
                            } catch (SQLException e) {
                                System.err.println("Error resetting auto-commit: " + e.getMessage());
                            }
                        }
                    } catch (SQLException e) {
                        if (attempt == MAX_RETRIES - 1) {
                            throw new RuntimeException("Failed to establish database connection", e);
                        }
                    }
                }
            }
        }

        private void ensureConnection() throws SQLException {
            synchronized(lock) {
                try {
                    if (conn != null && !conn.isClosed()) {
                        // Test the connection with a shorter timeout
                        try (Statement st = conn.createStatement()) {
                            st.setQueryTimeout(1);
                            st.execute("SELECT 1");
                            return; // Connection is valid
                        } catch (SQLException e) {
                            // Connection test failed, continue to create new connection
                            System.err.println("Connection test failed: " + e.getMessage());
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("Connection validation error: " + e.getMessage());
                }

                // Clean up old connection if it exists
                try {
                    if (conn != null) {
                        try {
                            conn.close();
                        } catch (SQLException ignored) {}
                    }
                } finally {
                    conn = null;
                }

                    // Create database directory if it doesn't exist
                    File f = new File(dbfile);
                    File parent = f.getAbsoluteFile().getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    // Configure SQLite connection for better concurrency
                    String url = "jdbc:sqlite:" + dbfile + "?busy_timeout=30000";
                    conn = DriverManager.getConnection(url);
                
                    // Configure connection for better reliability and concurrency
                    try (Statement stmt = conn.createStatement()) {
                        // Use WAL mode for better concurrency
                        stmt.execute("PRAGMA journal_mode=WAL");
                        // Normal synchronization mode for better performance while maintaining safety
                        stmt.execute("PRAGMA synchronous=NORMAL");
                        // Increase cache size for better performance
                        stmt.execute("PRAGMA cache_size=2000");
                        // Enable memory-mapped I/O for better performance
                        stmt.execute("PRAGMA mmap_size=268435456"); // 256MB
                        // Ensure foreign key support
                        stmt.execute("PRAGMA foreign_keys=ON");
                    }
            }
        }

        public List<Song> getAllSongs() {
            List<Song> out = new ArrayList<>();
            try (Statement st = conn.createStatement()) {
                ResultSet rs = st.executeQuery("SELECT * FROM songs ORDER BY id");
                while (rs.next()) out.add(rowToSong(rs));
            } catch (SQLException e) { e.printStackTrace(); }
            return out;
        }

        public List<Song> getSongsByMood(String mood) {
            List<Song> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM songs WHERE mood = ? ORDER BY id")) {
                ps.setString(1, mood);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) out.add(rowToSong(rs));
            } catch (SQLException e) { e.printStackTrace(); }
            return out;
        }

        public int insertSong(String title, String artist, String mood, String path) throws SQLException {
            if (title == null || title.trim().isEmpty()) {
                throw new SQLException("Title cannot be empty");
            }
            if (artist == null || artist.trim().isEmpty()) {
                throw new SQLException("Artist cannot be empty");
            }
            if (mood == null || mood.trim().isEmpty()) {
                throw new SQLException("Mood must be selected");
            }
            
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO songs(title, artist, mood, path) VALUES(?,?,?,?)", 
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, title.trim());
                ps.setString(2, artist.trim());
                ps.setString(3, mood);
                ps.setString(4, path != null ? path.trim() : "");
                
                int affected = ps.executeUpdate();
                if (affected > 0) {
                    ResultSet gk = ps.getGeneratedKeys();
                    if (gk.next()) {
                        return gk.getInt(1);
                    }
                }
                throw new SQLException("Failed to insert song");
            }
        }

        public int createPlaylist(String name) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO playlists(name) VALUES(?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.executeUpdate();
                ResultSet gk = ps.getGeneratedKeys();
                if (gk.next()) return gk.getInt(1);
                // if ignored (already exists), return existing id
                try (PreparedStatement ps2 = conn.prepareStatement("SELECT id FROM playlists WHERE name = ?")) {
                    ps2.setString(1, name);
                    ResultSet rs = ps2.executeQuery();
                    if (rs.next()) return rs.getInt("id");
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return -1;
        }

        public void addSongToPlaylist(int playlistId, int songId) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO playlist_songs(playlist_id, song_id) VALUES(?,?)")) {
                ps.setInt(1, playlistId);
                ps.setInt(2, songId);
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }

        public void addSongToPlaylistByName(String playlistName, int songId) {
            int pid = createPlaylist(playlistName);
            if (pid != -1) addSongToPlaylist(pid, songId);
        }

        public List<String> getAllPlaylistNames() {
            List<String> out = new ArrayList<>();
            try (Statement st = conn.createStatement()) {
                ResultSet rs = st.executeQuery("SELECT name FROM playlists ORDER BY name");
                while (rs.next()) out.add(rs.getString("name"));
            } catch (SQLException e) { e.printStackTrace(); }
            return out;
        }

        public List<Song> getSongsForPlaylist(String playlistName) {
            List<Song> out = new ArrayList<>();
            String sql = "SELECT s.* FROM songs s JOIN playlist_songs ps ON s.id = ps.song_id JOIN playlists p ON p.id = ps.playlist_id WHERE p.name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playlistName);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) out.add(rowToSong(rs));
            } catch (SQLException e) { e.printStackTrace(); }
            return out;
        }

        public List<Integer> findSongIdsByMood(String mood) {
            List<Integer> out = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM songs WHERE LOWER(mood) = LOWER(?) ORDER BY title")) {
                ps.setString(1, mood);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) out.add(rs.getInt("id"));
            } catch (SQLException e) { 
                e.printStackTrace();
                System.err.println("Error finding songs for mood: " + mood);
            }
            return out;
        }

        private Song rowToSong(ResultSet rs) throws SQLException {
            return new Song(rs.getInt("id"), rs.getString("title"), rs.getString("artist"), rs.getString("mood"), rs.getString("path"));
        }

        public Connection getConnection() throws SQLException {
            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                try {
                    ensureConnection();
                    // Test the connection
                    try (Statement st = conn.createStatement()) {
                        st.setQueryTimeout(1);
                        st.execute("SELECT 1");
                        return conn;
                    }
                } catch (SQLException e) {
                    if (attempt == MAX_RETRIES - 1) throw e;
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Interrupted while trying to connect", ie);
                    }
                }
            }
            throw new SQLException("Failed to get valid connection after " + MAX_RETRIES + " attempts");
        }

        public void close() {
            synchronized(lock) {
                try {
                    if (conn != null) {
                        conn.close();
                        conn = null;
                    }
                } catch (SQLException ignored) {}
            }
        }
    }
}
