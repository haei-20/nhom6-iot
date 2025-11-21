#include "BluetoothSerial.h"
#include <Wire.h>
#include <Adafruit_BMP085.h>
#include "MPU6050_6Axis_MotionApps20.h"
#include <QMC5883LCompass.h>

BluetoothSerial SerialBT;

// --- CẢM BIẾN & ĐỐI TƯỢNG ---
Adafruit_BMP085 bmp;
MPU6050 mpu;
QMC5883LCompass compass;

// --- BIẾN IMU & LA BÀN ---
int16_t accelX = 0, accelY = 0, accelZ = 0;
int16_t gyroX = 0, gyroY = 0, gyroZ = 0;
int compassX = 0, compassY = 0, compassZ = 0, compassHeading = 0;
float temp = 0.0;
int32_t pressure = 0;

// Biến Calibration La bàn
int xMinCalibra = 99999, xMaxCalibra = -99999;
int yMinCalibra = 99999, yMaxCalibra = -99999;
int zMinCalibra = 99999, zMaxCalibra = -99999;
bool isCalibration = false;

// MPU6050 DMP
bool DMPReady = false;
uint8_t devStatus;
uint8_t FIFOBuffer[64];
Quaternion q;
VectorFloat gravity;
VectorInt16 aa;
VectorInt16 aaReal;
float ypr[3];

float magStrength;
float maxMagStrength = -9999;
float yawCompassOffset = 0.0f;
bool compassValid = true;

// --- ENCODER & ĐO KHOẢNG CÁCH ---
// [QUAN TRỌNG] Thêm volatile để báo cho compiler biết biến này thay đổi trong ngắt
volatile int dem = 0;
volatile unsigned long dem2 = 0;
portMUX_TYPE timerMux = portMUX_INITIALIZER_UNLOCKED; // Khóa để bảo vệ biến chung

float alphaEn = 1;
float travel_distance = 0;
float duong_kinh_banh_xe = 6.6;
int enco = 13; 

float rpm = 0;
float tocdo = 0;
int timecho = 1000;
unsigned long thoigian = 0, hientai = 0;

// Hàm ngắt đếm xung
void IRAM_ATTR dem_xung() // IRAM_ATTR để đặt hàm trong RAM cho nhanh
{
  portENTER_CRITICAL_ISR(&timerMux);
  dem++;
  dem2++;
  portEXIT_CRITICAL_ISR(&timerMux);
}

// --- ĐỘNG CƠ L298N ---
const int enA = 2;  
const int in1 = 27; 
const int in2 = 26; 
const int in3 = 25; 
const int in4 = 33; 
const int enB = 4;  
int speed = 120;
int delta_speed = -14;
String dieu_khien;

// --- CẢM BIẾN SIÊU ÂM ---
#define LEFT_TRIG 5  
#define LEFT_ECHO 34 
#define RIGHT_TRIG 23 
#define RIGHT_ECHO 32 
#define FRONT_TRIG 18 
#define FRONT_ECHO 35 

long leftDistance = 0;
long rightDistance = 0;
long frontDistance = 0;

TaskHandle_t flowTaskHandle;

// --- KHAI BÁO HÀM ---
void tien();
void lui();
void trai();
void phai();
void Stop();
void dieuKhienDongCo(bool in1_val, bool in2_val, bool in3_val, bool in4_val);
long getDistance(int TRIG_PIN, int ECHO_PIN);
void gy87Task();
void flowTask(void * parameter);
void sendAllInformation();
float calculateAltitude(float pressure, float temperature);
void trySetYawCompassOffset();
void isMagneticFieldValid();
void getCalibrationMPU();
void calAngle();
void calculatingCalibration();
void resetCalibration();

