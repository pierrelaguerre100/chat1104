package com.example.chat1104;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides sign-in, sign-up, profile-photo upload, and sign-out features for the app.
 */
public class MainActivity extends AppCompatActivity {

    private LinearLayout signInContainer;
    private LinearLayout signUpContainer;
    private LinearLayout profileContainer;
    private TextInputEditText signInEmailEditText;
    private TextInputEditText signInPasswordEditText;
    private TextInputEditText firstNameEditText;
    private TextInputEditText lastNameEditText;
    private TextInputEditText signUpEmailEditText;
    private TextInputEditText signUpPasswordEditText;
    private TextInputEditText confirmPasswordEditText;
    private ImageView signUpPhotoImageView;
    private ImageView profilePhotoImageView;
    private TextView profileNameTextView;
    private TextView profileEmailTextView;
    private FirebaseAuth firebaseAuth;
    private DatabaseReference usersReference;
    private StorageReference profilePhotosReference;
    private boolean firebaseReady;
    private boolean realtimeDatabaseReady;
    private boolean storageReady;
    private Uri selectedPhotoUri;
    private ActivityResultLauncher<String> photoPicker;
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();

    /**
     * Creates the activity, connects the view controls, and prepares Firebase services.
     *
     * @param savedInstanceState the previously saved activity state, if available
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        configurePhotoPicker();
        configureListeners();
        initializeFirebase();
    }

    /**
     * Restores the authenticated user profile when Firebase already has an active session.
     */
    @Override
    protected void onStart() {
        super.onStart();
        if (firebaseReady && firebaseAuth.getCurrentUser() != null) {
            loadProfile(firebaseAuth.getCurrentUser());
        }
    }

