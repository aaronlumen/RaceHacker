# Icon Replacement Guide

## Overview

I've created **vector drawable placeholders** in red (#FF0000) for all navigation icons. These are temporary placeholders that you should replace with your actual racing-themed PNG images.

## Created Vector Icons

All icons are located in: `app/src/main/res/drawable/`

1. **ic_dashboard.xml** - Speedometer icon (placeholder for your racing speedometer image)
2. **ic_diagnostics.xml** - Gear/wrench icon (placeholder for your diagnostics image)
3. **ic_ecu_flash.xml** - Microchip icon (placeholder for your ECU chip image)
4. **ic_tuning.xml** - Slider controls icon (placeholder)
5. **ic_settings.xml** - Settings gear icon (placeholder)

## Bottom Navigation Menu Updated

The `bottom_navigation_menu.xml` has been updated to reference these new icons instead of Android system icons.

## How to Replace Vector Icons with Your PNG Images

### Method 1: Using Android Studio Image Asset Tool (RECOMMENDED)

This is the easiest method that automatically creates all required sizes:

1. **Open Android Studio** and load the ProRacingOBD project

2. **Right-click** on `app/src/main/res` in the Project view

3. Select **New → Image Asset**

4. **Configure the asset:**
   - **Icon Type**: Select "Action Bar and Tab Icons" (for navigation icons)
   - **Name**: Enter the icon name WITHOUT extension:
     - `ic_dashboard` (for your speedometer image)
     - `ic_diagnostics` (for your gear/diagnostics image)
     - `ic_ecu_flash` (for your ECU chip image)
     - `ic_tuning` (for tuning icon)
     - `ic_settings` (for settings icon)
   - **Asset Type**: Select "Image"
   - **Path**: Click folder icon and browse to your PNG file
   - **Trim**: Check if your image has transparent padding
   - **Padding**: Adjust if needed (0-20%)

5. **Preview**: Check all density sizes (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)

6. **Click "Next"** and then **"Finish"**

7. **Important**: When prompted, choose to **overwrite** the existing vector drawable files

8. **Repeat** for each icon

### Method 2: Manual Copying (More Control)

If you want more control over the icons:

#### For Navigation Icons (Bottom Nav)

Copy your PNG images to the appropriate density folders with these recommended sizes:

```
app/src/main/res/
├── drawable-mdpi/
│   ├── ic_dashboard.png      (48x48 dp = 48px)
│   ├── ic_diagnostics.png    (48x48 dp = 48px)
│   ├── ic_ecu_flash.png      (48x48 dp = 48px)
│   ├── ic_tuning.png         (48x48 dp = 48px)
│   └── ic_settings.png       (48x48 dp = 48px)
│
├── drawable-hdpi/
│   └── [all icons]           (48x48 dp = 72px)
│
├── drawable-xhdpi/
│   └── [all icons]           (48x48 dp = 96px)
│
├── drawable-xxhdpi/
│   └── [all icons]           (48x48 dp = 144px)
│
└── drawable-xxxhdpi/
    └── [all icons]           (48x48 dp = 192px)
```

#### For Launcher Icon

Copy your speedometer/launcher icon to mipmap folders:

```
app/src/main/res/
├── mipmap-mdpi/
│   ├── ic_launcher.png       (48x48 px)
│   └── ic_launcher_round.png (48x48 px)
│
├── mipmap-hdpi/
│   ├── ic_launcher.png       (72x72 px)
│   └── ic_launcher_round.png (72x72 px)
│
├── mipmap-xhdpi/
│   ├── ic_launcher.png       (96x96 px)
│   └── ic_launcher_round.png (96x96 px)
│
├── mipmap-xxhdpi/
│   ├── ic_launcher.png       (144x144 px)
│   └── ic_launcher_round.png (144x144 px)
│
└── mipmap-xxxhdpi/
    ├── ic_launcher.png       (192x192 px)
    └── ic_launcher_round.png (192x192 px)
```

### Method 3: Using PowerShell Script (Bulk Conversion)

If you have high-res PNG files and want to create all sizes automatically:

