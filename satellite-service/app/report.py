"""Hisobot PDF'i. Matnlar Java'dan TAYYOR (fermer tilida) keladi — tarjima bitta joyda qoladi.
Shrift: DejaVu Sans (matplotlib bilan keladi) — lotin, kirill, ʻ ’ ў қ ғ ҳ bor; EMOJI YO'Q (Java yubormaydi).
pyplot EMAS — Figure API (thread-safe)."""
import base64
import io
import textwrap

import matplotlib.image as mpimg
from matplotlib.backends.backend_pdf import PdfPages
from matplotlib.figure import Figure
from pydantic import BaseModel, Field

A4 = (8.27, 11.69)
MARGIN = 0.07
WRAP = 92                     # 10.5 pt da A4 kengligiga sig'adigan belgilar


class Section(BaseModel):
    heading: str = Field(max_length=200)
    lines: list[str] = Field(default_factory=list, max_length=60)


class Image(BaseModel):
    title: str = Field(max_length=200)
    png_base64: str = Field(max_length=4_000_000)


class Table(BaseModel):
    title: str = Field(max_length=200)
    headers: list[str] = Field(max_length=10)
    rows: list[list[str]] = Field(max_length=2000)


class PdfRequest(BaseModel):
    title: str = Field(max_length=200)
    subtitle: str = Field(default="", max_length=300)
    footer: str = Field(default="", max_length=300)
    sections: list[Section] = Field(default_factory=list, max_length=20)
    images: list[Image] = Field(default_factory=list, max_length=4)
    table: Table | None = None


class _Page:
    """Yuqoridan pastga yozadigan oddiy sahifa; joy tugasa yangi sahifa."""

    def __init__(self, pdf: PdfPages, footer: str):
        self.pdf, self.footer, self.no = pdf, footer, 0
        self._new()

    def _new(self):
        self.fig = Figure(figsize=A4)
        self.y = 1 - MARGIN
        self.no += 1

    def flush(self):
        if self.footer:
            self.fig.text(MARGIN, 0.03, self.footer, fontsize=8, color="#777777")
        self.fig.text(1 - MARGIN, 0.03, str(self.no), fontsize=8, color="#777777", ha="right")
        self.pdf.savefig(self.fig)
        self._new()

    def need(self, h: float):
        if self.y - h < MARGIN + 0.03:
            self.flush()

    def text(self, s: str, size=10.5, weight="normal", color="#1b1b1b", gap=0.006):
        for line in (textwrap.wrap(s, WRAP) or [""]):
            h = size / 72 / A4[1] * 1.45
            self.need(h)
            self.fig.text(MARGIN, self.y - h, line, fontsize=size, fontweight=weight, color=color, va="bottom")
            self.y -= h
        self.y -= gap

    def image(self, title: str, png: bytes, max_h=0.34):
        img = mpimg.imread(io.BytesIO(png), format="png")
        h_px, w_px = img.shape[:2]
        w = 1 - 2 * MARGIN
        h = min(max_h, w * (h_px / w_px) * A4[0] / A4[1])
        w = h / (h_px / w_px) / A4[0] * A4[1]
        # Sarlavha + rasm BIRGA sig'masa — yangi sahifa (sarlavha sahifa oxirida yolg'iz qolmasin)
        self.need(h + 0.05)
        self.text(title, size=12, weight="bold")
        ax = self.fig.add_axes((0.5 - w / 2, self.y - h, w, h))
        ax.imshow(img)
        ax.axis("off")
        self.y -= h + 0.015


def render_pdf(req: PdfRequest) -> bytes:
    buf = io.BytesIO()
    with PdfPages(buf, metadata={"Title": req.title, "Creator": "Agro"}) as pdf:
        p = _Page(pdf, req.footer)
        p.text(req.title, size=17, weight="bold", gap=0.004)
        if req.subtitle:
            p.text(req.subtitle, size=10, color="#555555", gap=0.02)
        imgs = list(req.images)
        if imgs:                                  # 1-rasm (xarita) — sarlavhadan keyin darhol
            first = imgs.pop(0)
            p.image(first.title, base64.b64decode(first.png_base64))
        line_h = 10.5 / 72 / A4[1] * 1.45
        for sec in req.sections:
            # Sarlavha sahifa oxirida yolg'iz qolmasin — kamida 2 qatori bilan birga
            p.need(line_h * (2 + min(2, len(sec.lines))))
            p.text(sec.heading, size=12, weight="bold", gap=0.004)
            for line in sec.lines:
                p.text(line)
            p.y -= 0.012
        for im in imgs:
            p.image(im.title, base64.b64decode(im.png_base64), max_h=0.3)
        if req.table and req.table.rows:
            p.flush()
            p.text(req.table.title, size=12, weight="bold", gap=0.01)
            p.text("    ".join(f"{h:<14}" for h in req.table.headers), size=9.5, weight="bold", gap=0.0)
            for row in req.table.rows:
                p.text("    ".join(f"{c:<14}" for c in row), size=9.5, gap=0.0)
        p.flush()
    return buf.getvalue()