void setup()
{
  Serial.begin(115200);
  SerialBT.begin("ESP_TEST");
  Serial.println("Bluetooth ready!");

  // Khởi động I2C
  Wire.begin(21, 22);
  Wire.setClock(400000);

  // BMP180
  if (!bmp.begin()) {
    Serial.println("BMP180 not found!");
  }

  // MPU6050
  mpu.initialize();
  if (!mpu.testConnection()) {
    Serial.println("MPU6050 not connected!");
    // Không while(1) để code vẫn chạy các phần khác
  }

  devStatus = mpu.dmpInitialize();
  getCalibrationMPU();

  if (devStatus == 0) {
    mpu.CalibrateAccel(6);
    mpu.CalibrateGyro(6);
    mpu.setDMPEnabled(true);
    DMPReady = true;
  } else {
    Serial.print("DMP Init failed: ");
    Serial.println(devStatus);
  }

  mpu.setI2CBypassEnabled(true);  

  // Compass
  compass.init();
  compass.setCalibration(-3620, -580, -1958, 926, -2641, 395);

  // Cấu hình chân Động cơ
  pinMode(enA, OUTPUT);
  pinMode(in1, OUTPUT);
  pinMode(in2, OUTPUT);
  pinMode(enB, OUTPUT);
  pinMode(in3, OUTPUT);
  pinMode(in4, OUTPUT);
  Stop();
  analogWrite(enA, speed);
  analogWrite(enB, speed); // Lưu ý: analogWrite trên ESP32 core cũ cần ledcSetup, core mới ok.

  // Cấu hình chân Siêu âm
  pinMode(LEFT_TRIG, OUTPUT);
  pinMode(LEFT_ECHO, INPUT);
  pinMode(RIGHT_TRIG, OUTPUT);
  pinMode(RIGHT_ECHO, INPUT);
  pinMode(FRONT_TRIG, OUTPUT);
  pinMode(FRONT_ECHO, INPUT);

  // Encoder
  pinMode(enco, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(enco), dem_xung, RISING);

  // Task đo cảm biến (Core 0)
  xTaskCreatePinnedToCore(
    flowTask,
    "Flow Task",
    5000,
    NULL,
    1,
    &flowTaskHandle,
    0
  );

  trySetYawCompassOffset();
}

void loop()
{
  thoigian = millis();

  // Lấy giá trị an toàn từ biến ngắt
  unsigned long current_dem2;
  portENTER_CRITICAL(&timerMux);
  current_dem2 = dem2;
  portEXIT_CRITICAL(&timerMux);

  travel_distance = (((float)current_dem2 / 20.0) * (duong_kinh_banh_xe * 3.14)) * alphaEn;
  isMagneticFieldValid();

  // Tính tốc độ mỗi giây
  if (thoigian - hientai >= timecho)
  {
    hientai = thoigian;
    
    int current_dem;
    // Đọc và reset biến đếm xung an toàn
    portENTER_CRITICAL(&timerMux);
    current_dem = dem;
    dem = 0; 
    portEXIT_CRITICAL(&timerMux);

    rpm = ((float)current_dem / 20.0) * 60.0;
    tocdo = ((float)current_dem / 20.0) * (duong_kinh_banh_xe / 100 * 3.14); 

    // Debug Serial (nếu cần)
    // Serial.print("RPM: "); Serial.println(rpm);
  }

  // Xử lý Bluetooth
  if (SerialBT.available())
  {
    dieu_khien = SerialBT.readStringUntil('\n');
    dieu_khien.trim(); // Xóa ký tự thừa

    // --- [MỚI] LOGIC CHẶN VẬT CẢN TẠI ARDUINO ---
    // Điều kiện: Có vật cản (0 < kc < 15) VÀ Lệnh là lệnh TIẾN (F, FR, FL)
    // Lưu ý: 999 là giá trị trả về khi không phát hiện vật cản (timeout)
    bool isObstacle = (frontDistance > 0 && frontDistance < 15);
    bool isMovingForward = (dieu_khien == "F" || dieu_khien == "FR" || dieu_khien == "FL");

    if (isObstacle && isMovingForward) {
        // Gặp vật cản -> Ép dừng ngay lập tức
        Stop(); 
        // (Xe sẽ đứng yên chờ. Khi app gửi lệnh F tiếp theo, nếu hết vật cản nó sẽ nhảy xuống else)
    } 
    else 
    {
        if (dieu_khien == "F") tien();
        else if (dieu_khien == "B") lui();
        else if (dieu_khien == "L") trai();
        else if (dieu_khien == "R") phai();
        else if (dieu_khien == "FR") phai(); // Logic cũ của bạn: FR gọi phai()
        else if (dieu_khien == "FL") trai();
        else if (dieu_khien == "BL") phai();
        else if (dieu_khien == "BR") trai();
        else if (dieu_khien == "S") Stop();
        else if (dieu_khien == "D") {
          // Reset quãng đường
          portENTER_CRITICAL(&timerMux);
          dem2 = 0;
          portEXIT_CRITICAL(&timerMux);
          travel_distance = 0;
        }
        else if (dieu_khien.indexOf("Speed") != -1) {
          speed = dieu_khien.substring(6).toInt();
          analogWrite(enA, speed);
          analogWrite(enB, speed + delta_speed);
        }
        else if (dieu_khien.indexOf("Delta_speed") != -1) {
          delta_speed = dieu_khien.substring(12).toInt();
          analogWrite(enA, speed);
          analogWrite(enB, speed + delta_speed);
        }
        else if (dieu_khien == "calculatingCalibration") {
          isCalibration = true;
          xMinCalibra = 99999; xMaxCalibra = -99999;
          yMinCalibra = 99999; yMaxCalibra = -99999;
          zMinCalibra = 99999; zMaxCalibra = -99999;
          compass.clearCalibration();
        }
        else if (dieu_khien == "resetCalibration") {
          resetCalibration();
        }
    }

    calculatingCalibration();
    sendAllInformation();
  }
}

