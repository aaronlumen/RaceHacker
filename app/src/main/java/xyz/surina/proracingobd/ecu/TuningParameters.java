package xyz.surina.proracingobd.ecu;

public class TuningParameters {

    // Air-Fuel Ratio Settings
    private float targetAfr;
    private float idleAfr;
    private float wotAfr;
    private float cruiseAfr;

    // Ignition Timing (degrees)
    private float baseTimingAdvance;
    private float maxTimingAdvance;
    private float timingRetardUnderBoost;

    // Boost Control (PSI)
    private float targetBoost;
    private float maxBoost;
    private float wastegateSpringPressure;

    // Fuel Settings
    private int fuelPumpDutyCycle;
    private float injectorScaling;
    private int fuelPressure;

    // Rev Limiter
    private int softRevLimit;
    private int hardRevLimit;

    // Launch Control
    private boolean launchControlEnabled;
    private int launchRpm;
    private float launchTimingRetard;

    // Anti-Lag
    private boolean antiLagEnabled;
    private float antiLagTimingRetard;
    private float antiLagFuelEnrichment;

    // Speed Limiter
    private boolean speedLimiterEnabled;
    private int maxSpeed;

    public TuningParameters() {
        setDefaultValues();
    }

    private void setDefaultValues() {
        // AFR defaults (Stoichiometric is 14.7:1 for gasoline)
        this.targetAfr = 14.7f;
        this.idleAfr = 14.7f;
        this.wotAfr = 12.5f; // Rich for power
        this.cruiseAfr = 15.0f; // Lean for economy

        // Timing defaults
        this.baseTimingAdvance = 15.0f;
        this.maxTimingAdvance = 30.0f;
        this.timingRetardUnderBoost = 2.0f;

        // Boost defaults
        this.targetBoost = 15.0f;
        this.maxBoost = 20.0f;
        this.wastegateSpringPressure = 7.0f;

        // Fuel defaults
        this.fuelPumpDutyCycle = 85;
        this.injectorScaling = 1.0f;
        this.fuelPressure = 43;

        // Rev limiter defaults
        this.softRevLimit = 6800;
        this.hardRevLimit = 7200;

        // Launch control defaults
        this.launchControlEnabled = false;
        this.launchRpm = 4000;
        this.launchTimingRetard = 10.0f;

        // Anti-lag defaults
        this.antiLagEnabled = false;
        this.antiLagTimingRetard = 15.0f;
        this.antiLagFuelEnrichment = 1.5f;

        // Speed limiter defaults
        this.speedLimiterEnabled = false;
        this.maxSpeed = 155;
    }

    public void applyConservativeTune() {
        this.wotAfr = 12.8f;
        this.maxTimingAdvance = 25.0f;
        this.targetBoost = 12.0f;
        this.hardRevLimit = 7000;
    }

    public void applyAggressiveTune() {
        this.wotAfr = 11.8f;
        this.maxTimingAdvance = 32.0f;
        this.targetBoost = 22.0f;
        this.hardRevLimit = 7500;
        this.antiLagEnabled = true;
    }

    public void applyStreetTune() {
        this.wotAfr = 12.5f;
        this.maxTimingAdvance = 28.0f;
        this.targetBoost = 15.0f;
        this.hardRevLimit = 7200;
    }

    public void applyRaceTune() {
        this.wotAfr = 11.5f;
        this.maxTimingAdvance = 35.0f;
        this.targetBoost = 25.0f;
        this.hardRevLimit = 8000;
        this.launchControlEnabled = true;
        this.antiLagEnabled = true;
    }

    // Getters and Setters
    public float getTargetAfr() { return targetAfr; }
    public void setTargetAfr(float targetAfr) { this.targetAfr = targetAfr; }

    public float getIdleAfr() { return idleAfr; }
    public void setIdleAfr(float idleAfr) { this.idleAfr = idleAfr; }

