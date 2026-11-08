#!/usr/bin/env python3
"""Cinema Frame / Cinema Remote 적응형 아이콘 후보 — 108 단위 캔버스(안전 영역 지름 66), 층별 SVG 생성."""
import math, os, json
OUT = os.path.dirname(os.path.abspath(__file__))
NAVY = '#080D13'   # 액자 화면 바탕 (MainActivity 111행)
GOLD = '#D3BD92'   # 액자 브랜드 글자 (41·48행)
LIGHT = '#F5D59A'  # 액자 진행 바 (103행)
DIM = '#5B5240'    # 진행 바 남은 쪽(금색을 바탕 쪽으로 낮춘 값) — 파생
BLUE = '#387AFF'   # 리모컨 One UI 9 시안 primary
WHITE = '#FCFCFF'

def f(x): return f'{x:.2f}'.rstrip('0').rstrip('.')
def rrect(x, y, w, h, r):
    return (f'M{f(x+r)} {f(y)}H{f(x+w-r)}A{f(r)} {f(r)} 0 0 1 {f(x+w)} {f(y+r)}V{f(y+h-r)}A{f(r)} {f(r)} 0 0 1 {f(x+w-r)} {f(y+h)}'
            f'H{f(x+r)}A{f(r)} {f(r)} 0 0 1 {f(x)} {f(y+h-r)}V{f(y+r)}A{f(r)} {f(r)} 0 0 1 {f(x+r)} {f(y)}Z')
def circle(cx, cy, r):
    return f'M{f(cx-r)} {f(cy)}A{f(r)} {f(r)} 0 1 0 {f(cx+r)} {f(cy)}A{f(r)} {f(r)} 0 1 0 {f(cx-r)} {f(cy)}Z'
def ring_c(cx, cy, r_out, r_in, gap_deg):
    """오른쪽이 열린 C — 열림 각 ±gap/2, 끝은 반지름 방향으로 자름"""
    a0 = math.radians(gap_deg / 2); a1 = math.radians(360 - gap_deg / 2)
    p = lambda r, a: (cx + r * math.cos(a), cy - r * math.sin(a))
    o0, o1, i1, i0 = p(r_out, a0), p(r_out, a1), p(r_in, a1), p(r_in, a0)
    return (f'M{f(o0[0])} {f(o0[1])}A{f(r_out)} {f(r_out)} 0 1 0 {f(o1[0])} {f(o1[1])}'
            f'L{f(i1[0])} {f(i1[1])}A{f(r_in)} {f(r_in)} 0 1 1 {f(i0[0])} {f(i0[1])}Z')
def note(sx=1.0, ox=0.0, oy=0.0):
    """8분음표 — 머리(기운 타원) + 기둥 + 깃발. 기준 좌표는 108 캔버스 중심 부근"""
    T = lambda x, y: (54 + (x - 54) * sx + ox, 54 + (y - 54) * sx + oy)
    # 머리: 타원을 -20° 기울인 경로(베지어 근사)
    cx, cy, rx, ry, rot = 49.6, 64.2, 6.6, 5.2, math.radians(-20)
    pts = []
    for k in range(4):
        a = k * math.pi / 2
        pts.append(a)
    kappa = 0.5523
    def E(t):  # 타원 위 점
        x, y = rx * math.cos(t), ry * math.sin(t)
        return (cx + x * math.cos(rot) - y * math.sin(rot), cy + x * math.sin(rot) + y * math.cos(rot))
    def D(t):  # 접선
        x, y = -rx * math.sin(t), ry * math.cos(t)
        return (x * math.cos(rot) - y * math.sin(rot), x * math.sin(rot) + y * math.cos(rot))
    seg = []
    for k in range(4):
        t0, t1 = k * math.pi / 2, (k + 1) * math.pi / 2
        p0, p1 = E(t0), E(t1); d0, d1 = D(t0), D(t1)
        c0 = (p0[0] + d0[0] * kappa, p0[1] + d0[1] * kappa); c1 = (p1[0] - d1[0] * kappa, p1[1] - d1[1] * kappa)
        seg.append((p0, c0, c1, p1))
    h = 'M' + ' '.join(map(f, T(*seg[0][0]))) + ''.join('C' + ' '.join(' '.join(map(f, T(*q))) for q in s[1:]) for s in seg) + 'Z'
    # 기둥: 머리 오른쪽 끝에서 위로
    sx0, sx1, y0, y1 = 54.6, 57.6, 38.0, 62.6
    stem = 'M' + ' '.join(map(f, T(sx0, y1))) + 'L' + ' '.join(map(f, T(sx0, y0))) + 'L' + ' '.join(map(f, T(sx1, y0))) + 'L' + ' '.join(map(f, T(sx1, y1))) + 'Z'
    flag = ('M' + ' '.join(map(f, T(57.6, 38.0))) + 'C' + ' '.join(map(f, T(57.6, 43.5) + T(65.0, 45.0) + T(65.0, 52.0)))
            + 'C' + ' '.join(map(f, T(65.0, 54.6) + T(64.2, 56.6) + T(63.2, 58.0)))
            + 'C' + ' '.join(map(f, T(63.8, 52.6) + T(60.8, 49.6) + T(57.6, 48.6))) + 'Z')
    return h + stem + flag

