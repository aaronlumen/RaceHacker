package xyz.surina.proracingobd.vehicles;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * VehicleProfile — defines OBD2 connection parameters and ECU capabilities
 * for a given vehicle platform.
 *
 * Auto-detection reads the VIN via OBD2 Mode 09 PID 02 and matches the
 * WMI (World Manufacturer Identifier, first 3 chars) to a known profile.
 * If the make/model is not built-in the app can optionally fetch a plugin
 * pack from a remote server via fetchPluginProfile().
 */
public class VehicleProfile {

    private static final String TAG = "VehicleProfile";

    private String name;
    private VehicleType type;
    private CableType cableType;
    private ProtocolType protocol;
    private Map<String, Integer> ecuAddresses;
    private boolean supportsEcuFlashing;
    private boolean supportsAdvancedTuning;

    // ─── Vehicle type enum ────────────────────────────────────────────────

    public enum VehicleType {
        // ── GM ──────────────────────────────────────────────────────────
        GMC_ACADIA        ("GMC Acadia",           true,  true),
        GMC_SIERRA        ("GMC Sierra",            true,  true),
        CHEVY_SILVERADO   ("Chevy Silverado",       true,  true),
        CHEVY_DURAMAX     ("Chevy Duramax Diesel",  true,  true),
        CHEVY_CAMARO      ("Chevy Camaro",          true,  true),
        CHEVY_CORVETTE    ("Chevy Corvette",        true,  true),

        // ── Ford ────────────────────────────────────────────────────────
        FORD_F150         ("Ford F-150",            true,  true),
        FORD_POWERSTROKE  ("Ford PowerStroke Diesel", true, true),
        FORD_MUSTANG      ("Ford Mustang",          true,  true),
        FORD_EXPLORER     ("Ford Explorer",         true,  false),
        FORD_RAPTOR       ("Ford Raptor",           true,  true),

        // ── Dodge / Ram / Chrysler ───────────────────────────────────────
        DODGE_HEMI        ("Dodge HEMI",            true,  true),
        DODGE_RAM_1500    ("Dodge Ram 1500",        true,  true),
        DODGE_CUMMINS     ("Dodge Cummins Diesel",  true,  true),
        DODGE_CHALLENGER  ("Dodge Challenger",      true,  true),
        DODGE_CHARGER     ("Dodge Charger",         true,  true),
        JEEP_CHEROKEE     ("Jeep Cherokee",         true,  false),
        JEEP_GRAND_CHEROKEE("Jeep Grand Cherokee",  true,  true),
        JEEP_WRANGLER     ("Jeep Wrangler",         true,  true),

        // ── Toyota / Lexus ──────────────────────────────────────────────
        TOYOTA_TACOMA     ("Toyota Tacoma",         true,  true),
        TOYOTA_TUNDRA     ("Toyota Tundra",         true,  true),
        TOYOTA_4RUNNER    ("Toyota 4Runner",        true,  false),
        TOYOTA_CAMRY      ("Toyota Camry",          false, false),
        TOYOTA_SUPRA      ("Toyota Supra (A90)",    true,  true),
        LEXUS_IS          ("Lexus IS",              true,  true),
        LEXUS_GS          ("Lexus GS",              true,  true),
        LEXUS_RX          ("Lexus RX",              false, false),
        LEXUS_RC_F        ("Lexus RC-F",            true,  true),

        // ── Nissan / Infiniti ────────────────────────────────────────────
        NISSAN_GTR        ("Nissan GT-R",           true,  true),
        NISSAN_370Z       ("Nissan 370Z",           true,  true),
        NISSAN_TITAN      ("Nissan Titan",          true,  false),
        NISSAN_FRONTIER   ("Nissan Frontier",       true,  false),
        INFINITI_Q50      ("Infiniti Q50",          true,  true),
        INFINITI_Q60      ("Infiniti Q60",          true,  true),

        // ── Honda / Acura ────────────────────────────────────────────────
        HONDA_CIVIC_SI    ("Honda Civic Si/Type R", true,  true),
        HONDA_ACCORD      ("Honda Accord",          false, false),
        HONDA_RIDGELINE   ("Honda Ridgeline",       false, false),
        ACURA_TLX         ("Acura TLX",             true,  false),
        ACURA_NSX         ("Acura NSX",             true,  true),
        ACURA_INTEGRA     ("Acura Integra",         true,  true),

        // ── Mitsubishi ───────────────────────────────────────────────────
        MITSUBISHI_EVO    ("Mitsubishi Evo (X)",    true,  true),
        MITSUBISHI_ECLIPSE("Mitsubishi Eclipse",    true,  true),
        MITSUBISHI_OUTLANDER("Mitsubishi Outlander", false, false),

        // ── BMW ──────────────────────────────────────────────────────────
        BMW_N54           ("BMW N54 Turbo",         true,  true),
        BMW_N55           ("BMW N55",               true,  true),
        BMW_S58           ("BMW S58 (M3/M4)",       true,  true),
        BMW_B58           ("BMW B58",               true,  true),

        // ── VW / Audi ────────────────────────────────────────────────────
        VW_VAG            ("VW/Audi VAG",           true,  true),

