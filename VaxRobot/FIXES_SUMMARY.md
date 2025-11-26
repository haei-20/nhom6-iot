# 📋 TÓM TẮT CÁC SỬA LỖI - APP TỰ TẮT SAU KHI KẾT NỐI BLUETOOTH

## 🎯 NGUYÊN NHÂN CHÍNH:

1. **Quyền Bluetooth không được cấp đủ** (Android 12+)
   - App crash với SecurityException khi gọi Bluetooth API
   - onCreate() không yêu cầu quyền runtime

2. **connectToDevice() không xử lý ngoại lệ**
   - Crash nếu model == null hoặc bluetoothAdapter == null
   - Không bắt SecurityException

3. **processBluetoothMessage() ném lỗi parse**
   - Nếu dữ liệu không đúng format từ Arduino
   - Dùng split() mà không kiểm tra độ dài array
   - Không bắt NumberFormatException

4. **onRequestPermissionsResult() để trống**
   - Callback quyền không được xử lý
   - Người dùng cấp/từ chối quyền nhưng app không phản hồi

---

## ✅ CÁC SỬA LỖI:

### **File: MainActivity.java**

#### Sửa 1: Xử lý callback yêu cầu quyền
```java
// TRƯỚC (lỗi):
@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
}  // ← TRỐNG!

// SAU (sửa):
@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (allGranted) {
            Toast.makeText(this, "✓ Quyền Bluetooth đã được cấp", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "✗ Vui lòng cấp quyền Bluetooth để kết nối", Toast.LENGTH_SHORT).show();
        }
    }
}
```

#### Sửa 2: Kiểm tra quyền trước gọi getPairedDevices()
```java
// TRƯỚC (có thể crash):
public void startPickDeviceActivity(){
    deviceList = getPairedDevices();  // ← Crash nếu chưa cấp quyền!
    Intent intent = new Intent(this, PickDeviceActivity.class);
    ...
}

// SAU (sửa):
public void startPickDeviceActivity(){
    // Kiểm tra quyền trước khi lấy danh sách thiết bị
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Chưa cấp quyền Bluetooth Connect", Toast.LENGTH_SHORT).show();
            requestBluetoothPermissions();
            return;
        }
    }
    
    deviceList = getPairedDevices();
    if (deviceList.isEmpty()) {
        Toast.makeText(this, "Chưa có thiết bị nào được ghép cặp", Toast.LENGTH_SHORT).show();
        return;
    }
    ...
}
```

---

### **File: MainPresenter.java**

#### Sửa 3: Thêm try-catch trong connectToDevice()
```java
// TRƯỚC (lỗi):
@Override
public void connectToDevice(String deviceAddress, String name) {
    model.connectToDevice(deviceAddress,  // ← Crash nếu model == null hoặc SecurityException!
            new BluetoothModel.ConnectionCallBack() {
                @Override public void onSuccess() { view.showConnectionSuccess(name); }
                @Override public void onFailure(String message) { view.showConnectionFailed(); }
            },
            ...);
}

// SAU (sửa):
@Override
public void connectToDevice(String deviceAddress, String name) {
    try {
        if (model == null) {
            view.showError("Lỗi: BluetoothModel chưa được khởi tạo");
            return;
        }
        model.connectToDevice(deviceAddress,
                new BluetoothModel.ConnectionCallBack() {
                    @Override public void onSuccess() { view.showConnectionSuccess(name); }
                    @Override public void onFailure(String message) { 
                        view.showConnectionFailed();
                        view.showError("Kết nối thất bại: " + message);
                    }
                },
                ...);
    } catch (SecurityException se) {
        view.showError("Lỗi quyền: " + se.getMessage());
    } catch (Exception e) {
        view.showError("Lỗi kết nối: " + e.getMessage());
    }
}
```

