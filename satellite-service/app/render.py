"""PNG'lar matnsiz (faqat raqam va rang) — til Java/bot tomonida, rasm har tilda ishlaydi.
pyplot EMAS, Figure API: pyplot global holati thread-safe emas (FastAPI thread pool)."""
import base64
import io
from datetime import date

import numpy as np
from matplotlib.colors import ListedColormap
from matplotlib.dates import DateFormatter
from matplotlib.figure import Figure
from shapely.geometry import Polygon

from app.analysis import GOOD_REL, WEAK_REL
from app.schemas import Period

# Rang-ko'r uchun ham ajraladigan (PRGn): zaif — binafsha, o'rta — sariq, yaxshi — yashil
WEAK, MID, GOOD = "#762a83", "#f1c232", "#1b7837"
NOW, PREV = "#1b7837", "#9e9e9e"


def _png(fig: Figure) -> str:
    buf = io.BytesIO()
    fig.savefig(buf, format="png", dpi=100, bbox_inches="tight")
    return base64.b64encode(buf.getvalue()).decode()


def zone_map(rel: np.ndarray, x: np.ndarray, y: np.ndarray, poly_utm: Polygon) -> str:
    cls = np.full(rel.shape, np.nan)
    cls[rel < WEAK_REL] = 0
    cls[(rel >= WEAK_REL) & (rel < GOOD_REL)] = 1
    cls[rel >= GOOD_REL] = 2
    fig = Figure(figsize=(8, 8))
    ax = fig.add_subplot()
    ax.imshow(cls, cmap=ListedColormap([WEAK, MID, GOOD]), vmin=0, vmax=2, interpolation="nearest",
              extent=(x.min() - 5, x.max() + 5, y.min() - 5, y.max() + 5))
    bx, by = poly_utm.exterior.xy
    ax.plot(bx, by, color="#222222", linewidth=2)
    _scale_bar(ax, poly_utm)
    ax.set_aspect("equal")
    ax.axis("off")
    return _png(fig)


def _scale_bar(ax, poly: Polygon) -> None:
    minx, miny, maxx, _ = poly.bounds
    width = maxx - minx
    length = next(m for m in (1000, 500, 200, 100, 50) if m <= width / 3) if width >= 150 else 50
    ax.plot([minx, minx + length], [miny - 15, miny - 15], color="#222222", linewidth=3)
    ax.text(minx + length / 2, miny - 25, f"{length} m", ha="center", va="top", fontsize=12)


def season_chart(dates: list[date], ndvi: np.ndarray, prev_dates: list[date] | None,
                 prev_ndvi: np.ndarray | None, season: Period | None, stress: list[Period]) -> str:
    fig = Figure(figsize=(9, 4.5))
    ax = fig.add_subplot()
    if season:
        ax.axvspan(season.start, season.end, color=GOOD, alpha=0.08)
    for p in stress:
        ax.axvspan(p.start, p.end, color=WEAK, alpha=0.25)
    if prev_dates:
        # O'tgan yilni bir yil oldinga suramiz — bir o'qda taqqoslanadi
        shifted = [d.replace(year=d.year + 1) if not (d.month == 2 and d.day == 29) else d.replace(year=d.year + 1, day=28)
                   for d in prev_dates]
        ax.plot(shifted, prev_ndvi, color=PREV, linewidth=2, linestyle="--", marker="o", markersize=3,
                label=f"{prev_dates[0].year}–{prev_dates[-1].year}")
    ax.plot(dates, ndvi, color=NOW, linewidth=2.5, marker="o", markersize=4,
            label=f"{dates[0].year}–{dates[-1].year}")
    ax.set_ylim(0, 1)
    ax.set_ylabel("NDVI")
    ax.xaxis.set_major_formatter(DateFormatter("%d.%m"))
    ax.grid(alpha=0.3)
    ax.legend(loc="upper left", frameon=False)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    return _png(fig)