    /**
     * Releases the background image-loading worker when this activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        imageExecutor.shutdownNow();
        super.onDestroy();
    }

    /**
     * Finds all controls declared in the activity layout.
     */
    private void bindViews() {
        signInContainer = findViewById(R.id.signInContainer);
        signUpContainer = findViewById(R.id.signUpContainer);
        profileContainer = findViewById(R.id.profileContainer);
        signInEmailEditText = findViewById(R.id.signInEmailEditText);
        signInPasswordEditText = findViewById(R.id.signInPasswordEditText);
        firstNameEditText = findViewById(R.id.firstNameEditText);
        lastNameEditText = findViewById(R.id.lastNameEditText);
        signUpEmailEditText = findViewById(R.id.signUpEmailEditText);
        signUpPasswordEditText = findViewById(R.id.signUpPasswordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        signUpPhotoImageView = findViewById(R.id.signUpPhotoImageView);
        profilePhotoImageView = findViewById(R.id.profilePhotoImageView);
        profileNameTextView = findViewById(R.id.profileNameTextView);
        profileEmailTextView = findViewById(R.id.profileEmailTextView);
    }

    /**
     * Initializes Firebase Auth and any provisioned Firebase data services.
     */
    private void initializeFirebase() {
        firebaseReady = FirebaseApp.initializeApp(this) != null;
        if (!firebaseReady) {
            return;
        }

        firebaseAuth = FirebaseAuth.getInstance();
        try {
            usersReference = FirebaseDatabase.getInstance().getReference("users");
            realtimeDatabaseReady = true;
        } catch (DatabaseException exception) {
            realtimeDatabaseReady = false;
        }

        try {
            profilePhotosReference = FirebaseStorage.getInstance().getReference("profile_images");
            storageReady = true;
        } catch (IllegalStateException exception) {
            storageReady = false;
        }
    }

    /**
     * Registers all button actions for the current screen.
     */
    private void configureListeners() {
        findViewById(R.id.signInButton).setOnClickListener(view -> signIn());
        findViewById(R.id.showSignUpButton).setOnClickListener(view -> showScreen(signUpContainer));
        findViewById(R.id.showSignInButton).setOnClickListener(view -> showScreen(signInContainer));
        findViewById(R.id.choosePhotoButton).setOnClickListener(view -> photoPicker.launch("image/*"));
        findViewById(R.id.createAccountButton).setOnClickListener(view -> createAccount());
        findViewById(R.id.signOutButton).setOnClickListener(view -> signOut());
    }

    /**
     * Configures the Android system picker for an optional profile photo.
     */
    private void configurePhotoPicker() {
        photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                selectedPhotoUri = uri;
                signUpPhotoImageView.setImageURI(uri);
                signUpPhotoImageView.setPadding(0, 0, 0, 0);
            }
        });
    }

    /**
     * Displays exactly one of the sign-in, sign-up, or profile screens.
     *
     * @param visibleScreen the layout container to show
     */
    private void showScreen(View visibleScreen) {
        signInContainer.setVisibility(visibleScreen == signInContainer ? View.VISIBLE : View.GONE);
        signUpContainer.setVisibility(visibleScreen == signUpContainer ? View.VISIBLE : View.GONE);
        profileContainer.setVisibility(visibleScreen == profileContainer ? View.VISIBLE : View.GONE);
    }

    /**
     * Signs in a user with the email and password entered on the sign-in screen.
     */
    private void signIn() {
        if (!requireFirebase()) {
            return;
        }

        String email = textOf(signInEmailEditText);
        String password = textOf(signInPasswordEditText);
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showToast("Enter your email and password.");
            return;
        }

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    showToast("Sign in successful.");
                    loadProfile(result.getUser());
                })
                .addOnFailureListener(error -> showToast("Sign in failed: " + error.getMessage()));
    }

    /**
     * Validates registration fields and creates a Firebase email/password account.
     */
    private void createAccount() {
        if (!requireFirebase()) {
            return;
        }

        String firstName = textOf(firstNameEditText);
        String lastName = textOf(lastNameEditText);
        String email = textOf(signUpEmailEditText);
        String password = textOf(signUpPasswordEditText);
        String confirmedPassword = textOf(confirmPasswordEditText);
        if (TextUtils.isEmpty(firstName) || TextUtils.isEmpty(lastName) || TextUtils.isEmpty(email)
                || TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmedPassword)) {
            showToast("Please fill out every field.");
            return;
        }
        if (!password.equals(confirmedPassword)) {
            showToast("Passwords do not match.");
            return;
        }
        if (password.length() < 6) {
            showToast("Password must contain at least 6 characters.");
            return;
        }

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> uploadPhotoAndSaveProfile(result.getUser(), firstName, lastName))
                .addOnFailureListener(error -> showToast("Account creation failed: " + error.getMessage()));
    }

    /**
     * Uploads the selected profile photo before saving the corresponding user profile.
     *
     * @param user the newly authenticated Firebase user
     * @param firstName the new user's first name
     * @param lastName the new user's last name
     */
    private void uploadPhotoAndSaveProfile(FirebaseUser user, String firstName, String lastName) {
        if (selectedPhotoUri == null || !storageReady) {
            if (selectedPhotoUri != null) {
                showToast("Cloud Storage is not configured; the profile photo was skipped.");
            }
            saveProfile(user, firstName, lastName, "");
            return;
        }

        StorageReference imageReference = profilePhotosReference.child(user.getUid() + ".jpg");
        imageReference.putFile(selectedPhotoUri)
                .addOnSuccessListener(snapshot -> imageReference.getDownloadUrl()
                        .addOnSuccessListener(photoUri -> saveProfile(user, firstName, lastName, photoUri.toString()))
                        .addOnFailureListener(error -> saveProfile(user, firstName, lastName, "")))
                .addOnFailureListener(error -> {
                    showToast("Photo upload failed; your account was created without a photo.");
                    saveProfile(user, firstName, lastName, "");
                });
    }

    /**
     * Writes the user profile to Realtime Database and opens the profile screen.
     *
     * @param user the authenticated Firebase user
     * @param firstName the user's first name
     * @param lastName the user's last name
     * @param photoUrl the Cloud Storage download URL, or an empty string
     */
    private void saveProfile(FirebaseUser user, String firstName, String lastName, String photoUrl) {
        if (!realtimeDatabaseReady) {
            showToast("Account created. Create Realtime Database in Firebase to save profiles.");
            clearRegistrationForm();
            showProfile(user, firstName + " " + lastName, photoUrl);
            return;
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("firstName", firstName);
        profile.put("lastName", lastName);
        profile.put("email", user.getEmail());
        profile.put("photoUrl", photoUrl);

        usersReference.child(user.getUid()).setValue(profile)
                .addOnSuccessListener(unused -> {
                    showToast("Account created successfully.");
                    clearRegistrationForm();
                    showProfile(user, firstName + " " + lastName, photoUrl);
                })
                .addOnFailureListener(error -> {
                    showToast("Account created, but profile storage failed: " + error.getMessage());
                    showProfile(user, firstName + " " + lastName, photoUrl);
                });
    }

    /**
     * Retrieves the saved profile details for an authenticated user.
     *
     * @param user the Firebase user whose profile should be displayed
     */
    private void loadProfile(FirebaseUser user) {
        if (!realtimeDatabaseReady) {
            showProfile(user, fallbackName(user), "");
            return;
        }

        usersReference.child(user.getUid()).get()
                .addOnSuccessListener(snapshot -> displayLoadedProfile(user, snapshot))
                .addOnFailureListener(error -> showProfile(user, fallbackName(user), ""));
    }

    /**
     * Converts database data into display-ready profile values.
     *
     * @param user the authenticated Firebase user
     * @param snapshot the database snapshot for that user's profile
     */
    private void displayLoadedProfile(FirebaseUser user, DataSnapshot snapshot) {
        String firstName = snapshot.child("firstName").getValue(String.class);
        String lastName = snapshot.child("lastName").getValue(String.class);
        String photoUrl = snapshot.child("photoUrl").getValue(String.class);
        String name = TextUtils.isEmpty(firstName) ? fallbackName(user)
                : firstName + (TextUtils.isEmpty(lastName) ? "" : " " + lastName);
        showProfile(user, name, photoUrl == null ? "" : photoUrl);
    }

    /**
     * Shows the signed-in profile screen.
     *
     * @param user the authenticated Firebase user
     * @param name the name to show in the profile
     * @param photoUrl the optional Cloud Storage image URL
     */
    private void showProfile(FirebaseUser user, String name, String photoUrl) {
        profileNameTextView.setText(name);
        profileEmailTextView.setText(user.getEmail());
        profilePhotoImageView.setImageResource(android.R.drawable.ic_menu_myplaces);
        profilePhotoImageView.setPadding(24, 24, 24, 24);
        if (!TextUtils.isEmpty(photoUrl)) {
            loadProfilePhoto(photoUrl);
        }
        showScreen(profileContainer);
    }

    /**
     * Downloads a remote profile photo without blocking the main thread.
     *
     * @param photoUrl the HTTPS URL of the image to display
     */
    private void loadProfilePhoto(String photoUrl) {
        imageExecutor.execute(() -> {
            try (InputStream stream = new URL(photoUrl).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                runOnUiThread(() -> {
                    if (bitmap != null && !isFinishing()) {
                        profilePhotoImageView.setImageBitmap(bitmap);
                        profilePhotoImageView.setPadding(0, 0, 0, 0);
                    }
                });
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * Signs out the current Firebase user and returns to the sign-in screen.
     */
    private void signOut() {
        if (!requireFirebase()) {
            return;
        }
        firebaseAuth.signOut();
        signInEmailEditText.setText("");
        signInPasswordEditText.setText("");
        showScreen(signInContainer);
        showToast("Signed out.");
    }

    /**
     * Clears values left in the registration form after a successful account creation.
     */
    private void clearRegistrationForm() {
        firstNameEditText.setText("");
        lastNameEditText.setText("");
        signUpEmailEditText.setText("");
        signUpPasswordEditText.setText("");
        confirmPasswordEditText.setText("");
        selectedPhotoUri = null;
        signUpPhotoImageView.setImageResource(android.R.drawable.ic_menu_camera);
        signUpPhotoImageView.setPadding(18, 18, 18, 18);
    }

    /**
     * Returns the text entered into an editable text field without leading or trailing whitespace.
     *
     * @param editText the text field to read
     * @return the trimmed field contents
     */
    private String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    /**
     * Builds a usable profile name when a database record is unavailable.
     *
     * @param user the authenticated Firebase user
     * @return a simple profile name
     */
    private String fallbackName(FirebaseUser user) {
        if (!TextUtils.isEmpty(user.getDisplayName())) {
            return user.getDisplayName();
        }
        String email = user.getEmail();
        return TextUtils.isEmpty(email) ? "Chat member" : email.substring(0, email.indexOf('@'));
    }

    /**
     * Ensures Firebase was initialized from a supplied google-services configuration file.
     *
     * @return true when Firebase operations may safely run
     */
    private boolean requireFirebase() {
        if (firebaseReady && firebaseAuth != null) {
            return true;
        }
        showToast("Add google-services.json to the app folder to connect Firebase.");
        return false;
    }

    /**
     * Shows a long toast message to the user.
     *
     * @param message text to display
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
