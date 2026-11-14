# 데모 주행(app/src/main/assets/demo/jeju-drive.json)과 예시 구역(app/src/main/assets/zones/jeju-sample.json)을 만듭니다.
# 사용: python tools/gen_demo.py app/src/main/assets
import json, math, sys
out_dir = sys.argv[1]
A=(33.4880,126.4980); B=(33.4985,126.4980); C=(33.4985,126.5030)
M_LAT=111195.0
M_LNG=111195.0*math.cos(math.radians(33.4985))
north=(B[0]-A[0])*M_LAT
def pos(s):
    if s<=north: return (A[0]+s/M_LAT, A[1])
    return (B[0], B[1]+(s-north)/M_LNG)
def s_of_lat(lat): return (lat-A[0])*M_LAT
school_c=s_of_lat(33.4945)
park_c=north+(126.5020-B[1])*M_LNG
def target(s):
    if s<30: return 30
    if s<450: return 35
    if s<640: return 40           # 어린이보호구역 진입 후 과속
    if s<900: return 28
    if s<1095: return 35
    if s<1210: return 13          # 우회전: 멈추지 않고 서행
    if s<park_c-35: return 30
    return 0
frames=[{"t":0,"gear":"P","speed":0,"lat":A[0],"lng":A[1]}]
t=2; s=0.0; v=0.0; turn=None
frames.append({"t":t,"gear":"D","speed":0})
while True:
    tgt=target(s)
    v = min(tgt, v+8) if v<tgt else max(tgt, v-9)
    if s>=park_c-35 and v<=0.5:
        v=0
    s+=v/3.6; t+=1
    lat,lng=pos(s)
    f={"t":t,"speed":round(v,1),"lat":round(lat,6),"lng":round(lng,6)}
    if turn is None and s>=1110: f["turn"]="RIGHT"; turn="on"
    if turn=="on" and s>=north+35: f["turn"]="OFF"; turn="off"
    frames.append(f)
    if v==0 and s>park_c-35: break
    if t>600: raise SystemExit("runaway")
t+=1; frames.append({"t":t,"gear":"P","speed":0})
for k in range(5,85,5): frames.append({"t":t+k})
end_s=s
demo={"_comment":"예시 주행(실제 단속 위치 아님). tools/gen_demo.py로 만든 파일이며 t는 초, 시작은 월요일 08:00 KST.",
      "start":"2026-11-15T23:00:00Z","frames":frames}
json.dump(demo,open(out_dir+"/demo/jeju-drive.json","w",encoding="utf-8"),ensure_ascii=False,separators=(",",":"))
zones={"version":1,"sample":True,
 "_comment":"예시 구역입니다. 실제 단속 위치·운영 시간이 아닙니다. 실제 데이터 형식과 변환은 docs/zone-data.md 참고.",
 "zones":[
  {"id":"demo-bus-1","kind":"BUS_LANE","name":"예시 버스전용차로","corridor":{"halfWidthM":12,"points":[[33.4885,126.4980],[33.4920,126.4980]]},
   "schedule":[{"days":"MON-FRI","start":"07:00","end":"09:00"},{"days":"MON-FRI","start":"17:00","end":"19:00"}]},
  {"id":"demo-school-1","kind":"SCHOOL_ZONE","name":"예시 어린이보호구역","limitKmh":30,"circle":{"lat":33.4945,"lng":126.4980,"radiusM":150}},
  {"id":"demo-right-1","kind":"RIGHT_TURN","name":"예시 우회전 교차로","rightTurnSignal":True,"circle":{"lat":33.4985,"lng":126.4980,"radiusM":20}},
  {"id":"demo-cctv-1","kind":"PARKING_ENFORCEMENT","name":"예시 주정차 단속","graceMinutes":2,"circle":{"lat":33.4985,"lng":126.5020,"radiusM":40},
   "schedule":[{"days":"ALL","start":"07:00","end":"22:00"}]}
 ]}
json.dump(zones,open(out_dir+"/zones/jeju-sample.json","w",encoding="utf-8"),ensure_ascii=False,indent=1)
print("frames",len(frames),"duration",frames[-1]["t"],"end_s",round(end_s),"park_c",round(park_c),"school_c",round(school_c),"north",round(north))
