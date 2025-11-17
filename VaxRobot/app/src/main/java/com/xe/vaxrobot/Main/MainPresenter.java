package com.xe.vaxrobot.Main;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.xe.vaxrobot.Model.BluetoothModel;
import com.xe.vaxrobot.Model.RobotModel;
import com.xe.vaxrobot.Model.SonicValue;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

// FAKE commit to test git

@Singleton
public class MainPresenter implements MainContract.Presenter {

    private MainActivity view;
    private BluetoothModel model;

    private Handler handler;

    private float receivedDistance = 0;
    private float traveledDistance = 0;

    private boolean isRoll = false;

    private BluetoothAdapter bluetoothAdapter;

    private boolean isShowSeekBaGroup = true;
    private boolean isSettingCompass = false;

    boolean isUp = false;
    boolean isDown = false;
    boolean isLeft = false;
    boolean isRight = false;


    private String commandSend = "S";
    /*  commandSend value meaning:
                                    F: Forward
                                    S: Stop
                                    B: Backward
                                    L: Left Rotate
                                    R: Right Rotate
                                    D: Delete mesage
         */

    @Inject
    public MainPresenter() {
    }

    public void setView(MainActivity view){
        this.view = view;
        init();
        handler = new Handler(Looper.getMainLooper());
        loopHandler();
    }

    @Override
    public void init(){
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Log.e("BluetoothVax", "Device does not support Bluetooth");
        if (bluetoothAdapter == null) {
            Log.e("Bluetooth", "Device does not support Bluetooth");
            view.showAlertDialog("Error", "Device does not support Bluetooth");
        } else {
            // Request to turn bluetooth on
            requestToTurnBluetoothOn();
        }
        // Init model
        this.model = new BluetoothModel(view, bluetoothAdapter);
    }

    private void requestToTurnBluetoothOn(){
        if (!bluetoothAdapter.isEnabled()) {
            Intent requestBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission((Context) view, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(view, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1001);
            }
            view.startActivityForResult(requestBT, 1001);
        }
    }

