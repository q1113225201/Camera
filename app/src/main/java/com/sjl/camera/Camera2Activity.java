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
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
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
    private int currentCameraId = CameraCharacteristics.LENS_FACING_FRONT;

    private CameraManager cameraManager;
    private HandlerThread handlerThread;
    private Handler handler;
    private Size previewSize;
    private CameraDevice cameraDevice;
    private ImageReader imageReader;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder previewBuilder;
    private CaptureRequest.Builder captureBuilder;

    //打开相机回调
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.i(TAG, "onOpened");
            //相机开启，打开预览
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.i(TAG, "onDisconnected");
            //相机关闭
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.i(TAG, "onError");
            //相机报错
            camera.close();
            cameraDevice = null;
            finish();
        }
    };

    //创建session回调
    private CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            try {
                cameraCaptureSession = session;
                previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                cameraCaptureSession.setRepeatingRequest(previewBuilder.build(), null, handler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            session.close();
            cameraCaptureSession = null;
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    //拍完照回调
    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
//            super.onCaptureProgressed(session, request, partialResult);
            try {
                //自动对焦
                captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                //重新打开预览
                session.setRepeatingRequest(previewBuilder.build(), null, handler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            cameraCaptureSession.close();
            cameraCaptureSession = null;
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);

        initView();
    }

    private void initView() {
        context = this;
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.i(TAG, "surfaceCreated");
                openCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.i(TAG, "surfaceChanged");
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.i(TAG, "surfaceDestroyed");
                closeCamera();
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
        initData();
    }

    private void initData() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        //Camera2全程异步
        handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }


    /**
     * 打开相机
     */
    private void openCamera() {
        PermisstionUtil.requestPermissions(context, PermisstionUtil.CAMERA, 101, "正在请求拍照权限", new PermisstionUtil.OnPermissionResult() {
            @Override
            public void granted(int requestCode) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                try {
                    //获取属性CameraDevice属性描述
                    CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(String.valueOf(currentCameraId));
                    //获取摄像头支持的配置属性
                    StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    previewSize = getMaxSize(map.getOutputSizes(SurfaceHolder.class));
                    initImageReader();
                    //第一个参数指定哪个摄像头，第二个参数打开摄像头的状态回调，第三个参数是运行在哪个线程(null是当前线程)
                    cameraManager.openCamera(String.valueOf(currentCameraId), stateCallback, handler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void denied(int requestCode) {

            }
        });
    }

    /**
     * 初始化拍照处理器
     */
    private void initImageReader() {
        imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 1);
        //监听ImageReader时间，有图像数据可用时回调，参数就是帧数据
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireLatestImage();
                //将帧数据转换成字节数组
                ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                byte[] data = new byte[byteBuffer.remaining()];
                byteBuffer.get(data);
                savePicture(data);
                image.close();
            }
        }, handler);
    }

    /**
     * 开始预览
     */
    private void startPreview() {
        try {
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //设置预览数据输出界面
            previewBuilder.addTarget(surfaceHolder.getSurface());
            //创建相机捕获会话，
            //第一个参数是捕获数据的输出Surface列表，第二个参数是CameraCaptureSession状态回调接口，第三个参数是在哪个线程(null是当前线程)
            cameraDevice.createCaptureSession(Arrays.asList(surfaceHolder.getSurface(), imageReader.getSurface()), sessionStateCallback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * 拍照
     */
    private void takePhoto() {
        try {
            captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            //自动对焦
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            cameraCaptureSession.stopRepeating();
            //拍照
            cameraCaptureSession.capture(captureBuilder.build(), captureCallback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 切换摄像头
     */
    private void switchCamera() {
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size maxSize = getMaxSize(map.getOutputSizes(SurfaceHolder.class));
                if (currentCameraId == CameraCharacteristics.LENS_FACING_BACK && characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    //前置转后置
                    previewSize = maxSize;
                    currentCameraId = CameraCharacteristics.LENS_FACING_FRONT;
                    cameraDevice.close();
                    openCamera();
                    break;
                } else if (currentCameraId == CameraCharacteristics.LENS_FACING_FRONT && characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    //后置转前置
                    previewSize = maxSize;
                    currentCameraId = CameraCharacteristics.LENS_FACING_BACK;
                    cameraDevice.close();
                    openCamera();
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭相机
     */
    private void closeCamera() {
        //关闭捕捉会话
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        //关闭相机
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        //关闭拍照处理器
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    /**
     * 获取最大预览尺寸
     *
     * @param outputSizes
     * @return
     */
    private Size getMaxSize(Size[] outputSizes) {
        Size sizeMax = null;
        if (outputSizes != null) {
            sizeMax = outputSizes[0];
            for (Size size : outputSizes) {
                if (size.getWidth() * size.getHeight() > sizeMax.getWidth() * sizeMax.getHeight()) {
                    sizeMax = size;
                }
            }
        }
        return sizeMax;
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
                            matrix.setRotate(currentCameraId == CameraCharacteristics.LENS_FACING_FRONT ? 90 : 270);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermisstionUtil.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
