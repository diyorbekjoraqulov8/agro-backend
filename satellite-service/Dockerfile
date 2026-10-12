FROM python:3.12-slim

# rasterio wheel'i GDAL'ni o'zi bilan olib keladi, lekin slim image'da yo'q
# libexpat.so.1 ga bog'liq — usiz `import rasterio` ImportError beradi
RUN apt-get update && apt-get install -y --no-install-recommends libexpat1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
# Mamlakat chegaralari (Natural Earth, public domain) image ichida — runtime'da tarmoq kerak emas
RUN python -m app.geometry

RUN useradd --system --uid 10001 app && mkdir -p /home/app && chown app /home/app
USER app
ENV HOME=/home/app MPLCONFIGDIR=/home/app/.mpl

EXPOSE 8090
# INTERNAL_KEY env majburiy — yo'q bo'lsa konteyner exit 1 (app/serve.py)
CMD ["python", "-m", "app.serve"]