C = {}
# ── A 포스터와 진행 바: 액자 실제 화면(세로 포스터 + 아래 금색 진행 바)
# 액자 테두리 + 매트 틈 2.5 + 포스터 판 (작은 크기에서 틈 ≥ 1.6px)
A_frame = rrect(38, 26, 32, 44, 3) + rrect(41, 29, 26, 38, 1.2)
A_poster = A_frame + rrect(43.5, 31.5, 21, 33, 0.6)
A_bar_done = rrect(38, 76, 22, 4, 2)
A_bar_rest = rrect(63, 76.5, 7, 3, 1.5)
# 리모컨: 같은 포스터 + One UI 파랑 원 버튼(▶). 포스터 오른쪽 아래를 버튼 둘레 r15 로 파서 띄움
# 포스터 x33–57 · y30–64, 버튼 중심 (63,65) r11.5, 둘레 틈 r14.5 — 묶음 가운데 ≈ (54,53)
cx, cy, R, gap = 63, 65, 11.5, 14.5
yi = cy - math.sqrt(gap**2 - (cx - 57)**2)
xi = cx - math.sqrt(gap**2 - (cy - 64)**2)
A2_poster = f'M36 30H54A3 3 0 0 1 57 33V{f(yi)}A{gap} {gap} 0 0 0 {f(xi)} 64H36A3 3 0 0 1 33 61V33A3 3 0 0 1 36 30Z'
A2_btn = circle(cx, cy, R)
A2_tri = 'M59.8 59.5L59.8 70.5L69.3 65Z'
# 확정(회장님 11-08): 리모컨 = 액자 아이콘 그대로 + 포스터 가운데 재생 버튼 하나
A_inner = rrect(43.5, 31.5, 21, 33, 0.6)
PCX, PCY, PR = 54, 48, 8.5          # 포스터 판 가운데 · 파랑 원
_h = 8.6; _w = _h * 0.866; _x0 = PCX - _w / 3
A_tri = f'M{f(_x0)} {f(PCY - _h/2)}L{f(_x0)} {f(PCY + _h/2)}L{f(_x0 + _w)} {f(PCY)}Z'   # 무게중심 = 원 중심
A_play = circle(PCX, PCY, PR)
C['A'] = {
 'name': '포스터와 진행 바', 'idea': '액자 화면 그대로 — 액자 테두리 안 세로 포스터와 그 아래 금색 진행 바. 리모컨은 같은 그림의 포스터 가운데에 One UI 파랑 재생 버튼 하나만 더함.',
 'frame': {'fg': [(A_frame, GOLD, 'evenodd'), (A_inner, GOLD, ''), (A_bar_done, LIGHT, ''), (A_bar_rest, DIM, '')], 'mono': [A_poster, A_bar_done, A_bar_rest]},
 # 단색: 포스터에서 원을 뚫고 그 안에 삼각을 채움 — 48px 테마 아이콘에서도 재생 모양이 남음(삼각 구멍만은 너무 작았음)
 'remote': {'fg': [(A_frame, GOLD, 'evenodd'), (A_inner, GOLD, ''), (A_bar_done, LIGHT, ''), (A_bar_rest, DIM, ''), (A_play, BLUE, ''), (A_tri, WHITE, '')],
            'mono': [A_poster, A_play, A_tri, A_bar_done, A_bar_rest]},
}
# ── B 음표 액자: 세로 액자 테두리 안 8분음표. 리모컨은 테두리가 파랑 휴대폰
B_frame = rrect(37, 28, 34, 52, 4) + rrect(41.5, 32.5, 25, 43, 1.5)
B_note = note(1.0, 0.6, 0.4)
B2_phone = rrect(39.5, 25, 29, 58, 8) + rrect(43.5, 29, 21, 50, 4.5)
B2_note = note(0.86, 0.4, 0.6)
C['B'] = {
 'name': '음표 액자', 'idea': '세로 액자 안의 음표 — 영화 포스터 자리에 음악이 걸린다. 리모컨은 같은 음표가 파랑 휴대폰 안에.',
 'frame': {'fg': [(B_frame, GOLD, 'evenodd'), (B_note, LIGHT, '')], 'mono': [B_frame, B_note]},
 'remote': {'fg': [(B2_phone, BLUE, 'evenodd'), (B2_note, LIGHT, '')], 'mono': [B2_phone, B2_note]},
}
# ── C 필름 C: Cinema 의 C 를 필름 구멍 낸 고리로. 리모컨은 C 열린 쪽으로 신호 두 줄(파랑)
def holes(cx, cy, r, angles, s=5.6):
    out = ''
    for a in angles:
        x, y = cx + r * math.cos(math.radians(a)), cy - r * math.sin(math.radians(a))
        out += rrect(x - s / 2, y - s / 2, s, s, 1.2)
    return out
