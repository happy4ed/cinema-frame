#!/usr/bin/env python3
"""spec.json → 비교 페이지(index.html) + 저장소 전달 폴더(SVG 층 · 밀도별 PNG · 미리보기 PNG)."""
import json, os, shutil, io
import cairosvg
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
D = json.load(open(os.path.join(HERE, 'spec.json'), encoding='utf-8'))
NAVY = D['NAVY']; SP = D['spec']; CN = D['C']
OUT = os.path.join(HERE, 'deliver'); shutil.rmtree(OUT, ignore_errors=True); os.makedirs(OUT)
def _circ(cx, cy, r):
    return f'M{cx-r} {cy}A{r} {r} 0 1 0 {cx+r} {cy}A{r} {r} 0 1 0 {cx-r} {cy}Z'
_base = SP['A-remote']['fg'][:-2]; _disc = SP['A-remote']['fg'][-2][0]; _tri = SP['A-remote']['fg'][-1][0]
def _L(h):
    h = h.lstrip('#'); c = [int(h[i:i+2], 16) / 255 for i in (0, 2, 4)]; c = [x / 12.92 if x <= .03928 else ((x + .055) / 1.055) ** 2.4 for x in c]
    return .2126 * c[0] + .7152 * c[1] + .0722 * c[2]
def _cr(a, b):
    la, lb = _L(a), _L(b); return (max(la, lb) + .05) / (min(la, lb) + .05)
PLAYV = [  # (id, 이름, 원 색, 삼각 색, 금 테두리, 설명)
 ('P0', '처음 파랑(참고)', '#387AFF', '#FCFCFF', False, 'One UI 시안 파랑 — 회장님 "촌스럽다"'),
 ('P1', '진한 금색', '#B8954A', '#080D13', False, '회장님 예시 계열 · 포스터와 대비가 낮아 48px 에서 흐릴 수 있음'),
 ('P2', '더 진한 금색', '#9C7A3C', '#080D13', False, '포스터와 대비를 지금 파랑과 비슷하게 맞춘 금색'),
 ('P3', '남색 검정', '#080D13', '#F5D59A', False, '액자 화면 바탕색 원 + 진행 바 금색 삼각 — 아이콘 바탕과 같은 색'),
 ('P4', '남색 검정 + 금 테두리', '#080D13', '#F5D59A', True, 'P3 에 진행 바 금색 테두리 1(원 크기 그대로 안쪽으로)'),
 ('P5', '순검정', '#000000', '#FFFFFF', False, '검정 원 + 흰 삼각 — 가장 또렷'),
 ('P6', '반투명 검정', '#5F5542', '#F5D59A', False, '검정 55% 를 포스터 위에 겹친 색(합성 #5F5542) + 밝은 금 삼각 — 포스터가 비쳐 보이는 느낌'),
 ('P7', '밝은 금색', '#F5D59A', '#080D13', False, '진행 바 금색 원 + 남색 삼각 — 포스터와 대비가 가장 낮음'),
]
for pid, nm, dc, tc, ring, note in PLAYV:
    fg = list(_base)
    if ring:
        fg += [(_disc, '#F5D59A', ''), (_circ(54, 48, 7.4), dc, '')]
    else:
        fg += [(_disc, dc, '')]
    fg += [(_tri, tc, '')]
    SP['A-remote@' + pid] = {'fg': fg, 'mono': SP['A-remote']['mono']}
SLUG = {'A': 'A-poster-bar', 'B': 'B-note-frame', 'C': 'C-film-c'}
APP = {'frame': ('Cinema Frame', '액자 앱 · android/'), 'remote': ('Cinema Remote', '폰 리모컨 · phone/')}
REC = 'A'

def fg_svg(key, size=None):
    w = f' width="{size}" height="{size}"' if size else ''
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"{w}>' + ''.join(
        f'<path fill="{c}"{" fill-rule=" + chr(34) + r + chr(34) if r else ""} d="{d}"/>' for d, c, r in SP[key]['fg']) + '</svg>'
