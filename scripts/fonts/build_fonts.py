"""Builds Aroma's font files from the variable sources: static instances, a few missing glyphs, Latin subsets.

python3 -I build_fonts.py <source dir> <output dir>
"""
import sys
from fontTools.ttLib import TTFont, newTable
from fontTools.ttLib.tables._g_l_y_f import Glyph, GlyphComponent, GlyphCoordinates
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.varLib import instancer
from fontTools import subset

SRC, OUT = sys.argv[1], sys.argv[2]

UNICODES = (
    list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) +
    [0x192, 0x2C6, 0x2DA, 0x2DC, 0x2009, 0x200A, 0x200B, 0x2060, 0x202F, 0x2044, 0x20AC, 0x2122, 0x2212, 0x2190, 0x2192, 0x2197]
    + list(range(0x2010, 0x2016)) + list(range(0x2018, 0x201F)) + [0x2020, 0x2021, 0x2022, 0x2026, 0x2030, 0x2032, 0x2033, 0x2039, 0x203A]
    + list(range(0x2150, 0x215F))
)

# Vulgar fractions: code point, numerator, denominator.
FRACTIONS = {
    0xBD: (1, 2), 0xBC: (1, 4), 0xBE: (3, 4), 0x2153: (1, 3), 0x2154: (2, 3), 0x2155: (1, 5), 0x2156: (2, 5),
    0x2157: (3, 5), 0x2158: (4, 5), 0x2159: (1, 6), 0x215A: (5, 6), 0x215B: (1, 8), 0x215C: (3, 8), 0x215D: (5, 8), 0x215E: (7, 8),
}
DIGITS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"]


def add_glyph(font, name, glyph, advance, lsb=None):
    glyf, hmtx = font["glyf"], font["hmtx"]
    order = font.getGlyphOrder()
    if name not in glyf.glyphs:
        order.append(name)
        font.setGlyphOrder(order)
    glyf.glyphs[name] = glyph
    glyph.recalcBounds(glyf)
    hmtx.metrics[name] = (advance, glyph.xMin if lsb is None and hasattr(glyph, "xMin") and glyph.numberOfContours != 0 else (lsb or 0))


def map_code(font, code, name):
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap[code] = name


def empty_glyph():
    pen = TTGlyphPen(None)
    return pen.glyph()


def composite(components):
    glyph = Glyph()
    glyph.numberOfContours = -1
    glyph.components = []
    for name, x, y in components:
        c = GlyphComponent()
        c.glyphName, c.x, c.y, c.flags = name, int(round(x)), int(round(y)), 0x4  # ROUND_XY_TO_GRID
        glyph.components.append(c)
    return glyph


def add_spaces(font, narrow_share):
    hmtx, cmap = font["hmtx"], font.getBestCmap()
    space = hmtx[cmap[0x20]][0]
    narrow = int(round(space * narrow_share))
    for code, name, width in [(0x202F, "uni202F", narrow), (0x2009, "uni2009", narrow), (0x200B, "uni200B", 0), (0x2060, "uni2060", 0)]:
        if code not in cmap:
            add_glyph(font, name, empty_glyph(), width, 0)
            map_code(font, code, name)
    hyphen = cmap[0x2D]
    for code, name in [(0x2010, "uni2010"), (0x2011, "uni2011"), (0xAD, "uni00AD")]:
        if code not in font.getBestCmap():
            add_glyph(font, name, composite([(hyphen, 0, 0)]), hmtx[hyphen][0], hmtx[hyphen][1])
            map_code(font, code, name)


