from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.pens.recordingPen import RecordingPen
import json, hashlib, zipfile, sys
root=Path(__file__).resolve().parents[1]
path=root/'app/src/main/assets/fonts/PingFangSC-VF.ttf'
font=TTFont(path)
assert font['fvar'].axes[0].axisTag=='wght'
assert font['fvar'].axes[0].minValue==100 and font['fvar'].axes[0].maxValue==900
assert len(font['fvar'].axes)==1
cmap=font.getBestCmap()
needed='0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz年月日星期周一二三四五六上午下午凌晨早晨中午晚上:/.-'
assert all(ord(c) in cmap for c in needed)
assert ord('龘') not in cmap
outlines={}
for char in '05GK上午':
    values=[]
    for weight in (100,500,900):
        pen=RecordingPen();font.getGlyphSet(location={'wght':weight})[cmap[ord(char)]].draw(pen)
        values.append(hashlib.sha256(repr(pen.value).encode()).hexdigest())
    assert len(set(values))==3,(char,values)
    outlines[char]=values
report={'font_bytes':path.stat().st_size,'characters':len(cmap),'weight_axis':[100,900],
        'outline_variations_verified':list(outlines),'font_sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
if len(sys.argv)>1:
  apk=Path(sys.argv[1])
  with zipfile.ZipFile(apk) as package:
    embedded=package.read('assets/fonts/PingFangSC-VF.ttf')
    assert embedded==path.read_bytes()
    assert package.getinfo('assets/fonts/PingFangSC-VF.ttf').compress_type==zipfile.ZIP_STORED
    assert b'TextControls' in package.read('classes.dex')
    assert b'StatusBarModule' in package.read('classes.dex')
    assert package.read('META-INF/xposed/java_init.list').strip()==b'dev.puitheme.StatusBarModule'
    assert package.read('META-INF/xposed/scope.list').strip()==b'com.android.systemui'
  report.update(apk_bytes=apk.stat().st_size,apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest())
print(json.dumps(report,ensure_ascii=False))
