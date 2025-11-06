package com.example.metube.login;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.metube.model.User;
import com.facebook.*;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.example.metube.R;

import java.util.Arrays;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;
    private CallbackManager callbackManager;
    private static final String TAG = "LoginActivity";

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            firebaseAuthWithGoogle(account.getIdToken());
                        }
                    } catch (ApiException e) {
                        Log.w(TAG, "Google sign-in failed", e);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();

        // If already logged in, skip to main activity
        if (mAuth.getCurrentUser() != null) {
            startMainActivity();
            return;
        }

        // ---- GOOGLE SIGN-IN ----
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        findViewById(R.id.btnGoogleSignIn).setOnClickListener(v -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });

        // ---- FACEBOOK SIGN-IN ----
        FacebookSdk.sdkInitialize(getApplicationContext());
        callbackManager = CallbackManager.Factory.create();

        Button facebookButton = findViewById(R.id.btnFacebookSignIn);
        facebookButton.setOnClickListener(v -> {
            LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("email", "public_profile"));
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
                }
            });
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                createUserIfNew();
            } else {
                Log.w(TAG, "Google authentication failed", task.getException());
            }
        });
    }

    private void handleFacebookAccessToken(AccessToken token) {
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                createUserIfNew();
            } else {
                Log.w(TAG, "Facebook authentication failed", task.getException());
            }
        });
    }

    private void createUserIfNew() {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) return;

        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users");
        String userID = firebaseUser.getUid();

        usersRef.child(userID).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                String name = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "New User";
                String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "";
                String profileURL = firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "";

                User newUser = new User(userID, name, email, false, false, profileURL);
                usersRef.child(userID).setValue(newUser);
            }

            startMainActivity();
        });
    }

    private void startMainActivity() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }
}
