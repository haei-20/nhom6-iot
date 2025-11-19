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

import java.util.ArrayList;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MainPresenter implements MainContract.Presenter {

    public static class TablePath {
        public String name;
        public ArrayList<String> commands;
        public TablePath(String name, ArrayList<String> commands) {
            this.name = name;
            this.commands = new ArrayList<>(commands);
        }
    }

    private MainActivity view;
    private BluetoothModel model;
    private Handler handler;

    private static final float MAP_SQUARE_SIZE_PX = 60f;
    private static final float MAP_SQUARE_SIZE_CM = 20f;

    private float receivedDistance = 0;
    private float traveledDistance = 0;
    private float currentX = 0f;
    private float currentY = 0f;
    private float currentAngleDeg = 0f;

    private ArrayList<String> currentRecording = new ArrayList<>();
    private ArrayList<TablePath> savedTables = new ArrayList<>();
    private boolean isAutoMode = false;
    private ArrayList<String> replaySequence = null;
    private int replayIndex = 0;

    private boolean isRoll = false;
    private BluetoothAdapter bluetoothAdapter;
    private boolean isShowSeekBaGroup = true;
    private boolean isSettingCompass = false;

    boolean isUp = false;
    boolean isDown = false;
    boolean isLeft = false;
    boolean isRight = false;

    private String commandSend = "S";
    private String lastCommandSend = "S"; // ✅ Thêm biến theo dõi lệnh trước

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
        if (bluetoothAdapter == null) {
            view.showAlertDialog("Error", "Device does not support Bluetooth");
        } else {
            requestToTurnBluetoothOn();
        }
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
                if (isAutoMode) {
                    processReplayControl();
                } else {
                    processManualControl();

                    // ✅ SỬA LỖI: Chỉ ghi lệnh khi có thay đổi (không phải "S")
                    if (!commandSend.equals("S")) {
                        currentRecording.add(commandSend);
                        lastCommandSend = commandSend;
                    } else if (!lastCommandSend.equals("S")) {
                        // Nếu vừa chuyển từ di chuyển sang dừng, ghi 1 lệnh "S"
                        currentRecording.add("S");
                        lastCommandSend = "S";
                    }
                }
                sendCommand(commandSend);
                loopHandler();
            }
        }, 50);
    }

    private void processReplayControl() {
        if (replaySequence != null && replayIndex < replaySequence.size()) {
            commandSend = replaySequence.get(replayIndex);
            replayIndex++;
        } else {
            commandSend = "S";
            isAutoMode = false;
            view.toastMessage("Đã đến nơi (Kết thúc hành trình)");
        }
    }

    private void processManualControl() {
        if(isUp){
            if(isRight){ commandSend = "FR"; setWhenRoll(); }
            else if(isLeft){ commandSend = "FL"; setWhenRoll(); }
            else{ setStopRoll(); commandSend = "F"; }
        }else if(isDown){
            if(isRight){ commandSend = "BR"; setWhenRoll(); }
            else if(isLeft){ commandSend = "BL"; setWhenRoll(); }
            else{ setStopRoll(); commandSend = "B"; }
        }else if(isRight){
            setWhenRoll();
            if(isUp) commandSend = "FR";
            else if(isDown) commandSend = "BR";
            else commandSend = "R";
        }else if(isLeft){
            setWhenRoll();
            if(isUp) commandSend = "FL";
            else if(isDown) commandSend = "BL";
            else commandSend = "L";
        }else{
            commandSend = "S";
        }
    }

    public void saveCurrentPosition(String tableName) {
        // ✅ SỬA LỖI: Kiểm tra xem có lệnh nào được ghi không
        if (currentRecording.isEmpty()) {
            view.toastMessage("Chưa có hành trình nào! Hãy lái xe trước khi lưu.");
            return;
        }

        TablePath path = new TablePath(tableName, currentRecording);
        savedTables.add(path);
        float timeSeconds = currentRecording.size() * 0.05f;
        view.toastMessage("Đã lưu: " + tableName + " (" + String.format("%.1f", timeSeconds) + "s, " + currentRecording.size() + " lệnh)");

        // ✅ Reset recording nhưng GIỮ NGUYÊN tọa độ
        currentRecording = new ArrayList<>();
        lastCommandSend = "S";
    }

    public void startNavigationTo(int tableIndex) {
        if (tableIndex >= 0 && tableIndex < savedTables.size()) {
            TablePath target = savedTables.get(tableIndex);

            // ✅ Kiểm tra xem hành trình có hợp lệ không
            if (target.commands == null || target.commands.isEmpty()) {
                view.showError("Hành trình " + target.name + " không có dữ liệu!");
                return;
            }

            // ⚠️ CẢNH BÁO: Xe sẽ chạy từ vị trí HIỆN TẠI, không phải từ gốc
            if (currentX != 0 || currentY != 0) {
                view.toastMessage("⚠️ Xe đang ở (" + String.format("%.1f", currentX) + ", " +
                        String.format("%.1f", currentY) + ")");
            }

            replaySequence = new ArrayList<>(target.commands);
            replayIndex = 0;
            isAutoMode = true;
            view.toastMessage("Đang chạy: " + target.name + " (" + replaySequence.size() + " lệnh)");

            // ✅ Debug: In ra 10 lệnh đầu
            StringBuilder preview = new StringBuilder("Preview: ");
            for (int i = 0; i < Math.min(10, replaySequence.size()); i++) {
                preview.append(replaySequence.get(i)).append(" ");
            }
            Log.d("MainPresenter", preview.toString());
        } else {
            view.showError("Dữ liệu bàn không hợp lệ!");
        }
    }

    public void stopAutoMode() {
        isAutoMode = false;
        commandSend = "S";
        sendCommand("S");
    }

    public void resetRecordingData() {
        currentRecording.clear();
        lastCommandSend = "S";

        // ✅ SỬA LỖI: CHỈ reset tọa độ khi người dùng THỰC SỰ muốn reset về gốc
        // KHÔNG reset khi đang trong quá trình dạy
        view.toastMessage("Đã xóa hành trình đang ghi. Tọa độ giữ nguyên.");

        // Nếu muốn reset cả map, uncomment dòng dưới:
        // this.currentX = 0;
        // this.currentY = 0;
        // this.traveledDistance = 0;
        // view.resetMap();
    }

    public ArrayList<TablePath> getSavedTables() {
        return savedTables;
    }

    private void setWhenRoll(){ isRoll = true; }
    private void setStopRoll(){ isRoll = false; }

    @Override
    public void connectToDevice(String deviceAddress, String name) {
        model.connectToDevice(deviceAddress,
                new BluetoothModel.ConnectionCallBack() {
                    @Override
                    public void onSuccess() { view.showConnectionSuccess(name); }
                    @Override
                    public void onFailure(String message) { view.showConnectionFailed(); }
                },
                new BluetoothModel.MessageCallBack() {
                    @Override
                    public void onMessageReceived(String message) { processBluetoothMessage(message); }
                    @Override
                    public void onError(String message) { view.showError(message); }
                });
    }

    @Override
    public Set<BluetoothDevice> getPairedDevice(){ return model.getPairedDevices(); }

    @Override
    public void sendCommand(String command) {
        command = command + "\n";
        model.sendData(command);
    }

    @Override
    public void setCommandSend(String commandSend) { this.commandSend = commandSend; }

    @Override
    public void disconnect() {
        model.disconnect();
        view.showDisconnected();
    }

    public void processOnStatusClick(){
        if(model.isConnected()){ disconnect(); }
        else{ view.startPickDeviceActivity(); }
    }

    private void processBluetoothMessage(String fullMessage) {
        RobotModel robotModel = new RobotModel();
        robotModel.setSquareSize(MAP_SQUARE_SIZE_PX);

        String[] lines = fullMessage.strip().split("\\R");

        try {
            String[] speedParts = lines[0].split("; ");
            receivedDistance = (float) Double.parseDouble(speedParts[1].split(": ")[1]);

            String[] yprParts = lines[5].replace("YPR:", "").replace("[", "").replace("]", "").split(";");
            float rawYAngle = Float.parseFloat(yprParts[0].replace("Y:", ""));

            this.currentAngleDeg = mapYAngleInto360(rawYAngle);
            robotModel.setAngle(this.currentAngleDeg);

            float delta = 0;
            if(!isRoll){
                delta = receivedDistance - traveledDistance;
                traveledDistance = receivedDistance;
                robotModel.setDistanceCm(delta);
            } else {
                traveledDistance = receivedDistance;
            }

            double angleInRadians = Math.toRadians(this.currentAngleDeg);
            this.currentX += delta * Math.sin(angleInRadians);
            this.currentY += delta * Math.cos(angleInRadians);

            float gridX = this.currentX / MAP_SQUARE_SIZE_CM;
            float gridY = this.currentY / MAP_SQUARE_SIZE_CM;

            robotModel.setFloatX(gridX);
            robotModel.setFloatY(gridY);

            String[] sonicParts = lines[2].replaceAll("[^0-9;]", "").split(";");
            int sonicL = Integer.parseInt(sonicParts[0]);
            int sonicR = Integer.parseInt(sonicParts[1]);
            int sonicF = Integer.parseInt(sonicParts[2]);

            if (isAutoMode && sonicF > 0 && sonicF < 15) {
                isAutoMode = false;
                commandSend = "S";
                view.showError("Gặp vật cản! Dừng.");
            }
            robotModel.setSonicValue(new SonicValue(sonicL, sonicR, sonicF));

        } catch (Exception e) {
            Log.e("MainPresenter", "Parse error: " + e.getMessage());
        }

        view.updateRobotModel(robotModel);
    }

    public void setIsShowSeekBarGroup(boolean isShow){
        view.setVisibleSeekBarGroup(isShow);
        isShowSeekBaGroup = isShow;
    }

    public boolean isSettingCompass() { return isSettingCompass; }

    public void setSettingCompass(boolean settingCompass) {
        isSettingCompass = settingCompass;
        if(settingCompass) sendCommand("calculatingCalibration");
        else sendCommand("resetCalibration");
    }

    public boolean isShowSeekBaGroup() { return isShowSeekBaGroup; }

    private int mapYAngleInto360(float yAngle){
        int mapped = (int) yAngle;
        if(mapped < 0) mapped = 360 + mapped;
        return mapped;
    }

    public void resetMap(){
        resetRecordingData();
        // ✅ Thêm option để reset về gốc tọa độ
        this.currentX = 0;
        this.currentY = 0;
        this.traveledDistance = 0;
        view.resetMap();
    }

    public void setUp(boolean up) { isUp = up; }
    public void setDown(boolean down) { isDown = down; }
    public void setLeft(boolean left) { isLeft = left; }
    public void setRight(boolean right) { isRight = right; }

    // ✅ Thêm hàm này để MainActivity có thể lấy số lệnh
    public int getCurrentRecordingSize() {
        return currentRecording.size();
    }
}