// --- CÁC HÀM PHỤ TRỢ ---

// [ĐÃ SỬA] Tối ưu hóa gửi chuỗi dùng Buffer
void sendAllInformation()
{
  char buffer[512]; // Buffer đệm đủ lớn

  if (!isCalibration)
  {
     float y_deg = ypr[0] * 180/M_PI;
     float p_deg = ypr[1] * 180/M_PI;
     float r_deg = ypr[2] * 180/M_PI;
     float altitude = calculateAltitude((float)pressure/100, temp);

     // Dùng snprintf để format an toàn và nhanh hơn String +=
     int len = snprintf(buffer, sizeof(buffer), 
        "Speed: %.2f; TravelDis: %.2f; Action: %s\n"
        "SpeedMotor: %d; Delta: %d\n"
        "Sonic: [L: %ld; R: %ld; F: %ld]\n"
        "Accel: [X: %d; Y: %d; Z: %d]\n"
        "Gyro: [X: %d; Y: %d; Z: %d]\n"
        "YPR: [Y: %.2f; P: %.2f; R: %.2f]\n"
        "Compass: [X: %d; Y: %d; Z: %d; H: %d]\n"
        "Env: [Temp: %.1f; Pres: %.1f; Alt: %.1f]\r\n",
        tocdo, travel_distance, dieu_khien.c_str(),
        speed, delta_speed,
        leftDistance, rightDistance, frontDistance,
        aaReal.x, aaReal.y, aaReal.z,
        gyroX, gyroY, gyroZ,
        y_deg, p_deg, r_deg,
        compassX, compassY, compassZ, compassHeading,
        temp, (float)pressure/100, altitude
     );
     SerialBT.write((uint8_t*)buffer, len);
  }
  else 
  {
    // Gửi thông tin Calibration
    snprintf(buffer, sizeof(buffer), 
       "\nCalibrating...\nX: [%d, %d]\nY: [%d, %d]\nZ: [%d, %d]\r\n",
       xMinCalibra, xMaxCalibra, 
       yMinCalibra, yMaxCalibra, 
       zMinCalibra, zMaxCalibra
    );
    SerialBT.print(buffer);
  }
}

void flowTask(void * parameter) {
  for (;;) {
    // Đo khoảng cách liên tục
    leftDistance = getDistance(LEFT_TRIG, LEFT_ECHO);
    vTaskDelay(10 / portTICK_PERIOD_MS); // Nghỉ nhẹ giữa các lần đo để tránh nhiễu sóng
    rightDistance = getDistance(RIGHT_TRIG, RIGHT_ECHO);
    vTaskDelay(10 / portTICK_PERIOD_MS);
    frontDistance = getDistance(FRONT_TRIG, FRONT_ECHO);
    
    gy87Task();
    vTaskDelay(50 / portTICK_PERIOD_MS); 
  }
}

void gy87Task()
{
    mpu.getMotion6(&accelX, &accelY, &accelZ, &gyroX, &gyroY, &gyroZ);
    temp = bmp.readTemperature();
    pressure = bmp.readPressure();
    compass.read();
    compassX = compass.getX();
    compassY = compass.getY();
    compassZ = compass.getZ();
    compassHeading = compass.getAzimuth();
    calAngle();
}

