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
    private Boolean isListening;
    private Context context;

    public BluetoothModel(Context context, BluetoothAdapter bluetoothAdapter) {
        this.context = context;
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public Set<BluetoothDevice> getPairedDevices() {
        if (bluetoothAdapter == null) {
            Log.e("Bluetooth", "Device does not support Bluetooth");
            return null;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Log.e("Bluetooth", "Bluetooth is not enabled");
            return null;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("Bluetooth", "Permission not granted");
            return null;
        }
        return bluetoothAdapter.getBondedDevices();
    }


    public void connectToDevice(String deviceAddress, ConnectionCallBack callBack, MessageCallBack messageCallBack) {
        executorService.execute( () -> {
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.S){
                if(context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED){
                    callBack.onFailure("Permission not granted");
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
                new Handler(Looper.getMainLooper()).post(() -> callBack.onFailure("ConnectionFail " + e.getMessage()));
            }
        });

    }

    public void closeSocket(){
        try{
            if(bluetoothSocket != null){
                bluetoothSocket.close();
                bluetoothSocket = null;
                outputStream = null;
                inputStream = null;
                isListening = false;
            }
        }catch (IOException e){
            Log.e("Bluetooth", "Error closing socket", e);
        }
    }

    public void sendData(String data) {
        executorService.execute(() -> {
            try {
                if (outputStream != null && bluetoothSocket != null && bluetoothSocket.isConnected()) {
                    outputStream.write(data.getBytes());
//                    Log.d("DATDEV1", "Sent data: " + data);
                } else {
                    Log.e("Bluetooth", "Not connected. Cannot send data.");
                }
            } catch (IOException e) {
                Log.e("Bluetooth", "Error sending data", e);
            }
        });
    }

    private void startListening(MessageCallBack messageCallBack) {
        isListening = true;
        new Thread( () -> {
           byte[] buffer = new byte[1024];
           int bytes;
           while(isListening){
               try{
                   if(inputStream.available() > 0){
                       bytes = inputStream.read(buffer);
                       String incomingMessage = new String(buffer, 0, bytes);
                       new Handler(Looper.getMainLooper()).post(() -> messageCallBack.onMessageReceived(incomingMessage));
                   }
               }catch (IOException e){
                   isListening = false;
                   new Handler(Looper.getMainLooper()).post(() -> messageCallBack.onError("Error reading message: " + e.getMessage()));
                   Log.e("Bluetooth", "Error reading data", e);
               }
           }
        }).start();
    }
    public void stopListening() {
        isListening = false;
        try {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void disconnect() {
        executorService.execute(() -> {
            try {
                if (outputStream != null) outputStream.close();
                if (bluetoothSocket != null) bluetoothSocket.close();
                Log.d("Bluetooth", "Disconnected");
            } catch (IOException e) {
                Log.e("Bluetooth", "Error during disconnect", e);
            }
        });
//        executorService.shutdown();
    }

    public boolean isConnected(){
        return bluetoothSocket != null && bluetoothSocket.isConnected();
    }

    public interface ConnectionCallBack{
        void onSuccess();
        void onFailure(String message);
    }

    public interface MessageCallBack{
        void onMessageReceived(String message);
        void onError(String message);
    }
}