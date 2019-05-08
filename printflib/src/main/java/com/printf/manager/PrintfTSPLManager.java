package com.printf.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Environment;

import com.printf.interfaceCall.MultiplePrintfResultCallBack;
import com.printf.interfaceCall.PrintfResultCallBack;
import com.printf.model.PrintfModel;
import com.printf.model.TSPLPrinterModel;
import com.printf.utils.ImageUtil;
import com.printf.utils.Util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class PrintfTSPLManager {

    /**
     * 是否取消打印
     */
    private boolean isCancelPrinter = false;

    //取消打印
    public void cancelPrinter(){
        isCancelPrinter = true;
    }

    private Context context;
    private BluetoothManager bluetoothManager;

    static class PrintfLabelManagerHolder {
        private static PrintfTSPLManager instance = new PrintfTSPLManager();
    }

    public static PrintfTSPLManager getInstance(final Context context) {
        if (PrintfTSPLManager.PrintfLabelManagerHolder.instance.context == null) {
            PrintfTSPLManager.PrintfLabelManagerHolder.instance.context
                    = context.getApplicationContext();
            PrintfLabelManagerHolder.instance.bluetoothManager = BluetoothManager.getInstance(context);
        }
        return PrintfTSPLManager.PrintfLabelManagerHolder.instance;
    }

    private PrintfTSPLManager() {
    }

    /**
     * 发送数据
     * -1:数据发送失败 蓝牙未连接
     * 1:数据发送成功
     * -2:数据发送失败 抛出异常 失败
     */
    private int sendBytes(byte[] bytes) {
        int write = bluetoothManager.write(bytes);
        return write;
    }

    public void printfLabelsAsync(final List<TSPLPrinterModel> TSPLPrinterModels, final MultiplePrintfResultCallBack multiplePrintfResultCallBack) {
        if (TSPLPrinterModels == null || TSPLPrinterModels.size() <= 0) {
            return;
        }
        //判断蓝牙是否连接
        boolean connect = bluetoothManager.isConnect();
        if (!connect) {
            multiplePrintfResultCallBack.printfCompleteResult(MultiplePrintfResultCallBack.MULTIPLE_PRINTF_ERROR);
            return;
        }

        ThreadExecutorManager.getInstance(context).getCachedThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                for(int i = 0; i < TSPLPrinterModels.size(); i++){
                    final int finalI = i;
                    //当前方法，是同步的方法 所以，这里是阻塞的
                    printfLabel(TSPLPrinterModels.get(i), new PrintfResultCallBack() {
                        @Override
                        public void callBack(int result) {
                            if (result == PrintfResultCallBack.PRINTF_RESULT_SUCCESS) {
                                multiplePrintfResultCallBack.printfIndexResult(MultiplePrintfResultCallBack.MULTIPLE_PRINTF_SUCCESS, finalI + 1, 0);
                            } else {
                                multiplePrintfResultCallBack.printfIndexResult(MultiplePrintfResultCallBack.MULTIPLE_PRINTF_ERROR, finalI + 1, 0);
                                isCancelPrinter = true;
                            }
                        }
                    });
                    if(isCancelPrinter){
                        isCancelPrinter = false;
                        multiplePrintfResultCallBack.printfCompleteResult
                                (MultiplePrintfResultCallBack.MULTIPLE_PRINTF_INTERRUPT);
                        break;
                    }
                }

            }
        });
    }


    /**
     * 处理图片 以及坐标的转化
     */
    private TSPLPrinterModel handleLabelPrinterModel(TSPLPrinterModel tSPLPrinterModel) {

        //打印方向
        int printfDirection = tSPLPrinterModel.getPrintfDirection();

        //标签的宽度
        int labelW = tSPLPrinterModel.getLabelW();
        int labelH = tSPLPrinterModel.getLabelH();

        List<PrintfModel> printfModels = tSPLPrinterModel.getPrintfModels();

        for (int i = 0; i < printfModels.size(); i++) {

            PrintfModel printfModel = printfModels.get(i);

            int rotate = printfModel.getRotate();

            int bitmapWMM = printfModel.getBitmapW();
            int bitmapHMM = printfModel.getBitmapH();

            float left = printfModel.getX();
            float top = printfModel.getY();

            //旋转的点
            PointF rotatePoint = printfModel.getRotatePoint();
            if (rotatePoint == null) {
                rotatePoint = new PointF();
                rotatePoint.x = (left + bitmapWMM / 2);
                rotatePoint.y = (top + bitmapHMM / 2);
            }

            Bitmap bitmap = handleBitmap(printfModel.getBitmap(),
                    bitmapWMM * tSPLPrinterModel.getMM_TO_PX(),
                    bitmapHMM * tSPLPrinterModel.getMM_TO_PX(), rotate + printfDirection + 180);
            //求出四个点
            PointF leftTopPoint = new PointF(left, top);
            PointF rightTopPoint = new PointF(left + bitmapWMM, top);
            PointF leftBottomPoint = new PointF(left, top + bitmapHMM);
            PointF rightBottomPoint = new PointF(left + bitmapWMM, top + bitmapHMM);

            //处理打印方向
            //需要先处理，打印方向
            if (printfDirection == 90) {

                rotatePoint = new PointF(labelH - rotatePoint.y, rotatePoint.x);

                PointF tempLeftTopPoint = new PointF(labelH - leftTopPoint.y, leftTopPoint.x);
                PointF tempRightTopPoint = new PointF(labelH - rightTopPoint.y, rightTopPoint.x);
                PointF tempLeftBottomPoint = new PointF(labelH - leftBottomPoint.y, leftBottomPoint.x);
                PointF tempRightBottomPoint = new PointF(labelH - rightBottomPoint.y, rightBottomPoint.x);

                leftTopPoint = tempLeftBottomPoint;
                leftBottomPoint = tempRightBottomPoint;
                rightBottomPoint = tempRightTopPoint;
                rightTopPoint = tempLeftTopPoint;

            } else if (printfDirection == 180) {
                rotatePoint = new PointF(labelW - rotatePoint.x, labelH - rotatePoint.y);
                PointF tempLeftTopPoint = new PointF(labelW - leftTopPoint.x, labelH - leftTopPoint.y);
                PointF tempRightTopPoint = new PointF(labelW - rightTopPoint.x, labelH - rightTopPoint.y);
                PointF tempLeftBottomPoint = new PointF(labelW - leftBottomPoint.x, labelH - leftBottomPoint.y);
                PointF tempRightBottomPoint = new PointF(labelW - rightBottomPoint.x, labelH - rightBottomPoint.y);

                leftTopPoint = tempRightBottomPoint;
                leftBottomPoint = tempRightTopPoint;
                rightBottomPoint = tempLeftTopPoint;
                rightTopPoint = tempLeftBottomPoint;

            } else if (printfDirection == 270) {

                rotatePoint = new PointF(rotatePoint.y, labelW - rotatePoint.x);
                PointF tempLeftTopPoint = new PointF(leftTopPoint.y, labelW - leftTopPoint.x);
                PointF tempRightTopPoint = new PointF(rightTopPoint.y, labelW - rightTopPoint.x);
                PointF tempLeftBottomPoint = new PointF(leftBottomPoint.y, labelW - leftBottomPoint.x);
                PointF tempRightBottomPoint = new PointF(rightBottomPoint.y, labelW - rightBottomPoint.x);

                leftTopPoint = tempRightTopPoint;
                leftBottomPoint = tempLeftTopPoint;
                rightBottomPoint = tempLeftBottomPoint;
                rightTopPoint = tempRightBottomPoint;
            }

            //处理旋转角度
            if (rotate == PrintfModel.RotateAngle.NINETY_ANGLE
                    || rotate == PrintfModel.RotateAngle.TOW_HUNDRED_SEVENTY) {
                int tempRotate = rotate == PrintfModel.RotateAngle.NINETY_ANGLE ? 90 : 270;
                leftTopPoint = Util.getRotatePointF(rotatePoint, leftTopPoint, tempRotate);
                leftBottomPoint = Util.getRotatePointF(rotatePoint, leftBottomPoint, tempRotate);
                rightBottomPoint = Util.getRotatePointF(rotatePoint, rightBottomPoint, tempRotate);
                rightTopPoint = Util.getRotatePointF(rotatePoint, rightTopPoint, tempRotate);
            }

            //判断用到哪个点
            PointF usePoint = null;
            if (rotate == PrintfModel.RotateAngle.NINETY_ANGLE) {
                usePoint = leftBottomPoint;
            } else if (rotate == PrintfModel.RotateAngle.ONE_HUNDRED_EIGHTY) {
                usePoint = rightBottomPoint;
            } else if (rotate == PrintfModel.RotateAngle.TOW_HUNDRED_SEVENTY) {
                usePoint = rightTopPoint;
            } else {
                usePoint = leftTopPoint;
            }

            //图片是否需要交换宽高
            int newBitmapH = 0;
            int newBitmapW = 0;
            int judgeRotate = (rotate + printfDirection + 180) % 360;
            if (judgeRotate == 90 || judgeRotate == 270) {
                newBitmapH = bitmapWMM;
                newBitmapW = bitmapHMM;
            } else {
                newBitmapH = bitmapHMM;
                newBitmapW = bitmapWMM;
            }

            int newLabelH = 0;
            int newLabelW = 0;
            //判断打印方向，如果打印方向是 90 或 270 需要交换Label的宽高
            if (printfDirection == TSPLPrinterModel.DirectionAngle.NINETY_ANGLE
                    || printfDirection == TSPLPrinterModel.DirectionAngle.TOW_HUNDRED_SEVENTY) {
                newLabelH = labelW;
                newLabelW = labelH;
            } else {
                newLabelH = labelH;
                newLabelW = labelW;
            }
            printfModel.setBitmap(bitmap);
            printfModel.setX(newLabelW - (usePoint.x + newBitmapW));
            printfModel.setY(newLabelH - (usePoint.y + newBitmapH));
            printfModel.setBitmapW(newBitmapW);
            printfModel.setBitmapH(newBitmapH);
        }

        //如果 打印方向 是 90° 与 180° 则需要交换宽高
        if (printfDirection == TSPLPrinterModel.DirectionAngle.NINETY_ANGLE
                || printfDirection == TSPLPrinterModel.DirectionAngle.TOW_HUNDRED_SEVENTY) {
            tSPLPrinterModel.setLabelW(labelH);
            tSPLPrinterModel.setLabelH(labelW);
        }

        return tSPLPrinterModel;
    }

    /**
     * @param tSPLPrinterModel1：当前打印的Model
     * @param printfResultCallBack         return
     *                                     1:打印失败  蓝牙未连接
     *                                     2:打印成功
     */
    private void printfLabel(final TSPLPrinterModel tSPLPrinterModel1, final PrintfResultCallBack printfResultCallBack) {

        if (tSPLPrinterModel1 == null) {
            if (printfResultCallBack != null) {
                printfResultCallBack.callBack(PrintfResultCallBack.PRINTF_RESULT_PARAMETER_ERROR);
            }
            return;
        }

        if (!bluetoothManager.isConnect()) {
            if (printfResultCallBack != null) {
                printfResultCallBack.callBack(PrintfResultCallBack.PRINTF_RESULT_BLUETOOTH);
            }
            return;
        }

        TSPLPrinterModel TSPLPrinterModel = handleLabelPrinterModel(tSPLPrinterModel1);
        //初始化画布
        initCanvas(TSPLPrinterModel.getLabelW(), TSPLPrinterModel.getLabelH());
        clearCanvas();

        List<PrintfModel> printfModels = TSPLPrinterModel.getPrintfModels();
        for (int i = 0; i < printfModels.size(); i++) {
            PrintfModel printfModel = printfModels.get(i);
            int x = (int) (printfModel.getX() * TSPLPrinterModel.getMM_TO_PX());
            int y = (int) (printfModel.getY() * TSPLPrinterModel.getMM_TO_PX());
            printBitmap(x, y, printfModel.getBitmap(), printfModel.getThreshold());
        }

        beginPrintf(1, TSPLPrinterModel.getPrintfNumber());

        PrintfInfoManager.getInstance(context).getPrinterPaperState(new PrintfInfoManager.GetPrinterCmdCallBack() {
            @Override
            public void getComplete() {

            }

            @Override
            public void getError(int error) {
                if (printfResultCallBack != null) {
                    printfResultCallBack.callBack(PrintfResultCallBack.PRINTF_RESULT_CMD_ERROR);
                }
            }

            @Override
            public void getSuccess() {
                if (printfResultCallBack != null) {
                    printfResultCallBack.callBack(PrintfResultCallBack.PRINTF_RESULT_SUCCESS);
                }
            }
        });
    }

    public void printfLabelAsync(final TSPLPrinterModel tSPLPrinterModel1, final PrintfResultCallBack printfResultCallBack){

        ThreadExecutorManager.getInstance(context).getCachedThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                printfLabel(tSPLPrinterModel1,printfResultCallBack);
            }
        });

    }

    /**
     * 保存方法
     */
    private void saveBitmap(Bitmap bitmap, String name) {

        String labelNamePath = Environment.getExternalStoragePublicDirectory("") + "/" + "com.mht/file/";

        File f = new File(labelNamePath, name);
        if (f.exists()) {
            f.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * 求出当前图片的半色调风格
     */
    public int getThreshold(Bitmap img) {
        int width = img.getWidth();//获取位图的宽  
        int height = img.getHeight();//获取位图的高  

        int[] pixels = new int[width * height]; //通过位图的大小创建像素点数组  

        img.getPixels(pixels, 0, width, 0, 0, width, height);
        long total = 0;
        long position = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int grey = pixels[width * i + j];
                int red = ((grey & 0x00FF0000) >> 16);
                int green = ((grey & 0x0000FF00) >> 8);
                int blue = (grey & 0x000000FF);
                int tempThreshold = (int) (0.29900 * red + 0.58700 * green + 0.11400 * blue); // 灰度转化公式;

                total += tempThreshold;
            }
        }
        long l = total / width / height;
        return (int) l;
    }


    /**
     * 打印图片
     */
    public void printBitmap(int x, int y, Bitmap bitmap, int threshold) {

        //求出当前图片的半色调阈值
        bitmap = ImageUtil.convertGreyImgByFloyd(bitmap, context);

        StringBuilder BITMAP = new StringBuilder()
                .append("BITMAP ")
                .append(x)
                .append(",")
                .append(y)
                .append(",")
                .append((bitmap.getWidth()) / 8)
                .append(",")
                .append(bitmap.getHeight())
                .append(",1,");
        sendBytes(BITMAP.toString().getBytes());
        byte[] bitmapData = convertToBMW(bitmap, threshold);
        sendBytes(bitmapData);
        byte[] crlf = {0x0d, 0x0a};
        sendBytes(crlf);
    }

    /**
     * 开始打印
     *
     * @param sequence : 序列
     * @param number   ： 张数
     */
    public void beginPrintf(int sequence, int number) {
        if (sequence < 1) {
            sequence = 1;
        }
        if (number < 1) {
            number = 1;
        }
        String PRINT = "PRINT " + sequence + "," + number + "\r\n";
        sendBytes(PRINT.getBytes());
    }

    /**
     * 初始化画布
     *
     * @param w 标签的宽度
     * @param h 标签的高度
     */
    public void initCanvas(int w, int h) {
        byte[] data = new StringBuilder().append("SIZE ").append(w + " mm").append(",")
                .append(h + " mm").append("\r\n").toString().getBytes();
        sendBytes(data);
    }

    /**
     * 清除画布
     */
    public void clearCanvas() {
        byte[] bytes = "CLS\r\n".getBytes();
        sendBytes(bytes);
    }

    /**
     * 处理图片的大小
     *
     * @param bitmap
     * @param newBitmapW
     * @param newBitmapH
     * @return
     */
    private Bitmap handleBitmap(Bitmap bitmap, int newBitmapW, int newBitmapH, int rotate) {

        int height = bitmap.getHeight();
        int width = bitmap.getWidth();

        // 计算缩放比例
        float scaleWidth = ((float) newBitmapW) / width;
        float scaleHeight = ((float) newBitmapH) / height;

        // 取得想要缩放的matrix参数
        Matrix matrix = new Matrix();
        //先旋转，后缩小
        matrix.setRotate(rotate, bitmap.getWidth() / 2, bitmap.getHeight() / 2);
        matrix.postScale(scaleWidth, scaleHeight);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        return bitmap;
    }

    /**
     * 图片二值化
     *
     * @param bmp
     * @return
     */
    public static byte[] convertToBMW(Bitmap bmp, int concentration) {
        if (concentration <= 0 || concentration >= 255) {
            concentration = 128;
        }
        int width = bmp.getWidth(); // 获取位图的宽
        int height = bmp.getHeight(); // 获取位图的高
        byte[] bytes = new byte[(width) / 8 * height];
        int[] p = new int[8];
        int n = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width / 8; j++) {
                for (int z = 0; z < 8; z++) {
                    int grey = bmp.getPixel(j * 8 + z, i);
                    int red = ((grey & 0x00FF0000) >> 16);
                    int green = ((grey & 0x0000FF00) >> 8);
                    int blue = (grey & 0x000000FF);
                    int gray = (int) (0.29900 * red + 0.58700 * green + 0.11400 * blue); // 灰度转化公式
//                    int gray = (int) ((float) red * 0.3 + (float) green * 0.59 + (float) blue * 0.11);
                    if (gray <= concentration) {
                        gray = 0;//黑色
                    } else {
                        gray = 1;//白色

                    }
                    p[z] = gray;
                }
                byte value = (byte) (p[0] * 128 + p[1] * 64 + p[2] * 32 + p[3] * 16 + p[4] * 8 + p[5] * 4 + p[6] * 2 + p[7]);
                bytes[width / 8 * i + j] = value;
            }
        }
        return bytes;
    }


}