def mono_svg(key, color='#FFFFFF', size=None):
    w = f' width="{size}" height="{size}"' if size else ''
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"{w}><path fill="{color}" fill-rule="evenodd" d="{SP[key]["mono"]}"/></svg>'
def bg_svg(): return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><rect width="108" height="108" fill="{NAVY}"/></svg>'
def png(svg, px): return Image.open(io.BytesIO(cairosvg.svg2png(bytestring=svg.encode(), output_width=px, output_height=px))).convert('RGBA')

# ── 1. 저장소 전달 파일
DENS = [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]
def squircle_mask(px):
    m = Image.new('L', (px, px), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, px - 1, px - 1], radius=int(px * 0.3), fill=255); return m
for k, slug in SLUG.items():
    for app in ('frame', 'remote'):
        key = f'{k}-{app}'; d = os.path.join(OUT, slug, app); os.makedirs(d)
        open(os.path.join(d, 'ic_launcher_foreground.svg'), 'w').write(fg_svg(key))
        open(os.path.join(d, 'ic_launcher_monochrome.svg'), 'w').write(mono_svg(key))
        open(os.path.join(d, 'ic_launcher_background.svg'), 'w').write(bg_svg())
        for name, s in DENS:
            md = os.path.join(d, 'res', f'mipmap-{name}'); os.makedirs(md)
            fpx = int(108 * s); lpx = int(48 * s)
            png(fg_svg(key), fpx).save(os.path.join(md, 'ic_launcher_foreground.png'))
            png(mono_svg(key), fpx).save(os.path.join(md, 'ic_launcher_monochrome.png'))
            # 레거시(48dp): 바탕 + 전경의 가운데 72/108 을 둥근 사각으로
            full = Image.new('RGBA', (int(lpx * 1.5), int(lpx * 1.5)), NAVY); full.alpha_composite(png(fg_svg(key), int(lpx * 1.5)))
            o = int(lpx * 0.25); crop = full.crop((o, o, o + lpx, o + lpx)); out = Image.new('RGBA', (lpx, lpx), (0, 0, 0, 0)); out.paste(crop, (0, 0), squircle_mask(lpx))
            out.save(os.path.join(md, 'ic_launcher.png'))
        av = os.path.join(d, 'res', 'mipmap-anydpi-v26'); os.makedirs(av)
        open(os.path.join(av, 'ic_launcher.xml'), 'w').write(
            '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/ic_launcher_background"/>\n    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
            '    <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>\n</adaptive-icon>\n')
        vd = os.path.join(d, 'res', 'values'); os.makedirs(vd)
        open(os.path.join(vd, 'ic_launcher_colors.xml'), 'w').write(f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">{NAVY}</color>\n</resources>\n')
        # 미리보기 PNG: 원형 · 둥근 사각 · 물방울 (192)
        full = Image.new('RGBA', (288, 288), NAVY); full.alpha_composite(png(fg_svg(key), 288)); crop = full.crop((48, 48, 240, 240))
        sheet = Image.new('RGBA', (192 * 3 + 32, 192), (0, 0, 0, 0))
        for i, shape in enumerate(('circle', 'squircle', 'teardrop')):
            m = Image.new('L', (192, 192), 0); dr = ImageDraw.Draw(m)
            if shape == 'circle': dr.ellipse([0, 0, 191, 191], fill=255)
            elif shape == 'squircle': dr.rounded_rectangle([0, 0, 191, 191], radius=58, fill=255)
            else: dr.ellipse([0, 0, 191, 191], fill=255); dr.rounded_rectangle([96, 96, 191, 191], radius=18, fill=255)
            sheet.paste(crop, (i * 208, 0), m)
        sheet.save(os.path.join(d, 'preview-masks.png'))

# ── 1b. 재생 버튼 색 시안 PNG (확정판 res 는 그대로)
PC = os.path.join(OUT, 'A-poster-bar', 'play-color'); os.makedirs(PC, exist_ok=True)
def _tile(key, px, bg=NAVY):
    full = Image.new('RGBA', (int(px * 1.5),) * 2, bg); full.alpha_composite(png(fg_svg(key), int(px * 1.5))); o = int(px * .25)
    t = full.crop((o, o, o + px, o + px)); out = Image.new('RGBA', (px, px), (0, 0, 0, 0)); out.paste(t, (0, 0), squircle_mask(px)); return out
for pid, nm, dc, tc, ring, note in PLAYV:
    key = 'A-remote@' + pid; W = 48 + 108 + 192 + 72 * 2 + 16 * 6
    sh = Image.new('RGBA', (W, 224), (22, 24, 26, 255)); x = 16
    for px in (48, 108, 192): sh.alpha_composite(_tile(key, px), (x, 16 + 192 - px)); x += px + 16
    wl = Image.new('RGBA', (72 * 2 + 48, 104), (220, 228, 238, 255)); wl.alpha_composite(_tile('A-frame', 72), (16, 16)); wl.alpha_composite(_tile(key, 72), (104, 16))
    sh.alpha_composite(wl, (x, 16)); wd = Image.new('RGBA', (72 * 2 + 48, 104), (10, 14, 20, 255)); wd.alpha_composite(_tile('A-frame', 72), (16, 16)); wd.alpha_composite(_tile(key, 72), (104, 16)); sh.alpha_composite(wd, (x, 120))
    sh.save(os.path.join(PC, f'{pid}-{dc.lstrip("#")}.png'))
ov = Image.new('RGBA', (len(PLAYV) * 124 + 16, 140), (22, 24, 26, 255))
for i, v in enumerate(PLAYV): ov.alpha_composite(_tile('A-remote@' + v[0], 108), (16 + i * 124, 16))
ov.save(os.path.join(PC, 'overview-108.png'))

# ── 2. 비교 페이지
def tile(key, px, shape='squircle', mono=None):
    """적응형 아이콘: 108 층을 1.5배로 그리고 가운데 72 만 마스크로 보임"""
    if mono:
        inner = mono_svg(key, mono[1]); bg = mono[0]
    else:
        inner = fg_svg(key); bg = NAVY
    return f'<span class="ic {shape}" style="--s:{px}px;background:{bg}">{inner}</span>'
TH = {'light': ('#D9E2FF', '#13264A'), 'dark': ('#2B3953', '#D9E2FF')}
CSS = """
:root{color-scheme:dark}*{box-sizing:border-box}
body{margin:0;background:#0b0c0d;color:#eceae4;font-family:'Inter','Noto Sans KR',sans-serif;line-height:1.5;word-break:keep-all;padding:24px 16px 64px}
.wrap{max-width:1180px;margin:0 auto}h1{font-size:22px;margin:0 0 8px}h2{font-size:19px;margin:0}h3{font-size:15px;margin:24px 0 8px;color:#c2c6bf}
p,li{font-size:15px;color:#c2c6bf;margin:0 0 8px}.mute{color:#9aa098;font-size:14px}
.hold{display:inline-block;margin-left:8px;padding:0 8px;border-radius:999px;background:#4a4d52;color:#e6e6e6;font-size:14px;line-height:22px;vertical-align:2px}
.banner{margin:16px 0 0;padding:16px;border-radius:16px;background:#1d2a1f;color:#cfe8d2;font-size:15px}
.rec{display:inline-block;margin-left:8px;padding:0 8px;border-radius:999px;background:#ff50a0;color:#fff;font-size:14px;line-height:22px;vertical-align:2px}
section{margin-top:40px;padding-top:24px;border-top:1px solid rgba(255,255,255,.12)}
.ic{position:relative;display:inline-block;width:var(--s);height:var(--s);overflow:hidden;flex:0 0 auto}
.ic svg{position:absolute;left:calc(var(--s) * -.25);top:calc(var(--s) * -.25);width:calc(var(--s) * 1.5);height:calc(var(--s) * 1.5)}
.circle{border-radius:50%}.squircle{border-radius:30%}.teardrop{border-radius:50% 50% 10% 50%}
.row{display:flex;flex-wrap:wrap;gap:24px;align-items:flex-end}
.cell{display:flex;flex-direction:column;align-items:center;gap:8px}.cell b{font-size:14px;font-weight:400;color:#9aa098}
.pair{display:grid;grid-template-columns:1fr 1fr;gap:24px}@media (max-width:760px){.pair{grid-template-columns:1fr}}
.card{background:#16181a;border-radius:16px;padding:16px}.card h4{margin:0 0 16px;font-size:15px}
.wall{display:flex;gap:32px;justify-content:center;padding:24px 16px;border-radius:16px}
.wall.light{background:linear-gradient(160deg,#eef2f7,#cfd9e6)}.wall.dark{background:linear-gradient(160deg,#1b2430,#05070a)}
.app{display:flex;flex-direction:column;align-items:center;gap:8px;width:96px}.app span.l{font-size:14px;text-align:center;line-height:18px}
.light .app span.l{color:#111}.dark .app span.l{color:#fff}
.walls{display:grid;grid-template-columns:1fr 1fr;gap:16px}@media (max-width:760px){.walls{grid-template-columns:1fr}}
.inv{display:flex;flex-wrap:wrap;gap:16px}.inv span.sw{display:inline-flex;align-items:center;justify-content:center;width:88px;height:88px;border-radius:16px}
.inv svg{width:72px;height:72px}
.layers{display:flex;flex-wrap:wrap;gap:16px}.layer{display:flex;flex-direction:column;gap:8px;align-items:center}
.layer .bx{position:relative;width:162px;height:162px;background:repeating-conic-gradient(#222 0 25%,#2c2c2c 0 50%) 0 0/16px 16px;border-radius:8px;overflow:hidden}
.layer .bx svg{width:162px;height:162px;display:block}.layer .bx::after{content:'';position:absolute;left:31.5px;top:31.5px;width:99px;height:99px;border-radius:50%;box-shadow:0 0 0 1px #ff50a0}
table{border-collapse:collapse;width:100%;font-size:14px;margin-top:8px}td,th{border-bottom:1px solid rgba(255,255,255,.12);padding:8px;text-align:left;vertical-align:top;color:#c2c6bf}th{color:#eceae4}
code{font-size:14px;color:#9cc2ff}
"""
h = [f'<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Cinema Frame · Cinema Remote 앱 아이콘 후보</title>'
     '<link rel="icon" href="data:image/svg+xml,' + fg_svg('A-frame').replace('"', "'").replace('#', '%23') + '">'
     '<link rel="preconnect" href="https://fonts.googleapis.com"><link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&family=Noto+Sans+KR:wght@400;700&display=swap" rel="stylesheet">'
     f'<style>{CSS}</style></head><body><div class="wrap">']
h.append('<h1>Cinema Frame · Cinema Remote 앱 아이콘</h1>')
h.append('<p class="banner">확정: A (회장님 11-08) — 리모컨 아이콘은 액자 아이콘과 같은 그림에 포스터 가운데 재생 버튼 하나만 더함. 재생 버튼 = P3(남색 원 #080D13 · 진행 바 금색 삼각 #F5D59A). B · C 는 보류. 앱에 넣는 일은 codex(design/icons/README.md).</p>')
h.append('<p>두 앱을 한 가족으로: 바탕은 액자 화면 바탕 <code>#080D13</code>, 표식은 액자 금색 <code>#D3BD92</code> · 진행 바 <code>#F5D59A</code>(MainActivity.java). 리모컨은 같은 그림에 재생 버튼 하나만 더해 구분(B · C 후보는 One UI 파랑 <code>#387AFF</code> 사용).</p>')
h.append('<p class="mute">안드로이드 적응형 아이콘: 108dp 층(가운데 72dp 가 보임 · 안전 영역 지름 66dp) · 배경 층 · 단색(테마 아이콘) 층. 둥근 사각은 One UI 바탕화면 모양을 반지름 30% 로 근사. 테마 아이콘 색은 바탕화면에 따라 바뀌며 여기 색은 예시.</p>')
# 한눈에 비교
h.append('<section><h2>한눈에 비교 — 바탕화면 72px (A 확정 · B · C 보류)</h2><div class="walls">')
for wall in ('light', 'dark'):
    h.append(f'<div class="wall {wall}" style="flex-wrap:wrap">')
    for k in 'ABC':
        for app in ('frame', 'remote'):
            h.append(f'<div class="app">{tile(f"{k}-{app}", 72)}<span class="l">{k} · {APP[app][0].split()[1]}</span></div>')
    h.append('</div>')
h.append('</div></section>')
for k in 'ABC':
    h.append(f'<section id="{k}"><h2>{k} · {CN[k]["name"]}{"<span class=rec>확정</span>" if k == REC else "<span class=hold>보류</span>"}</h2><p style="margin-top:8px">{CN[k]["idea"]}</p>')
    h.append('<div class="pair">')
    for app in ('frame', 'remote'):
        key = f'{k}-{app}'
        h.append(f'<div class="card"><h4>{APP[app][0]} <span class="mute">{APP[app][1]}</span></h4>')
        h.append('<h3 style="margin-top:0">마스크 3종 · 108px</h3><div class="row">' + ''.join(f'<div class="cell">{tile(key, 108, s)}<b>{n}</b></div>' for s, n in (('circle', '원형'), ('squircle', '둥근 사각'), ('teardrop', '물방울'))) + '</div>')
        h.append('<h3>크기 48 · 72 · 108 · 192</h3><div class="row">' + ''.join(f'<div class="cell">{tile(key, px)}<b>{px}</b></div>' for px in (48, 72, 108, 192)) + '</div>')
        h.append('<h3>테마 아이콘(단색) · 라이트 / 다크</h3><div class="row">' + ''.join(f'<div class="cell">{tile(key, 72, "squircle", TH[t])}<b>{"라이트" if t == "light" else "다크"}</b></div>' for t in ('light', 'dark'))
                 + ''.join(f'<div class="cell">{tile(key, 48, "circle", TH[t])}<b>48</b></div>' for t in ('light', 'dark')) + '</div>')
        h.append('<h3>단색 · 반전</h3><div class="inv">' + ''.join(f'<span class="sw" style="background:{b}">{mono_svg(key, c)}</span>' for b, c in (('#FFFFFF', '#000000'), ('#000000', '#FFFFFF'), ('#D3BD92', '#080D13'), ('#080D13', '#D3BD92'))) + '</div>')
        h.append('</div>')
    h.append('</div>')
    h.append('<h3>바탕화면에서 두 앱 나란히</h3><div class="walls">' + ''.join(
        f'<div class="wall {w}">' + ''.join(f'<div class="app">{tile(f"{k}-{a}", 72)}<span class="l">{APP[a][0]}</span></div>' for a in ('frame', 'remote')) + '</div>' for w in ('light', 'dark')) + '</div>')
    h.append('<h3>층 보기 — 분홍 원 = 안전 영역(지름 66dp)</h3><div class="layers">')
    for app in ('frame', 'remote'):
        key = f'{k}-{app}'
        h.append(f'<div class="layer"><div class="bx">{fg_svg(key)}</div><b class="mute">{APP[app][0].split()[1]} 전경</b></div>')
        h.append(f'<div class="layer"><div class="bx">{mono_svg(key)}</div><b class="mute">{APP[app][0].split()[1]} 단색</b></div>')
    h.append(f'<div class="layer"><div class="bx" style="background:{NAVY}"></div><b class="mute">배경 {NAVY}</b></div></div>')
    h.append('</section>')
h.append('<section id="play"><h2>재생 버튼 색 — 리모컨 시안 <span class="rec">P3 확정</span></h2>'
         '<p style="margin-top:8px">액자 아이콘 그림 · 원 크기(r8.5) · 위치(포스터 가운데) · 삼각 모양은 그대로, 원과 삼각 색만 바꿈. 대비는 WCAG 명암비(원 ↔ 포스터 #D3BD92 · 삼각 ↔ 원).</p>'
         '<h3>단색 테마 아이콘 — 모든 시안 공통</h3><p class="mute">테마 아이콘은 색을 쓰지 않고 모양만 쓰므로 8안 모두 같음: 포스터에서 원을 뚫고 안에 삼각 → 48px 에서도 재생 모양이 남음.</p><div class="row">'
         + ''.join(f'<div class="cell">{tile("A-remote", px, sh, TH[t])}<b>{"라이트" if t == "light" else "다크"} {px}</b></div>' for t in ('light', 'dark') for px, sh in ((48, 'circle'), (72, 'squircle'))) + '</div>')
for pid, nm, dc, tc, ring, note in PLAYV:
    key = 'A-remote@' + pid
    h.append(f'<div class="card" style="margin-top:16px"><h4>{pid} · {nm}{" <span class=rec>확정</span>" if pid == "P3" else ""} <span class="mute">원 {dc} · 삼각 {tc}{" · 테두리 #F5D59A" if ring else ""}</span></h4>'
             f'<p class="mute">{note} — 원↔포스터 {_cr(dc, "#D3BD92"):.2f} : 1 · 삼각↔원 {_cr(tc, dc):.2f} : 1</p>'
             '<div class="row">' + f'<div class="cell">{tile("A-frame", 72)}<b>액자(그대로)</b></div>'
             + ''.join(f'<div class="cell">{tile(key, px)}<b>{px}</b></div>' for px in (48, 108, 192)) + '</div>'
             '<div class="walls" style="margin-top:16px">' + ''.join(f'<div class="wall {w}">' + ''.join(f'<div class="app">{tile(k2, 72)}<span class="l">{lb}</span></div>' for k2, lb in (('A-frame', 'Cinema Frame'), (key, 'Cinema Remote'))) + '</div>' for w in ('light', 'dark')) + '</div></div>')
h.append('</section>')
h.append('<section><h2>근거 · 시험</h2><table><tr><th>항목</th><th>값</th><th>근거</th></tr>'
         '<tr><td>바탕</td><td>#080D13</td><td>액자 화면 바탕 MainActivity.java 111행</td></tr>'
         '<tr><td>금색 · 밝은 금색</td><td>#D3BD92 · #F5D59A</td><td>액자 "C I N E M A F R A M E" 글자 41행 · 진행 바 103행</td></tr>'
         '<tr><td>진행 바 남은 쪽</td><td>#5B5240</td><td>파생 — 금색을 바탕 쪽으로 낮춤(A 만)</td></tr>'
         '<tr><td>파랑</td><td>#387AFF</td><td>리모컨 One UI 9 시안 primary — B · C 후보와 P0 참고에만(확정 A 에는 안 씀)</td></tr>'
         '<tr><td>안전 영역</td><td>전경이 지름 66dp 안</td><td>렌더 픽셀 실측: 6개 모두 밖 0px</td></tr>'
         '<tr><td>A 리모컨 = 액자 + 재생</td><td>포스터 가운데 (54,48) · 원 r8.5 #080D13 · 삼각 #F5D59A (P3)</td><td>216px 렌더 비교: 재생 버튼 원 밖 픽셀 차이 0 (액자 그림과 완전히 같음)</td></tr>'
         '<tr><td>A 리모컨 단색</td><td>포스터에서 원을 뚫고 안에 삼각</td><td>48px 테마 아이콘 라이트 · 다크 · 흑백 반전에서 재생 모양 남음(삼각 구멍만 낸 안은 48px 에서 너무 작아 뺌)</td></tr>'
         '<tr><td>작은 크기 · 단색 · 반전</td><td>48px · 단색 4종 · 테마 아이콘 라이트/다크</td><td>이 페이지에 실제 크기로 그림</td></tr></table>'
         '<p class="mute" style="margin-top:16px">원본 SVG · 밀도별 PNG · 넣는 방법: GitHub happy4ed/cinema-frame · ux/settings-progress · design/icons/README.md</p></section>')
h.append('</div></body></html>')
html = ''.join(h)
open(os.path.join(OUT, 'index.html'), 'w', encoding='utf-8').write(html)
print('ok', sum(len(fs) for _, _, fs in os.walk(OUT)), 'files')
