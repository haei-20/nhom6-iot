package com.xe.vaxrobot.Model;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BluetoothModel {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Boolean isListening = false;
    private Context context;

    public BluetoothModel(Context context, BluetoothAdapter bluetoothAdapter) {
        this.context = context;
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public Set<BluetoothDevice> getPairedDevices() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
        }
        return bluetoothAdapter.getBondedDevices();
    }

    public void connectToDevice(String deviceAddress, ConnectionCallBack callBack, MessageCallBack messageCallBack) {
        executorService.execute(() -> {
            // Kiểm tra quyền trước khi kết nối
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    new Handler(Looper.getMainLooper()).post(() -> callBack.onFailure("Thiếu quyền Bluetooth Connect"));
                    return;
                }
            }

            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothSocket.connect();

                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();

                new Handler(Looper.getMainLooper()).post(callBack::onSuccess);
                startListening(messageCallBack);
            } catch (IOException e) {
                closeSocket();
                new Handler(Looper.getMainLooper()).post(() -> callBack.onFailure("Lỗi IO: " + e.getMessage()));
            } catch (SecurityException e) {
                // BẮT LỖI CRASH TẠI ĐÂY
                new Handler(Looper.getMainLooper()).post(() -> callBack.onFailure("Lỗi bảo mật: " + e.getMessage()));
            }
        });
    }

    public void closeSocket(){
        try{
            isListening = false;
            if(bluetoothSocket != null){
                bluetoothSocket.close();
                bluetoothSocket = null;
                outputStream = null;
                inputStream = null;
            }
        }catch (IOException e){ e.printStackTrace(); }
    }

    public void sendData(String data) {
        executorService.execute(() -> {
            try {
                if (outputStream != null && bluetoothSocket != null && bluetoothSocket.isConnected()) {
                    outputStream.write(data.getBytes());
                }
            } catch (IOException e) { e.printStackTrace(); }
        });
    }

    private void startListening(MessageCallBack messageCallBack) {
        isListening = true;
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            while (isListening) {
                try {
                    if (inputStream != null && inputStream.available() > 0) {
                        bytes = inputStream.read(buffer);
                        if (bytes > 0) {
                            String incomingMessage = new String(buffer, 0, bytes);
                            new Handler(Looper.getMainLooper()).post(() -> {
                                try {
                                    messageCallBack.onMessageReceived(incomingMessage);
                                } catch (Exception e) {
                                    Log.e("BT_Listen", "Error in messageCallBack: " + e.getMessage());
                                }
                            });
                        }
                    } else {
                        Thread.sleep(10);
                    }
                } catch (IOException e) {
                    Log.e("BT_Listen", "IO Error: " + e.getMessage());
                    isListening = false;
                } catch (Exception e) {
                    Log.e("BT_Listen", "Error: " + e.getMessage());
                    isListening = false;
                }
            }
        }).start();
    }

    public void disconnect() { closeSocket(); }
    public boolean isConnected(){ return bluetoothSocket != null && bluetoothSocket.isConnected(); }

    public interface ConnectionCallBack{
        void onSuccess();
        void onFailure(String message);
    }
    public interface MessageCallBack{
        void onMessageReceived(String message);
        void onError(String message);
    }
}