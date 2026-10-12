"""Konteyner kirish nuqtasi.

`uvicorn --workers N` da asosiy jarayon yiqilgan worker'ni qayta-qayta ko'taradi —
INTERNAL_KEY yo'q yoki kutubxona yetishmasa konteyner o'lmay osilib turadi.
Shuning uchun ilova worker'lardan OLDIN shu yerda bir marta import qilinadi:
xato bo'lsa jarayon exit 1 bilan tugaydi, Docker/compose buni ko'radi.
"""
import os

import uvicorn

import app.main  # noqa: F401 — fail-fast: INTERNAL_KEY, rasterio/GDAL, mamlakatlar fayli

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=int(os.environ.get("PORT", "8090")),
                workers=int(os.environ.get("WORKERS", "2")))
