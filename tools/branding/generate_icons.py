"""Export the selected A / 文 design to native vectors and SVG; no font dependencies."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'app/src/main/res'
BRAND = ROOT / 'docs/brand'
BACKGROUND = '#B9E4FB'
BACK = 'M331,269 H621 C711,269 765,330 765,419 V507 C765,556 738,582 694,582 C636,581 603,601 570,642 C543,673 540,722 501,752 C480,770 453,776 422,776 H341 Q332,775 325,781 L243,849 Q220,864 220,840 V778 Q220,768 210,762 C175,743 159,708 159,661 V442 C159,343 227,269 331,269 Z'
FRONT = 'M810,492 H898 Q916,491 928,494 C906,528 903,568 918,616 Q920,627 930,624 C977,614 1023,622 1067,576 Q1098,615 1098,680 V867 C1098,929 1069,976 1018,1004 Q1011,1008 1012,1018 V1072 Q1013,1099 991,1085 L903,1025 Q898,1021 888,1022 H599 C498,1022 431,951 431,855 V819 C507,818 551,788 576,724 C597,665 630,625 694,625 C756,628 797,596 807,550 Q812,528 810,492 Z'
LEAF = 'M936,596 C922,510 974,452 1082,446 C1086,553 1052,590 957,593 C972,565 994,536 1021,511 C981,530 954,562 936,596 Z'
LETTER_A = 'M397,371 H457 Q460,371 462,377 L568,642 L543,668 H515 Q511,668 510,663 L487,601 H366 L343,663 Q342,668 338,668 H281 Q276,668 278,662 L391,377 Q393,371 397,371 Z M426,438 L384,551 H468 Z'
LETTER_WEN = 'M811,655 H846 Q850,655 850,659 V701 H978 Q982,701 982,705 V736 Q982,741 978,741 H930 C914,785 891,822 861,854 C897,877 936,895 978,905 Q983,906 981,911 L958,946 Q956,951 951,949 C905,933 865,912 827,883 C790,911 745,934 698,949 Q693,951 691,946 L674,913 Q672,907 678,905 C721,893 763,875 796,854 C767,824 745,787 729,741 H679 Q675,741 675,737 V705 Q675,701 680,701 H807 V659 Q807,655 811,655 Z M774,741 C788,775 805,803 828,826 C850,801 868,773 882,741 Z'
PATHS = {'back': BACK, 'front': FRONT, 'leaf': LEAF, 'a': LETTER_A, 'wen': LETTER_WEN}
COLORS = {'back': '#045674', 'front': '#28AAA0', 'leaf': '#087582', 'a': '#F7FAFC', 'wen': '#F7FAFC'}
GRADIENTS = {'back': ('#075D7D', '#034A66'), 'front': ('#32B5A8', '#22A19D'), 'leaf': ('#148894', '#00626E')}

def vector(kinds, *, adaptive=False, background=None, monochrome=False, mask=False, monet=False):
    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="1254" android:viewportHeight="1254">']
    if background:
        lines.append(f'    <path android:fillColor="{BACKGROUND}" android:pathData="{background}" />')
    if adaptive:
        lines.append('    <group android:pivotX="627" android:pivotY="650" android:scaleX="0.67344" android:scaleY="0.67344" android:translateY="-23">')
    elif background and background.startswith('M627'):
        lines.append('    <group android:pivotX="627" android:pivotY="627" android:scaleX="0.9" android:scaleY="0.9">')
    else:
        lines.append('    <group>')
    if monochrome:
        # Cut letters out of each bubble to preserve legibility in themed launchers.
        lines.append(f'        <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{BACK} {LETTER_A} {FRONT} {LETTER_WEN} {LEAF}" />')
    else:
        for kind in kinds:
            role = {'back': 'primary', 'front': 'tertiary', 'leaf': 'primary', 'a': 'on_primary', 'wen': 'on_tertiary'}[kind]
            color = '#FFFFFFFF' if mask else f'@color/launcher_monet_{role}' if monet else COLORS[kind]
            attrs = f'android:fillColor="{color}" android:fillType="evenOdd" android:pathData="{PATHS[kind]}"'
            if not mask and not monet and kind in GRADIENTS:
                attrs = attrs.replace(f'android:fillColor="{color}" ', '')
                first, last = GRADIENTS[kind]
                lines.extend([f'        <path {attrs}>', f'            <aapt:attr name="android:fillColor"><gradient android:type="linear" android:startX="159" android:startY="269" android:endX="1098" android:endY="1085" android:startColor="{first}" android:endColor="{last}" /></aapt:attr>', '        </path>'])
            else:
                lines.append(f'        <path {attrs} />')
    return '\n'.join(lines + ['    </group>', '</vector>', ''])

def write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf8')

rounded = 'M320,0 H934 Q1254,0 1254,320 V934 Q1254,1254 934,1254 H320 Q0,1254 0,934 V320 Q0,0 320,0 Z'
write(RES / 'drawable/teawords_mark.xml', vector(PATHS, adaptive=True))
write(RES / 'drawable/teawords_mark_monet.xml', vector(PATHS, adaptive=True, monet=True))
write(RES / 'drawable/teawords_monochrome.xml', vector([], adaptive=True, monochrome=True))
write(RES / 'drawable/teawords_logo.xml', vector(PATHS, background=rounded))
write(RES / 'drawable/teawords_logo_round.xml', vector(PATHS, background='M627,0 A627,627 0,1 1,627,1254 A627,627 0,1 1,627,0 Z'))
for filename, kind in [('symbol', 'back'), ('accent', 'front'), ('leaf', 'leaf'), ('letter_a', 'a'), ('letter_wen', 'wen')]:
    write(RES / f'drawable/teawords_{filename}.xml', vector([kind], mask=True))
def color_resources(colors):
    return '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + ''.join(f'    <color name="{name}">{value}</color>\n' for name, value in colors.items()) + '</resources>\n'

fallback = {'background': BACKGROUND, 'primary': COLORS['back'], 'tertiary': COLORS['front'], 'on_primary': COLORS['a'], 'on_tertiary': COLORS['wen']}
write(RES / 'values/icon_colors.xml', color_resources({'launcher_background': BACKGROUND, **{f'launcher_monet_{role}': value for role, value in fallback.items()}}))
for dark in (False, True):
    night = '-night' if dark else ''
    tones = {'background': 'accent1_700' if dark else 'accent1_100', 'primary': 'accent1_200' if dark else 'accent1_600',
             'tertiary': 'accent3_200' if dark else 'accent3_600', 'on_primary': 'accent1_800' if dark else 'accent1_0',
             'on_tertiary': 'accent3_800' if dark else 'accent3_0'}
    write(RES / f'values{night}-v31/icon_monet_colors.xml', color_resources({f'launcher_monet_{role}': f'@android:color/system_{tone}' for role, tone in tones.items()}))
    mode = 'dark' if dark else 'light'
    roles = {'background': 'primary_container', 'primary': 'primary', 'tertiary': 'tertiary', 'on_primary': 'on_primary', 'on_tertiary': 'on_tertiary'}
    write(RES / f'values{night}-v34/icon_monet_colors.xml', color_resources({f'launcher_monet_{role}': f'@android:color/system_{native}_{mode}' for role, native in roles.items()}))
write(RES / 'mipmap-anydpi/ic_launcher_monet.xml', '<?xml version="1.0" encoding="utf-8"?>\n<layer-list xmlns:android="http://schemas.android.com/apk/res/android"><item android:drawable="@drawable/teawords_logo" /></layer-list>\n')
for version in (26, 33):
    mono = '\n    <monochrome android:drawable="@drawable/teawords_monochrome" />' if version >= 33 else ''
    write(RES / f'mipmap-anydpi-v{version}/ic_launcher_monet.xml', f'<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/launcher_monet_background" />\n    <foreground android:drawable="@drawable/teawords_mark_monet" />{mono}\n</adaptive-icon>\n')
defs = '\n'.join(f'<linearGradient id="{kind}" x1="0" y1="0" x2="1" y2="1"><stop stop-color="{first}"/><stop offset="1" stop-color="{last}"/></linearGradient>' for kind, (first, last) in GRADIENTS.items())
shapes = '\n'.join(f'<path d="{path}" fill="{("url(#" + kind + ")") if kind in GRADIENTS else COLORS[kind]}" fill-rule="evenodd"/>' for kind, path in PATHS.items())
write(BRAND / 'teawords-logo.svg', f'''<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 1254 1254">
<title>teaWords — English / Chinese speech bubbles and tea leaf</title>
<defs>{defs}</defs>
<rect width="1254" height="1254" rx="320" fill="{BACKGROUND}"/>
{shapes}
</svg>
''')
print('Generated selected A / 文 speech-bubble icon resources')