        // ── Subaru ──────────────────────────────────────────────────────
        SUBARU_WRX        ("Subaru WRX/STI",        true,  true),

        // ── Harley-Davidson ─────────────────────────────────────────────
        HARLEY_M8_TOURING ("Harley M8 Touring (Road King/Street Glide)", true, true),
        HARLEY_M8_SOFTAIL ("Harley M8 Softail (Fat Boy/Heritage)",       true, true),
        HARLEY_SPORTSTER  ("Harley Sportster 883/1200",                  true, true),
        HARLEY_SPORTSTER_S("Harley Sportster S (Revolution Max 1250)",   true, true),
        HARLEY_PAN_AMERICA("Harley Pan America 1250",                    true, true),
        HARLEY_NIGHTSTER  ("Harley Nightster 975",                       true, true),
        HARLEY_STREET_BOB ("Harley Street Bob / Low Rider",              true, true),

        // ── Memphis Performance M8 ECU / Aftermarket ───────────────────
        MEMPHIS_M8_PERF   ("Memphis Performance M8 (Stage 1-4)",         true, true),

        // ── Indian Motorcycle ───────────────────────────────────────────
        INDIAN_CHIEF      ("Indian Chief / Chieftain / Roadmaster",      true, true),
        INDIAN_SCOUT      ("Indian Scout / Scout Bobber",                true, true),
        INDIAN_CHALLENGER ("Indian Challenger",                          true, true),

        // ── Ducati ──────────────────────────────────────────────────────
        DUCATI_PANIGALE   ("Ducati Panigale V4 / V2",                   true, true),
        DUCATI_MONSTER    ("Ducati Monster 821 / 937 / 1200",           true, true),
        DUCATI_MULTISTRADA("Ducati Multistrada",                        true, false),
        DUCATI_DIAVEL     ("Ducati Diavel",                             true, true),

        // ── Yamaha ──────────────────────────────────────────────────────
        YAMAHA_R1         ("Yamaha YZF-R1 / R1M",                      true, true),
        YAMAHA_R6         ("Yamaha YZF-R6",                            true, true),
        YAMAHA_MT09       ("Yamaha MT-09 / MT-10",                     true, true),
        YAMAHA_NIKEN      ("Yamaha Niken / Tracer",                    false, false),

        // ── Kawasaki ─────────────────────────────────────────────────────
        KAWASAKI_ZX10R    ("Kawasaki ZX-10R / ZX-10RR",               true, true),
        KAWASAKI_ZX6R     ("Kawasaki ZX-6R / ZX-636",                 true, true),
        KAWASAKI_Z900     ("Kawasaki Z900 / Z900RS",                   true, false),
        KAWASAKI_H2       ("Kawasaki Ninja H2 / H2R (Supercharged)",   true, true),

        // ── Suzuki ───────────────────────────────────────────────────────
        SUZUKI_GSXR1000   ("Suzuki GSX-R1000 / R",                    true, true),
        SUZUKI_GSXR600    ("Suzuki GSX-R600 / 750",                    true, true),
        SUZUKI_HAYABUSA   ("Suzuki Hayabusa (Gen 3)",                  true, true),

        // ── Honda (Moto) ─────────────────────────────────────────────────
        HONDA_CBR1000RR   ("Honda CBR1000RR FireBlade / SP",           true, true),
        HONDA_CBR600RR    ("Honda CBR600RR",                           true, true),
        HONDA_AFRICA_TWIN ("Honda Africa Twin CRF1100",                true, false),
        HONDA_GOLDWING    ("Honda Gold Wing GL1800",                   false, false),

        // ── BMW Moto ─────────────────────────────────────────────────────
        BMW_S1000RR       ("BMW S1000RR / M 1000 RR",                  true, true),
        BMW_R1250GS       ("BMW R1250GS / Adventure",                  true, false),
        BMW_F900          ("BMW F900R / F900XR",                       true, false),

        // ── KTM ──────────────────────────────────────────────────────────
        KTM_DUKE          ("KTM 390 / 790 / 890 Duke",                true, true),
        KTM_SUPERDUKE     ("KTM 1290 Super Duke R / GT",              true, true),
        KTM_RC            ("KTM RC 390 / RC 8C",                      true, true),

        // ── Triumph ──────────────────────────────────────────────────────
        TRIUMPH_BONNEVILLE("Triumph Bonneville / T120 / Thruxton",    true, false),
        TRIUMPH_SPEED_TRI ("Triumph Speed Triple 1200 RS",            true, true),
        TRIUMPH_STREET_TR ("Triumph Street Triple R / RS",            true, true),
        TRIUMPH_TIGER     ("Triumph Tiger 900 / 1200",                true, false),

        // ── Misc ────────────────────────────────────────────────────────
        MILWAUKEE_117     ("Milwaukee 117 Cable",   true,  false),
        BULLYDOG_GENERIC  ("BullyDog Generic",      false, false),
        DIESEL_GENERIC    ("Generic Diesel",        true,  false),
        GENERIC_OBD2      ("Generic OBD2 Vehicle",  false, false),
        MOTO_GENERIC      ("Generic Motorcycle OBD2", false, false),
        PLUGIN_PACK       ("Plugin Pack (Custom)",  false, false);

