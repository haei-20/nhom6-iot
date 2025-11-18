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

    // --- CLASS LƯU HÀNH TRÌNH ---
    public static class TablePath {
        public String name;
        // Lưu danh sách các lệnh theo thời gian (mỗi phần tử tương ứng 50ms)
        public ArrayList<String> commands;

        public TablePath(String name, ArrayList<String> commands) {
            this.name = name;
            // Copy dữ liệu hiện tại sang một list mới để lưu trữ
            this.commands = new ArrayList<>(commands);
        }
    }

    private MainActivity view;
    private BluetoothModel model;
    private Handler handler;

    // --- BIẾN CHO CHỨC NĂNG GHI & PHÁT LẠI ---
    // List chứa hành trình đang ghi (Manual Mode)
    private ArrayList<String> currentRecording = new ArrayList<>();

    // Danh sách các bàn đã lưu
    private ArrayList<TablePath> savedTables = new ArrayList<>();

    // Biến cho chế độ Auto (Replay)
    private boolean isAutoMode = false;
    private ArrayList<String> replaySequence = null; // Chuỗi lệnh cần phát lại
    private int replayIndex = 0; // Con trỏ vị trí đang phát
    // -----------------------------------------

    private boolean isRoll = false;
    private BluetoothAdapter bluetoothAdapter;
    private boolean isShowSeekBaGroup = true;
    private boolean isSettingCompass = false;

    boolean isUp = false;
    boolean isDown = false;
    boolean isLeft = false;
    boolean isRight = false;

    private String commandSend = "S";

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

    // --- VÒNG LẶP CHÍNH (50ms/lần) ---
    private void loopHandler(){
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAutoMode) {
                    // CHẾ ĐỘ CHẠY: Phát lại lệnh từ bộ nhớ
                    processReplayControl();
                } else {
                    // CHẾ ĐỘ DẠY: Điều khiển tay & Ghi âm lệnh
                    processManualControl();

                    // Ghi lại lệnh hiện tại vào bộ nhớ (Record)
                    // Mỗi 50ms ghi 1 lệnh. 1 giây = 20 lệnh.
                    currentRecording.add(commandSend);
                }

                // Gửi lệnh xuống Robot
                sendCommand(commandSend);

                loopHandler();
            }
        }, 50);
    }

    // Xử lý phát lại (Replay)
    private void processReplayControl() {
        if (replaySequence != null && replayIndex < replaySequence.size()) {
            // Lấy lệnh tiếp theo trong chuỗi
            commandSend = replaySequence.get(replayIndex);
            replayIndex++;
        } else {
            // Hết lệnh -> Dừng xe
            commandSend = "S";
            isAutoMode = false;
            view.toastMessage("Đã đến nơi (Kết thúc hành trình)");
        }

        // An toàn: Nếu gặp vật cản gần khi đang chạy -> Dừng khẩn cấp
        // (Cần check biến sonicValue nếu muốn tích hợp)
    }

    // Xử lý điều khiển tay (Manual)
    private void processManualControl() {
        if(isUp){
            if(isRight){ commandSend = "FR"; }
            else if(isLeft){ commandSend = "FL"; }
            else{ commandSend = "F"; }
        }else if(isDown){
            if(isRight){ commandSend = "BR"; }
            else if(isLeft){ commandSend = "BL"; }
            else{ commandSend = "B"; }
        }else if(isRight){
            if(isUp) commandSend = "FR";
            else if(isDown) commandSend = "BR";
            else commandSend = "R";
        }else if(isLeft){
            if(isUp) commandSend = "FL";
            else if(isDown) commandSend = "BL";
            else commandSend = "L";
        }else{
            commandSend = "S";
        }
    }

    // --- CÁC HÀM GIAO TIẾP VỚI UI ---

    // 1. Lưu hành trình hiện tại (Dùng ở chế độ Dạy)
    public void saveCurrentPosition(String tableName) {
        // Lưu toàn bộ những gì đã ghi từ lúc Reset đến giờ
        TablePath path = new TablePath(tableName, currentRecording);
        savedTables.add(path);

        int stepCount = currentRecording.size();
        float timeSeconds = stepCount * 0.05f; // 50ms = 0.05s
        view.toastMessage("Đã lưu: " + tableName + " (" + String.format("%.1f", timeSeconds) + "s)");
    }

    // 2. Bắt đầu chạy lại hành trình (Dùng ở chế độ Chạy)
    public void startNavigationTo(int tableIndex) {
        if (tableIndex >= 0 && tableIndex < savedTables.size()) {
            // Lấy hành trình đã lưu
            TablePath target = savedTables.get(tableIndex);
            replaySequence = target.commands;
            replayIndex = 0; // Reset về đầu băng
            isAutoMode = true;

            view.toastMessage("Đang chạy đến " + target.name + "...\nHãy chắc chắn xe đang ở Vạch Xuất Phát!");
        } else {
            view.showError("Dữ liệu bàn không hợp lệ!");
        }
    }

    // 3. Dừng chạy / Hủy
    public void stopAutoMode() {
        isAutoMode = false;
        commandSend = "S";
        sendCommand("S");
    }

    // 4. Reset bộ ghi (Xóa hành trình cũ để dạy lại từ đầu)
    // Bạn có thể gọi hàm này khi bấm nút "Reset Map" hoặc "Delete"
    public void resetRecordingData() {
        currentRecording.clear();
        view.toastMessage("Đã xóa bộ nhớ tạm. Hãy dạy xe từ Vạch Xuất Phát.");
        view.resetMap();
    }

    // Getter cho MainActivity lấy danh sách hiển thị
    public ArrayList<TablePath> getSavedTables() {
        return savedTables;
    }

    // --- CÁC HÀM CŨ (GIỮ NGUYÊN ĐỂ TRÁNH LỖI COMPILER) ---

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
        // Vẫn giữ logic parse để hiển thị Sonic/Map nếu cần
        // Nhưng không dùng tọa độ để điều khiển nữa
        RobotModel robotModel = new RobotModel();
        String[] lines = fullMessage.strip().split("\\R");

        try {
            // Parse Sonic để hiển thị hoặc dừng khẩn cấp
            String[] sonicParts = lines[2].replaceAll("[^0-9;]", "").split(";");
            int sonicL = Integer.parseInt(sonicParts[0]);
            int sonicR = Integer.parseInt(sonicParts[1]);
            int sonicF = Integer.parseInt(sonicParts[2]);

            SonicValue sv = new SonicValue(sonicL, sonicR, sonicF);
            robotModel.setSonicValue(sv);

            // An toàn: Nếu đang Auto Mode mà gặp vật cản < 15cm -> Dừng
            if (isAutoMode && sonicF > 0 && sonicF < 15) {
                isAutoMode = false;
                commandSend = "S";
                view.showError("Gặp vật cản! Dừng khẩn cấp.");
            }
        } catch (Exception e) {}

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

    public void resetMap(){
        // Khi reset map -> Reset luôn bộ ghi
        resetRecordingData();
    }

    public void setUp(boolean up) { isUp = up; }
    public void setDown(boolean down) { isDown = down; }
    public void setLeft(boolean left) { isLeft = left; }
    public void setRight(boolean right) { isRight = right; }
}