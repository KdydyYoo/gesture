package com.example.cameraapplication;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.posservice.PoseService;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements CameraFragment.OnFragmentInteractionListener {
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 여기에 fragment_container FrameLayout 있어야 함

        // 권한 체크 및 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            launchCameraFragment();
            startPoseService();
        }
    }

    private void launchCameraFragment() {
        logAvailableCameraIds();
        // 기본 카메라 ID는 0으로 시작 (필요 시 CameraController로 리스트 확인 가능)
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, CameraFragment.newInstance("USBFragment", "0"))
                .commit();
    }

    // CameraFragment → MainActivity 메시지 전달 시 처리
    @Override
    public void onFragmentMessage(Message msg) {
        Log.i(TAG, "Received fragment message. arg1: " + msg.arg1 + ", obj: " + msg.obj);
        // 필요 시 다시 Fragment 재생성 등 처리 가능
    }

    // 권한 승인 결과 처리
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCameraFragment();
            } else {
                Log.e(TAG, "Camera permission denied.");
                finish();
            }
        }
    }

    private void logAvailableCameraIds() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                Integer hwLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);

                Log.i("CameraCheck", "Camera ID: " + id);
                Log.i("CameraCheck", "  Lens Facing: " + lensFacingToString(lensFacing));
                Log.i("CameraCheck", "  HW Level: " + hwLevelToString(hwLevel));
                Log.i("CameraCheck", "  Capabilities: " + Arrays.toString(capabilities));
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

        private String lensFacingToString(Integer lensFacing) {
            if (lensFacing == null) return "Unknown";
            switch (lensFacing) {
                case CameraCharacteristics.LENS_FACING_FRONT: return "FRONT";
                case CameraCharacteristics.LENS_FACING_BACK: return "BACK";
                case CameraCharacteristics.LENS_FACING_EXTERNAL: return "EXTERNAL";
                default: return "Unknown";
            }
        }

        private String hwLevelToString(Integer level) {
            if (level == null) return "Unknown";
            switch (level) {
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY: return "LEGACY";
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED: return "LIMITED";
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL: return "FULL";
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3: return "LEVEL_3";
                default: return "Unknown";
            }
        }
        private void startPoseService() {
            Intent intent = new Intent(this, PoseService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }

        private void stopPoseService() {
            Intent intent = new Intent(this, PoseService.class);
            stopService(intent);
        }


    }

