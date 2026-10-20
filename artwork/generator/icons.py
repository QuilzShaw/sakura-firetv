"""App icon + TV banner for Sakura Home, cut from the glow wallpaper."""
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

src, out = sys.argv[1], sys.argv[2]
wp = Image.open(src).convert("RGBA")


def font(size):
    for name in ("seguisb.ttf", "segoeui.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def glow_text(img, xy, text, size, anchor="mm"):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(xy, text, font=font(size), fill=(255, 150, 190, 255), anchor=anchor)
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(size / 6)))
    ImageDraw.Draw(img).text(xy, text, font=font(size), fill=(255, 246, 250, 255), anchor=anchor)


# banner 320x180 (xhdpi TV banner), crop around the moon and mountain
banner = wp.crop((560, 120, 1600, 705)).resize((640, 360), Image.LANCZOS)
dark = Image.new("RGBA", banner.size, (20, 8, 26, 90))
banner.alpha_composite(dark)
glow_text(banner, (320, 180), "Sakura Home", 72)
banner.resize((320, 180), Image.LANCZOS).save(f"{out}/app_banner.png")

# square icon
icon = wp.crop((900, 160, 1480, 740)).resize((512, 512), Image.LANCZOS)
mask = Image.new("L", (512, 512), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, 511, 511], radius=110, fill=255)
glow_text(icon, (256, 256), "❀", 230)  # floral heart glyph, falls back gracefully
icon.putalpha(mask)
icon.resize((192, 192), Image.LANCZOS).save(f"{out}/app_icon.png")
print("ok")