def add_fractions(font, heavy, scale, cap):
    """Vulgar fractions from small figures of a heavier cut, so their strokes match the text's."""
    glyf, hmtx, hglyf, hhmtx = font["glyf"], font["hmtx"], heavy["glyf"], heavy["hmtx"]
    hcmap = heavy.getBestCmap()
    small = {}
    for digit, name in enumerate(DIGITS):
        source = hglyf[hcmap[0x30 + digit]]
        coords, ends, flags = source.getCoordinates(hglyf)
        pen = TTGlyphPen(None)
        glyph = Glyph()
        glyph.numberOfContours = len(ends)
        glyph.coordinates = GlyphCoordinates([(x * scale, y * scale) for x, y in coords])
        glyph.coordinates.toInt()
        glyph.endPtsOfContours = list(ends)
        glyph.flags = bytearray(flags)
        glyph.program = None
        from fontTools.ttLib.tables import ttProgram
        glyph.program = ttProgram.Program(); glyph.program.fromBytecode(b"")
        advance = int(round(hhmtx[hcmap[0x30 + digit]][0] * scale))
        add_glyph(font, name + ".small", glyph, advance)
        small[digit] = name + ".small"
    # The fraction slash: a parallelogram from the baseline to the cap height.
    run, stem = cap * 0.5, cap * 0.095
    pen = TTGlyphPen(None)
    pen.moveTo((0, 0)); pen.lineTo((stem, 0)); pen.lineTo((run + stem, cap)); pen.lineTo((run, cap)); pen.closePath()
    slash = pen.glyph()
    add_glyph(font, "fraction", slash, int(round(run * 0.55 + stem)), 0)
    map_code(font, 0x2044, "fraction")
    lift = cap - cap * scale
    gap = cap * 0.03
    for code, (numerator, denominator) in FRACTIONS.items():
        n, d = small[numerator], small[denominator]
        glyf[n].recalcBounds(glyf); glyf[d].recalcBounds(glyf)
        n_left = cap * 0.04
        n_right = n_left + glyf[n].xMax
        # Where the slash passes the numerator's foot and the denominator's head.
        x_slash = n_right + gap - run * (lift / cap) - glyf[n].xMin * 0
        d_left = x_slash + run * ((cap * scale) / cap) + stem + gap - glyf[d].xMin
        advance = d_left + glyf[d].xMax + cap * 0.04
        name = "frac%d%d" % (numerator, denominator)
        add_glyph(font, name, composite([(n, n_left, lift), ("fraction", x_slash, 0), (d, d_left, 0)]), int(round(advance)), int(round(n_left)))
        map_code(font, code, name)


def finish(font, path, family, style, full):
    name = font["name"]
    for record in list(name.names):
        if record.nameID in (1, 2, 3, 4, 6, 16, 17, 21, 22, 25):
            name.removeNames(nameID=record.nameID)
    name.setName(family, 1, 3, 1, 0x409); name.setName(style, 2, 3, 1, 0x409)
    name.setName(full, 4, 3, 1, 0x409); name.setName(full.replace(" ", ""), 6, 3, 1, 0x409)
    name.setName(full + "; Aroma", 3, 3, 1, 0x409)
    if "STAT" in font: del font["STAT"]
    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    options.glyph_names = False
    options.hinting = False
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=UNICODES)
    subsetter.subset(font)
    font.save(path)


def sans(weight, out):
    vf = TTFont(f"{SRC}/instrumentsans__InstrumentSans[wdth,wght].ttf")
    font = instancer.instantiateVariableFont(vf, {"wdth": 100, "wght": weight})
    heavy = instancer.instantiateVariableFont(TTFont(f"{SRC}/instrumentsans__InstrumentSans[wdth,wght].ttf"), {"wdth": 100, "wght": min(700, weight + 160)})
    add_spaces(font, 0.6)
    add_fractions(font, heavy, 0.58, font["OS/2"].sCapHeight)
    style = "Regular" if weight == 400 else "Medium"
    finish(font, out, "Instrument Sans" if weight == 400 else "Instrument Sans Medium", "Regular", f"Instrument Sans {style}")


def serif(source, opsz, out, label, italic=False):
    font = instancer.instantiateVariableFont(TTFont(f"{SRC}/{source}"), {"opsz": opsz, "wght": 400})
    add_spaces(font, 0.55)
    family = f"Newsreader {label}"
    finish(font, out, family, "Italic" if italic else "Regular", family + (" Italic" if italic else ""))


sans(400, f"{OUT}/instrument_sans_regular.ttf")
sans(500, f"{OUT}/instrument_sans_medium.ttf")
serif("newsreader__Newsreader[opsz,wght].ttf", 34, f"{OUT}/newsreader_display.ttf", "Display")
serif("newsreader__Newsreader[opsz,wght].ttf", 22, f"{OUT}/newsreader_subhead.ttf", "Subhead")
serif("newsreader__Newsreader[opsz,wght].ttf", 17, f"{OUT}/newsreader_text.ttf", "Text")
serif("newsreader__Newsreader-Italic[opsz,wght].ttf", 17, f"{OUT}/newsreader_text_italic.ttf", "Text", italic=True)
print("done")