    private void loopHandler(){
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Send command to robot continuously 1 time for 5 millisecond
                // Default send S - Stop

                if(isUp){
                    if(isRight){
                        commandSend = "FR";
                        setWhenRoll();
                    }else if(isLeft){
                        commandSend = "FL";
                        setWhenRoll();
                    }else{
                        setStopRoll();
                        commandSend = "F";
                    }
                }else if(isDown){
                    if(isRight){
                        commandSend = "BR";
                        setWhenRoll();
                    }else if(isLeft){
                        commandSend = "BL";
                        setWhenRoll();
                    }else{
                        setStopRoll();
                        commandSend = "B";
                    }
                }else if(isRight){
                    setWhenRoll();
                    if(isUp){
                        commandSend = "FR";
                    }else if(isDown){
                        commandSend = "BR";
                    }else{
                        commandSend = "R";
                    }
                }else if(isLeft){
                    setWhenRoll();
                    if(isUp){
                        commandSend = "FL";
                    }else if(isDown){
                        commandSend = "BL";
                    }else{
                        commandSend = "L";
                    }
                }else{
                    commandSend = "S";
                }

                sendCommand(commandSend);

                loopHandler();
            }
        }, 5);
    }

    private void setWhenRoll(){
        Log.i("FIX_ROLL", "setWhenRoll: " + receivedDistance + " traveled Distance: " + traveledDistance);
        isRoll = true;
//        traveledDistance = receivedDistance;
    }

    private void setStopRoll(){
        Log.i("FIX_ROLL", "setStopRoll: " + receivedDistance + " traveled Distance: " + traveledDistance);
        isRoll = false;
    }

    @Override
    public void connectToDevice(String deviceAddress, String name) {
        model.connectToDevice(deviceAddress,
                new BluetoothModel.ConnectionCallBack() {
                    @Override
                    public void onSuccess() {
                        view.showConnectionSuccess(name);
                    }

                    @Override
                    public void onFailure(String message) {
                        view.showConnectionFailed();
                    }
                },
                new BluetoothModel.MessageCallBack() {
                    @Override
                    public void onMessageReceived(String message) {
                        view.showMessage(message);
                        // parse message
                        processBluetoothMessage(message);
                        // update sonic


                    }
                    @Override
                    public void onError(String message) {
                        view.showError(message);
                    }
                });
    }

    @Override
    public Set<BluetoothDevice> getPairedDevice(){
        return model.getPairedDevices();
    }

    @Override
    public void sendCommand(String command) {
        command = command + "\n";
        model.sendData(command);
    }

    @Override
    public void setCommandSend(String commandSend) {
        this.commandSend = commandSend;
        sendCommand(commandSend);
    }

    @Override
    public void disconnect() {
        model.disconnect();
        view.showDisconnected();
    }

    public void processOnStatusClick(){
        if(model.isConnected()){
            disconnect();
        }else{
            view.startPickDeviceActivity();
        }
    }

    // TODO:: get Heading.
    private void processBluetoothMessage(String fullMessage) {
        RobotModel robotModel = new RobotModel();
        Log.d("fullMess", fullMessage);
        String[] lines = fullMessage.strip().split("\\R"); // split by line
        // process distance, yAngle
        try{
            String[] speedParts = lines[0].split("; ");
            double speed = Double.parseDouble(speedParts[0].split(": ")[1]);
            receivedDistance = (float) Double.parseDouble(speedParts[1].split(": ")[1]);
            String action = speedParts[2].split(":")[1];
            robotModel.setAction(action);
            String[] yprParts = lines[5]
                    .replace("YPR:", "")
                    .replace("[", "")
                    .replace("]", "")
                    .split(";");
            float tempYAngle = Float.parseFloat(yprParts[0].replace("Y:", ""));
            //double pitch = Double.parseDouble(yprParts[1].replace("P:", ""));
            //double roll = Double.parseDouble(yprParts[2].replace("R:", ""));
            // Map yAngle into 0-360 and set to robotModelClone
            processDistance(mapYAngleInto360(tempYAngle));
            robotModel.setAngle(mapYAngleInto360(tempYAngle));
            if(!isRoll){
                float delta =  (receivedDistance - traveledDistance);
//                if(delta >= squareSizeCm)
//                {

                    // TODO:: MERGE INTO setRobotModel only
//                                robotModelClone.setDistance(delta);
//                    view.moveRobotCar(delta, commandSend);
                    robotModel.setDistanceCm(delta);
//                    Log.d("fix_delta", "delta: " + delta);
                    traveledDistance = receivedDistance;
//                }

            }else{
                traveledDistance = receivedDistance;
            }
        }catch (Exception e){
            Log.e("fix_delta", "parseBluetoothMessage: " + e + e.getMessage());
        }

        // 3. Sonic
        try{
            String[] sonicParts = lines[2].replaceAll("[^0-9;]", "").split(";");
            int sonicL = Integer.parseInt(sonicParts[0]);
            int sonicR = Integer.parseInt(sonicParts[1]);
            int sonicF = Integer.parseInt(sonicParts[2]);
            if (sonicL > 200) sonicL = -1;
            if (sonicR > 200) sonicR = -1;
            if (sonicF > 200) sonicF = -1;
            SonicValue sonicValue = new SonicValue(
                    sonicL,
                    sonicR,
                    sonicF
            );
            //Log.i("MapView", "Sonic L/R/F = " + sonicL + "/" + sonicR + "/" + sonicF);
//            view.processSonicValue(sonicValue);
            robotModel.setSonicValue(sonicValue);
        }catch (Exception e){
            Log.e("fix_delta", "parseBluetoothMessage: " + e);
        }


        // 4. Accelerometer
        try{
            String[] accelParts = lines[3].replaceAll("[^X0-9Y:Z\\-; ]", "").split("[; ]+");
            int accelX = Integer.parseInt(accelParts[2]);
            int accelY = Integer.parseInt(accelParts[4]);
            int accelZ = Integer.parseInt(accelParts[6]);
            Log.i("MapView", "Accel X/Y/Z = " + accelY );
        }catch(Exception e){
            Log.e("fix_delta", "processBluetoothMessage: " + e);
        }


        // 5. Gyroscope
//        String[] gyroParts = lines[4].replaceAll("[^X0-9Y:Z\\-; ]", "").split("[; ]+");
//        int gyroX = Integer.parseInt(gyroParts[1]);
//        int gyroY = Integer.parseInt(gyroParts[3]);
//        int gyroZ = Integer.parseInt(gyroParts[5]);

        // 6. Yaw-Pitch-Roll


//        Log.i("MapView", "Sonic L/R/F = " + sonicL + "/" + sonicR + "/" + sonicF);
//        Log.i("MapView", "Accel X/Y/Z = " + accelX + "/" + accelY + "/" + accelZ);
//        Log.i("MapView", "Gyro X/Y/Z = " + gyroX + "/" + gyroY + "/" + gyroZ);
//        Log.i("MapView", "Temp = " + temp + "°C, Pressure = " + pressure + " Pa");
        view.updateRobotModel(robotModel);
    }

    private void processDistance(float angle){

    }

    public void setIsShowSeekBarGroup(boolean isShow){
        view.setVisibleSeekBarGroup(isShow);
        isShowSeekBaGroup = isShow;
    }

    public boolean isSettingCompass() {
        return isSettingCompass;
    }

    public void setSettingCompass(boolean settingCompass) {
        isSettingCompass = settingCompass;
        if(settingCompass){
            sendCommand("calculatingCalibration");
            view.toastMessage("calculatingCalibration");
            return;
        }
        sendCommand("resetCalibration");
        view.toastMessage("resetCalibration");
    }

    public boolean isShowSeekBaGroup() {
        return isShowSeekBaGroup;
    }

    /*
     * yAngle input is  |0-->180 -->180  -->360
     * Map yAngle into  |0-->180 -->-180 -->0
     */
    private int mapYAngleInto360(float yAngle){
        int mapped = (int) yAngle;
        if(mapped < 0) mapped = 360 + mapped;
        return mapped;
    }

    public void resetMap(){
        view.resetMap();
    }

    public void setUp(boolean up) {
        isUp = up;
    }

    public void setDown(boolean down) {
        isDown = down;
    }

    public void setLeft(boolean left) {
        isLeft = left;
    }

    public void setRight(boolean right) {
        isRight = right;
    }
}
