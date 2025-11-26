package com.xe.vaxrobot.Main;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences; // [MỚI]
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.gson.Gson; // [MỚI]
import com.google.gson.reflect.TypeToken; // [MỚI]
import com.xe.vaxrobot.Model.BluetoothModel;
import com.xe.vaxrobot.Model.MarkerModel;
import com.xe.vaxrobot.Model.RobotModel;
import com.xe.vaxrobot.Model.SonicValue;

import java.lang.reflect.Type; // [MỚI]
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

    // [MỚI] Tên file lưu trữ
    private static final String PREFS_NAME = "VaxRobotData";

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

    // Biến lưu khoảng cách phía trước
    private int currentSonicFront = 999;

    // Biến an toàn
    private long lastObstacleTime = 0;
    private long resumeStartTime = 0;
    private static final long SAFETY_LOCK_MS = 1000;
    private static final long RESUME_COMPENSATION_MS = 200;

    boolean isUp = false, isDown = false, isLeft = false, isRight = false;
    private String commandSend = "S";
    private String lastCommandSend = "S";

    @Inject
    public MainPresenter() {}

    public void setView(MainContract.View view){
        this.view = view;
        init();

        // [MỚI] Tải dữ liệu cũ ngay khi mở App
        loadMapData();

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

    // --- [MỚI] HÀM LƯU DỮ LIỆU ---
    private void saveMapData() {
        if (view instanceof Context) {
            Context context = (Context) view;
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            Gson gson = new Gson();

            // Lưu danh sách đường đi
            String jsonRoutes = gson.toJson(savedRoutes);
            editor.putString("savedRoutes", jsonRoutes);

            // Lưu danh sách điểm mốc
            String jsonMarkers = gson.toJson(savedMarkers);
            editor.putString("savedMarkers", jsonMarkers);

            // Lưu vị trí hiện tại (để khi mở lại biết xe đang ở đâu)
            editor.putString("currentStartPoint", currentStartPoint);

            editor.apply();
            // Log.d("STORAGE", "Đã lưu dữ liệu!");
        }
    }

    // --- [MỚI] HÀM TẢI DỮ LIỆU ---
    private void loadMapData() {
        if (view instanceof Context) {
            Context context = (Context) view;
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Gson gson = new Gson();

            // Tải Routes
            String jsonRoutes = prefs.getString("savedRoutes", null);
            if (jsonRoutes != null) {
                Type type = new TypeToken<ArrayList<RoutePath>>() {}.getType();
                savedRoutes = gson.fromJson(jsonRoutes, type);
            }

            // Tải Markers
            String jsonMarkers = prefs.getString("savedMarkers", null);
            if (jsonMarkers != null) {
                Type type = new TypeToken<HashMap<String, MarkerModel>>() {}.getType();
                savedMarkers = gson.fromJson(jsonMarkers, type);

                // Cập nhật lên bản đồ ngay khi tải xong
                if(savedMarkers != null && !savedMarkers.isEmpty()){
                    view.updateMapMarkers(new ArrayList<>(savedMarkers.values()));
                }
            }

            // Tải vị trí cuối cùng
            String savedStartPoint = prefs.getString("currentStartPoint", null);
            if(savedStartPoint != null) {
                currentStartPoint = savedStartPoint;
            }
        }
    }

    private void loopHandler(){
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (model != null && model.isConnected()) {
                    boolean isSafetyLocked = (System.currentTimeMillis() - lastObstacleTime < SAFETY_LOCK_MS);

                    if (isAutoMode) {
                        boolean isBlocked = (currentSonicFront > 0 && currentSonicFront < 40);
                        if (isBlocked || isSafetyLocked) {
                            commandSend = "S";
                            if (isBlocked) {
                                lastObstacleTime = System.currentTimeMillis();
                                view.toastMessage("Vật cản! Đang chờ...");
                            }
                            resumeStartTime = 0;
                        } else {
                            if (resumeStartTime == 0) {
                                resumeStartTime = System.currentTimeMillis();
                            }
                            long timeSinceResume = System.currentTimeMillis() - resumeStartTime;
                            if (timeSinceResume < RESUME_COMPENSATION_MS) {
                                if (currentReplaySequence != null && replayIndex < currentReplaySequence.size()) {
                                    commandSend = currentReplaySequence.get(replayIndex);
                                } else {
                                    commandSend = "F";
                                }
                            } else {
                                processAutoRun();
                            }
                        }
                    } else {
                        processManualControl(isSafetyLocked);

                        if (!commandSend.equals("S")) {
                            currentRecording.add(commandSend);
                            lastCommandSend = commandSend;
                        } else if (!lastCommandSend.equals("S")) {
                            currentRecording.add("S");
                            lastCommandSend = "S";
                        }
                    }
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

        // [MỚI] Lưu xuống bộ nhớ ngay sau khi lưu hành trình
        saveMapData();

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

        // [MỚI] Reset thì xóa sạch dữ liệu cũ
        savedRoutes.clear();
        savedMarkers.clear();
        saveMapData(); // Lưu trạng thái rỗng

        view.resetMap();
        view.toastMessage("Đã reset bản đồ và dữ liệu!");
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

            // [MỚI] Cập nhật lại bộ nhớ sau khi xóa
            saveMapData();

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

    private void processManualControl(boolean isSafetyLocked) {
        boolean isFrontBlocked = (currentSonicFront > 0 && currentSonicFront < 40);

        if (isFrontBlocked) {
            lastObstacleTime = System.currentTimeMillis();
        }

        if(isUp){
            if (isFrontBlocked || isSafetyLocked) {
                commandSend = "S";
                if (isFrontBlocked) view.toastMessage("Phanh gấp! Vật cản gần.");
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
            if(isUp && !isFrontBlocked && !isSafetyLocked) commandSend = "FR";
            else if(isDown) commandSend = "BR";
            else commandSend = "R";
        }else if(isLeft){
            setWhenRoll();
            if(isUp && !isFrontBlocked && !isSafetyLocked) commandSend = "FL";
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
        try {
            if (model == null) return;
            model.connectToDevice(deviceAddress,
                    new BluetoothModel.ConnectionCallBack() {
                        @Override public void onSuccess() { view.showConnectionSuccess(name); }
                        @Override public void onFailure(String message) { view.showConnectionFailed(); }
                    },
                    new BluetoothModel.MessageCallBack() {
                        @Override public void onMessageReceived(String message) { processBluetoothMessage(message); }
                        @Override public void onError(String message) { view.showError(message); }
                    });
        } catch (Exception e) {
            view.showError("Lỗi kết nối: " + e.getMessage());
        }
    }

    @Override public Set<BluetoothDevice> getPairedDevice(){ return model.getPairedDevices(); }
    @Override public void sendCommand(String command) { command += "\n"; model.sendData(command); }
    @Override public void setCommandSend(String commandSend) { this.commandSend = commandSend; }
    @Override public void disconnect() { model.disconnect(); view.showDisconnected(); }
    public void processOnStatusClick(){ if(model.isConnected()) disconnect(); else view.startPickDeviceActivity(); }

    private void processBluetoothMessage(String fullMessage) {
        view.showMessage(fullMessage);

        RobotModel robotModel = new RobotModel();
        robotModel.setSquareSize(MAP_SQUARE_SIZE_PX);

        try {
            if(fullMessage == null || fullMessage.isEmpty()) return;
            String[] lines = fullMessage.trim().split("\n");

            for (String line : lines) {
                line = line.trim();

                if (line.startsWith("Speed:")) {
                    try {
                        String[] parts = line.split("; ");
                        if(parts.length > 1) {
                            receivedDistance = (float) Double.parseDouble(parts[1].split(": ")[1]);
                        }
                    } catch (Exception e) {}
                }
                else if (line.startsWith("YPR:")) {
                    try {
                        String[] parts = line.replace("YPR:", "").replace("[", "").replace("]", "").split(";");
                        if (parts.length > 0) {
                            float rawYAngle = Float.parseFloat(parts[0].replace("Y:", "").trim());
                            this.currentAngleDeg = mapYAngleInto360(rawYAngle);
                            robotModel.setAngle(this.currentAngleDeg);
                        }
                    } catch (Exception e) {}
                }
                else if (line.startsWith("Sonic:")) {
                    try {
                        String rawNums = line.replaceAll("[^0-9;]", "");
                        String[] parts = rawNums.split(";");

                        if (parts.length >= 3) {
                            int sonicL = Integer.parseInt(parts[0]);
                            int sonicR = Integer.parseInt(parts[1]);
                            int sonicF = Integer.parseInt(parts[2]);

                            this.currentSonicFront = sonicF;

                            if (sonicF > 0 && sonicF < 40) {
                                lastObstacleTime = System.currentTimeMillis();
                                if (!commandSend.equals("S")) {
                                    sendCommand("S");
                                    commandSend = "S";
                                    view.toastMessage("Phanh gấp! Vật cản: " + sonicF + "cm");
                                }
                            }

                            robotModel.setSonicValue(new SonicValue(sonicL, sonicR, sonicF));
                        }
                    } catch (Exception e) {}
                }
            }

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

        } catch (Exception e) {
            view.showError("Lỗi dữ liệu: " + e.getMessage());
        }

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