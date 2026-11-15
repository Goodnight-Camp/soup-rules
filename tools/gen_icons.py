# 규칙 아이콘 벡터(app/src/main/res/drawable/ic_rule_*.xml)를 만듭니다.
# 사용: python tools/gen_icons.py app/src/main/res/drawable
import sys, os
out = sys.argv[1]
os.makedirs(out, exist_ok=True)
W = "#FFFFFFFF"

def stroke(d, w=2.0):
    return f'  <path android:pathData="{d}" android:strokeColor="{W}" android:strokeWidth="{w}" android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000"/>'
def fill(d):
    return f'  <path android:pathData="{d}" android:fillColor="{W}"/>'
def circle(cx, cy, r):
    return f"M{cx-r},{cy}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0"

BUS = "M5,3h14a2,2 0 0 1 2,2v11a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-11a2,2 0 0 1 2,-2z"
icons = {
 # 어린이보호구역: 삼각 표지 + 두 아이
 "school": [stroke("M12,2.8L21.5,20.2H2.5Z", 1.8), fill(circle(9.6,10.6,1.3)), stroke("M9.6,12.3V15.8M9.6,13.6L8.2,14.8M9.6,15.8L8.6,17.8M9.6,15.8L10.6,17.8", 1.3),
            fill(circle(14.4,11.8,1.1)), stroke("M14.4,13.3V16.3M14.4,14.4L15.6,15.4M14.4,16.3L13.5,18M14.4,16.3L15.3,18", 1.3)],
 # 감속: 속도계
 "slow_down": [stroke("M3.5,17A8.5,8.5 0 1,1 20.5,17"), stroke("M12,16.5L8,10.5", 2.2), fill(circle(12,16.5,1.6))],
 "right_turn": [stroke("M7,21V12a4,4 0 0 1 4,-4h5.5", 2.4), fill("M15.5,4L21,8L15.5,12Z")],
 "left_turn": [stroke("M17,21V12a4,4 0 0 0 -4,-4h-5.5", 2.4), fill("M8.5,4L3,8L8.5,12Z")],
 # 횡단보도: 줄무늬
 "crosswalk": [fill("M3,4h3v16h-3zM8,4h3v16h-3zM13,4h3v16h-3zM18,4h3v16h-3z")],
 "bus_lane": [stroke(BUS, 1.8), stroke("M5,6.5h14v4.5h-14z", 1.4), fill(circle(7.5,20,1.6)), fill(circle(16.5,20,1.6))],
 # 통학버스: 버스 + 깜빡이는 등
 "school_bus": [stroke(BUS, 1.8), stroke("M5,6.5h14v4.5h-14z", 1.4), fill(circle(7.5,20,1.6)), fill(circle(16.5,20,1.6)),
                stroke("M1.5,1.5L3.5,3.5M22.5,1.5L20.5,3.5", 1.4), fill(circle(6.5,14,1)), fill(circle(17.5,14,1))],
 # 주정차 금지: 원 + X
 "no_stopping": [stroke("M2.5,12a9.5,9.5 0 1,0 19,0a9.5,9.5 0 1,0 -19,0"), stroke("M5.4,5.4L18.6,18.6M18.6,5.4L5.4,18.6")],
 # 단속 카메라
 "parking_camera": [stroke("M3,7.5h12.5v9h-12.5z", 1.8), stroke("M15.5,10.5L21,7.5V16.5L15.5,13.5", 1.8), stroke("M7,16.5V20.5H12", 1.8), fill(circle(7,11,1.3))],
 # 안전띠: 앉은 사람 + 대각선 띠
 "seatbelt": [fill(circle(12,5,2.3)), stroke("M7.5,21V12.5a4.5,4.5 0 0 1 9,0V21", 2.0), stroke("M8.3,10.8L15.7,19.5", 2.4)],
 # 회전교차로: 반시계 방향 화살표 원
 "roundabout": [stroke("M20,12A8,8 0 1,0 12,20", 2.2), fill("M11,16.2L15.6,20L11,23.8Z"), fill(circle(12,12,2.2))],
 # 역방향(진입 금지)
 "wrong_way": [stroke("M2.5,12a9.5,9.5 0 1,0 19,0a9.5,9.5 0 1,0 -19,0"), stroke("M6.5,12H17.5", 3.2)],
 # 방향지시등: ◀ ▶
 "turn_signal": [fill("M2,12L9,5.5V18.5Z"), fill("M22,12L15,5.5V18.5Z")],
 # 요금소: 차단봉 + 하이패스 카드
 "toll": [stroke("M4,21V7", 2.6), stroke("M4,8.5H21", 2.2), stroke("M8,8.5V11M12,8.5V11M16,8.5V11", 1.2), stroke("M12.5,14.5h8v5h-8z", 1.6)],
}
for name, paths in icons.items():
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- 규칙 아이콘(흰색 단색). 화면에서는 심각도 색으로 칠하고, 알림 작은 아이콘으로도 씁니다. -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
           + "\n".join(paths) + "\n</vector>\n")
    open(f"{out}/ic_rule_{name}.xml", "w", encoding="utf-8").write(xml)
print(len(icons), "icons")