        private final String displayName;
        private final boolean ecuFlashSupport;
        private final boolean advancedTuningSupport;

        VehicleType(String d, boolean f, boolean t) {
            displayName = d; ecuFlashSupport = f; advancedTuningSupport = t;
        }

        public String getDisplayName()       { return displayName; }
        public boolean supportsEcuFlashing() { return ecuFlashSupport; }
        public boolean supportsAdvancedTuning() { return advancedTuningSupport; }
    }

    // ─── Cable type enum ──────────────────────────────────────────────────

    public enum CableType {
        ELM327_BLUETOOTH  ("ELM327 Bluetooth"),
        ELM327_WIFI       ("ELM327 WiFi"),
        OBD_LINK_MX       ("OBDLink MX+"),
        MILWAUKEE_117     ("Milwaukee 117 Cable"),
        ROSS_TECH_VCDS    ("Ross-Tech VCDS"),
        TACTRIX_OPENPORT  ("Tactrix OpenPort 2.0"),
        BULLYDOG_GT       ("BullyDog GT"),
        BULLYDOG_PMT      ("BullyDog PMT"),
        J2534_PASSTHRU    ("J2534 PassThru"),
        KDCAN_CABLE       ("K+DCAN Cable"),
        VAGCOM_USB        ("VAG-COM USB"),
        MONGOOSE_PRO      ("Drew-Tech Mongoose Pro"),
        FLASH_PRO         ("Hondata FlashPro"),
        ECU_FLASH_USB     ("Generic ECU Flash USB"),
        // ── Motorcycle / Harley-Davidson ────────────────────────────────
        OBD_MOTO_ADAPTER  ("OBD2 Motorcycle Adapter (6-pin/4-pin)"),
        HARLEY_DIGI_TECH  ("Harley-Davidson Digital Technician II"),
        DYNOJET_POWERVISION("Dynojet Power Vision"),
        VANCE_HINES_FP3   ("Vance & Hines FuelPak FP3"),
        INDIAN_RIDE_COMMAND("Indian Ride Command Interface"),
        BMW_MOTO_GS911    ("GS-911 BMW Moto Diagnostics");

        private final String displayName;
        CableType(String d) { displayName = d; }
        public String getDisplayName() { return displayName; }
    }

    // ─── Protocol type enum ───────────────────────────────────────────────

    public enum ProtocolType {
        ISO_9141_2        ("ISO 9141-2"),
        ISO_14230_4_KWP   ("ISO 14230-4 KWP2000"),
        ISO_15765_4_CAN   ("ISO 15765-4 CAN"),
        SAE_J1939         ("SAE J1939 (Heavy Duty)"),
        SAE_J1850_PWM     ("SAE J1850 PWM"),
        SAE_J1850_VPW     ("SAE J1850 VPW"),
        KWP2000_FAST      ("KWP2000 Fast Init"),
        BMW_DS2           ("BMW DS2"),
        VAG_KWP           ("VAG KWP2000"),
        AUTO              ("Auto Detect");

        private final String displayName;
        ProtocolType(String d) { displayName = d; }
        public String getDisplayName() { return displayName; }
    }

    // ─── Constructor ──────────────────────────────────────────────────────

    public VehicleProfile(String name, VehicleType type, CableType cableType, ProtocolType protocol) {
        this.name = name;
        this.type = type;
        this.cableType = cableType;
        this.protocol = protocol;
        this.ecuAddresses = new HashMap<>();
        this.supportsEcuFlashing = type.supportsEcuFlashing();
        this.supportsAdvancedTuning = type.supportsAdvancedTuning();
        initializeEcuAddresses();
    }

