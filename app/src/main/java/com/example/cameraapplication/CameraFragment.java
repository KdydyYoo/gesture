package com.example.cameraapplication;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import java.util.List;
import android.os.Handler;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.PoseLandmarkerOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

public class CameraFragment extends Fragment implements ActivityCompat.OnRequestPermissionsResultCallback
{
    private static final String TAG = "say_CameraFragment";
    private String LOG_TAG = "say_CameraFragment";
    private static final String ARG_FRAGMENT_NAME = "fragment_name";
    private static final String ARG_CAMERA_ID = "camera_id";
    private long lastGestureTime = 0;
    private static final long GESTURE_COOLDOWN_MS = 3000;

    public interface OnFragmentInteractionListener{
        void onFragmentMessage( Message msg );
    }
    private OnFragmentInteractionListener mListener;
    private String mCameraID;
    private TextView mTextCameraMsg;
    private TextureView mTextureView;
    private Button mBtnReConnect, mBtnStart;
    private CameraController mCameraController = null;
    private String mName;
    private int mWidth = -1;
    private int mHeight = -1;
    private boolean mHasFocus = false;
    private Button mFocusedButton;
    private LinearLayout mBottomLayout;
    private TextView mTVDepth_1, mTVDepth_2, mTVDepth_3, mTVDepth_4, mTVDepth_5, mTVDepth_6, mTVDepth_7;
    private TextView mTVDepthCheck;
    private int mIndexMode = 0;

    private Handler mHandler = null;
    private static final int MSG_DISPLAY_DEPTH = 100;
    private PoseLandmarker poseLandmarker;
    private HandLandmarker handLandmarker;
    private long lastProcessTime = 0;
    private OverlayView overlayView;

    private void initPoseLandmarker(Context context) {
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath("pose_landmarker_full.task")  // assets/ 위치에 있어야 함
                    .build();

            PoseLandmarkerOptions options = PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.VIDEO)
                    .build();

