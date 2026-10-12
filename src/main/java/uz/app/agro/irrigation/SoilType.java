package uz.app.agro.irrigation;

/**
 * Tuproq — suvni qancha ushlaydi. TAW (mm suv / 1 m ildiz qatlami) — FAO-56, 19-jadval o'rtachalari.
 * V13 ck_field_water_soil bilan bir xil.
 */
public enum SoilType {
    SAND(80),        // qumloq — suvni tez o'tkazadi, tez-tez sug'oriladi
    LOAM(150),       // qumoq / o'rtacha
    CLAY(180);       // og'ir, soz

    public final int tawMmPerM;

    SoilType(int tawMmPerM) {
        this.tawMmPerM = tawMmPerM;
    }
}
