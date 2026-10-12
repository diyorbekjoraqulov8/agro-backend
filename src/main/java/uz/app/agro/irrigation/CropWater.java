package uz.app.agro.irrigation;

import uz.app.agro.field.Crop;

/**
 * Ekin parametrlari (FAO-56, 22-jadval o'rtachalari): Zr — ildiz chuqurligi (m),
 * p — stresssiz ishlatiladigan suv ulushi (RAW = p × TAW). Pilot ma'lumotida sozlanadi.
 */
public record CropWater(double rootDepthM, double p) {

    public static CropWater of(Crop crop) {
        return switch (crop) {
            case COTTON -> new CropWater(1.0, 0.65);
            case WHEAT -> new CropWater(1.0, 0.55);
            case CORN -> new CropWater(1.0, 0.55);
            case RICE -> new CropWater(0.5, 0.20);
            case ALFALFA -> new CropWater(1.2, 0.55);
            case VEGETABLES -> new CropWater(0.5, 0.40);
            case ORCHARD -> new CropWater(1.2, 0.50);
            case OTHER -> new CropWater(0.8, 0.50);
        };
    }

    public double tawMm(SoilType soil) {
        return soil.tawMmPerM * rootDepthM;
    }

    public double rawMm(SoilType soil) {
        return tawMm(soil) * p;
    }

    /** Kc = 1.457·NDVI − 0.1725 (Python weather.kc_from_ndvi bilan bir xil); NDVI yo'q — 0.9 */
    public static double kcFromNdvi(Double ndvi) {
        if (ndvi == null) return 0.9;
        return Math.max(0.15, Math.min(1.2, 1.457 * ndvi - 0.1725));
    }
}
