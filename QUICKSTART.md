# Pro Racing OBD - Quick Start Guide

## What You've Built

A professional Android OBD2 application with:
- **Real-time racing dashboard** with 12+ gauges
- **DTC diagnostics** (read/clear codes)
- **ECU flashing** capability (backup & restore)
- **Advanced tuning** (AFR, timing, boost, rev limiter)
- **Multi-vehicle support** (BMW, VW, Dodge, Diesel variants)
- **Data logging** to CSV
- **Racing-themed UI** (black/red theme)

## Directory Structure

```
ProRacingOBD/
├── app/
│   ├── src/main/
│   │   ├── java/shop/surina/proracingobd/
│   │   │   ├── activities/          # Main activity
│   │   │   ├── fragments/           # 5 main screens
│   │   │   ├── services/            # OBD connection & DTC
│   │   │   ├── ecu/                 # ECU flash & tuning
│   │   │   ├── vehicles/            # Vehicle profiles
│   │   │   ├── models/              # Data models
│   │   │   ├── adapters/            # RecyclerView adapters
│   │   │   └── utils/               # Data logging
│   │   ├── res/
│   │   │   ├── layout/              # XML layouts
│   │   │   ├── values/              # Colors, strings, styles
│   │   │   └── menu/                # Navigation menu
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

## Building the App

### Prerequisites
1. Install Android Studio (latest version)
2. Install JDK 11 or higher
3. Install Android SDK API 34

### Build Steps

1. **Open in Android Studio**:
   ```bash
   cd C:\Users\Computer User\Desktop\obd2\ProRacingOBD
   ```
   - Open Android Studio
   - File > Open > Select `ProRacingOBD` folder

2. **Sync Gradle**:
   - Android Studio will auto-sync
   - Or: File > Sync Project with Gradle Files

3. **Build**:
   - Build > Make Project (Ctrl+F9)
   - Or run: `./gradlew build`

4. **Install on Device**:
   - Connect Android device via USB
   - Enable Developer Options & USB Debugging
   - Run > Run 'app' (Shift+F10)

## Testing the App

### Without OBD2 Adapter (Demo Mode)

The dashboard includes simulated data, so you can test the UI without hardware:

1. Launch the app
2. Go to Dashboard tab
3. Gauges will show random simulated values
4. Navigate through all 5 tabs to see features

### With OBD2 Adapter

1. **Setup**:
   - Pair Bluetooth ELM327 adapter in Android settings
   - Plugin adapter to vehicle OBD2 port
   - Turn ignition to ON (engine can be off for testing)

2. **Connect**:
   - Open app > Settings tab
   - Select your vehicle type
   - Select cable type
   - Tap "Scan for Devices"
   - Select your adapter
   - Tap "Connect"

3. **Test Features**:
   - **Dashboard**: View real-time data
   - **Diagnostics**: Read/clear codes
   - **Tuning**: Adjust parameters (careful!)
   - **ECU Flash**: Backup ROM first!

## Key Features by Tab

### 1. Dashboard
- **Purpose**: Real-time performance monitoring
- **Gauges**: RPM, Speed, Boost, AFR, Temps, Pressures
- **Colors**: Green=OK, Orange=Warning, Red=Critical

### 2. Diagnostics
- **Read Codes**: Get active/pending/permanent DTCs
- **Clear Codes**: Reset check engine light
- **MIL Status**: See if check engine light is on

### 3. ECU Flash
- **Backup**: Save original ECU ROM (.bin file)
- **Flash**: Load custom tune
- **Progress**: Real-time status updates
- **⚠️ WARNING**: Can damage engine if done wrong!

### 4. Tuning
- **Presets**: Conservative, Street, Aggressive, Race
- **Manual**: Adjust AFR, timing, boost, rev limit
- **Features**: Launch control, anti-lag
- **Apply**: Push changes to ECU

### 5. Settings
- **Vehicle**: Choose from 10+ vehicle types
- **Cable**: Select adapter type
- **Connection**: Scan & connect to Bluetooth

## Supported Vehicles

| Vehicle | Cable | Protocol | Flash | Tuning |
|---------|-------|----------|-------|--------|
| BMW N54 | K+DCAN | BMW DS2 | ✅ | ✅ |
| Milwaukee 117 | Milwaukee 117 | ISO CAN | ✅ | ❌ |
| VW/Audi | VCDS | VAG KWP | ✅ | ✅ |
| Dodge HEMI | J2534 | ISO CAN | ✅ | ✅ |
| Dodge Cummins | J2534 | SAE J1939 | ✅ | ✅ |
| BullyDog | BullyDog GT | ISO CAN | ❌ | ❌ |
| Ford PowerStroke | J2534 | SAE J1939 | ✅ | ✅ |
| Chevy Duramax | J2534 | SAE J1939 | ✅ | ✅ |
| Subaru WRX/STI | Tactrix | ISO CAN | ✅ | ✅ |

## Common OBD2 Commands Used

```
ATZ      - Reset adapter
ATE0     - Echo off
ATL0     - Linefeeds off
ATS0     - Spaces off
ATH1     - Headers on
ATSP6    - Set protocol (ISO 15765-4 CAN)
0100     - Request supported PIDs
03       - Request DTCs
04       - Clear DTCs
07       - Request pending DTCs
0A       - Request permanent DTCs
```

## Safety Checklist

Before using ECU flash or tuning:

- [ ] Backed up original ECU ROM
- [ ] Battery voltage is 13.5-14.5V
- [ ] Vehicle is in safe location
- [ ] Engine is OFF (for flashing)
- [ ] Verified .bin file is correct
- [ ] Understand the risks
- [ ] Have a professional tuner's guidance

## Troubleshooting

### App won't build
- Sync Gradle files
- Clean project: Build > Clean Project
- Invalidate caches: File > Invalidate Caches
- Check JDK version (needs 11+)

### Can't connect to adapter
- Verify Bluetooth pairing
- Check adapter is plugged into OBD2 port
- Turn ignition to ON
- Try different protocol in Settings

### No data on gauges
- Verify vehicle is on
- Check protocol selection
- Some gauges need engine running
- Try generic OBD2 adapter first

### ECU flash fails
- Check battery voltage
- Don't disconnect during flash
- Verify .bin file compatibility
- Some vehicles need special procedures

## Next Steps

1. **Customize UI**: Edit colors in `res/values/colors.xml`
2. **Add Gauges**: Modify `GaugeData.java` and `DashboardFragment.java`
3. **Add Vehicles**: Extend `VehicleProfile.java`
4. **Improve OBD**: Enhance `ObdConnectionService.java`
5. **Add Features**: Create new fragments

## Resources

- **OBD2 PIDs**: https://en.wikipedia.org/wiki/OBD-II_PIDs
- **ELM327 Commands**: https://www.elmelectronics.com/wp-content/uploads/2017/01/ELM327DS.pdf
- **Android Dev**: https://developer.android.com/
- **Material Design**: https://material.io/

## License & Disclaimer

**Educational use only**. Modifying vehicle ECUs can:
- Void warranties
- Damage engines
- Violate emissions laws
- Cause safety issues

Always consult professionals before making changes.

---

Created by Aaron Surina - 2025
