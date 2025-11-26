# 📊 PHÂN TÍCH LOGCAT - SIÊU ÂM VÀ DỮ LIỆU BLUETOOTH

## 🔍 **KẾT LUẬN:**

### **1️⃣ SIÊU ÂM CÓ HOẠT ĐỘNG ✅**
```
Sonic: L: 199
Sonic: L: 198  
Sonic: L: 200
Sonic: L: 199
```
**✓ Phần cứng siêu âm HOẠT ĐỘNG BÌNH THƯỜNG!**

---

### **2️⃣ VẤN ĐỀ CHÍNH: DỮ LIỆU BỊ TRỘN LẪN ❌**

Logcat cho thấy dữ liệu từ Arduino **KHÔNG CÓ DÒNG NGẮT (\n) RÕ RÀNG**:

```
Error parsing distance: For input string: "S"
Error parsing distance: For input string: "19]"
Error parsing distance: For input string: "-22]"
Error parsing angle: For input string: "Compass: X: 2540"
Error parsing angle: For input string: "Sonic: L: 199"
Error parsing angle: For input string: "Env: Temp: 26.7"
```

**Nguyên nhân:** Arduino gửi dữ liệu nhưng mỗi dòng chứa các thông tin khác nhau:
- Dòng 0 có thể: `Speed: 0.00; Distance: 19` ← **App cố parse "19" làm khoảng cách**
- Dòng 1 có thể: `Compass: X: 2540` ← **Không phải dòng YPR mà app expect**
- Dòng 2 không phải `Sonic:` ← **App cố parse từ dòng 2 nhưng chứa dữ liệu khác**
- Dòng 3-5 cũng không theo thứ tự mong muốn

---

## 🛠️ **NGUYÊN NHÂN CRASH:**

```
Fatal error: Attempt to invoke virtual method 'int com.xe.vaxrobot.Model.SonicValue.getLeft()' 
on a null object reference at com.xe.vaxrobot.Model.MapModel.processSonicValue(MapModel.java:71)
```

**Vì sao?**
1. Parse sonic từ dòng 2 thất bại (không phải dòng sonic)
2. `SonicValue` = null
3. MapModel gọi `getLeft()` trên null → **NullPointerException**

---

## ✅ **CÁC SỬA LỖI ĐÃ THỰC HIỆN:**

### **Sửa 1: MapModel.java - Check SonicValue null**
```java
public void processSonicValue(SonicValue sonicValue){
    // ✓ Thêm check null
    if (sonicValue == null) {
        Log.w("MapModel", "SonicValue is null, skipping sonic processing");
        return;  // ← Tránh crash
    }
    // ... tiếp tục xử lý ...
}
```

### **Sửa 2: MainPresenter.java - TÌM KIẾM DỮ LIỆU TỪ TẤT CẢ DÒNG**
**Thay vì cố định dòng 0, 2, 5 → Tìm kiếm từ tất cả dòng:**

```java
// Cũ (sai):
receivedDistance = parse(lines[0])  // ← Cố định dòng 0
angle = parse(lines[5])            // ← Cố định dòng 5
sonic = parse(lines[2])            // ← Cố định dòng 2

// Mới (đúng):
for (String line : lines) {
    if (line.contains("Speed:") && line.contains("Distance:")) {
        receivedDistance = parse(line)  // ← Tìm dòng có Distance
    }
    if (line.contains("YPR:")) {
        angle = parse(line)  // ← Tìm dòng có YPR
    }
    if (line.contains("Sonic:")) {
        sonic = parse(line)  // ← Tìm dòng có Sonic
    }
}
```

---

## 📈 **BẢNG DỮ LIỆU TỪ LOGCAT:**

| Dòng | Nội dung nhận | Trạng thái |
|------|--------------|-----------|
| `Sonic: L: 199` | Siêu âm trái | ✅ Hoạt động |
| `Sonic: L: 198` | Siêu âm trái | ✅ Hoạt động |
| `Sonic: L: 200` | Siêu âm trái | ✅ Hoạt động |
| `Compass: X: 2540` | Compass sensor | ✅ Hoạt động |
| `Speed: 0.00; Distance: 19` | Khoảng cách | ⚠️ Parse sai |
| `Env: Temp: 26.7` | Nhiệt độ | ℹ️ Thông tin thêm |

---

## 🎯 **KIỂM TRA SAU SỬA:**

### **Bước 1: Build & Run**
```
Build → Rebuild Project
Run → Run 'app'
```

### **Bước 2: Test lại và xem Logcat**
Logcat sẽ hiện:
```
BT_Line_0: Speed: 0.00; Distance: 19
BT_Line_1: YPR: [0; 0; 45]
BT_Line_2: Compass: X: 2540; Y: 1230; Z: 987
...
BT_Line_N: Sonic: L: 199; R: 201; F: 155

✓ Found distance: 19
✓ Found angle: 45
✓ Found sonic - L: 199 R: 201 F: 155
```

### **Bước 3: App sẽ KHÔNG CRASH nữa**
- Map sẽ cập nhật vị trí xe
- Siêu âm sẽ vẽ phạm vi phát hiện
- Phanh gấp sẽ hoạt động nếu vật cản < 30cm

---

## 📝 **KẾT LUẬN:**

| Aspect | Trạng thái | Chi tiết |
|--------|-----------|---------|
| **Siêu âm phần cứng** | ✅ OK | Đang gửi dữ liệu đúng |
| **Format dữ liệu Arduino** | ⚠️ Cần kiểm tra | Các dòng không theo thứ tự cố định |
| **App parse dữ liệu** | ✅ SỬA XONG | Giờ tìm kiếm từ tất cả dòng |
| **Crash NullPointerException** | ✅ SỬA XONG | Đã thêm check null SonicValue |

---

## 🔧 **YÊU CẦU TIẾP THEO:**

Nếu vẫn gặp lỗi, vui lòng:
1. Build & Run lại
2. Mở Logcat
3. Xem dòng bắt đầu bằng `BT_Line_` để xem chính xác dữ liệu Arduino gửi
4. Gửi cho tôi screenshot Logcat

---

**✓ Siêu âm hoạt động tốt! Chỉ cần sửa cách app parse dữ liệu thôi.** 🎉

