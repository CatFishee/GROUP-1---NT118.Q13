package com.example.metube.ui.login;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.metube.R;
import com.example.metube.model.User;
import com.example.metube.ui.home.HomepageActivity;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;

import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;
    private CallbackManager callbackManager;
    private static final String TAG = "LoginActivity";

    // Variables for Verification
    private int verificationCode;
    private String pendingGoogleIdToken;

    // TODO: REPLACE THESE WITH YOUR OWN CREDENTIALS
    // Note: Use a Google App Password, not your real password.
    private static final String SENDER_EMAIL = "giangcam2005@gmail.com";
    private static final String SENDER_PASSWORD = "testingtesting";

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            Log.d(TAG, "Google account: " + account.getEmail());
                            // INTERCEPT: Check if user exists before logging in
                            checkUserExistsAndVerify(account);
                        }
                    } catch (ApiException e) {
                        Log.w(TAG, "Google sign-in failed", e);
                        Toast.makeText(this, "Google Sign-in Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        printKeyHash();

        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() != null) {
            startHomeActivity();
            return;
        }

        // ---- GOOGLE SIGN-IN ----
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        findViewById(R.id.btnGoogleSignIn).setOnClickListener(v -> googleSignInLauncher.launch(googleSignInClient.getSignInIntent()));

        // ---- FACEBOOK SIGN-IN ----
        callbackManager = CallbackManager.Factory.create();

        LoginManager.getInstance().registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                handleFacebookAccessToken(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "Facebook sign-in canceled");
            }

            @Override
            public void onError(FacebookException error) {
                Log.w(TAG, "Facebook sign-in failed", error);
                Toast.makeText(LoginActivity.this, "Facebook Sign-in Failed", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnFacebookSignIn).setOnClickListener(v -> {
            LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("email", "public_profile"));
        });
    }

    /**
     * Step 1: Check if the user already exists in Firestore.
     * If Yes -> Login immediately.
     * If No -> Send Verification Code.
     */
    private void checkUserExistsAndVerify(GoogleSignInAccount account) {
        String email = account.getEmail();
        if (email == null) {
            Toast.makeText(this, "Error: Email not found", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        // NOTE: This query assumes your Firestore rules allow unauthenticated reads to "users"
        // or that you are okay with this check. Ideally, secure via Cloud Functions.
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        // User exists, proceed to login directly
                        Log.d(TAG, "User exists, skipping verification.");
                        firebaseAuthWithGoogle(account.getIdToken());
                    } else {
                        // User is NEW, start verification
                        Log.d(TAG, "New user detected, sending verification email.");
                        pendingGoogleIdToken = account.getIdToken();
                        startEmailVerification(email);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking user existence", e);
                    Toast.makeText(this, "Network Error", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Step 2: Generate Code and Send Email
     */
    private void startEmailVerification(String email) {
        Random random = new Random();
        verificationCode = 100000 + random.nextInt(900000); // Generate 6-digit code

        Toast.makeText(this, "Sending verification code...", Toast.LENGTH_LONG).show();

        new Thread(() -> {
            try {
                sendJavaMail(email, verificationCode);
                runOnUiThread(() -> showVerificationDialog(email));
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Failed to send email. Check internet or credentials.", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /**
     * Step 3: Send Email via SMTP (JavaMail)
     */
    private void sendJavaMail(String recipientEmail, int code) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(SENDER_EMAIL));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
        message.setSubject("MeTube Verification Code");
        message.setText("Welcome to MeTube! \n\nYour verification code is: " + code + "\n\nPlease enter this code to complete your registration.");

        Transport.send(message);
    }

    /**
     * Step 4: Show Dialog for User to Enter Code
     */
    private void showVerificationDialog(String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Verify Email");
        builder.setMessage("A 6-digit code was sent to " + email + ". Enter it below:");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("Verify", (dialog, which) -> {
            String enteredCode = input.getText().toString();
            if (enteredCode.equals(String.valueOf(verificationCode))) {
                Toast.makeText(LoginActivity.this, "Verified!", Toast.LENGTH_SHORT).show();
                // Code matches -> Proceed to Firebase Auth
                firebaseAuthWithGoogle(pendingGoogleIdToken);
            } else {
                Toast.makeText(LoginActivity.this, "Incorrect Code. Try logging in again.", Toast.LENGTH_SHORT).show();
                // Optional: Sign out Google client to allow retrying from scratch
                googleSignInClient.signOut();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            googleSignInClient.signOut();
        });

        builder.show();
    }

    // ---- EXISTING AUTH LOGIC ----

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Google auth successful.");
                        createUserIfNew(() -> startHomeActivity());
                    } else {
                        Log.w(TAG, "Google auth failed", task.getException());
                        Toast.makeText(this, "Login failed!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleFacebookAccessToken(com.facebook.AccessToken token) {
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Facebook auth successful. Navigating to Home.");
                createUserIfNew(() -> startHomeActivity());
            } else {
                Log.w(TAG, "Facebook auth failed", task.getException());
            }
        });
    }

    private void createUserIfNew(Runnable onComplete) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            Log.w(TAG, "FirebaseUser null when creating user.");
            onComplete.run();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userDocRef = db.collection("users").document(firebaseUser.getUid());

        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && !task.getResult().exists()) {
                User newUser = new User(
                        firebaseUser.getUid(),
                        firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "New User",
                        firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "",
                        false, false,
                        firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : ""
                );
                userDocRef.set(newUser)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "New user created.");
                            onComplete.run();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error creating user", e);
                            onComplete.run();
                        });
            } else {
                Log.d(TAG, "User exists or failed check.");
                onComplete.run();
            }
        });
    }


    private void startHomeActivity() {
        Intent intent = new Intent(this, HomepageActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        callbackManager.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void printKeyHash() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                    getPackageName(),
                    PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.d("KeyHash", "KeyHash:" + Base64.encodeToString(md.digest(), Base64.DEFAULT));
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("KeyHash", "Exception(NameNotFound):", e);
        } catch (NoSuchAlgorithmException e) {
            Log.e("KeyHash", "Exception(NoSuchAlgorithm):", e);
        }
    }
}