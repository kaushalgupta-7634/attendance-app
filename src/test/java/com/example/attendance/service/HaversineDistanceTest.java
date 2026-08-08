package com.example.attendance.service;

import com.example.attendance.repository.AttendanceRecordRepository;
import com.example.attendance.repository.ClassSessionRepository;
import com.example.attendance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class HaversineDistanceTest {

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private ClassSessionRepository classSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private QrCodeService qrCodeService;

    @InjectMocks
    private AttendanceService attendanceService;

    @Test
    void testCalculateHaversineMeters_SameLocation_ReturnsZero() {
        double dist = attendanceService.calculateHaversineMeters(12.9716, 77.5946, 12.9716, 77.5946);
        assertEquals(0.0, dist, 0.001);
    }

    @Test
    void testCalculateHaversineMeters_NearbyClassroomCoordinates() {
        // ~50-60 meters apart
        double lat1 = 12.971599;
        double lng1 = 77.594563;
        double lat2 = 12.971950;
        double lng2 = 77.594950;

        double dist = attendanceService.calculateHaversineMeters(lat1, lng1, lat2, lng2);
        assertTrue(dist > 30.0 && dist < 80.0, "Expected distance to be around 50-60m, got: " + dist);
    }

    @Test
    void testCalculateHaversineMeters_DistantLocations() {
        // Bangalore (12.9716, 77.5946) to Mumbai (19.0760, 72.8777) ~840 km
        double dist = attendanceService.calculateHaversineMeters(12.9716, 77.5946, 19.0760, 72.8777);
        assertTrue(dist > 800000.0, "Expected distance to be > 800 km, got: " + dist);
    }
}
