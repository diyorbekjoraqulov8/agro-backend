package uz.app.agro.satellite.dto;

import java.util.List;

/**
 * Python /report/pdf shartnomasi (app/report.py). Matnlar TAYYOR — fermer tilida, EMOJI'siz
 * (PDF shrifti DejaVu Sans'da emoji yo'q). Maydon nomlari Python bilan bir xil (bitta so'z — case farqi yo'q).
 */
public record PdfRequest(
        String title,
        String subtitle,
        String footer,
        List<Section> sections,
        List<Image> images,
        Table table
) {
    public record Section(String heading, List<String> lines) {}

    // png_base64 — snake_case
    public record Image(String title, @com.fasterxml.jackson.annotation.JsonProperty("png_base64") String pngBase64) {}

    public record Table(String title, List<String> headers, List<List<String>> rows) {}
}
