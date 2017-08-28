package com.sjl.camera.util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.text.TextUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class BitmapUtil {
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // 源图片的高度和宽度
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            // 计算出实际宽高和目标宽高的比率
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            // 选择宽和高中最小的比率作为inSampleSize的值，这样可以保证最终图片的宽和高
            // 一定都会大于等于目标的宽和高。
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId, int reqWidth, int reqHeight) {
        // 第一次解析将inJustDecodeBounds设置为true，来获取图片大小
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);
        // 调用上面定义的方法计算inSampleSize值
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        // 使用获取到的inSampleSize值再次解析图片
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    public static Bitmap decodeSampledBitmapFromFilePath(String filePath, int reqWidth, int reqHeight) {
        // 第一次解析将inJustDecodeBounds设置为true，来获取图片大小
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
//	    InputStream is = new FileInputStream(filePath);
//	    System.out.println("is=" + is);
        BitmapFactory.decodeFile(filePath, options);
        // 调用上面定义的方法计算inSampleSize值
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        // 使用获取到的inSampleSize值再次解析图片
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filePath, options);
    }

    /**
     * 旋转图片
     *
     * @param bitmap            Bitmap
     * @param orientationDegree 旋转角度
     * @return 旋转之后的图片
     */
    public static Bitmap rotate(Bitmap bitmap, int orientationDegree) {
        Matrix matrix = new Matrix();
        matrix.reset();
        matrix.setRotate(orientationDegree);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                bitmap.getHeight(), matrix, true);
    }

    /**
     * 保存bitmap图片
     *
     * @param bitmap
     * @param outFile
     * @return
     * @throws IOException
     */
    public static boolean save(Bitmap bitmap, String outFile)
            throws IOException {
        if (TextUtils.isEmpty(outFile) || bitmap == null)
            return false;
        byte[] data = bitmap2byte(bitmap);
        return save(data, outFile);
    }

    /**
     * 保存图片字节
     *
     * @param bitmapBytes
     * @param outFile
     * @return
     * @throws IOException
     */
    public static boolean save(byte[] bitmapBytes, String outFile)
            throws IOException {
        FileOutputStream output = null;
        FileChannel channel = null;
        try {
            new File(outFile).delete();
            FileUtil.createFile(outFile);
            output = new FileOutputStream(outFile);
            channel = output.getChannel();
            ByteBuffer buffer = ByteBuffer.wrap(bitmapBytes);
            channel.write(buffer);
            return true;
        } finally {
            IOUtil.close(channel);
            IOUtil.close(output);
        }
    }

    /**
     * 将Bitmap转化为字节数组
     *
     * @param bitmap
     * @return byte[]
     * @throws IOException
     */
    public static byte[] bitmap2byte(Bitmap bitmap) throws IOException {
        ByteArrayOutputStream baos = null;
        try {
            baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            byte[] array = baos.toByteArray();
            baos.flush();
            return array;
        } finally {
            IOUtil.close(baos);
        }
    }

    /**
     * 获取压缩图片
     *
     * @param srcPath
     * @param width
     * @param height
     * @return
     */
    private Bitmap compressImage(String srcPath, float width, float height) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        //只获取图片信息
        options.inJustDecodeBounds = true;
        Bitmap bitmap = BitmapFactory.decodeFile(srcPath, options);

        options.inJustDecodeBounds = false;
        int scaleWidth = (int) (options.outWidth / width);
        int scaleHeight = (int) (options.outHeight / height);
        //获取较大的压缩比
        int scale = scaleWidth > scaleHeight ? scaleWidth : scaleHeight;
        scale = scale <= 0 ? 1 : scale;
        options.inSampleSize = scale;
        bitmap = BitmapFactory.decodeFile(srcPath, options);
        return compressImage(bitmap);//大小压缩完后进行质量压缩
    }

    /**
     * 质量压缩图片
     *
     * @param bitmap
     * @return
     */
    public static Bitmap compressImage(Bitmap bitmap) {
        //图片质量
        int options = 100;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        //质量压缩方法，这里100表示不压缩，把压缩后的数据存放到baos中
        bitmap.compress(Bitmap.CompressFormat.JPEG, options, baos);
        while (baos.toByteArray().length / 1024 > 100) {//如果图片大于100k，进行压缩
            //清空baos
            baos.reset();
            options -= 10;
            bitmap.compress(Bitmap.CompressFormat.JPEG, options, baos);
        }
        //压缩后的数据存放在bais中
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        //生成图片
        Bitmap resultBmp = BitmapFactory.decodeStream(bais, null, null);
        return resultBmp;
    }
}
