# Asset Integration Guide - Pro Racing OBD

## Status Update

✅ **Vector icon placeholders created** - All navigation icons now have red vector placeholders
✅ **Bottom navigation updated** - Menu now references custom icons instead of Android system icons
📝 **See ICON_REPLACEMENT_GUIDE.md** for detailed instructions on replacing vectors with your PNG files

## Your Assets

You have the following racing-themed assets to integrate:

1. **carbon_fiber.png** - Carbon fiber texture background
2. **ic_launcher.png** - Racing speedometer app icon (for launcher and dashboard nav)
3. **diagnostics_icon.png** - Gear/wrench diagnostics icon (for diagnostics nav)
4. **ecu_chip_icon.png** - Microchip/ECU icon (for ECU flash nav)

## Directory Structure

```
app/src/main/res/
├── drawable/              ← Place racing icons here
│   ├── carbon_fiber_bg.png
│   ├── ic_diagnostics.png
│   ├── ic_ecu.png
│   ├── ic_dashboard.png
│   └── ic_settings.png
│
├── mipmap-hdpi/          ← App launcher icon (48x48)
│   ├── ic_launcher.png
│   └── ic_launcher_round.png
│
├── mipmap-mdpi/          ← App launcher icon (48x48)
├── mipmap-xhdpi/         ← App launcher icon (72x72)
├── mipmap-xxhdpi/        ← App launcher icon (96x96)
└── mipmap-xxxhdpi/       ← App launcher icon (144x144)
```

## Step-by-Step Integration

### 1. Prepare Your Images

#### A. App Launcher Icon (Speedometer)
Your racing speedometer icon needs multiple sizes:

```
mipmap-mdpi/ic_launcher.png       →  48x48 px
mipmap-hdpi/ic_launcher.png       →  72x72 px
mipmap-xhdpi/ic_launcher.png      →  96x96 px
mipmap-xxhdpi/ic_launcher.png     → 144x144 px
mipmap-xxxhdpi/ic_launcher.png    → 192x192 px
```

**Quick way**: Use Android Studio:
1. Right-click `res` folder
2. New > Image Asset
3. Choose "Launcher Icons (Adaptive and Legacy)"
4. Select your speedometer PNG
5. Click Next > Finish

#### B. Bottom Navigation Icons
Resize to **24x24dp** (approximately):
- `drawable-hdpi/`: 36x36px
- `drawable-xhdpi/`: 48x48px
- `drawable-xxhdpi/`: 72x72px
- `drawable-xxxhdpi/`: 96x96px

Or use single PNG at 96x96px in `drawable/` folder.

### 2. Copy Files to Project

```bash
# Navigate to your project
cd "C:\Users\Computer User\Desktop\obd2\SurinaSpeed\SurinaSpeed\ProRacingOBD"

# Create necessary directories
mkdir -p app/src/main/res/drawable-xxhdpi
mkdir -p app/src/main/res/drawable-xhdpi
mkdir -p app/src/main/res/drawable-hdpi

# Copy your images (adjust paths as needed)
# From parent obd2 directory or wherever you saved them:

# Carbon fiber background
cp /path/to/carbon_fiber.png app/src/main/res/drawable/carbon_fiber_bg.png

# Navigation icons
cp /path/to/diagnostics_icon.png app/src/main/res/drawable/ic_diagnostics.png
cp /path/to/ecu_chip_icon.png app/src/main/res/drawable/ic_ecu_flash.png

# App icon - use Image Asset Studio in Android Studio instead
```

### 3. Android Studio Method (Easiest)

#### Add App Icon:
1. In Android Studio, right-click `app/src/main/res`
2. New > Image Asset
3. Icon Type: Launcher Icons
4. Name: `ic_launcher`
5. Asset Type: Image
6. Path: Browse to your speedometer PNG
7. Background Layer: Optional (use carbon fiber or solid color)
8. Click Finish

#### Add Navigation Icons:
1. Right-click `app/src/main/res/drawable`
2. Paste your PNG files here
3. Or use: New > Image Asset > Action Bar and Tab Icons

### 4. File Naming Convention

✅ **Correct**:
- `ic_launcher.png`
- `ic_diagnostics.png`
- `ic_ecu_flash.png`
- `carbon_fiber_bg.png`