```powershell
# Save as: resize_icons.ps1

# Install ImageMagick first: winget install ImageMagick.ImageMagick

$icons = @(
    @{name="ic_dashboard"; source="path\to\your\speedometer.png"},
    @{name="ic_diagnostics"; source="path\to\your\diagnostics.png"},
    @{name="ic_ecu_flash"; source="path\to\your\ecu_chip.png"},
    @{name="ic_tuning"; source="path\to\your\tuning.png"},
    @{name="ic_settings"; source="path\to\your\settings.png"}
)

$densities = @{
    "mdpi" = 48
    "hdpi" = 72
    "xhdpi" = 96
    "xxhdpi" = 144
    "xxxhdpi" = 192
}

$baseDir = "C:\Users\Computer User\Desktop\obd2\SurinaSpeed\SurinaSpeed\ProRacingOBD\app\src\main\res"

foreach ($icon in $icons) {
    foreach ($density in $densities.GetEnumerator()) {
        $folder = "$baseDir\drawable-$($density.Key)"
        New-Item -ItemType Directory -Force -Path $folder | Out-Null

        $output = "$folder\$($icon.name).png"
        $size = $density.Value

        magick convert $icon.source -resize ${size}x${size} $output
        Write-Host "Created: $output ($size x $size)"
    }
}

Write-Host "`nDone! All navigation icons created."
```

**Run the script:**
```powershell
powershell -ExecutionPolicy Bypass -File resize_icons.ps1
```

## Updating AndroidManifest.xml for Launcher Icon

After replacing the launcher icon, update `AndroidManifest.xml`:

```xml
<application
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:label="@string/app_name"
    android:supportsRtl="true"
    android:theme="@style/AppTheme">
```

## Image Requirements

### For Navigation Icons:
- **Format**: PNG with transparency (32-bit RGBA)
- **Colors**: Racing red (#FF0000) or white (#FFFFFF) work best on dark theme
- **Style**: Simple, recognizable silhouettes
- **Background**: Transparent

### For Launcher Icon:
- **Format**: PNG with transparency
- **Shape**: Square or circular (Android will mask automatically)
- **Safe Area**: Keep important content within 80% of canvas (avoid edges)
- **Colors**: Bold racing colors (red, black, white, orange)

## Testing Icons

After replacing icons:

1. **Clean and rebuild** the project:
   ```bash
   gradle clean
   gradle assembleDebug
   ```

2. **Install on device/emulator**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Check launcher icon** on home screen

4. **Open app** and verify bottom navigation icons appear correctly

## Troubleshooting

### Icons Not Showing
- Ensure PNG files are named correctly (lowercase, no spaces)
- Verify files are in correct density folders
- Clean and rebuild project
- Uninstall and reinstall app

### Icons Look Blurry
- Provide higher resolution source images
- Use xxxhdpi size as baseline (192x192) and scale down
- Ensure PNG quality is high (no JPEG artifacts)

### Wrong Colors
- Icons should be monochrome (single color + transparency)
- Let Android's tinting system handle colors
- For bottom nav, icons will be tinted based on selection state

### Vector Icons Still Showing
- Delete the vector `.xml` files from `drawable/` folder
- Android prefers PNG over vector when both exist
- Clean project after deleting

## Recommended Workflow

1. **Start with launcher icon** (most visible)
2. **Test with one navigation icon** (ic_dashboard)
3. **If it looks good, replace the rest**
4. **Keep vector placeholders as backup** (rename to .xml.backup)

## Your Image Files

Based on your description:

1. **ic_launcher.png** → Your speedometer racing icon
   - Use for: Launcher icon (mipmap folders)
   - Also consider using for: ic_dashboard (navigation)

2. **diagnostics icon** → Your gear/wrench icon
   - Use for: ic_diagnostics.png (navigation)

3. **ECU chip icon** → Your microchip icon
   - Use for: ic_ecu_flash.png (navigation)

4. **carbon fiber texture** → Background (see ASSETS_INTEGRATION_GUIDE.md)

## Next Steps

1. Choose Method 1 (Android Studio Image Asset Tool) - easiest
2. Replace ic_dashboard with your speedometer icon
3. Replace ic_diagnostics with your gear icon
4. Replace ic_ecu_flash with your ECU chip icon
5. Rebuild and test
6. Adjust sizes/padding as needed

The vector placeholders I created will work as temporary icons, but your actual racing-themed PNG images will make the app look much more professional!
