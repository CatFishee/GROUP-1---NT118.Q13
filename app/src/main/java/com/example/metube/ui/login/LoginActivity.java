package com.example.metube.ui.login;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

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

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;
    private CallbackManager callbackManager;
    private static final String TAG = "LoginActivity";

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            Log.d(TAG, "Google account: " + account.getEmail());
                            Log.d(TAG, "ID Token: " + account.getIdToken());
                            firebaseAuthWithGoogle(account.getIdToken());
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
//        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
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

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Google auth successful.");
                        createUserIfNew(() -> startHomeActivity()); // callback sau khi tạo user
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
                createUserIfNew(() -> startHomeActivity()); // Chạy tác vụ nền để tạo user
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

    // Facebook SDK vẫn cần phương thức này để nhận kết quả
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