    private void initializeEcuAddresses() {
        switch (type) {
            case BMW_N54: case BMW_N55: case BMW_S58: case BMW_B58:
                ecuAddresses.put("DME",   0x12);
                ecuAddresses.put("EGS",   0x13);
                ecuAddresses.put("KOMBI", 0x18);
                break;
            case VW_VAG:
                ecuAddresses.put("ENGINE",       0x01);
                ecuAddresses.put("TRANSMISSION", 0x02);
                ecuAddresses.put("ABS",          0x03);
                break;
            case DODGE_HEMI: case DODGE_CUMMINS:
            case DODGE_RAM_1500: case DODGE_CHALLENGER: case DODGE_CHARGER:
            case JEEP_CHEROKEE: case JEEP_GRAND_CHEROKEE: case JEEP_WRANGLER:
                ecuAddresses.put("PCM", 0x10);
                ecuAddresses.put("TCM", 0x11);
                break;
            case FORD_F150: case FORD_POWERSTROKE:
            case FORD_MUSTANG: case FORD_EXPLORER: case FORD_RAPTOR:
                ecuAddresses.put("PCM", 0x7E0);
                ecuAddresses.put("TCM", 0x7E1);
                break;
            case GMC_ACADIA: case GMC_SIERRA:
            case CHEVY_SILVERADO: case CHEVY_DURAMAX:
            case CHEVY_CAMARO: case CHEVY_CORVETTE:
                ecuAddresses.put("ECM", 0x7E0);
                ecuAddresses.put("TCM", 0x7E1);
                ecuAddresses.put("BCM", 0x7E4);
                break;
            case TOYOTA_TACOMA: case TOYOTA_TUNDRA: case TOYOTA_4RUNNER:
            case TOYOTA_CAMRY: case TOYOTA_SUPRA:
            case LEXUS_IS: case LEXUS_GS: case LEXUS_RX: case LEXUS_RC_F:
                ecuAddresses.put("ECM", 0x7E0);
                ecuAddresses.put("TCM", 0x7E1);
                break;
            case NISSAN_GTR: case NISSAN_370Z:
            case NISSAN_TITAN: case NISSAN_FRONTIER:
            case INFINITI_Q50: case INFINITI_Q60:
                ecuAddresses.put("ECM", 0x7E0);
                break;
            case HONDA_CIVIC_SI: case HONDA_ACCORD: case HONDA_RIDGELINE:
            case ACURA_TLX: case ACURA_NSX: case ACURA_INTEGRA:
                ecuAddresses.put("ECM", 0x7E0);
                ecuAddresses.put("TCM", 0x7E1);
                break;
            case MITSUBISHI_EVO: case MITSUBISHI_ECLIPSE: case MITSUBISHI_OUTLANDER:
                ecuAddresses.put("ECM", 0x7E0);
                break;
            case SUBARU_WRX:
                ecuAddresses.put("ECM", 0x7E0);
                break;
            // ── Harley-Davidson (Twin Cam / Milwaukee-Eight) ─────────────
            // HD uses a proprietary J1850 VPW variant on older bikes;
            // Milwaukee-Eight (2017+) supports OBD2 CAN via SAE J1939 subset.
            case HARLEY_M8_TOURING: case HARLEY_M8_SOFTAIL:
            case HARLEY_NIGHTSTER:  case HARLEY_STREET_BOB:
            case MEMPHIS_M8_PERF:
                ecuAddresses.put("ECM",  0x7E0);  // primary engine ECU
                ecuAddresses.put("TSM",  0x7E3);  // turn signal / security module
                ecuAddresses.put("ICM",  0x7E4);  // instrument cluster
                break;
            case HARLEY_SPORTSTER: case HARLEY_SPORTSTER_S:
            case HARLEY_PAN_AMERICA:
                ecuAddresses.put("ECM",  0x7E0);
                ecuAddresses.put("BRC",  0x7E5);  // brake control
                break;
            // ── Indian Motorcycle (Polaris / Athena ECU) ─────────────────
            case INDIAN_CHIEF: case INDIAN_SCOUT: case INDIAN_CHALLENGER:
                ecuAddresses.put("ECM",  0x7E0);
                ecuAddresses.put("TCM",  0x7E1);
                break;
            // ── Ducati (Magneti Marelli / Bosch ME17) ────────────────────
            case DUCATI_PANIGALE: case DUCATI_MONSTER:
            case DUCATI_MULTISTRADA: case DUCATI_DIAVEL:
                ecuAddresses.put("ECM",  0x7E0);
                ecuAddresses.put("DDS",  0x7E2);  // Ducati Data System / IMU
                break;
            // ── Yamaha (YCC-T / FI ECU) ──────────────────────────────────
            case YAMAHA_R1: case YAMAHA_R6: case YAMAHA_MT09: case YAMAHA_NIKEN:
                ecuAddresses.put("ECU",  0x7E0);
                ecuAddresses.put("ABS",  0x7E3);
                break;
            // ── Kawasaki (Keihin ECU) ─────────────────────────────────────
            case KAWASAKI_ZX10R: case KAWASAKI_ZX6R:
            case KAWASAKI_Z900:  case KAWASAKI_H2:
                ecuAddresses.put("FI",   0x7E0);  // fuel injection ECU
                ecuAddresses.put("KQS",  0x7E2);  // Kawasaki quick shifter module
                break;
            // ── Suzuki (Denso ECU) ───────────────────────────────────────
            case SUZUKI_GSXR1000: case SUZUKI_GSXR600: case SUZUKI_HAYABUSA:
                ecuAddresses.put("ECM",  0x7E0);
                break;
            // ── Honda Moto (PGM-FI) ──────────────────────────────────────
            case HONDA_CBR1000RR: case HONDA_CBR600RR:
            case HONDA_AFRICA_TWIN: case HONDA_GOLDWING:
                ecuAddresses.put("PGM-FI", 0x7E0);
                ecuAddresses.put("ABS",    0x7E3);
                break;
            // ── BMW Moto (BMS-K / BMS-X) ─────────────────────────────────
            case BMW_S1000RR: case BMW_R1250GS: case BMW_F900:
                ecuAddresses.put("BMS",  0x7E0);  // engine management
                ecuAddresses.put("ABS",  0x7E3);  // ABS / DTC module
                ecuAddresses.put("BCU",  0x7E4);  // body control
                break;
            // ── KTM (Bosch / Keihin ECU) ─────────────────────────────────
            case KTM_DUKE: case KTM_SUPERDUKE: case KTM_RC:
                ecuAddresses.put("ECM",  0x7E0);
                ecuAddresses.put("ABS",  0x7E3);
                break;
            // ── Triumph (Keihin / Magneti Marelli) ───────────────────────
            case TRIUMPH_BONNEVILLE: case TRIUMPH_SPEED_TRI:
            case TRIUMPH_STREET_TR:  case TRIUMPH_TIGER:
                ecuAddresses.put("ECM",  0x7E0);
                break;
            default:
                ecuAddresses.put("ECU", 0x7E0);
        }
    }