            poseLandmarker = PoseLandmarker.createFromOptions(context, options);
            Log.i(TAG, "PoseLandmarker initialized.");
        } catch (Exception e) {
            Log.e(TAG, "PoseLandmarker initialization failed: ", e);
        }
    }
    private void initHandLandmarker(Context context) {
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")  // assets/ 경로에 존재해야 함
                    .build();

            HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumHands(2)  // 필요한 손 개수
                    .build();

            handLandmarker = HandLandmarker.createFromOptions(context, options);
            Log.i(TAG, "HandLandmarker initialized.");
        } catch (Exception e) {
            Log.e(TAG, "HandLandmarker initialization failed: ", e);
        }
    }
    public static CameraFragment newInstance( String fragmentName, String cameraId )
    {
        CameraFragment fragment = new CameraFragment();
        Bundle args = new Bundle();
        args.putString( ARG_FRAGMENT_NAME, fragmentName );
        args.putString( ARG_CAMERA_ID, cameraId );
        fragment.setArguments( args );
        return fragment;
    }

    @Override
    public void onAttach( @NonNull Context context )
    {
        super.onAttach( context );
        try{
            mListener = (OnFragmentInteractionListener) context;
        }catch( ClassCastException e ){
            throw new ClassCastException( context.toString() + " must implement OnFragmentInteractionListener" );
        }
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState )
    {
        Log.i(LOG_TAG, "info. onCreateView" );
        View view = inflater.inflate( R.layout.fragment_camera, container, false );
        overlayView = view.findViewById(R.id.overlayView);

        mBottomLayout = view.findViewById( R.id.bottomLayout );
        mTVDepth_1 = view.findViewById( R.id.tv_depth_step1 );
        mTVDepth_2 = view.findViewById( R.id.tv_depth_step2 );
        mTVDepth_3 = view.findViewById( R.id.tv_depth_step3 );
        mTVDepth_4 = view.findViewById( R.id.tv_depth_step4 );
        mTVDepth_5 = view.findViewById( R.id.tv_depth_step5 );
        mTVDepth_6 = view.findViewById( R.id.tv_depth_step6 );
        mTVDepth_7 = view.findViewById( R.id.tv_depth_step7 );

        mTVDepthCheck = view.findViewById( R.id.tv_depth_check );

        mBtnReConnect = view.findViewById( R.id.btnReConnect );
        DataUtils.setButtonStyle( mBtnReConnect );
        mBtnReConnect.setOnClickListener( (v) -> {
            Log.i(LOG_TAG, "info. click ReConnect." );
            reConnect();
        });

        mBtnStart = view.findViewById( R.id.btnStartStop );
        DataUtils.setButtonStyle( mBtnStart );
        mBtnStart.setOnClickListener( (v) -> {
            Log.i(LOG_TAG, "info. click Start&Stop." );
            if( mCameraController != null ){
                Log.i( LOG_TAG, "info. isCamera( " + mCameraController.isCameraInUse() + " ). isRunning( " + mCameraController.isRunning() + " ).");
            }

            if( mCameraController == null || !mCameraController.isCameraInUse() ){
                if( !isRunning() ){
                    openProcess();
                    mBtnStart.setText( "TOF STOP" );
                }
            }else{
                if( !isRunning() ){
                    closeProcess();
                    mBtnStart.setText( "TOF START" );
                }
            }
        });


        return view;
    }

    private boolean isRunning()
    {
        if( mCameraController == null )
            return false;

        return mCameraController.isRunning();
    }

    public boolean hasFocusLeftButton()
    {
        if( mBtnStart.getVisibility() == View.GONE )
            return mBtnReConnect.hasFocus();

        return mBtnStart.hasFocus();
    }

    public void focusCameraButton()
    {
        if( mBtnStart == null || mBtnReConnect == null )
            return;

        if( mBtnStart.getVisibility() == View.GONE || mBtnStart.hasFocus() ){
            mFocusedButton = mBtnReConnect;
        }else{
            mFocusedButton = mBtnStart;
        }

        mFocusedButton.requestFocus();
    }

    public void changeViewMode( int view_depth_flag)
    {
        if( mCameraController == null )
            return;

        if( !TextUtils.equals( mName, "TOFFragment" ) ) {
            mCameraController.applyMirrorEffect( mTextureView );
            return;
        }

        if( view_depth_flag > 0 )
            mIndexMode = 1;

        Log.i(TAG, "info. changeViewMode: " + mIndexMode );
        switch( mIndexMode ){
            case 0:
                //mCameraController.applyScaleEffect( mTextureView, 244 );
                mCameraController.applyMirrorEffect( mTextureView );
                break;
            case 1:
                mCameraController.applyQuarterEffect( mTextureView );

                break;
            default:
                break;
        }

        if( view_depth_flag <= 0 )
        {
            mIndexMode++;
            if( mIndexMode >= 2 ) mIndexMode = 0;
        }
    }

    public Button getCameraButton()
    {
        return mBtnReConnect;
    }

    @Override
    public void onViewCreated( final View view, Bundle savedInstanceState )
    {
        Log.i(LOG_TAG, "info. onViewCreated");
        mTextCameraMsg = view.findViewById( R.id.tvCameraMsg );
        mTextureView = view.findViewById( R.id.textureView );
        initPoseLandmarker(requireContext());
        initHandLandmarker(requireContext());
        if( getArguments() != null ) {
            mName = getArguments().getString(ARG_FRAGMENT_NAME);
            LOG_TAG = "say_" + mName;
            mCameraID = getArguments().getString(ARG_CAMERA_ID);
            Log.i(LOG_TAG, "info. " + mName + " Camera Id: " + mCameraID );

            if( !TextUtils.equals("-1", mCameraID ) ) {
                initCamera();

            }else{
                closeCamera( false );
            }
        }
    }

    private void initCamera()
    {
        if( mCameraController != null )
            closeCamera( false );

        mCameraController = new CameraController( getContext() );
        mCameraController.setCameraEventListener( new CameraController.OnCameraControllerInteractionListener()
        {
            @Override
            public void onCameraControllerMessage( Message msg )
            {
                final Activity activity = getActivity();
                if( activity != null ){
                    activity.runOnUiThread( new Runnable()
                    {
                        @Override
                        public void run() {
                            mBtnStart.setText((msg.arg1 == 1) ? "TOF STOP" : "TOF START");
                        }
                    } );
                }
            }
        });

        try{
            List<Size> arraySize = mCameraController.getSupportedResolutions( mCameraID );
            Log.i( LOG_TAG, "info. get supported resolutions size.ID( " + mCameraID + " )( " + arraySize.size() + " )." );
            for( Size size: arraySize ){
                Log.i( LOG_TAG, "info. get supported resolution.( " + mCameraID + " )( " + size.toString() + " )." );
            }

            boolean supportsMultiCamera = mCameraController.getSupportedMultiCamera( mCameraID );
            Log.i( LOG_TAG, "info. get supported multi camera.ID( " + mCameraID + " )( " + supportsMultiCamera + " )." );

        }catch( CameraAccessException e ){
            throw new RuntimeException( e );
        }
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        Log.i(LOG_TAG, "info. onDestroyView()");
        if( mCameraController != null ){
            closeCamera( true );
        }

        if( mHandler != null ){
            mHandler.removeCallbacksAndMessages( null );
            mHandler = null;
        }
    }

    @Override
    public void onResume()
    {
        Log.i(LOG_TAG, "info. onResume. has focus( " + mHasFocus + " )." );
        super.onResume();
        setFocus(true);
        openProcess();
    }

    @Override
    public void onPause()
    {
        Log.i(LOG_TAG, "info. onPause. has focus( " + mHasFocus + " )." );
        closeCamera( false );
        super.onPause();
    }

    public void setFocus( boolean isOpen )
    {
        mHasFocus = true;
        if( isOpen )
            openProcess();
        else
            mBtnStart.setVisibility( View.VISIBLE );
    }

    public void leaveFocus()
    {
        mHasFocus = false;
        mBottomLayout.setVisibility( View.INVISIBLE );
        closeCamera( false );
    }

    private void closeProcess()
    {
        Log.i( LOG_TAG, "info. close camera process." );
        closeCamera( false );
    }

    private void openProcess()
    {
        Log.i( LOG_TAG, "info. open camera process. has focus( " + mHasFocus + " )." );
        if( mHasFocus == false ){
            return;
        }
        if( TextUtils.equals("-1", mCameraID ) ) {
            mTextCameraMsg.setText( "CAM ID: " + mCameraID + "\n" + "카메라 연결 후 Reconnect 버튼 선택 필요");
            return;
        }
        mTextCameraMsg.setText( "CAM ID: " + mCameraID );

        if( mTextureView.isAvailable() ){
            Log.i(LOG_TAG, "info. textureView.isAvailable()" );

            openCamera( mCameraID, mTextureView.getWidth(), mTextureView.getHeight() );

        }else{
            mTextureView.setSurfaceTextureListener( new TextureView.SurfaceTextureListener()
            {
                @Override
                public void onSurfaceTextureAvailable( SurfaceTexture surface, int width, int height )
                {
                    Log.i(LOG_TAG, "info. onSurfaceTextureAvailable. width( " + width + " ). height( " + height + " )." );
                    mWidth = width;
                    mHeight = height;
                    openCamera( mCameraID, mWidth, mHeight );
                }
                @Override
                public void onSurfaceTextureSizeChanged( SurfaceTexture surface, int width, int height ){
                    Log.i(LOG_TAG, "info. onSurfaceTextureSizeChanged. width( " + width + " ). height( " + height + " )." );
                }
                @Override
                public boolean onSurfaceTextureDestroyed( SurfaceTexture surface )
                {
                    Log.i(LOG_TAG, "info. onSurfaceTextureDestroyed" );
                    return true;
                }
                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                    long now = System.currentTimeMillis();
                    if (now - lastProcessTime > 500) {
                        lastProcessTime = now;

                        if (poseLandmarker == null || mTextureView == null) return;

                        Bitmap bitmap = mTextureView.getBitmap();
                        if (bitmap == null) {
                            Log.e(TAG, "Bitmap is null!");
                            return;
                        }
                        MPImage mpImage = new BitmapImageBuilder(bitmap).build();

                        try {
                            // PoseLandmarker로부터 결과 얻기
                            PoseLandmarkerResult result = poseLandmarker.detectForVideo(mpImage, now);
                            //HandLandmarkerResult handResult = handLandmarker.detectForVideo(mpImage, now);

                            // 랜드마크가 비어 있지 않은지 확인
                            if (result.landmarks().isEmpty()) {
                                Log.i(TAG, "No landmarks detected.");
                                //return;  // 랜드마크가 없으면 처리하지 않음
                            }
                            else{
                                Log.i(TAG, "Detected landmarks: " + result.landmarks().size());
                                overlayView.setLandmarks(result.landmarks().get(0));  // 첫 번째 사람의 랜드마크
                            }

                            // 첫 번째 사람의 랜드마크 처리
                            List<NormalizedLandmark> landmarkList = result.landmarks().get(0);
                            for (int i = 0; i < landmarkList.size(); i++) {
                                float x = landmarkList.get(i).x();
                                float y = landmarkList.get(i).y();
                                //Log.d("POSE", "Pose Landmark[" + i + "]: x=" + x + ", y=" + y);
                            }

                            // 특정 동작 인식 (만세, 손 올리기 등)
                            float leftWristY = landmarkList.get(15).y();
                            float rightWristY = landmarkList.get(16).y();
                            float leftShoulderY = landmarkList.get(11).y();
                            float rightShoulderY = landmarkList.get(12).y();
                            Activity activity = getActivity();

                            boolean isHandsUp = leftWristY < leftShoulderY && rightWristY < rightShoulderY;
                            if (isHandsUp && now - lastGestureTime > GESTURE_COOLDOWN_MS) {
                                lastGestureTime = now;
                                adjustVolume(1);
                                Log.d("volume", "volume up");
                                if (activity != null) {
                                    activity.runOnUiThread(() ->
                                            Toast.makeText(activity, "만세", Toast.LENGTH_LONG).show()
                                    );
                                }
                            }

                            boolean isRightHandUp = rightWristY < rightShoulderY;
                            boolean isLeftHandDown = leftWristY >= leftShoulderY;
                            if (isRightHandUp && isLeftHandDown && now - lastGestureTime > GESTURE_COOLDOWN_MS) {
                                lastGestureTime = now;
                                adjustVolume(0);
                                Log.d("volume", "volume down");

                                if (activity != null) {
                                    activity.runOnUiThread(() ->
                                            Toast.makeText(activity, "오른손 up", Toast.LENGTH_LONG).show()
                                    );
                                }

                            }

                        } catch (Exception e) {
                            //Log.e(TAG, "Pose detection failed", e);
                        }
                        /*
                        try {
                            HandLandmarkerResult handResult = handLandmarker.detectForVideo(mpImage, now);
                            Log.i(TAG, "Hands detected: " + handResult.landmarks().size());

                            for (List<NormalizedLandmark> handLandmarks : handResult.landmarks()) {
                                boolean isVSign = isVGesture(handLandmarks);

                                if (isVSign) {
                                    Activity activity = getActivity();
                                    if (activity != null) {
                                        activity.runOnUiThread(() ->
                                                Toast.makeText(activity, "V 사인 인식됨!", Toast.LENGTH_SHORT).show()
                                        );
                                    }
                                }
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "Hand detection failed", e);
                        }*/
                    }
                }
            });
        }
    }
    private boolean isVGesture(List<NormalizedLandmark> landmarks) {
        // 간단한 기준: 검지(8)와 중지(12)는 펴져 있고, 약지(16)와 새끼손(20)은 접혀 있음
        float yWrist = landmarks.get(0).y();

        float yIndex = landmarks.get(8).y();   // 검지 끝
        float yMiddle = landmarks.get(12).y(); // 중지 끝
        float yRing = landmarks.get(16).y();   // 약지 끝
        float yPinky = landmarks.get(20).y();  // 새끼 끝

        return yIndex < yWrist && yMiddle < yWrist && yRing > yWrist && yPinky > yWrist;
    }

    private void reConnect()
    {
        Message msg = new Message();
        msg.arg1 = 0;
        msg.obj = mName;
        mListener.onFragmentMessage( msg );
    }

    public void changemCameraID( String CameraID )
    {
        Log.i(LOG_TAG, "info. " + mName + " Change Camera Id: " + mCameraID + " > " + CameraID );
        mCameraID = CameraID;
        closeProcess();
        openProcess();
    }

    private void openCamera( String CameraID, int width, int height )
    {
        Log.i(LOG_TAG, "info. openCamera ID( " + CameraID + " )." );
        if( TextUtils.equals("-1", CameraID ) )
            return;

        if( mCameraController == null ) {
            Log.i(LOG_TAG, "info. CameraController is null." );
            initCamera();
        }
        if (mTextureView != null && mTextureView.isAvailable()) {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            if (texture != null) {
                try {
                    texture.detachFromGLContext();  // 🔥 이전 연결 강제 해제 (중요)
                } catch (Exception e) {
                    Log.w(TAG, "SurfaceTexture.detachFromGLContext failed: " + e.getMessage());
                }
                texture.setDefaultBufferSize(width, height);
                Log.i(TAG, "SurfaceTexture reset for size: " + width + "x" + height);
            }
        }

        mCameraController.openCamera( getActivity(), CameraID, mTextureView, width, height );
        mIndexMode = 1;
        changeViewMode(0);
    }

    private void closeCamera( boolean isdestroy )
    {
        Log.i(LOG_TAG, "info. closeCamera" );

        if( mCameraController != null ) {
            mCameraController.closeCamera();
            if( isdestroy ) mCameraController.destroyCamera();
            mCameraController = null;
        }
        Log.i(LOG_TAG, "info. closeCamera. out" );
    }

    public static class ErrorDialog extends DialogFragment
    {
        private static final String ARG_MESSAGE = "message";
        public static ErrorDialog newInstance( String message )
        {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString( ARG_MESSAGE, message );
            dialog.setArguments( args );
            return dialog;
        }
        @NonNull
        @Override
        public Dialog onCreateDialog( Bundle savedInstanceState )
        {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage( getArguments().getString( ARG_MESSAGE ) )
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick( DialogInterface dialogInterface, int i )
                        {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }

    public static class ConfirmationDialog extends DialogFragment
    {
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState )
        {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder( getActivity() )
                    .setMessage( "Dialog." )
                    .setPositiveButton( android.R.string.ok, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick( DialogInterface dialog, int which )
                        {
                        }
                    })
                    .setNegativeButton( android.R.string.cancel, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick( DialogInterface dialog, int which )
                        {
                            Activity activity = parent.getActivity();
                            if( activity != null ){
                                activity.finish();
                            }
                        }
                    })
                    .create();
        }
    }
    private void showToast( final String text )
    {
        final Activity activity = getActivity();
        if( activity != null ){
            activity.runOnUiThread( new Runnable()
            {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            } );
        }
    }
    private void adjustVolume(int i) {
        Activity activity = getActivity();
        if (activity != null) {
            AudioManager audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
            int current = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM);
            Log.i(TAG, "Current volume: " + current + "/" + max);
            if(i==1){
                audioManager.adjustStreamVolume(
                        AudioManager.STREAM_SYSTEM,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                );
            }
            else{
                audioManager.adjustStreamVolume(
                        AudioManager.STREAM_SYSTEM,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                );
            }
        }
    }




}
