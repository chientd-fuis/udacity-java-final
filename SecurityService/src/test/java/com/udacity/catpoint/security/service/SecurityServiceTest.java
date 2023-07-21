package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.FakeImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import static com.udacity.catpoint.security.data.AlarmStatus.ALARM;
import static com.udacity.catpoint.security.data.AlarmStatus.NO_ALARM;
import static com.udacity.catpoint.security.data.AlarmStatus.PENDING_ALARM;

import static com.udacity.catpoint.security.data.ArmingStatus.ARMED_HOME;
import static com.udacity.catpoint.security.data.ArmingStatus.DISARMED;

class SecurityServiceTest {

    @InjectMocks
    private SecurityService securityService;
    @Mock
    private Sensor sensor;
    @Mock
    private FakeImageService fakeImageService;
    @Mock
    private SecurityRepository repository;
    @Mock
    private BufferedImage image;
    @Mock
    private StatusListener statusListener;

    private Set<Sensor> listSensors;

    private ArgumentCaptor<AlarmStatus> captor;

    @BeforeEach
    void setup() {
        securityService = new SecurityService(repository, fakeImageService);
        sensor = new Sensor(UUID.randomUUID().toString(), SensorType.MOTION);
        listSensors  = new HashSet<>();
        for (int i = 0; i <= 2; i++) {
            listSensors.add(new Sensor(UUID.randomUUID().toString(), SensorType.MOTION));
        }
        listSensors.forEach(sensor -> sensor.setActive(false));
        image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        captor = ArgumentCaptor.forClass(AlarmStatus.class);
    }

    @BeforeEach
    public void setupEach() {
        MockitoAnnotations.openMocks(this);
    }


    //    1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
    @Test
    @DisplayName("when_SystemArmedAndSensorActivated_changeStatusToPending")
    void changeSensorActivationStatus_ToPending() {
        when(repository.getAlarmStatus()).thenReturn(NO_ALARM);
        when(repository.getArmingStatus()).thenReturn(ARMED_HOME);

        securityService.changeSensorActivationStatus(sensor, true);
        verify(repository, atMostOnce()).updateSensor(sensor);
        verify(repository, atMostOnce()).setAlarmStatus(PENDING_ALARM);
    }

    //    2. If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.
    @Test
    @DisplayName("when_SystemArmedAndSensorActivatedAndPendingState_changeStatusToAlarm")
    void changeSensorActivationStatus_ToAlarm() {
        when(repository.getAlarmStatus()).thenReturn(PENDING_ALARM);
        when(repository.getArmingStatus()).thenReturn(ARMED_HOME);

        securityService.changeSensorActivationStatus(sensor, true);
        verify(repository, atMostOnce()).updateSensor(sensor);
        verify(repository, atMost(2)).setAlarmStatus(ALARM);
    }

    //    3. If pending alarm and all sensors are inactive, return to no alarm state.
    @Test
    @DisplayName("when_PendingAlarmAndAllSensorInactive_returnNoAlarmState")
    void changeSensorActivationStatus_NoAlarm() {
        when(repository.getAlarmStatus()).thenReturn(PENDING_ALARM);
        sensor = listSensors.iterator().next();
        sensor.setActive(true);

        securityService.changeSensorActivationStatus(sensor, false);

        assertFalse(sensor.getActive());
        verify(repository, atMostOnce()).updateSensor(sensor);
        verify(repository, atMostOnce()).setAlarmStatus(NO_ALARM);
    }

    //    4. If alarm is active, change in sensor state should not affect the alarm state.
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @DisplayName("when_AlarmIsActive_changeSensorShouldNotAffectAlarmState")
    void changeSensorActivationStatus_noCall_setAlarmStatus(boolean status) {
        when(repository.getAlarmStatus()).thenReturn(ALARM);

        securityService.changeSensorActivationStatus(sensor, status);
        verify(repository, atMostOnce()).updateSensor(sensor);
        verify(repository, never()).setAlarmStatus(any(AlarmStatus.class));

    }

    //    5. If a sensor is activated while already active and the system is in pending state, change it to alarm state.
    @Test
    @DisplayName("when_SensorActivatedWhileActiveAndPendingAlarm_changeStatusToAlarm")
    void changeSensorActivationStatus_Active_ToAlarm() {
        when(repository.getAlarmStatus()).thenReturn(PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, true);
        // verify
        verify(repository, atMostOnce()).updateSensor(sensor);
        verify(repository, atMostOnce()).setAlarmStatus(ALARM);
    }

