package com.example.cameraapplication;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraController
{
    private static final String TAG = "say_CameraController";
    private static final int MAX_PREVIEW_WIDTH = 1920; //Max preview width that is guaranteed by Camera2 API
    private static final int MAX_PREVIEW_HEIGHT = 1080; //Max preview height that is guaranteed by Camera2 API
    private Activity mActivity;
    private TextureView mTextureView;
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private Size mPreviewSize;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private boolean mIsCameraInUse = false;
    private boolean mIsRunning = false;
    //private ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    public interface OnCameraControllerInteractionListener{
        void onCameraControllerMessage( Message msg );
    }
    private OnCameraControllerInteractionListener mListener = null;

    public CameraController( Context context )
    {
        Log.i(TAG, "info. CameraController");
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    public void setCameraEventListener( OnCameraControllerInteractionListener listener )
    {
        mListener = listener;
    }

    private void updateRunningState( boolean isRunning )
    {
        Log.i( TAG, "info. update running state( " + isRunning + " )." );
        mIsRunning = isRunning;
    }
    private void updateCameraState( boolean isCameraInUse )
    {
        Log.i( TAG, "info. update camera state( " + isCameraInUse + " )." );
        mIsCameraInUse = isCameraInUse;
        if( mListener == null )
            return;
        Message msg = new Message();
        msg.arg1 = isCameraInUse ? 1 : 0;
        mListener.onCameraControllerMessage( msg );
    }

    public boolean isCameraInUse()
    {
        Log.i( TAG, "info. is camera using state: " + mIsCameraInUse );
        return mIsCameraInUse;
    }

    public boolean isRunning()
    {
        Log.i( TAG, "info. is running state: " + mIsRunning );
        return mIsRunning;
    }

    public List<String> getAvailableCameras() throws CameraAccessException
    {
        return Arrays.asList( mCameraManager.getCameraIdList() );
    }

    public List<Size> getSupportedResolutions( String cameraId ) throws CameraAccessException
    {
        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics( cameraId );
        boolean isZoomSupported = characteristics.get( CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM ) > 1.0;
        boolean isFlashSupported = characteristics.get( CameraCharacteristics.FLASH_INFO_AVAILABLE );
        Log.i(TAG, "info. Zoom Supported ( " + isZoomSupported + " ).");
        Log.i(TAG, "info. Flash Supported ( " + isFlashSupported + " ).");

        StreamConfigurationMap map = characteristics.get( CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP );
        int[] formats = map.getOutputFormats();
        for( int format : formats ){
            Log.d(TAG, "info. Supported format: " + format);
        }

        if( map != null ){
            return Arrays.asList( map.getOutputSizes( SurfaceTexture.class ) );
        }else{
            return Collections.emptyList();
        }
    }

    public boolean getSupportedMultiCamera( String cameraId ) throws CameraAccessException
    {
        CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics( cameraId );
        int[] capabilities = characteristics.get( CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES );
        boolean supportsMultiCamera = Arrays.asList( capabilities ).contains( CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA );

        return supportsMultiCamera;
    }

    public void openCamera( Activity activity, String cameraId, TextureView textureView, int width, int height )
    {
        Log.i(TAG, "info. openCamera. in");
        updateCameraState( true );
        updateRunningState( true );
        mActivity = activity;
        mTextureView = textureView;

        startBackgroundThread();

        if( textureView.isAvailable() ){
            Log.i(TAG, "info. textureView.isAvailable(). Camera Id : " + cameraId);
            try{
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if( facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT ){
                    Log.e(TAG, "warning. facing failed");
                }
                StreamConfigurationMap map = characteristics.get( CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP );
                if( map == null ){
                    Log.e(TAG, "warning. SCALER_STREAM_CONFIGURATION_MAP failed");
                }

                Size largest = Collections.max( Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea() );
                Point displaySize = new Point();
                mActivity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                mPreviewSize = chooseOptimalSize( map.getOutputSizes(SurfaceTexture.class), rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest );
                //List<Size> sizes = getSupportedResolutions( cameraId );
                //mPreviewSize = sizes.get( 11 );
                //Size size = new Size(640, 240);
                Log.i( TAG, "info. camera resolution.( " + mPreviewSize.toString() + " )." );

                if( !mCameraOpenCloseLock.tryAcquire( 2500, TimeUnit.MILLISECONDS ) ){
                    throw new RuntimeException("Time out waiting to lock camera opening.");
                }

                if( ActivityCompat.checkSelfPermission( mActivity, Manifest.permission.CAMERA ) != PackageManager.PERMISSION_GRANTED ) return;
                mCameraManager.openCamera(cameraId, mStateCallback, mBackgroundHandler);
            }catch( CameraAccessException e ){
                Log.e(TAG, "CameraAccessException: " + e.getMessage());
                e.printStackTrace();
                updateRunningState( false );
            }catch( NullPointerException e ){
                Log.e(TAG, "NullPointerException caught", e);
                updateRunningState( false );
            }catch( InterruptedException e ){
                throw new RuntimeException( "Interrupted while trying to lock camera opening.", e );
            }
        }
        Log.i(TAG, "info. openCamera. out");
    }

    public void closeCamera()
    {
        Log.d(TAG, "info. closeCamera. in");
        try{
            mCameraOpenCloseLock.acquire();
            if( null != mCameraDevice ){
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }catch( InterruptedException e ){
            Log.e(TAG, "warning. Error during closeCamera: " + e.getMessage());
        }catch( NullPointerException e ){
            Log.e(TAG, "NullPointerException caught", e);
        }finally{
            mCameraOpenCloseLock.release();
            stopBackgroundThread();
            updateCameraState( false );
            updateRunningState( false );
        }
        Log.d(TAG, "info. closeCamera. out");
    }

    public void destroyCamera()
    {
        Log.i(TAG, "info. destroyCamera.");
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback()
    {
        @Override
        public void onOpened( @NonNull CameraDevice cameraDevice )
        {
            Log.d(TAG, "info. onOpened - CameraDevice");
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
            updateCameraState( true );
            updateRunningState( false );
        }

        @Override
        public void onDisconnected( @NonNull CameraDevice cameraDevice )
        {
            Log.d(TAG, "info. onDisconnected - CameraDevice");
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            updateCameraState( false );
            updateRunningState( false );
        }

        @Override
        public void onError( @NonNull CameraDevice cameraDevice, int error )
        {
            Log.d(TAG, "info. onError - CameraDevice, error code: " + error);
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            updateCameraState( false );
            updateRunningState( false );
            switch (error) {
                case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                    Log.e(TAG, "error. Camera is already in use.");
                    break;
                case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                    Log.e(TAG, "error. Max number of cameras are in use.");
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                    Log.e(TAG, "error. Camera is disabled due to device policies.");
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                    Log.e(TAG, "error. Fatal error occurred with the camera device.");
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                    Log.e(TAG, "error. Fatal error occurred in the camera service.");
                    break;
                default:
                    Log.e(TAG, "error. Unknown camera error.");
                    break;
            }
        }
    };

    private void createCameraPreviewSession()
    {
        try{
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            Surface surface = new Surface(texture);
            //ImageReader imageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            //Surface imageReaderSurface = imageReader.getSurface();
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest( CameraDevice.TEMPLATE_PREVIEW );
            /*
            mPreviewRequestBuilder.set( CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ); //For Autofocus
            mPreviewRequestBuilder.set( CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 2 ); //For AE
            mPreviewRequestBuilder.set( CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO ); //For WB
            mPreviewRequestBuilder.set( CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH ); //For Flash
            */
            mPreviewRequestBuilder.addTarget( surface );

            //mCameraDevice.createCaptureSession( Arrays.asList(surface, imageReaderSurface), new CameraCaptureSession.StateCallback()
            mCameraDevice.createCaptureSession( Arrays.asList(surface), new CameraCaptureSession.StateCallback()
            {
                @Override
                public void onConfigured( @NonNull CameraCaptureSession cameraCaptureSession )
                {
                    if( null == mCameraDevice )
                        return;

                    try{
                        mPreviewRequestBuilder.set( CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE );
                        cameraCaptureSession.setRepeatingRequest( mPreviewRequestBuilder.build(), null, mBackgroundHandler );
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "CameraAccessException: " + e.getMessage());
                        e.printStackTrace();
                    }

                    //applyMirrorEffect( mTextureView );
                }

                @Override
                public void onConfigureFailed( @NonNull CameraCaptureSession cameraCaptureSession )
                {
                    Log.e( TAG, "warning. camera configure failed.( " + cameraCaptureSession.toString() + " )." );
                    //showToast( "Failed" );
                }
            }, null);
        }catch( CameraAccessException e ){
            Log.e(TAG, "CameraAccessException: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void applyQuarterEffect(TextureView textureView) {
        Matrix matrix = new Matrix();
        float width = textureView.getWidth();
        float height = textureView.getHeight();
        /*
        matrix.preScale(-2.0f, 2.0f, width / 2, height / 2);
        float translateX = width / 2;
        float translateY = height / 2;
         */
        matrix.preScale(-2.0f, 1.0f, width / 2, height / 2);
        float translateX = (width / 2) +2 ;
        float translateY = 0;
        matrix.postTranslate( -translateX, translateY );

        textureView.setTransform(matrix);
    }

    public void applyScaleEffect(TextureView textureView, float originY )
    {
        Matrix matrix = new Matrix(); // 1/61
        float width = textureView.getWidth();
        float height = textureView.getHeight();
        float makeY = originY - (originY / 61);
        float scaleY = originY / makeY;
        matrix.preScale( -1.0f, scaleY, width / 2, height / 2 );
        float translateX = 0;
        float translateY = (( height * scaleY ) - height) / 2;
        matrix.postTranslate( -translateX, translateY );
        textureView.setTransform(matrix);
    }

    public void applyMirrorEffect( TextureView textureView )
    {
        Matrix matrix = new Matrix();
        float width = textureView.getWidth();
        float height = textureView.getHeight();
        matrix.preScale( -1.0f, 1.0f, width / 2, height / 2 );
        textureView.setTransform(matrix);
    }

    private void startBackgroundThread()
    {
        Log.i(TAG, "info. startBackgroundThread");
        mBackgroundThread = new HandlerThread( "CameraBackground" );
        mBackgroundThread.start();
        mBackgroundHandler = new Handler( mBackgroundThread.getLooper() );
    }

    private void stopBackgroundThread()
    {
        Log.i(TAG, "info. stopBackgroundThread");
        if( mBackgroundThread != null ){
            mBackgroundThread.quitSafely();
            try{
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            }catch( InterruptedException e ){
                e.printStackTrace();
            }
        }
    }

    static class CompareSizesByArea implements Comparator<Size>
    {
        @Override
        public int compare( Size lhs, Size rhs )
        {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight() );
        }
    }

    private static Size chooseOptimalSize( Size[] choices, int textureViewWidth, int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio )
    {
        Log.i(TAG, "info. chooseOptimalSize");
        List<Size> bigEnough = new ArrayList<>();
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for( Size option : choices ){
            if( option.getWidth() <= maxWidth && option.getHeight() <= maxHeight && option.getHeight() == option.getWidth() * h / w ){
                if( option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight ){
                    bigEnough.add( option );
                }else{
                    notBigEnough.add( option );
                }
            }
        }

        if( bigEnough.size() > 0 ){
            return Collections.min( bigEnough, new CompareSizesByArea() );
        }else if( notBigEnough.size() > 0 ){
            return Collections.max( notBigEnough, new CompareSizesByArea() );
        }else{
            Log.e( TAG, "Couldn't find any suitable preview size" );
            return choices[ 0 ];
        }
    }
}