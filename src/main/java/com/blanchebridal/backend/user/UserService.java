package com.blanchebridal.backend.user;

import com.blanchebridal.backend.user.dto.req.MeasurementsRequest;
import com.blanchebridal.backend.user.dto.req.UpdateProfileRequest;
import com.blanchebridal.backend.user.dto.res.MeasurementsResponse;
import com.blanchebridal.backend.user.dto.res.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {

    UserResponse getProfile(UUID userId);

    UserResponse updateProfile(UUID userId, UpdateProfileRequest request);

    // recordedById = the admin's UUID extracted from their token
    MeasurementsResponse saveMeasurements(UUID customerId, UUID recordedById,
                                          MeasurementsRequest request);

    List<MeasurementsResponse> getMeasurements(UUID customerId);
}