#### Sửa 4: Cải thiện processBluetoothMessage() - Bảo vệ khỏi crash parse dữ liệu
```java
// TRƯỚC (lỗi):
private void processBluetoothMessage(String fullMessage) {
    view.showMessage(fullMessage);
    
    RobotModel robotModel = new RobotModel();
    robotModel.setSquareSize(MAP_SQUARE_SIZE_PX);
    
    try {
        String[] lines = fullMessage.trim().split("\n");
        if (lines.length < 6) return;
        
        // Parse từng phần nhưng để trống exception handlers
        String[] speedParts = lines[0].split("; ");
        receivedDistance = (float) Double.parseDouble(speedParts[1].split(": ")[1]);  // ← Crash nếu format sai!
        
        String[] yprParts = lines[5].replace(...).split(";");
        float rawYAngle = Float.parseFloat(yprParts[0]...);  // ← Crash nếu yprParts trống!
        
        ...
    } catch (Exception e) {}  // ← Bóp hết lỗi, app crash im lặng!
    
    view.updateRobotModel(robotModel);
}

// SAU (sửa):
private void processBluetoothMessage(String fullMessage) {
    view.showMessage(fullMessage);
    
    try {
        if(fullMessage == null || fullMessage.isEmpty()) return;
        
        String[] lines = fullMessage.trim().split("\\n");
        if (lines.length < 6) {
            view.showError("Dữ liệu không đủ (" + lines.length + " dòng)");
            return;
        }
        
        RobotModel robotModel = new RobotModel();
        robotModel.setSquareSize(MAP_SQUARE_SIZE_PX);
        
        // Parse với kiểm tra từng bước
        try {
            String[] speedParts = lines[0].split("; ");
            if(speedParts.length > 1) {
                String distanceStr = speedParts[1].split(": ")[1].trim();
                receivedDistance = (float) Double.parseDouble(distanceStr);
            }
        } catch (Exception e) {
            Log.e("BT_Distance", "Error parsing distance: " + e.getMessage());
        }
        
        try {
            String[] yprParts = lines[5].replace("YPR:", "").replace("[", "").replace("]", "").split(";");
            if (yprParts.length > 0) {
                float rawYAngle = Float.parseFloat(yprParts[0].replace("Y:", "").trim());
                this.currentAngleDeg = mapYAngleInto360(rawYAngle);
                robotModel.setAngle(this.currentAngleDeg);
            }
        } catch (Exception e) {
            Log.e("BT_Angle", "Error parsing angle: " + e.getMessage());
        }
        
        // ... các phần khác với try-catch riêng ...
        
        view.updateRobotModel(robotModel);
        
    } catch (Exception e) {
        // Bắt mọi lỗi chưa xác định
        view.showError("Lỗi xử lý dữ liệu: " + e.getMessage());
        Log.e("BT_Process", "Fatal error: " + e.getMessage(), e);
    }
}
```

---

### **File: BluetoothModel.java**

#### Sửa 5: Cải thiện log và xử lý lỗi trong startListening()
```java
// TRƯỚC (không log lỗi):
private void startListening(MessageCallBack messageCallBack) {
    isListening = true;
    new Thread(() -> {
        ...
        while (isListening) {
            try {
                ...
                bytes = inputStream.read(buffer);
                if (bytes > 0) {
                    String incomingMessage = new String(buffer, 0, bytes);
                    new Handler(Looper.getMainLooper()).post(() -> messageCallBack.onMessageReceived(incomingMessage));
                }
            } catch (Exception e) {
                isListening = false;  // ← Lỗi im lặng, app có thể crash vô thừa nhận
            }
        }
    }).start();
}

// SAU (sửa):
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
```

---

## 📊 BẢNG SO SÁNH TRƯỚC/SAU:

| Vấn đề | Trước | Sau | Kết quả |
|--------|-------|-----|---------|
| Callback quyền | TRỐNG | Xử lý đủ | Toast phản hồi |
| Check quyền trước API | Không | Có | Không SecurityException |
| connectToDevice() | Không try-catch | Có | Không crash |
| processBluetoothMessage() | Try-catch chung | Try-catch từng phần | Xác định lỗi chính xác |
| Log lỗi | Không | Có (Log.e) | Debug dễ |

---

## 🚀 TIẾP THEO:

1. **Build & Run app** trên điện thoại
2. **Kiểm tra quyền** khi mở app → chọn "Allow"
3. **Test kết nối Bluetooth** → nếu vẫn crash, lấy Logcat
4. **Theo dõi Toast + Logcat** để xác định nguyên nhân

---

**Nếu vẫn gặp lỗi, vui lòng cung cấp:** 
- Logcat khi crash xảy ra
- Phiên bản Android trên điện thoại
- Model thiết bị Bluetooth được ghép cặp

