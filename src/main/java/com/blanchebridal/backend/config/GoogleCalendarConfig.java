package com.blanchebridal.backend.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;

@Slf4j
@Configuration
public class GoogleCalendarConfig {

    @Value("${google.service-account-key}")
    private String serviceAccountKeyBase64;

    @Bean
    public Calendar googleCalendarClient() throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(serviceAccountKeyBase64);

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(keyBytes))
                .createScoped(List.of(CalendarScopes.CALENDAR));

        HttpCredentialsAdapter requestInitializer = new HttpCredentialsAdapter(credentials);

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer)
                .setApplicationName("Blanche Bridal")
                .build();
    }
}