    //    6. If a sensor is deactivated while already inactive, make no changes to the alarm state.
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"ALARM", "NO_ALARM", "PENDING_ALARM"})
    @DisplayName("when_sensorDeactivatedWhileInactive_noChangesToAlarmState")
    void changeSensorActivationStatus_deactivated_NoChangeState(AlarmStatus status) {
        when(repository.getAlarmStatus()).thenReturn(status);
        securityService.changeSensorActivationStatus(sensor, false);
        assertFalse(sensor.getActive());
        verify(repository, atMostOnce()).updateSensor(sensor);
        verify(repository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    //    7. If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.
    @Test
    @DisplayName("when_ImageServiceIdentifiesCatWhileAlarmArmedHome_changeStatusToAlarm")
    void processImage_putIntoSystem_Alarm() {
        when(repository.getArmingStatus()).thenReturn(ARMED_HOME);
        when(fakeImageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        securityService.processImage(image);
        verify(repository, atMostOnce()).updateSensor(sensor);
        verify(repository, atMostOnce()).setAlarmStatus(ALARM);
    }

    //    8. If the image service identifies an image that does not contain a cat, change the status to no alarm as long as the sensors are not active.
    @Test
    @DisplayName("when_imageServiceIdentifiesNoCatImage_changeStatusToNoAlarmAsLongSensorsNotActive")
    void processImage_putIntoSystem_NoAlarm() {
        when(repository.getSensors()).thenReturn(listSensors);
        when(fakeImageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));
        verify(repository, atMostOnce()).updateSensor(sensor);
        verify(repository, atMostOnce()).setAlarmStatus(NO_ALARM);
    }

    //    9. If the system is disarmed, set the status to no alarm.
    @Test
    @DisplayName("when_systemDisarmed_setNoAlarmState")
    void setArmingStatus_disarmed() {
        securityService.setArmingStatus(DISARMED);
        verify(repository, atMostOnce()).updateSensor(sensor);
        verify(repository, atMostOnce()).setAlarmStatus(NO_ALARM);
    }

    //    10. If the system is armed, reset all sensors to inactive.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    @DisplayName("when_systemArmed_resetSensorsToInactive")
    void setArmingStatus_inactive(ArmingStatus status) {
        when(repository.getSensors()).thenReturn(listSensors);
        when(repository.getAlarmStatus()).thenReturn(PENDING_ALARM);
        securityService.setArmingStatus(status);

        verify(repository, atMostOnce()).updateSensor(sensor);
        securityService.getSensors().forEach(sensor -> {
            assertFalse(sensor.getActive());
        });
    }

    //    11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
    @Test
    @DisplayName("when_systemArmedHomeWhileImageServiceIdentifiesCat_changeStatusToAlarm")
    void processImage_setArmingStatus_Alarm() {
        when(repository.getArmingStatus()).thenReturn(DISARMED);
        when(fakeImageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(image);
        securityService.setArmingStatus(ARMED_HOME);
        verify(repository, atMostOnce()).updateSensor(sensor);
        verify(repository, atMostOnce()).setAlarmStatus(ALARM);
    }

    // extra test for full method
    @Test
    @DisplayName("Extra test for addStatusListener")
    void addStatusListener() {
        securityService.addStatusListener(statusListener);
    }

    @Test
    @DisplayName("Extra test for removeStatusListener")
    void removeStatusListener() {
        securityService.removeStatusListener(statusListener);
    }

    // extra test for full method
    @Test
    @DisplayName("Extra test for addSensor")
    void addSensor() {
        securityService.addSensor(sensor);
    }

    @Test
    @DisplayName("Extra test for removeSensor")
    void removeSensor() {
        securityService.removeSensor(sensor);
    }

    @Test
    @DisplayName("Extra test for changeSensorActivationStatus")
    void changeSensorActivationStatus_PENDING_ALARM() {
        when(repository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        sensor = new Sensor(UUID.randomUUID().toString(), SensorType.MOTION);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(repository, atMostOnce()).setAlarmStatus(AlarmStatus.PENDING_ALARM);

    }


}