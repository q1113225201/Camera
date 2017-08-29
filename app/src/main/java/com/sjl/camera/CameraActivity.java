package com.sjl.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import com.sjl.camera.util.BitmapUtil;
import com.sjl.camera.util.PermisstionUtil;

import java.io.IOException;

public class CameraActivity extends Activity {
    private static final String TAG = "CameraActivity";
    private static final String FILEPATH = Environment.getExternalStorageDirectory() + "/MyCamera/";
    private Context context;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private int currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        initView();
    }

    private void initView() {
        context = this;
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                openCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                startPreview(camera, holder);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopPreview();
            }
        });
        surfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(camera==null){
                    return;
                }
                camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        Log.i(TAG, "自动对焦：" + success);
                    }
                });
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
    }

    /**
     * 打开照相机
     */
    private void openCamera() {
        //权限请求
        PermisstionUtil.requestPermissions(context, PermisstionUtil.CAMERA, 100, "正在请求拍照权限", new PermisstionUtil.OnPermissionResult() {
            @Override
            public void granted(int requestCode) {
                if (camera == null) {
                    camera = Camera.open(currentCameraId);
                }
                startPreview(camera, surfaceHolder);
            }

            @Override
            public void denied(int requestCode) {
                Toast.makeText(context, "拍照权限被禁止", Toast.LENGTH_SHORT).show();
            }
        });
    }
    /**
     * 打开预览
     */
    private void startPreview(Camera camera, SurfaceHolder surfaceHolder) {
        if (camera == null) {
            return;
        }
        try {
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止预览释放相机
     */
    private void stopPreview() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    /**
     * 拍照
     */
    private void takePhoto() {
        camera.takePicture(new Camera.ShutterCallback() {
            @Override
            public void onShutter() {
                //按下快门
            }
        }, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {

            }
        }, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                savePicture(data);
                startPreview(camera, surfaceHolder);
            }
        });
    }

    /**
     * 切换摄像头
     */
    private void switchCamera() {
        int count = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < count; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                //后置变前置
                stopPreview();
                currentCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
                openCamera();
                break;
            } else if (currentCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                //前置变后置
                stopPreview();
                currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
                openCamera();
                break;
            }
        }
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
                            matrix.setRotate(currentCameraId==Camera.CameraInfo.CAMERA_FACING_BACK?90:270);
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
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermisstionUtil.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