    public float getWotAfr() { return wotAfr; }
    public void setWotAfr(float wotAfr) { this.wotAfr = wotAfr; }

    public float getCruiseAfr() { return cruiseAfr; }
    public void setCruiseAfr(float cruiseAfr) { this.cruiseAfr = cruiseAfr; }

    public float getBaseTimingAdvance() { return baseTimingAdvance; }
    public void setBaseTimingAdvance(float baseTimingAdvance) { this.baseTimingAdvance = baseTimingAdvance; }

    public float getMaxTimingAdvance() { return maxTimingAdvance; }
    public void setMaxTimingAdvance(float maxTimingAdvance) { this.maxTimingAdvance = maxTimingAdvance; }

    public float getTimingRetardUnderBoost() { return timingRetardUnderBoost; }
    public void setTimingRetardUnderBoost(float timingRetardUnderBoost) {
        this.timingRetardUnderBoost = timingRetardUnderBoost;
    }

    public float getTargetBoost() { return targetBoost; }
    public void setTargetBoost(float targetBoost) { this.targetBoost = targetBoost; }

    public float getMaxBoost() { return maxBoost; }
    public void setMaxBoost(float maxBoost) { this.maxBoost = maxBoost; }

    public float getWastegateSpringPressure() { return wastegateSpringPressure; }
    public void setWastegateSpringPressure(float wastegateSpringPressure) {
        this.wastegateSpringPressure = wastegateSpringPressure;
    }

    public int getFuelPumpDutyCycle() { return fuelPumpDutyCycle; }
    public void setFuelPumpDutyCycle(int fuelPumpDutyCycle) { this.fuelPumpDutyCycle = fuelPumpDutyCycle; }

    public float getInjectorScaling() { return injectorScaling; }
    public void setInjectorScaling(float injectorScaling) { this.injectorScaling = injectorScaling; }

    public int getFuelPressure() { return fuelPressure; }
    public void setFuelPressure(int fuelPressure) { this.fuelPressure = fuelPressure; }

    public int getSoftRevLimit() { return softRevLimit; }
    public void setSoftRevLimit(int softRevLimit) { this.softRevLimit = softRevLimit; }

    public int getHardRevLimit() { return hardRevLimit; }
    public void setHardRevLimit(int hardRevLimit) { this.hardRevLimit = hardRevLimit; }

    public boolean isLaunchControlEnabled() { return launchControlEnabled; }
    public void setLaunchControlEnabled(boolean launchControlEnabled) {
        this.launchControlEnabled = launchControlEnabled;
    }

    public int getLaunchRpm() { return launchRpm; }
    public void setLaunchRpm(int launchRpm) { this.launchRpm = launchRpm; }

    public float getLaunchTimingRetard() { return launchTimingRetard; }
    public void setLaunchTimingRetard(float launchTimingRetard) {
        this.launchTimingRetard = launchTimingRetard;
    }

    public boolean isAntiLagEnabled() { return antiLagEnabled; }
    public void setAntiLagEnabled(boolean antiLagEnabled) { this.antiLagEnabled = antiLagEnabled; }

    public float getAntiLagTimingRetard() { return antiLagTimingRetard; }
    public void setAntiLagTimingRetard(float antiLagTimingRetard) {
        this.antiLagTimingRetard = antiLagTimingRetard;
    }

    public float getAntiLagFuelEnrichment() { return antiLagFuelEnrichment; }
    public void setAntiLagFuelEnrichment(float antiLagFuelEnrichment) {
        this.antiLagFuelEnrichment = antiLagFuelEnrichment;
    }

    public boolean isSpeedLimiterEnabled() { return speedLimiterEnabled; }
    public void setSpeedLimiterEnabled(boolean speedLimiterEnabled) {
        this.speedLimiterEnabled = speedLimiterEnabled;
    }

    public int getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(int maxSpeed) { this.maxSpeed = maxSpeed; }
}
