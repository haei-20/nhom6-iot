# 🔧 HƯỚNG DẪN DEBUG - APP TỰ TẮT SAU KHI KẾT NỐI BLUETOOTH

## ✅ CÁC SỬA LỖI ĐÃ THỰC HIỆN:

1. **MainActivity.onRequestPermissionsResult()** - ✓ Thêm xử lý callback
2. **MainActivity.startPickDeviceActivity()** - ✓ Kiểm tra quyền trước gọi API
3. **MainPresenter.connectToDevice()** - ✓ Thêm try-catch bảo vệ
4. **MainPresenter.processBluetoothMessage()** - ✓ Cải thiện xử lý lỗi parse dữ liệu
5. **BluetoothModel.startListening()** - ✓ Cải thiện log và xử lý ngoại lệ

---

## 🔍 CÁCH LẤY LOGCAT ĐỂ TÌM LỖI:

### **Cách 1: Android Studio (DỄ NHẤT)**
```
1. Mở Android Studio
2. Tab "Logcat" ở dưới cùng (nếu không thấy: View → Tool Windows → Logcat)
3. Chọn device đang chạy app
4. Chọn filter: "Show only selected application"
5. Mở app → kết nối Bluetooth → nếu crash sẽ thấy lỗi đầu tiên
```

### **Cách 2: Dòng lệnh (ADB)**
```powershell
# Xóa log cũ
adb logcat -c

# Xem log real-time với filter
adb logcat | Select-String -Pattern "BT_|E/|crash|Exception" -Context 2

# Hoặc lưu vào file
adb logcat > logcat.txt

# Mở file
notepad logcat.txt
```

---

## 🔴 CÁC LỖI THƯỜNG GẶP VÀ CÁCH SỬA:

### **Lỗi 1: SecurityException - Thiếu quyền Bluetooth**
```
E/BT_Connect: SecurityException: Missing permission BLUETOOTH_CONNECT
```
**Cách sửa:**
- App sẽ tự yêu cầu quyền khi mở (nếu bạn chưa cấp)
- Bạn phải **chọn "Allow"** trong popup cấp quyền
- Nếu từ chối, app sẽ không thể kết nối

### **Lỗi 2: IOException - Không kết nối được**
```
E/BT_Connect: IO Error: Unable to establish connection
```
**Cách sửa:**
- Kiểm tra Bluetooth trên điện thoại đã bật chưa
- Kiểm tra thiết bị có được ghép cặp không (Cài đặt → Bluetooth)
- Tắt/bật Bluetooth trên điện thoại
- Khởi động lại app

### **Lỗi 3: Crash khi parse dữ liệu Bluetooth**
```
E/BT_Distance: Error parsing distance: NumberFormatException
E/BT_Angle: Error parsing angle: ArrayIndexOutOfBoundsException
E/BT_Sonic: Error parsing sonic: Exception
```
**Cách sửa:**
- Dữ liệu từ Arduino không đúng format
- Kiểm tra Arduino gửi đúng 6 dòng dữ liệu không
- Xem dữ liệu trong "Message" textview trên app

### **Lỗi 4: NullPointerException trong processBluetoothMessage()**
```
E/BT_Process: Fatal error: NullPointerException
```
**Cách sửa:** Đã được bảo vệ bằng try-catch, app sẽ hiển thị lỗi thay vì crash

---

## 📊ỈNH TEST TỪNG BƯỚC:

### **Bước 1: Test yêu cầu quyền**
1. Cài app mới
2. Mở app → sẽ hiện popup cấp quyền
3. Chọn **"Allow"**
4. Toast sẽ hiện: ✓ Quyền Bluetooth đã được cấp
5. **Nếu không hiện popup → Lỗi ở requestBluetoothPermissions()**

### **Bước 2: Test lấy danh sách thiết bị**
1. Bấm nút Bluetooth (trái ở trên)
2. Nếu lỗi → Toast: "Chưa cấp quyền..." hoặc "Chưa có thiết bị..."
3. Nếu thành công → Hiển thị danh sách thiết bị

### **Bước 3: Test kết nối**
1. Chọn thiết bị từ danh sách
2. Logcat sẽ hiện: "BT_Connect: Connecting to [device]"
3. Nếu thành công:
   - Biểu tượng Bluetooth sẽ hiển thị kết nối
   - Toast: "Connected"
   - **App KHÔNG tắt**
4. Nếu crash → Xem Logcat để tìm nguyên nhân

### **Bước 4: Test dữ liệu Bluetooth**
1. App kết nối thành công
2. Xem dữ liệu nhận từ Arduino trong mục "Message"
3. Nếu có dữ liệu:
   - Map sẽ cập nhật vị trí xe
   - Số liệu siêu âm sẽ hiện
   - **App tiếp tục chạy**
4. Nếu không có dữ liệu:
   - Kiểm tra Arduino có gửi không
   - Kiểm tra baud rate (115200)

---

## 🛠️ CÔNG CỤ DEBUG TRONG APP:

**Textview "Message"** (phía trên cùng) hiển thị:
- `Message: [dữ liệu nhận từ Bluetooth]` → Dữ liệu OK
- `Error: [lỗi]` → Có lỗi, xem chi tiết

**Toast** (thông báo bao quanh màn hình):
- ✓ Quyền Bluetooth đã được cấp
- ✗ Vui lòng cấp quyền Bluetooth để kết nối
- Connected / Connection Failed / Disconnected
- Phanh gấp! Vật cản: Xcm
- Lỗi xử lý dữ liệu: [chi tiết]

---

## 📱 TÀI NGUYÊN HỮU DỤNG:

1. **Bluetooth Permission Android 12+**: https://developer.android.com/guide/topics/connectivity/bluetooth/permissions
2. **Android Logcat**: https://developer.android.com/tools/logcat
3. **SecurityException**: https://developer.android.com/reference/java/lang/SecurityException

---

## ❓ NẾU VẪN CÒN CRASH:

1. **Ghi lại Logcat khi crash xảy ra**
   - Mở Logcat → Mở app → Kết nối Bluetooth
   - Sao chép toàn bộ log từ lúc app mở đến khi crash
   
2. **Kiểm tra những phần sau:**
   - Phiên bản Android trên điện thoại (Settings → About)
   - Phiên bản Build.gradle trong app
   - Liệu có thiết bị nào đã ghép cặp không

3. **Test theo các bước trên** để xác định chính xác lỗi ở đâu

---

**Chúc bạn debug thành công! 🎉**