// [ĐÃ SỬA] Thêm timeout để tránh treo
long getDistance(int TRIG_PIN, int ECHO_PIN) {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10); // Tăng lên 10us cho chắc chắn
  digitalWrite(TRIG_PIN, LOW);
  
  // Timeout 30000us (30ms) ~ 5 mét. Nếu quá thời gian sẽ trả về 0 ngay lập tức.
  long duration = pulseIn(ECHO_PIN, HIGH, 30000); 
  
  if (duration == 0) return 999; // Không thấy vật cản hoặc quá xa
  
  long distance = int(duration / 2 / 29.412);
  return distance;
}

void dieuKhienDongCo(bool in1_val, bool in2_val, bool in3_val, bool in4_val)
{
  digitalWrite(in1, in1_val);
  digitalWrite(in2, in2_val);
  digitalWrite(in3, in3_val);
  digitalWrite(in4, in4_val);
}

void tien() { dieuKhienDongCo(HIGH, LOW, LOW, HIGH); }
void lui() { dieuKhienDongCo(LOW, HIGH, HIGH, LOW); }
void trai() { dieuKhienDongCo(HIGH, LOW, HIGH, LOW); } // Xoay tại chỗ trái
void phai() { dieuKhienDongCo(LOW, HIGH, LOW, HIGH); } // Xoay tại chỗ phải
void Stop() { dieuKhienDongCo(LOW, LOW, LOW, LOW); }


void calculatingCalibration()
{
  if(isCalibration)
  {
    compass.read();
    compassX = compass.getX();
    compassY = compass.getY();
    compassZ = compass.getZ();

    if (xMinCalibra > compassX) xMinCalibra = compassX;
    if (xMaxCalibra < compassX) xMaxCalibra = compassX;
    
    if (yMinCalibra > compassY) yMinCalibra = compassY;
    if (yMaxCalibra < compassY) yMaxCalibra = compassY;
    
    if (zMinCalibra > compassZ) zMinCalibra = compassZ;
    if (zMaxCalibra < compassZ) zMaxCalibra = compassZ;
  }
}

void resetCalibration()
{
  compass.setCalibration(xMinCalibra, xMaxCalibra, yMinCalibra, yMaxCalibra, zMinCalibra, zMaxCalibra);
  isCalibration = false;
}

void calAngle()
{
  if (!DMPReady) return;
  if (mpu.dmpGetCurrentFIFOPacket(FIFOBuffer)) {
      mpu.dmpGetQuaternion(&q, FIFOBuffer);
      mpu.dmpGetGravity(&gravity, &q);
      mpu.dmpGetYawPitchRoll(ypr, &q, &gravity);
      mpu.dmpGetAccel(&aa, FIFOBuffer);
      mpu.dmpGetLinearAccel(&aaReal, &aa, &gravity);
  }
}

void getCalibrationMPU() {
  mpu.setXGyroOffset(0);
  mpu.setYGyroOffset(0);
  mpu.setZGyroOffset(0);
  mpu.setXAccelOffset(0);
  mpu.setYAccelOffset(0);
  mpu.setZAccelOffset(0);
}

float calculateAltitude(float pressure, float temperature) {
  const float P0 = 1013.25; 
  return (temperature + 273.15) / 0.0065 * (1 - pow(pressure / P0, 1.0 / 5.257));
}

float normalizeAngle(float angle) {
  if (angle < 0) return angle + 360.0;
  return angle;
}

void trySetYawCompassOffset() {
  float rawYawDeg = ypr[0]* 180/M_PI;
  float compassHeadingDeg = (float) compassHeading;
  rawYawDeg = normalizeAngle(rawYawDeg);
  compassHeadingDeg = normalizeAngle(compassHeadingDeg);
  yawCompassOffset = compassHeadingDeg - rawYawDeg;
  Serial.print("Offset: "); Serial.println(yawCompassOffset);
}

void isMagneticFieldValid() {
  magStrength = sqrt(compassX*compassX + compassY*compassY + compassZ*compassZ);
  compassValid = magStrength >= 20 && magStrength <= 3000; 
  if(maxMagStrength < magStrength) maxMagStrength = magStrength;
}