# 열림 ±45°(깨끗한 각) · 왼쪽으로 무거운 C 를 2 오른쪽으로(광학 중심)
C_ring = ring_c(56, 54, 27, 15, 90) + holes(56, 54, 21, [128, 180, 232])
C2_ring = ring_c(50.5, 54, 23.5, 13, 90) + holes(50.5, 54, 18.25, [128, 180, 232], 4.9)
def arc(cx, cy, r, a0, a1, w):
    """둥근 끝 호(선 두께 w)를 채운 경로로"""
    ro, ri = r + w / 2, r - w / 2
    p = lambda rr, a: (cx + rr * math.cos(math.radians(a)), cy - rr * math.sin(math.radians(a)))
    o0, o1, i1, i0 = p(ro, a0), p(ro, a1), p(ri, a1), p(ri, a0)
    return (f'M{f(o0[0])} {f(o0[1])}A{f(ro)} {f(ro)} 0 0 0 {f(o1[0])} {f(o1[1])}A{f(w/2)} {f(w/2)} 0 0 0 {f(i1[0])} {f(i1[1])}'
            f'A{f(ri)} {f(ri)} 0 0 1 {f(i0[0])} {f(i0[1])}A{f(w/2)} {f(w/2)} 0 0 0 {f(o0[0])} {f(o0[1])}Z')
C2_sig = arc(58.5, 54, 12, -38, 38, 4.2) + arc(58.5, 54, 20, -34, 34, 4.2)
C['C'] = {
 'name': '필름 C', 'idea': 'Cinema 의 C 를 필름 구멍 셋 낸 고리로. 리모컨은 같은 C 의 열린 쪽으로 파랑 신호 두 줄.',
 'frame': {'fg': [(C_ring, GOLD, 'evenodd')], 'mono': [C_ring]},
 'remote': {'fg': [(C2_ring, GOLD, 'evenodd'), (C2_sig, BLUE, '')], 'mono': [C2_ring + C2_sig]},
}
os.makedirs(os.path.join(OUT, 'svg'), exist_ok=True)
def svg(paths, bg=None, mono=None, size=None):
    s = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"{f" width={chr(34)}{size}{chr(34)} height={chr(34)}{size}{chr(34)}" if size else ""}>'
    if bg: s += f'<rect width="108" height="108" fill="{bg}"/>'
    if mono:
        s += f'<path fill="{mono}" fill-rule="evenodd" d="{"".join(paths)}"/>'
    else:
        for d, col, rule in paths: s += f'<path fill="{col}"{" fill-rule=" + chr(34) + rule + chr(34) if rule else ""} d="{d}"/>'
    return s + '</svg>'
spec = {}
for k, c in C.items():
    for app in ('frame', 'remote'):
        fg = svg(c[app]['fg']); full = svg(c[app]['fg'], NAVY); mono = svg(c[app]['mono'], mono='#000')
        for nm, body in (('fg', fg), ('full', full), ('mono', mono)):
            open(os.path.join(OUT, 'svg', f'{k}-{app}-{nm}.svg'), 'w').write(body)
        spec[f'{k}-{app}'] = {'fg': c[app]['fg'], 'mono': ''.join(c[app]['mono'])}
json.dump({'C': {k: {'name': v['name'], 'idea': v['idea']} for k, v in C.items()}, 'spec': spec, 'NAVY': NAVY}, open(os.path.join(OUT, 'spec.json'), 'w'), ensure_ascii=False)
print('ok', sorted(os.listdir(os.path.join(OUT, 'svg'))))
