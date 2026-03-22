package edu.bridalshop.backend.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class GoogleTokenVerifier {

    @Value("${google.client-id}")
    private String clientId;

    // What we extract from Google's token
    @Getter
    public static class GoogleUserInfo {
        public final String googleId;
        public final String email;
        public final String fullName;
        public final String pictureUrl;

        public GoogleUserInfo(String googleId, String email,
                              String fullName, String pictureUrl) {
            this.googleId   = googleId;
            this.email      = email;
            this.fullName   = fullName;
            this.pictureUrl = pictureUrl;
        }
    }

    public GoogleUserInfo verify(String idToken) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier
                    .Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(clientId))
                    .build();

            GoogleIdToken googleIdToken = verifier.verify(idToken);

            if (googleIdToken == null) {
                throw new RuntimeException("Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = googleIdToken.getPayload();

            return new GoogleUserInfo(
                    payload.getSubject(),                        // google_id
                    payload.getEmail(),                          // email
                    (String) payload.get("name"),                // full name
                    (String) payload.get("picture")              // profile picture
            );

        } catch (Exception e) {
            throw new RuntimeException("Google token verification failed: "
                    + e.getMessage());
        }
    }
}