import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.sql.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class LoginPage extends Application {
    private static final String DB_FILE = "mood_music.db";
    private Connection conn;
    
    @Override
    public void start(Stage primaryStage) {
        initializeDB();
        
        VBox loginBox = new VBox(15);
        loginBox.setAlignment(Pos.CENTER);
        loginBox.setPadding(new Insets(20));
        loginBox.setStyle("-fx-background-color: linear-gradient(#000000, #1B263B);");
        
        Label title = new Label("Mood Music");
        title.setStyle("-fx-font-family: 'Poppins', system; -fx-font-size: 32px; -fx-font-weight: bold;");
        title.setTextFill(Color.web("#E0E1DD"));
        
        TextField username = new TextField();
        username.setPromptText("Username");
        styleInput(username);
        
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        styleInput(password);
        
        Button loginBtn = new Button("Login");
        styleButton(loginBtn);
        
        Button registerBtn = new Button("Register");
        styleButton(registerBtn);
        
        Label message = new Label("");
        message.setTextFill(Color.web("#E0E1DD"));
        
        loginBtn.setOnAction(e -> handleLogin(username.getText(), password.getText(), message, primaryStage));
        registerBtn.setOnAction(e -> showRegistrationDialog(message));
        
        loginBox.getChildren().addAll(title, username, password, loginBtn, registerBtn, message);
        
        Scene scene = new Scene(loginBox, 400, 500);
        primaryStage.setTitle("Login - Mood Music");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    private void styleInput(TextField input) {
        input.setStyle("-fx-background-color: rgba(224,225,221,0.1); " +
                      "-fx-text-fill: #E0E1DD; " +
                      "-fx-font-size: 14px; " +
                      "-fx-padding: 8px;");
        input.setPrefWidth(250);
    }
    
    private void styleButton(Button btn) {
        btn.setStyle("-fx-background-color: #415A77; " +
                    "-fx-text-fill: #E0E1DD; " +
                    "-fx-font-size: 14px; " +
                    "-fx-padding: 8px 16px;");
        btn.setPrefWidth(250);
    }
    
    private void initializeDB() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE);
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                        "username TEXT PRIMARY KEY, " +
                        "password TEXT NOT NULL, " +
                        "is_admin BOOLEAN DEFAULT 0)");
            
            // Create default admin if not exists
            PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO users (username, password, is_admin) VALUES (?, ?, 1)");
            ps.setString(1, "admin");
            ps.setString(2, hashPassword("admin123")); // Default admin password
            ps.executeUpdate();
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private void handleLogin(String username, String password, Label message, Stage primaryStage) {
        if (username.isEmpty() || password.isEmpty()) {
            message.setTextFill(Color.RED);
            message.setText("Please enter both username and password");
            return;
        }
        
        try {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT is_admin FROM users WHERE username = ? AND password = ?");
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                boolean isAdmin = rs.getBoolean("is_admin");
                // Launch main application
                MoodMusicPlayer player = new MoodMusicPlayer();
                player.setUserCredentials(username, isAdmin);
                player.start(new Stage());
                primaryStage.close();
            } else {
                message.setTextFill(Color.RED);
                message.setText("Invalid username or password");
            }
        } catch (Exception e) {
            e.printStackTrace();
            message.setTextFill(Color.RED);
            message.setText("Login error occurred");
        }
    }
    
    private void showRegistrationDialog(Label message) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Register New User");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        TextField username = new TextField();
        username.setPromptText("Username");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Confirm Password");
        
        grid.add(new Label("Username:"), 0, 0);
        grid.add(username, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(password, 1, 1);
        grid.add(new Label("Confirm:"), 0, 2);
        grid.add(confirmPassword, 1, 2);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                if (!password.getText().equals(confirmPassword.getText())) {
                    message.setTextFill(Color.RED);
                    message.setText("Passwords do not match");
                    return null;
                }
                try {
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO users (username, password, is_admin) VALUES (?, ?, 0)");
                    ps.setString(1, username.getText());
                    ps.setString(2, hashPassword(password.getText()));
                    ps.executeUpdate();
                    message.setTextFill(Color.GREEN);
                    message.setText("Registration successful! Please login.");
                } catch (SQLException e) {
                    message.setTextFill(Color.RED);
                    message.setText("Username already exists");
                }
            }
            return null;
        });
        
        dialog.showAndWait();
    }
    
    @Override
    public void stop() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}