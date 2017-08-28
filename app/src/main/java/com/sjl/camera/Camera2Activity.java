package com.sjl.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import com.sjl.camera.util.BitmapUtil;
import com.sjl.camera.util.PermisstionUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

@SuppressLint("NewApi")
public class Camera2Activity extends Activity {
    private static final String TAG = "Camera2Activity";
    private static final String FILEPATH = Environment.getExternalStorageDirectory() + "/MyCamera/";
    private Context context;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private int currentCameraType = CameraCharacteristics.LENS_FACING_BACK;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private ImageReader imageReader;

    private HandlerThread handlerThread;
    private Handler childHandler;
    private Handler mainHandler;

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            stopPreview();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Toast.makeText(context, "摄像头开启失败", Toast.LENGTH_SHORT).show();
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);

        initView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        openCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPreview();
    }

    private void initView() {
        context = this;
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
        findViewById(R.id.btnTakePhoto).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });
        findViewById(R.id.btnSwitch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchCamera();
            }
        });
        initCamera();
    }

    /**
     * 初始化
     */
    private void initHandler() {
        //Camera2全程异步
        handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        childHandler = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(getMainLooper());
    }

    /**
     * 初始化摄像头
     */
    private void initCamera() {
        PermisstionUtil.requestPermissions(context, PermisstionUtil.CAMERA, 101, "正在请求拍照权限", new PermisstionUtil.OnPermissionResult() {
            @Override
            public void granted(int requestCode) {
                initHandler();
                initImageReader();
//                openCamera();
            }

            @Override
            public void denied(int requestCode) {
                Toast.makeText(context, "拍照权限被禁止", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 打开摄像头
     */
    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            cameraManager.openCamera(String.valueOf(currentCameraType), stateCallback, mainHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void initImageReader() {
        surfaceView.measure(0,0);
        Log.i(TAG,surfaceView.getWidth()+","+surfaceView.getHeight());
        imageReader = ImageReader.newInstance(surfaceView.getWidth(),surfaceView.getHeight(), ImageFormat.JPEG,1);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                cameraDevice.close();
                Image image = reader.acquireNextImage();
                ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);
                savePicture(bytes);
                openCamera();
            }
        },mainHandler);
    }

    /**
     * 保存图像
     *
     * @param data
     */
    private void savePicture(final byte[] data) {
        PermisstionUtil.requestPermissions(context, PermisstionUtil.STORAGE, 101, "正在获取读写权限", new PermisstionUtil.OnPermissionResult() {
            @Override
            public void granted(int requestCode) {
                Toast.makeText(context, "正在保存照片。。。", Toast.LENGTH_LONG).show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Matrix matrix = new Matrix();
                            matrix.setRotate(currentCameraType== Camera.CameraInfo.CAMERA_FACING_BACK?90:270);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(data,0,data.length);
                            bitmap = Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
                            BitmapUtil.save(bitmap, FILEPATH + System.currentTimeMillis() + ".jpg");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(context, "照片保存成功", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }

            @Override
            public void denied(int requestCode) {
                Toast.makeText(context, "读写权限被禁止", Toast.LENGTH_SHORT).show();
            }
        });
    }
    /**
     * 开始预览
     */
    private void startPreview() {
        try {
            final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surfaceHolder.getSurface());
            cameraDevice.createCaptureSession(Arrays.asList(surfaceHolder.getSurface(), imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) {
                        return;
                    }
                    cameraCaptureSession = session;
                    try {
                        //自动对焦
                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                        //显示预览
                        CaptureRequest previewRequest = builder.build();
                        cameraCaptureSession.setRepeatingRequest(previewRequest, null, childHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(context, "配置失败", Toast.LENGTH_SHORT).show();
                }
            }, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 结束预览
     */
    private void stopPreview() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    /**
     * 拍照
     */
    private void takePhoto() {
        if(cameraDevice==null){
            return;
        }
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());

            cameraCaptureSession.capture(builder.build(),null,childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 切换摄像头
     */
    private void switchCamera() {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermisstionUtil.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
