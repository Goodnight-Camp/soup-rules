package com.goodnight.souprules.core

/** 안내 제목(2~4단어). 운전 중 한눈에 읽히도록 짧게 씁니다. [Strings]가 기본 표에 합칩니다. */
internal object HeadlineStrings {
 private val keys=listOf(Msg.H_RIGHT_STOP,Msg.H_RIGHT_ARROW,Msg.H_CROSSWALK,Msg.H_STOP_CROSSWALK,Msg.H_SCHOOL,Msg.H_SLOW_DOWN,Msg.H_SCHOOL_BUS,
  Msg.H_BUS_LANE,Msg.H_NO_STOP,Msg.H_PARKING_CAMERA,Msg.H_PARKING_RULES,Msg.H_SIGNAL,Msg.H_SEATBELT,Msg.H_ROUNDABOUT,Msg.H_WRONG_WAY,
  Msg.H_LEFT_ARROW,Msg.H_UNPROTECTED_LEFT,Msg.H_TOLL)

 private fun row(vararg s:String):Map<Msg,String> { require(s.size==keys.size) { "제목 ${s.size}개 ≠ ${keys.size}개" }; return keys.zip(s).toMap() }

 val table:Map<Lang,Map<Msg,String>> = mapOf(
  Lang.EN to row("Right turn: stop first","Right on green arrow only","Crosswalk ahead","Stop at the crosswalk","School zone {limit}","Slow down to {limit}","School bus: stop",
   "Bus lane in use","No stopping here","Parking camera","Parking rules","Use your turn signal","Fasten seatbelt","Roundabout: counter-clockwise","Wrong way!",
   "Left on green arrow only","Unprotected left","Toll gate: pick your lane"),
  Lang.KO to row("우회전: 먼저 멈춤","초록 화살표에만 우회전","횡단보도 주의","횡단보도 앞 정지","어린이보호구역 {limit}","{limit}km/h로 감속","통학버스: 정지",
   "버스전용차로 운영 중","정차 금지","주정차 단속 카메라","주차 규칙","방향지시등 켜기","안전띠 착용","회전교차로: 반시계 방향","역방향!",
   "초록 화살표에만 좌회전","비보호 좌회전","요금소: 차로 확인"),
  Lang.JA to row("右折：まず停止","右折は青矢印のみ","横断歩道あり","横断歩道の前で停止","スクールゾーン {limit}","時速{limit}キロに減速","通学バス：停止",
   "バス専用車線 運用中","駐停車禁止","駐車取り締まりカメラ","駐車ルール","ウインカーを出す","シートベルト着用","ラウンドアバウト：反時計回り","逆走！",
   "左折は青矢印のみ","非保護左折","料金所：車線を確認"),
  Lang.ZH_HANS to row("右转：先停车","仅绿色箭头可右转","前方人行横道","人行横道前停车","儿童保护区 {limit}","减速至{limit}","校车：停车",
   "公交专用道运行中","禁止停车","违停抓拍","停车规则","请打转向灯","系好安全带","环岛：逆时针","逆行！",
   "仅绿色箭头可左转","无保护左转","收费站：选对车道"),
  Lang.ZH_HANT to row("右轉：先停車","僅綠色箭頭可右轉","前方行人穿越道","行人穿越道前停車","兒童保護區 {limit}","減速至{limit}","校車：停車",
   "公車專用道實施中","禁止停車","違停攝影機","停車規則","請打方向燈","繫好安全帶","圓環：逆時針","逆向！",
   "僅綠色箭頭可左轉","無保護左轉","收費站：選對車道"),
  Lang.MS to row("Belok kanan: berhenti dahulu","Belok kanan hanya anak panah hijau","Lintasan pejalan kaki","Berhenti di lintasan","Zon sekolah {limit}","Perlahan ke {limit}","Bas sekolah: berhenti",
   "Lorong bas beroperasi","Dilarang berhenti","Kamera letak kereta","Peraturan letak kereta","Guna lampu isyarat","Pakai tali pinggang","Bulatan: lawan jam","Arah salah!",
   "Belok kiri hanya anak panah hijau","Belok kiri tanpa perlindungan","Tol: pilih lorong"),
  Lang.TH to row("เลี้ยวขวา: หยุดก่อน","เลี้ยวขวาเฉพาะลูกศรเขียว","ทางม้าลายข้างหน้า","หยุดก่อนทางม้าลาย","เขตโรงเรียน {limit}","ลดเหลือ {limit}","รถนักเรียน: หยุด",
   "ช่องรถบัสใช้งานอยู่","ห้ามหยุดรถ","กล้องตรวจจับการจอด","กฎการจอด","เปิดไฟเลี้ยว","คาดเข็มขัด","วงเวียน: ทวนเข็มนาฬิกา","ผิดทาง!",
   "เลี้ยวซ้ายเฉพาะลูกศรเขียว","เลี้ยวซ้ายไม่มีสัญญาณ","ด่านเก็บเงิน: เลือกช่อง"),
  Lang.VI to row("Rẽ phải: dừng trước","Rẽ phải khi mũi tên xanh","Sắp có vạch sang đường","Dừng trước vạch sang đường","Khu trường học {limit}","Giảm xuống {limit}","Xe đưa đón học sinh: dừng",
   "Làn xe buýt đang hoạt động","Cấm dừng","Camera đỗ xe","Quy định đỗ xe","Bật xi nhan","Thắt dây an toàn","Vòng xuyến: ngược chiều kim đồng hồ","Sai chiều!",
   "Rẽ trái khi mũi tên xanh","Rẽ trái không bảo vệ","Trạm thu phí: chọn làn"),
  Lang.ID to row("Belok kanan: berhenti dulu","Belok kanan hanya panah hijau","Ada zebra cross","Berhenti di zebra cross","Zona sekolah {limit}","Pelankan ke {limit}","Bus sekolah: berhenti",
   "Jalur bus berlaku","Dilarang berhenti","Kamera parkir","Aturan parkir","Nyalakan sein","Pasang sabuk pengaman","Bundaran: berlawanan jarum jam","Arah salah!",
   "Belok kiri hanya panah hijau","Belok kiri tanpa pelindung","Gerbang tol: pilih jalur"),
  Lang.FR to row("Virage à droite : arrêt d'abord","À droite seulement sur flèche verte","Passage piéton","Arrêt au passage piéton","Zone scolaire {limit}","Ralentir à {limit}","Autobus scolaire : arrêt",
   "Voie d'autobus en vigueur","Arrêt interdit","Caméra de stationnement","Règles de stationnement","Mettez le clignotant","Bouclez la ceinture","Giratoire : sens antihoraire","Mauvais sens !",
   "À gauche seulement sur flèche verte","Virage à gauche non protégé","Péage : choisissez la voie"),
  Lang.ES to row("Giro a la derecha: alto primero","A la derecha solo con flecha verde","Cruce peatonal adelante","Alto en el cruce peatonal","Zona escolar {limit}","Baje a {limit}","Autobús escolar: alto",
   "Carril de autobús activo","Prohibido detenerse","Cámara de estacionamiento","Reglas de estacionamiento","Use la direccional","Abróchese el cinturón","Glorieta: sentido antihorario","¡Sentido contrario!",
   "A la izquierda solo con flecha verde","Giro a la izquierda no protegido","Peaje: elija el carril"),
 )
}
