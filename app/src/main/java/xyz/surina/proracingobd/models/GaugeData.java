package xyz.surina.proracingobd.models;

public class GaugeData {
    private String name;
    private String unit;
    private float currentValue;
    private float minValue;
    private float maxValue;
    private float warningThreshold;
    private float criticalThreshold;
    private GaugeType type;

    public enum GaugeType {
        RPM,
        SPEED,
        BOOST,
        AFR,
        OIL_TEMP,
        COOLANT_TEMP,
        OIL_PRESSURE,
        FUEL_PRESSURE,
        INTAKE_TEMP,
        EXHAUST_TEMP,
        TIMING,
        THROTTLE_POSITION,
        BATTERY_VOLTAGE,
        FUEL_LEVEL
    }

    public GaugeData(String name, String unit, GaugeType type) {
        this.name = name;
        this.unit = unit;
        this.type = type;
        this.currentValue = 0;
        setDefaultRanges(type);
    }

    private void setDefaultRanges(GaugeType type) {
        switch(type) {
            case RPM:
                this.minValue = 0;
                this.maxValue = 8000;
                this.warningThreshold = 6500;
                this.criticalThreshold = 7500;
                break;
            case SPEED:
                this.minValue = 0;
                this.maxValue = 200;
                this.warningThreshold = 150;
                this.criticalThreshold = 180;
                break;
            case BOOST:
                this.minValue = -15;
                this.maxValue = 30;
                this.warningThreshold = 20;
                this.criticalThreshold = 25;
                break;
            case AFR:
                this.minValue = 10;
                this.maxValue = 18;
                this.warningThreshold = 11.5f;
                this.criticalThreshold = 11.0f;
                break;
            case OIL_TEMP:
                this.minValue = 0;
                this.maxValue = 300;
                this.warningThreshold = 240;
                this.criticalThreshold = 280;
                break;
            case COOLANT_TEMP:
                this.minValue = 0;
                this.maxValue = 250;
                this.warningThreshold = 210;
                this.criticalThreshold = 230;
                break;
            case OIL_PRESSURE:
                this.minValue = 0;
                this.maxValue = 100;
                this.warningThreshold = 20;
                this.criticalThreshold = 10;
                break;
            case FUEL_PRESSURE:
                this.minValue = 0;
                this.maxValue = 100;
                this.warningThreshold = 35;
                this.criticalThreshold = 30;
                break;
            case INTAKE_TEMP:
                this.minValue = -20;
                this.maxValue = 200;
                this.warningThreshold = 140;
                this.criticalThreshold = 170;
                break;
            case EXHAUST_TEMP:
                this.minValue = 0;
                this.maxValue = 1800;
                this.warningThreshold = 1500;
                this.criticalThreshold = 1650;
                break;
            case TIMING:
                this.minValue = -10;
                this.maxValue = 40;
                this.warningThreshold = 35;
                this.criticalThreshold = 38;
                break;
            case THROTTLE_POSITION:
                this.minValue = 0;
                this.maxValue = 100;
                this.warningThreshold = 90;
                this.criticalThreshold = 100;
                break;
            case BATTERY_VOLTAGE:
                this.minValue = 10;
                this.maxValue = 16;
                this.warningThreshold = 11.5f;
                this.criticalThreshold = 11.0f;
                break;
            case FUEL_LEVEL:
                this.minValue = 0;
                this.maxValue = 100;
                this.warningThreshold = 20;
                this.criticalThreshold = 10;
                break;
        }
    }

    public boolean isWarning() {
        if (type == GaugeType.OIL_PRESSURE || type == GaugeType.FUEL_PRESSURE ||
            type == GaugeType.BATTERY_VOLTAGE || type == GaugeType.FUEL_LEVEL) {
            return currentValue <= warningThreshold;
        }
        return currentValue >= warningThreshold;
    }

    public boolean isCritical() {
        if (type == GaugeType.OIL_PRESSURE || type == GaugeType.FUEL_PRESSURE ||
            type == GaugeType.BATTERY_VOLTAGE || type == GaugeType.FUEL_LEVEL) {
            return currentValue <= criticalThreshold;
        }
        return currentValue >= criticalThreshold;
    }

    public float getPercentage() {
        float range = maxValue - minValue;
        float value = currentValue - minValue;
        return Math.max(0, Math.min(100, (value / range) * 100));
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public float getCurrentValue() { return currentValue; }
    public void setCurrentValue(float currentValue) { this.currentValue = currentValue; }

    public float getMinValue() { return minValue; }
    public void setMinValue(float minValue) { this.minValue = minValue; }

    public float getMaxValue() { return maxValue; }
    public void setMaxValue(float maxValue) { this.maxValue = maxValue; }

    public float getWarningThreshold() { return warningThreshold; }
    public void setWarningThreshold(float warningThreshold) { this.warningThreshold = warningThreshold; }

    public float getCriticalThreshold() { return criticalThreshold; }
    public void setCriticalThreshold(float criticalThreshold) { this.criticalThreshold = criticalThreshold; }

    public GaugeType getType() { return type; }
    public void setType(GaugeType type) { this.type = type; }

    public boolean hasData() {
        return !Float.isNaN(currentValue);
    }

    public String getFormattedValue() {
        if (Float.isNaN(currentValue)) return "--";
        if (type == GaugeType.AFR || type == GaugeType.BATTERY_VOLTAGE) {
            return String.format("%.1f", currentValue);
        } else if (type == GaugeType.RPM) {
            return String.format("%.0f", currentValue);
        } else {
            return String.format("%.1f", currentValue);
        }
    }
}
