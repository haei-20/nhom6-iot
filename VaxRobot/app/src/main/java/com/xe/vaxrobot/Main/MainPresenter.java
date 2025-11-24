package com.xe.vaxrobot.Main;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;

import com.xe.vaxrobot.Model.BluetoothModel;
import com.xe.vaxrobot.Model.MarkerModel;
import com.xe.vaxrobot.Model.RobotModel;
import com.xe.vaxrobot.Model.SonicValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MainPresenter implements MainContract.Presenter {

    public static class RoutePath {
        public String startPoint;
        public String endPoint;
        public ArrayList<String> commands;

        public RoutePath(String start, String end, ArrayList<String> commands) {
            this.startPoint = start;
            this.endPoint = end;
            this.commands = new ArrayList<>(commands);
        }
        public String getName() { return startPoint + " ➝ " + endPoint; }
    }

    private MainContract.View view;
    private BluetoothModel model;
    private Handler handler;

    // CẤU HÌNH BẢN ĐỒ
    private static final float MAP_SQUARE_SIZE_PX = 60f;
    private static final float MAP_SQUARE_SIZE_CM = 20f;

    // Biến hành trình
    private ArrayList<String> currentRecording = new ArrayList<>();
    private ArrayList<RoutePath> savedRoutes = new ArrayList<>();
    private HashMap<String, MarkerModel> savedMarkers = new HashMap<>();
    private String currentStartPoint = "Điểm xuất phát";

    // Biến Auto Mode
    private boolean isAutoMode = false;
    private Queue<RoutePath> routeQueue = new LinkedList<>();
    private ArrayList<String> currentReplaySequence = null;
    private int replayIndex = 0;

    private boolean isRoll = false;
    private BluetoothAdapter bluetoothAdapter;
    private boolean isSettingCompass = false;
    private boolean isShowSeekBaGroup = true;

    // Biến đo đạc & Cảm biến
    private float receivedDistance = 0;
    private float traveledDistance = 0;
    private float currentX = 0f;
    private float currentY = 0f;
    private float currentAngleDeg = 0f;

    // Biến lưu khoảng cách phía trước (Mặc định 999 là an toàn)
    private int currentSonicFront = 999;

    boolean isUp = false, isDown = false, isLeft = false, isRight = false;
    private String commandSend = "S";
    private String lastCommandSend = "S";

    @Inject
    public MainPresenter() {}

    public void setView(MainContract.View view){
        this.view = view;
        init();
        handler = new Handler(Looper.getMainLooper());
        loopHandler();
    }

    @Override
    public void init(){
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) view.showAlertDialog("Error", "Bluetooth not supported");
        else requestToTurnBluetoothOn();
        if (view instanceof Context) {
            this.model = new BluetoothModel((Context) view, bluetoothAdapter);
        }
    }

    // [LOGIC ĐIỀU KHIỂN CHÍNH]
    private void loopHandler(){
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (model != null && model.isConnected()) {
                    if (isAutoMode) {
                        // [SỬA] Logic tạm dừng khi gặp vật cản
                        boolean isBlocked = (currentSonicFront > 0 && currentSonicFront < 15);

                        if (isBlocked) {
                            // Nếu có vật cản: Gửi lệnh dừng, KHÔNG tăng replayIndex
                            commandSend = "S";
                            view.toastMessage("Vật cản! Đang chờ...");
                        } else {
                            // Nếu hết vật cản: Tiếp tục chạy lệnh tiếp theo
                            processAutoRun();
                        }
                    } else {
                        processManualControl();
                        // Logic ghi lại hành trình (Record)
                        if (!commandSend.equals("S")) {
                            currentRecording.add(commandSend);
                            lastCommandSend = commandSend;
                        } else if (!lastCommandSend.equals("S")) {
                            currentRecording.add("S");
                            lastCommandSend = "S";
                        }
                    }
                    // Gửi lệnh xuống xe
                    sendCommand(commandSend);
                }

                loopHandler();
            }
        }, 100);
    }

    private void processAutoRun() {
        if (currentReplaySequence != null && replayIndex < currentReplaySequence.size()) {
            commandSend = currentReplaySequence.get(replayIndex);
            replayIndex++;
        } else {
            if (!routeQueue.isEmpty()) {
                RoutePath nextRoute = routeQueue.poll();
                currentReplaySequence = nextRoute.commands;
                replayIndex = 0;
                view.toastMessage("Đã tới " + nextRoute.startPoint + ". Đi tiếp tới " + nextRoute.endPoint);
            } else {
                commandSend = "S";
                isAutoMode = false;
                view.toastMessage("Đã đến đích!");
            }
        }
    }

    public void startNavigation(String start, String end) {
        ArrayList<RoutePath> pathSegments = findPathBFS(start, end);
        if (pathSegments == null) {
            view.showError("Không tìm thấy đường từ " + start + " đến " + end);
            return;
        }
        routeQueue.clear();
        routeQueue.addAll(pathSegments);
        RoutePath first = routeQueue.poll();
        currentReplaySequence = first.commands;
        replayIndex = 0;
        isAutoMode = true;
        view.toastMessage("Bắt đầu: " + start + " -> " + end);
    }

    private ArrayList<RoutePath> findPathBFS(String start, String end) {
        if (start.equals(end)) return null;
        Queue<String> queue = new LinkedList<>();
        queue.add(start);
        Map<String, RoutePath> cameFrom = new HashMap<>();
        ArrayList<String> visited = new ArrayList<>();
        visited.add(start);
        boolean found = false;

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(end)) {
                found = true;
                break;
            }
            for (RoutePath route : savedRoutes) {
                if (route.startPoint.equals(current) && !visited.contains(route.endPoint)) {
                    visited.add(route.endPoint);
                    cameFrom.put(route.endPoint, route);
                    queue.add(route.endPoint);
                }
            }
        }
        if (!found) return null;

        ArrayList<RoutePath> path = new ArrayList<>();
        String curr = end;
        while (!curr.equals(start)) {
            RoutePath segment = cameFrom.get(curr);
            path.add(segment);
            curr = segment.startPoint;
        }
        Collections.reverse(path);
        return path;
    }

    public void saveRoute(String endPointName) {
        if (currentRecording.isEmpty()) {
            view.toastMessage("Chưa có dữ liệu!");
            return;
        }
        RoutePath newRoute = new RoutePath(currentStartPoint, endPointName, currentRecording);
        savedRoutes.add(newRoute);

        float gridX = this.currentX / MAP_SQUARE_SIZE_CM;
        float gridY = this.currentY / MAP_SQUARE_SIZE_CM;
        MarkerModel marker = new MarkerModel(endPointName, gridX, gridY);
        savedMarkers.put(endPointName, marker);
        view.updateMapMarkers(new ArrayList<>(savedMarkers.values()));

        view.toastMessage("Đã lưu: " + newRoute.getName());
        currentStartPoint = endPointName;
        currentRecording = new ArrayList<>();
        lastCommandSend = "S";
    }

    @Override
    public void resetMap(String newStartPointName) {
        currentRecording.clear();
        lastCommandSend = "S";
        this.currentStartPoint = newStartPointName;
        this.currentX = 0;
        this.currentY = 0;
        this.traveledDistance = 0;
        view.resetMap();
        view.toastMessage("Đã reset tọa độ tại: " + newStartPointName);
    }

    @Override
    public void deleteLocation(String name) {
        if (savedMarkers.containsKey(name)) {
            savedMarkers.remove(name);
            ArrayList<RoutePath> routesToRemove = new ArrayList<>();
            for (RoutePath route : savedRoutes) {
                if (route.startPoint.equals(name) || route.endPoint.equals(name)) {
                    routesToRemove.add(route);
                }
            }
            savedRoutes.removeAll(routesToRemove);
            view.updateMapMarkers(new ArrayList<>(savedMarkers.values()));
            view.toastMessage("Đã xóa: " + name);
        } else {
            view.showError("Không tìm thấy điểm: " + name);
        }
    }

    @Override
    public ArrayList<String> getSavedMarkerNames() {
        return new ArrayList<>(savedMarkers.keySet());
    }

    public String getCurrentStartPoint() { return currentStartPoint; }

    public ArrayList<String> getAvailablePoints() {
        ArrayList<String> points = new ArrayList<>();
        if(!points.contains(currentStartPoint)) points.add(currentStartPoint);
        for (RoutePath r : savedRoutes) {
            if (!points.contains(r.startPoint)) points.add(r.startPoint);
            if (!points.contains(r.endPoint)) points.add(r.endPoint);
        }
        return points;
    }

    public void stopAutoMode() {
        isAutoMode = false;
        commandSend = "S";
        sendCommand("S");
    }

    private void processManualControl() {
        boolean isFrontBlocked = (currentSonicFront > 0 && currentSonicFront < 15);

        if(isUp){
            if (isFrontBlocked) {
                commandSend = "S";
                view.showError("Vật cản! Không thể đi tiếp.");
            } else {
                if(isRight){ commandSend = "FR"; setWhenRoll(); }
                else if(isLeft){ commandSend = "FL"; setWhenRoll(); }
                else{ setStopRoll(); commandSend = "F"; }
            }
        }else if(isDown){
            if(isRight){ commandSend = "BR"; setWhenRoll(); }
            else if(isLeft){ commandSend = "BL"; setWhenRoll(); }
            else{ setStopRoll(); commandSend = "B"; }
        }else if(isRight){
            setWhenRoll();
            if(isUp && !isFrontBlocked) commandSend = "FR";
            else if(isDown) commandSend = "BR";
            else commandSend = "R";
        }else if(isLeft){
            setWhenRoll();
            if(isUp && !isFrontBlocked) commandSend = "FL";
            else if(isDown) commandSend = "BL";
            else commandSend = "L";
        }else{
            commandSend = "S";
        }
    }

    private void setWhenRoll(){ isRoll = true; }
    private void setStopRoll(){ isRoll = false; }

    @Override
    public void connectToDevice(String deviceAddress, String name) {
        model.connectToDevice(deviceAddress,
                new BluetoothModel.ConnectionCallBack() {
                    @Override public void onSuccess() { view.showConnectionSuccess(name); }
                    @Override public void onFailure(String message) { view.showConnectionFailed(); }
                },
                new BluetoothModel.MessageCallBack() {
                    @Override public void onMessageReceived(String message) { processBluetoothMessage(message); }
                    @Override public void onError(String message) { view.showError(message); }
                });
    }

    @Override public Set<BluetoothDevice> getPairedDevice(){ return model.getPairedDevices(); }
    @Override public void sendCommand(String command) { command += "\n"; model.sendData(command); }
    @Override public void setCommandSend(String commandSend) { this.commandSend = commandSend; }
    @Override public void disconnect() { model.disconnect(); view.showDisconnected(); }
    public void processOnStatusClick(){ if(model.isConnected()) disconnect(); else view.startPickDeviceActivity(); }

    private void processBluetoothMessage(String fullMessage) {
        view.showMessage(fullMessage); // Hiện thông tin debug

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

            robotModel.setFloatX(this.currentX / MAP_SQUARE_SIZE_CM);
            robotModel.setFloatY(this.currentY / MAP_SQUARE_SIZE_CM);

            String[] sonicParts = lines[2].replaceAll("[^0-9;]", "").split(";");
            int sonicL = Integer.parseInt(sonicParts[0]);
            int sonicR = Integer.parseInt(sonicParts[1]);
            int sonicF = Integer.parseInt(sonicParts[2]);

            // Cập nhật giá trị cảm biến để loopHandler xử lý
            this.currentSonicFront = sonicF;

            // [SỬA] Bỏ đoạn logic hủy AutoMode tại đây
            // Logic chặn xe giờ được chuyển sang loopHandler

            robotModel.setSonicValue(new SonicValue(sonicL, sonicR, sonicF));
        } catch (Exception e) {}
        view.updateRobotModel(robotModel);
    }

    private int mapYAngleInto360(float yAngle){ int mapped = (int) yAngle; if(mapped < 0) mapped = 360 + mapped; return mapped; }
    public void setSettingCompass(boolean settingCompass) { isSettingCompass = settingCompass; if(settingCompass) sendCommand("calculatingCalibration"); else sendCommand("resetCalibration"); }
    public boolean isSettingCompass() { return isSettingCompass; }

    public void setIsShowSeekBarGroup(boolean isShow){
        view.setVisibleSeekBarGroup(isShow);
        isShowSeekBaGroup = isShow;
    }
    public boolean isShowSeekBaGroup() { return isShowSeekBaGroup; }

    private void requestToTurnBluetoothOn(){
        if (view instanceof Context) {
            Context context = (Context) view;
            if (!bluetoothAdapter.isEnabled()) {
                Intent requestBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // Handle permission
                }
            }
        }
    }
    public void setUp(boolean up) { isUp = up; }
    public void setDown(boolean down) { isDown = down; }
    public void setLeft(boolean left) { isLeft = left; }
    public void setRight(boolean right) { isRight = right; }
}