❌ **Incorrect** (Android won't accept):
- `diagnostics-icon.png` (no hyphens)
- `ECU_Icon.png` (no capitals)
- `icon diagnostics.png` (no spaces)

### 5. Update Your Files

The following files will be automatically updated once you add the images:

#### bottom_navigation_menu.xml
```xml
<item
    android:id="@+id/nav_diagnostics"
    android:icon="@drawable/ic_diagnostics"
    android:title="@string/nav_diagnostics" />

<item
    android:id="@+id/nav_ecu_flash"
    android:icon="@drawable/ic_ecu_flash"
    android:title="@string/nav_ecu_flash" />
```

#### AndroidManifest.xml
```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    ...>
```

## Quick Integration Script

If you have your images ready, create this PowerShell script:

```powershell
# save as copy_assets.ps1
$projectPath = "C:\Users\Computer User\Desktop\obd2\SurinaSpeed\SurinaSpeed\ProRacingOBD"
$assetsPath = "C:\Users\Computer User\Desktop\obd2"  # Adjust to where your images are

# Copy carbon fiber background
Copy-Item "$assetsPath\carbon_fiber_bg.png" "$projectPath\app\src\main\res\drawable\"

# Copy navigation icons
Copy-Item "$assetsPath\ic_diagnostics.png" "$projectPath\app\src\main\res\drawable\"
Copy-Item "$assetsPath\ic_ecu.png" "$projectPath\app\src\main\res\drawable\"

Write-Host "Assets copied! Now use Android Studio Image Asset for launcher icon."
```

Then run: `.\copy_assets.ps1`

## Using Carbon Fiber Background

### Option 1: Set as Activity Background
Update `styles.xml`:

```xml
<style name="AppTheme" parent="Theme.MaterialComponents.DayNight.NoActionBar">
    <item name="android:windowBackground">@drawable/carbon_fiber_bg</item>
    <item name="colorPrimary">@color/racing_black</item>
    <item name="colorAccent">@color/racing_red</item>
</style>
```

### Option 2: Use in Layouts
In any layout XML:

```xml
<LinearLayout
    android:background="@drawable/carbon_fiber_bg"
    ...>
```

### Option 3: Tiled Background
Create `drawable/carbon_fiber_tiled.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/carbon_fiber_bg"
    android:tileMode="repeat" />
```

Then use: `android:background="@drawable/carbon_fiber_tiled"`

## Current Asset Locations

Based on your desktop folder structure:

```
C:\Users\Computer User\Desktop\obd2\
├── carbon_fiber_bg.png         ← Copy this
├── checkered_flag_bg.webp      ← Optional racing flag
├── dashboard.webp              ← Optional gauge background
├── ic_dashboard.webp           ← Dashboard icon
├── ic_race.webp                ← Racing icon
├── ic_sensors.webp             ← Sensors icon
└── obdRacingApp.webp           ← Another racing graphic
```

## Icon Recommendations

For **best quality**, use these sizes for your navigation icons:

| Icon | Recommended Size | Purpose |
|------|-----------------|---------|
| Dashboard | 96x96px | Main gauge view |
| Diagnostics | 96x96px | DTC codes & MIL |
| ECU Flash | 96x96px | ROM backup/flash |
| Tuning | 96x96px | Performance tuning |
| Settings | 96x96px | Configuration |

## Testing

After adding assets:

1. **Clean & Rebuild**:
   ```bash
   ./gradlew clean assembleDebug
   ```

2. **Check Build Output**:
   - Look for errors like "resource not found"
   - Verify icons appear in APK

3. **Visual Test**:
   - Install APK
   - Check launcher icon on home screen
   - Navigate through all tabs
   - Verify icons are crisp and clear

## Common Issues

### Issue: "Resource not found"
**Solution**: Check file naming (lowercase, underscores only)

### Issue: Icons look blurry
**Solution**: Provide higher resolution PNGs (96x96 or larger)

### Issue: Carbon fiber slows down app
**Solution**: Optimize PNG size using TinyPNG or similar

### Issue: Launcher icon has white background
**Solution**: Use transparent PNG or add adaptive icon background layer

## Color Extraction

Your carbon fiber and speedometer likely use these colors:

```xml
<!-- Add to colors.xml -->
<color name="carbon_fiber_black">#0A0A0A</color>
<color name="carbon_fiber_gray">#1A1A1A</color>
<color name="speedometer_red">#FF0000</color>
<color name="speedometer_white">#FFFFFF</color>
<color name="gauge_green">#00FF00</color>
```

## Next Steps

1. ✅ Copy images to `res/drawable/`
2. ✅ Use Image Asset Studio for launcher icon
3. ✅ Update `bottom_navigation_menu.xml` with new icons
4. ✅ Set carbon fiber as background
5. ✅ Rebuild and test
6. ✅ Enjoy your racing-themed app!

---

**Need Help?**
- Image Asset Studio: Tools > Resource Manager > + > Image Asset
- Icon resizing: Use Android Studio's built-in tool
- PNG optimization: https://tinypng.com/