    // ─── Static factory methods ───────────────────────────────────────────

    // GM
    public static VehicleProfile createGmcAcadiaProfile() {
        return new VehicleProfile("GMC Acadia", VehicleType.GMC_ACADIA,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createGmcSierraProfile() {
        return new VehicleProfile("GMC Sierra", VehicleType.GMC_SIERRA,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createChevySilveradoProfile() {
        return new VehicleProfile("Chevy Silverado", VehicleType.CHEVY_SILVERADO,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createChevyDuramaxProfile() {
        return new VehicleProfile("Chevy Duramax", VehicleType.CHEVY_DURAMAX,
                CableType.ELM327_BLUETOOTH, ProtocolType.SAE_J1939);
    }
    public static VehicleProfile createChevyCamaroProfile() {
        return new VehicleProfile("Chevy Camaro", VehicleType.CHEVY_CAMARO,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }

    // Ford
    public static VehicleProfile createFordF150Profile() {
        return new VehicleProfile("Ford F-150", VehicleType.FORD_F150,
                CableType.J2534_PASSTHRU, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createFordPowerstrokeProfile() {
        return new VehicleProfile("Ford PowerStroke", VehicleType.FORD_POWERSTROKE,
                CableType.J2534_PASSTHRU, ProtocolType.SAE_J1939);
    }
    public static VehicleProfile createFordMustangProfile() {
        return new VehicleProfile("Ford Mustang", VehicleType.FORD_MUSTANG,
                CableType.J2534_PASSTHRU, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createFordRaptorProfile() {
        return new VehicleProfile("Ford Raptor", VehicleType.FORD_RAPTOR,
                CableType.J2534_PASSTHRU, ProtocolType.ISO_15765_4_CAN);
    }

    // Dodge / Jeep
    public static VehicleProfile createDodgeHemiProfile() {
        return new VehicleProfile("Dodge HEMI", VehicleType.DODGE_HEMI,
                CableType.J2534_PASSTHRU, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createDodgeRam1500Profile() {
        return new VehicleProfile("Dodge Ram 1500", VehicleType.DODGE_RAM_1500,
                CableType.J2534_PASSTHRU, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createDodgeCumminsProfile() {
        return new VehicleProfile("Dodge Cummins", VehicleType.DODGE_CUMMINS,
                CableType.J2534_PASSTHRU, ProtocolType.SAE_J1939);
    }
    public static VehicleProfile createDodgeChallengerProfile() {
        return new VehicleProfile("Dodge Challenger", VehicleType.DODGE_CHALLENGER,
                CableType.J2534_PASSTHRU, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createDodgeChargerProfile() {
        return new VehicleProfile("Dodge Charger", VehicleType.DODGE_CHARGER,
                CableType.J2534_PASSTHRU, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createJeepCherokeeProfile() {
        return new VehicleProfile("Jeep Cherokee", VehicleType.JEEP_CHEROKEE,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createJeepGrandCherokeeProfile() {
        return new VehicleProfile("Jeep Grand Cherokee", VehicleType.JEEP_GRAND_CHEROKEE,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createJeepWranglerProfile() {
        return new VehicleProfile("Jeep Wrangler", VehicleType.JEEP_WRANGLER,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }

    // Toyota / Lexus
    public static VehicleProfile createToyotaTacomaProfile() {
        return new VehicleProfile("Toyota Tacoma", VehicleType.TOYOTA_TACOMA,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createToyotaTundraProfile() {
        return new VehicleProfile("Toyota Tundra", VehicleType.TOYOTA_TUNDRA,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createToyotaSupraProfile() {
        return new VehicleProfile("Toyota Supra (A90)", VehicleType.TOYOTA_SUPRA,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createLexusIsProfile() {
        return new VehicleProfile("Lexus IS", VehicleType.LEXUS_IS,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createLexusRcFProfile() {
        return new VehicleProfile("Lexus RC-F", VehicleType.LEXUS_RC_F,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }

    // Nissan / Infiniti
    public static VehicleProfile createNissanGtrProfile() {
        return new VehicleProfile("Nissan GT-R", VehicleType.NISSAN_GTR,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createNissan370zProfile() {
        return new VehicleProfile("Nissan 370Z", VehicleType.NISSAN_370Z,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createNissanTitanProfile() {
        return new VehicleProfile("Nissan Titan", VehicleType.NISSAN_TITAN,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createInfinitiQ50Profile() {
        return new VehicleProfile("Infiniti Q50", VehicleType.INFINITI_Q50,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createInfinitiQ60Profile() {
        return new VehicleProfile("Infiniti Q60", VehicleType.INFINITI_Q60,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }

    // Honda / Acura
    public static VehicleProfile createHondaCivicSiProfile() {
        return new VehicleProfile("Honda Civic Si/Type R", VehicleType.HONDA_CIVIC_SI,
                CableType.FLASH_PRO, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createHondaAccordProfile() {
        return new VehicleProfile("Honda Accord", VehicleType.HONDA_ACCORD,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createAcuraTlxProfile() {
        return new VehicleProfile("Acura TLX", VehicleType.ACURA_TLX,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createAcuraNsxProfile() {
        return new VehicleProfile("Acura NSX", VehicleType.ACURA_NSX,
                CableType.FLASH_PRO, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createAcuraIntegraProfile() {
        return new VehicleProfile("Acura Integra", VehicleType.ACURA_INTEGRA,
                CableType.FLASH_PRO, ProtocolType.ISO_15765_4_CAN);
    }

    // Mitsubishi
    public static VehicleProfile createMitsubishiEvoProfile() {
        return new VehicleProfile("Mitsubishi Evo X", VehicleType.MITSUBISHI_EVO,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createMitsubishiEclipseProfile() {
        return new VehicleProfile("Mitsubishi Eclipse", VehicleType.MITSUBISHI_ECLIPSE,
                CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
    }

    // BMW
    public static VehicleProfile createBmwN54Profile() {
        return new VehicleProfile("BMW N54 Turbo", VehicleType.BMW_N54,
                CableType.KDCAN_CABLE, ProtocolType.BMW_DS2);
    }
    public static VehicleProfile createBmwN55Profile() {
        return new VehicleProfile("BMW N55", VehicleType.BMW_N55,
                CableType.KDCAN_CABLE, ProtocolType.BMW_DS2);
    }
    public static VehicleProfile createBmwS58Profile() {
        return new VehicleProfile("BMW S58 (M3/M4)", VehicleType.BMW_S58,
                CableType.KDCAN_CABLE, ProtocolType.ISO_15765_4_CAN);
    }

    // VW / Audi
    public static VehicleProfile createVwVagProfile() {
        return new VehicleProfile("VW/Audi", VehicleType.VW_VAG,
                CableType.ROSS_TECH_VCDS, ProtocolType.VAG_KWP);
    }

    // Subaru
    public static VehicleProfile createSubaruWrxProfile() {
        return new VehicleProfile("Subaru WRX/STI", VehicleType.SUBARU_WRX,
                CableType.TACTRIX_OPENPORT, ProtocolType.ISO_15765_4_CAN);
    }

    // ── Harley-Davidson / Motorcycle ─────────────────────────────────────
    public static VehicleProfile createHarleyM8TouringProfile() {
        return new VehicleProfile("Harley M8 Touring", VehicleType.HARLEY_M8_TOURING,
                CableType.HARLEY_DIGI_TECH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createHarleyM8SoftailProfile() {
        return new VehicleProfile("Harley M8 Softail", VehicleType.HARLEY_M8_SOFTAIL,
                CableType.HARLEY_DIGI_TECH, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createHarleySportsterProfile() {
        return new VehicleProfile("Harley Sportster 883/1200", VehicleType.HARLEY_SPORTSTER,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.SAE_J1850_VPW);
    }
    public static VehicleProfile createHarleySportsterSProfile() {
        return new VehicleProfile("Harley Sportster S (RevMax 1250)", VehicleType.HARLEY_SPORTSTER_S,
                CableType.VANCE_HINES_FP3, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createHarleyPanAmericaProfile() {
        return new VehicleProfile("Harley Pan America 1250", VehicleType.HARLEY_PAN_AMERICA,
                CableType.VANCE_HINES_FP3, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createHarleyNightsterProfile() {
        return new VehicleProfile("Harley Nightster 975", VehicleType.HARLEY_NIGHTSTER,
                CableType.VANCE_HINES_FP3, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createHarleyStreetBobProfile() {
        return new VehicleProfile("Harley Street Bob / Low Rider", VehicleType.HARLEY_STREET_BOB,
                CableType.DYNOJET_POWERVISION, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createMemphisM8PerfProfile() {
        return new VehicleProfile("Memphis Performance M8 (Stage 1-4)", VehicleType.MEMPHIS_M8_PERF,
                CableType.DYNOJET_POWERVISION, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createIndianChiefProfile() {
        return new VehicleProfile("Indian Chief / Chieftain", VehicleType.INDIAN_CHIEF,
                CableType.INDIAN_RIDE_COMMAND, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createIndianScoutProfile() {
        return new VehicleProfile("Indian Scout", VehicleType.INDIAN_SCOUT,
                CableType.INDIAN_RIDE_COMMAND, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createIndianChallengerProfile() {
        return new VehicleProfile("Indian Challenger", VehicleType.INDIAN_CHALLENGER,
                CableType.INDIAN_RIDE_COMMAND, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createDucatiPanigaleProfile() {
        return new VehicleProfile("Ducati Panigale V4/V2", VehicleType.DUCATI_PANIGALE,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createDucatiMonsterProfile() {
        return new VehicleProfile("Ducati Monster", VehicleType.DUCATI_MONSTER,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createYamahaR1Profile() {
        return new VehicleProfile("Yamaha YZF-R1", VehicleType.YAMAHA_R1,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createYamahaMt09Profile() {
        return new VehicleProfile("Yamaha MT-09/MT-10", VehicleType.YAMAHA_MT09,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createKawasakiZx10rProfile() {
        return new VehicleProfile("Kawasaki ZX-10R", VehicleType.KAWASAKI_ZX10R,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createKawasakiH2Profile() {
        return new VehicleProfile("Kawasaki Ninja H2 Supercharged", VehicleType.KAWASAKI_H2,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createSuzukiHayabusaProfile() {
        return new VehicleProfile("Suzuki Hayabusa Gen3", VehicleType.SUZUKI_HAYABUSA,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createSuzukiGsxr1000Profile() {
        return new VehicleProfile("Suzuki GSX-R1000/R", VehicleType.SUZUKI_GSXR1000,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createHondaCbr1000rrProfile() {
        return new VehicleProfile("Honda CBR1000RR FireBlade", VehicleType.HONDA_CBR1000RR,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createHondaAfricaTwinProfile() {
        return new VehicleProfile("Honda Africa Twin CRF1100", VehicleType.HONDA_AFRICA_TWIN,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createBmwS1000rrProfile() {
        return new VehicleProfile("BMW S1000RR / M 1000 RR", VehicleType.BMW_S1000RR,
                CableType.BMW_MOTO_GS911, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createBmwR1250gsProfile() {
        return new VehicleProfile("BMW R1250GS / Adventure", VehicleType.BMW_R1250GS,
                CableType.BMW_MOTO_GS911, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createKtmSuperdukeProfile() {
        return new VehicleProfile("KTM 1290 Super Duke R", VehicleType.KTM_SUPERDUKE,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createKtmDukeProfile() {
        return new VehicleProfile("KTM 390/790/890 Duke", VehicleType.KTM_DUKE,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createTriumphSpeedTripleProfile() {
        return new VehicleProfile("Triumph Speed Triple 1200 RS", VehicleType.TRIUMPH_SPEED_TRI,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createTriumphStreetTripleProfile() {
        return new VehicleProfile("Triumph Street Triple R/RS", VehicleType.TRIUMPH_STREET_TR,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createMotoGenericProfile() {
        return new VehicleProfile("Generic Motorcycle OBD2", VehicleType.MOTO_GENERIC,
                CableType.OBD_MOTO_ADAPTER, ProtocolType.AUTO);
    }

    // Legacy
    public static VehicleProfile createMilwaukee117Profile() {
        return new VehicleProfile("Milwaukee 117", VehicleType.MILWAUKEE_117,
                CableType.MILWAUKEE_117, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createBullyDogProfile() {
        return new VehicleProfile("BullyDog GT", VehicleType.BULLYDOG_GENERIC,
                CableType.BULLYDOG_GT, ProtocolType.ISO_15765_4_CAN);
    }
    public static VehicleProfile createGenericProfile() {
        return new VehicleProfile("Generic OBD2", VehicleType.GENERIC_OBD2,
                CableType.ELM327_BLUETOOTH, ProtocolType.AUTO);
    }

    // ─── Auto-detection from VIN ──────────────────────────────────────────

    /**
     * Detect make/profile from a VIN string.
     * The WMI (first 3 chars) uniquely identifies the manufacturer.
     * Call this after reading VIN via OBD Mode 09 PID 02.
     *
     * @param vin  17-char VIN string (or partial — only first 3 chars needed)
     * @return best-match VehicleProfile (falls back to GENERIC_OBD2)
     */
    public static VehicleProfile detectFromVin(String vin) {
        if (vin == null || vin.length() < 3) {
            Log.w(TAG, "VIN too short for detection: " + vin);
            return createGenericProfile();
        }

        String wmi = vin.substring(0, 3).toUpperCase();
        Log.d(TAG, "Auto-detect WMI: " + wmi);

        // ── GM ──────────────────────────────────────────────────────────
        if (wmi.matches("1G[A-Z]|2G[A-Z]|KL[A-Z]")) {
            // Use 4th char to narrow down model line
            if (vin.length() >= 4) {
                char model = vin.charAt(3);
                if (model == 'K' || model == 'C') return createGmcAcadiaProfile();
                if (model == 'T') return createGmcSierraProfile();
                if (model == '1' || model == '2') return createChevySilveradoProfile();
                if (model == 'B') return createChevyCamaroProfile();
            }
            return createChevySilveradoProfile();
        }

        // ── Ford ────────────────────────────────────────────────────────
        if (wmi.matches("1FA|1FB|1FC|1FD|1FT|2FA|3FA")) {
            if (wmi.equals("1FT") || wmi.equals("2FT")) return createFordF150Profile();
            if (wmi.equals("1FA")) return createFordMustangProfile();
            return createFordF150Profile();
        }

        // ── Dodge / Chrysler / Jeep ──────────────────────────────────────
        if (wmi.matches("1C[0-9A-Z]|2C[0-9A-Z]|3C[0-9A-Z]")) {
            if (vin.length() >= 5) {
                String sub = vin.substring(0, 5).toUpperCase();
                if (sub.contains("J")  || sub.startsWith("1C4")) return createJeepGrandCherokeeProfile();
                if (sub.startsWith("1C6") || sub.startsWith("3C6")) return createDodgeRam1500Profile();
                if (sub.startsWith("2C3")) return createDodgeChallengerProfile();
            }
            return createDodgeRam1500Profile();
        }

        // ── Toyota ──────────────────────────────────────────────────────
        if (wmi.matches("1NZ|2T1|4T1|4T3|5YJ|JTD|JTM|JTN|5TF|5TD")) {
            if (wmi.startsWith("5TF") || wmi.startsWith("5TD")) return createToyotaTundraProfile();
            if (wmi.startsWith("5T")) return createToyotaTacomaProfile();
            return new VehicleProfile("Toyota", VehicleType.TOYOTA_CAMRY,
                    CableType.ELM327_BLUETOOTH, ProtocolType.ISO_15765_4_CAN);
        }

        // ── Lexus ────────────────────────────────────────────────────────
        if (wmi.matches("JTH|2T2|JT8|JT6")) {
            return createLexusIsProfile();
        }

        // ── Nissan ──────────────────────────────────────────────────────
        if (wmi.matches("JN1|JN8|5N1|3N1|1N4|1N6")) {
            if (wmi.equals("JN1")) return createNissanGtrProfile();
            if (wmi.equals("1N6") || wmi.equals("5N1")) return createNissanTitanProfile();
            return createNissan370zProfile();
        }

        // ── Infiniti ─────────────────────────────────────────────────────
        if (wmi.matches("JN1|JH4") && vin.length() >= 4) {
            char c4 = vin.charAt(3);
            if (c4 == 'C' || c4 == 'V') return createInfinitiQ50Profile();
        }

        // ── Honda ────────────────────────────────────────────────────────
        if (wmi.matches("1HG|2HG|3HG|19X|5J6|SHH")) {
            return createHondaAccordProfile();
        }

        // ── Acura ────────────────────────────────────────────────────────
        if (wmi.matches("JH4|19U|2HK")) {
            return createAcuraTlxProfile();
        }

        // ── Mitsubishi ───────────────────────────────────────────────────
        if (wmi.matches("JA3|JA4|4A3|4A4")) {
            if (vin.length() >= 5 && vin.substring(3,5).toUpperCase().contains("BE"))
                return createMitsubishiEvoProfile();
            return createMitsubishiEclipseProfile();
        }

        // ── BMW ──────────────────────────────────────────────────────────
        if (wmi.matches("WBA|WBS|WBY|5UX|5YM")) {
            return createBmwN54Profile();
        }

        // ── VW / Audi ────────────────────────────────────────────────────
        if (wmi.matches("WAU|WVW|WV2|1VW|3VW")) {
            return createVwVagProfile();
        }

        // ── Subaru ───────────────────────────────────────────────────────
        if (wmi.matches("JF1|JF2|4S3|4S4")) {
            return createSubaruWrxProfile();
        }

        Log.w(TAG, "Unknown WMI " + wmi + " — falling back to generic profile");
        return createGenericProfile();
    }

    /**
     * Stub for fetching a plugin pack from a remote server when the
     * vehicle is not built-in.  Replace SERVER_URL with your endpoint.
     * The server should return a JSON profile that maps to VehicleProfile fields.
     *
     * Call on a background thread — never on the UI thread.
     */
    public static VehicleProfile fetchPluginProfile(String vin) {
        String[] endpoints = {
            "https://race.surina.xyz/api/v1/profiles/",
            "https://race.e164.cloud/api/v1/profiles/"
        };
        String vin8 = vin.substring(0, Math.min(8, vin.length()));
        for (String base : endpoints) {
            try {
                java.net.URL url = new java.net.URL(base + vin8);
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    conn.disconnect();
                    Log.d(TAG, "Plugin pack received from " + base + " for VIN: " + vin);
                    // TODO: parse JSON sb.toString() into a VehicleProfile
                    return new VehicleProfile("Plugin: " + vin8,
                            VehicleType.PLUGIN_PACK, CableType.ELM327_BLUETOOTH,
                            ProtocolType.AUTO);
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "Plugin fetch failed for " + base + ": " + e.getMessage());
            }
        }
        return detectFromVin(vin); // fall back to local detection
    }

    // ─── Getters / Setters ────────────────────────────────────────────────

    public String getName()                          { return name; }
    public void setName(String name)                 { this.name = name; }

    public VehicleType getType()                     { return type; }
    public void setType(VehicleType type)            { this.type = type; }

    public CableType getCableType()                  { return cableType; }
    public void setCableType(CableType cableType)    { this.cableType = cableType; }

    public ProtocolType getProtocol()                { return protocol; }
    public void setProtocol(ProtocolType protocol)   { this.protocol = protocol; }

    public Map<String, Integer> getEcuAddresses()    { return ecuAddresses; }

    public boolean supportsEcuFlashing()             { return supportsEcuFlashing; }
    public boolean supportsAdvancedTuning()          { return supportsAdvancedTuning; }
}
