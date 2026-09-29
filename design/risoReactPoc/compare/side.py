"""Two crops side by side, magnified without smoothing, for looking at pixels.

    python -c "from side import side; side(a, b, box_a, box_b, scale, out)"
"""
from PIL import Image
def side(a, b, boxa, boxb, scale, out):
    A = Image.open(a).convert('RGB').crop(boxa); B = Image.open(b).convert('RGB').crop(boxb)
    A = A.resize((A.width*scale, A.height*scale), Image.NEAREST); B = B.resize((B.width*scale, B.height*scale), Image.NEAREST)
    m = Image.new('RGB', (A.width + B.width + 10, max(A.height, B.height)), (255,0,0))
    m.paste(A, (0,0)); m.paste(B, (A.width+10, 0)); m.save(out)
