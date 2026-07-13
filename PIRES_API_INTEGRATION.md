# Pires OBD-Java API Integration

## Overview

Your Pro Racing OBD app now integrates the **Pires OBD-Java API**, giving you access to 80+ standard OBD2 PIDs with professional-grade error handling and protocol support.

## What's Been Added

### 1. Dependencies

**build.gradle**:
```gradle
implementation 'com.github.pires:obd-java-api:1.0'
```

**settings.gradle**:
```gradle
maven { url 'https://jitpack.io' }
```

### 2. New Classes

#### PiresObdManager.java
Wrapper for Pires API with easy-to-use methods:
- Standard OBD2 commands (RPM, Speed, Throttle, etc.)
- Temperature monitoring (Coolant, Air)
- Pressure readings (Intake, Barometric, Boost calculation)
- Diagnostic codes (Trouble codes, Pending codes)
- Raw command execution

#### EnhancedObdService.java
Hybrid service combining:
- **Pires API** for standard OBD2 (80+ PIDs)
- **Custom code** for ECU flashing and advanced tuning
- Unified interface for both

## Available Commands

### Engine Parameters (Pires API)
```java
enhancedObd.getRPM()                    // Engine RPM
enhancedObd.getSpeed()                  // Vehicle speed (km/h)
enhancedObd.getThrottlePosition()       // Throttle position (0-100%)
```

### Temperature Sensors (Pires API)
```java
enhancedObd.getCoolantTemperature()     // Coolant temp (°C)
piresManager.executeCommand("AIR_TEMP") // Ambient air temp
```

### Pressure Sensors (Pires API)
```java
enhancedObd.getBoostPressure()          // Boost pressure (PSI)
piresManager.getIntakePressure()        // Intake manifold pressure
piresManager.getBarometricPressure()    // Barometric pressure
```

### Fuel System (Pires API)
```java
enhancedObd.getFuelLevel()              // Fuel level (%)
```

### Diagnostics (Custom + Pires)
```java
DtcManager dtc = enhancedObd.getDtcManager();
dtc.readDtcCodes()                      // Read all codes
dtc.clearDtcCodes()                     // Clear codes
dtc.getDtcCount()                       // Count of codes
dtc.isMilOn()                           // Check engine light status

piresManager.getTroubleCodes()          // Pires trouble codes
piresManager.getPendingTroubleCodes()   // Pires pending codes
```

### Advanced/Custom
```java
enhancedObd.sendRawCommand("ATZ")       // Raw AT commands
enhancedObd.executePiresCommand("RPM")  // Execute by name
```

## Pires API Supported Commands (80+)

### Speed & Distance
- Vehicle Speed
- Distance Since Codes Cleared
- Distance with MIL On

### Engine
- RPM
- Engine Load
- Absolute Load Value
- Fuel Rate
- Engine Runtime
- Warm-ups Since Codes Cleared
- Time Since Codes Cleared
- Time with MIL On

### Fuel System
- Fuel Level
- Fuel Type
- Fuel Pressure
- Fuel Rail Pressure
- Fuel Trim (Short/Long Term)
- Fuel System Status
- Fuel Injection Timing

### Temperature
- Coolant Temperature
- Oil Temperature
- Air Intake Temperature
- Ambient Air Temperature
- Catalyst Temperature (Bank 1 & 2)

### Pressure
- Intake Manifold Pressure
- Barometric Pressure
- Fuel Rail Pressure
- Turbo/Supercharger Pressure

### Exhaust
- O2 Sensor Readings (All banks)
- Catalyst Temperature
- EGR Error

### Electrical
- Battery Voltage
- Control Module Voltage
- Hybrid Battery Pack Remaining Life

### Advanced
- MAF Air Flow Rate
- Commanded EGR
- EGR Error
- Evaporative Purge
- Timing Advance
- VIN (Vehicle Identification Number)

## Usage Examples

### Basic Gauge Reading
```java
EnhancedObdService obd = new EnhancedObdService(vehicleProfile);
obd.connect(bluetoothDevice);

// In your update loop
int rpm = obd.getRPM();
int speed = obd.getSpeed();
float boost = obd.getBoostPressure();
float coolant = obd.getCoolantTemperature();
```

### Diagnostic Codes
```java
DtcManager dtcManager = obd.getDtcManager();
List<DiagnosticTroubleCode> codes = dtcManager.readDtcCodes();

for (DiagnosticTroubleCode code : codes) {
    Log.d("DTC", code.getCode() + ": " + code.getDescription());
}

// Clear codes
dtcManager.clearDtcCodes();
```

### Custom/Advanced
```java
// For ECU flashing - use custom service
String response = obd.sendRawCommand("ATZ");

// For standard OBD - use Pires
PiresObdManager pires = obd.getPiresManager();
int intakePressure = pires.getIntakePressure();
```

## Architecture

```
EnhancedObdService
├── PiresObdManager (Standard OBD2)
│   ├── 80+ PIDs
│   ├── Protocol auto-detection
│   └── Error handling
│
└── ObdConnectionService (Custom)
    ├── ECU Flashing
    ├── Advanced Tuning
    └── Raw commands

DtcManager (Uses both)
├── Pires for code reading
└── Custom for clearing
```

## Benefits

### Pires API
✅ 80+ standard OBD2 commands
✅ Automatic protocol detection
✅ Well-tested and maintained
✅ Proper error handling
✅ Industry-standard implementation

### Custom Code
✅ ECU flashing capability
✅ Advanced tuning parameters
✅ Vehicle-specific protocols
✅ Raw command access
✅ Custom features

## Migration from Custom to Pires

If you want to use Pires for a gauge, simply change:

**Before (Custom)**:
```java
obdService.sendAndReceive("010C")  // RPM command
// Parse hex response manually
```

**After (Pires)**:
```java
enhancedObd.getRPM()  // Returns integer directly
```

## Testing

### 1. Build with Pires
```bash
./gradlew assembleDebug
```

### 2. Test Standard OBD2
```java
EnhancedObdService obd = new EnhancedObdService(vehicleProfile);
obd.connect(device);

if (obd.isPiresInitialized()) {
    int rpm = obd.getRPM();
    int speed = obd.getSpeed();
}
```

### 3. Test Custom Features
```java
// ECU flashing still uses custom code
EcuFlashManager flasher = new EcuFlashManager(vehicleProfile);
flasher.backupEcuRom(file);
```

## Next Steps

1. **Update DashboardFragment** to use Pires methods
2. **Keep ECU flash** using custom code
3. **Migrate gauges** one by one to Pires
4. **Add more PIDs** from Pires library
5. **Test with real adapter**

## Recommended Approach

**For Standard Gauges**: Use Pires API
- RPM, Speed, Coolant Temp, Throttle
- Fuel Level, Battery Voltage
- Intake Pressure (for Boost)

**For Custom Features**: Use Custom Code
- ECU Flashing
- Tuning Parameters
- Vehicle-specific protocols
- Raw ECU communication

## API Documentation

Pires OBD-Java API: https://github.com/pires/obd-java-api

Full command list: https://github.com/pires/obd-java-api/tree/master/src/main/java/com/github/pires/obd/commands

---

**Status**: Integrated ✅
**Build**: Ready to test
**Compatibility**: Android 7.0+ (API 24)
