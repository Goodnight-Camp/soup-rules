package com.goodnight.souprules.data

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import com.goodnight.souprules.core.Gear
import com.goodnight.souprules.core.Signal
import com.goodnight.souprules.core.TurnSignal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private const val TAG="SoupCar"

interface CarSignals {
 val signals:SharedFlow<Signal>
 fun start()
 fun close()
}

/**
 * 실제 차량 신호.
 * - 차속: AAOS PERF_VEHICLE_SPEED(m/s → km/h, CAR_SPEED). Pleos Vehicle SDK 2.3.2에는 차속 API가 없습니다.
 * - 기어: AAOS GEAR_SELECTION(CAR_POWERTRAIN). Pleos Gear API는 비권장이라 쓰지 않습니다(hyundai-autolog와 같은 선택).
 * - 깜빡이: Pleos TurnSignal(pleos.car.permission.CAR_LIGHTS)을 우선 쓰고, AAOS TURN_SIGNAL_STATE(CAR_EXTERIOR_LIGHTS)를 함께 구독합니다.
 *   두 경로가 같은 값을 보내도 엔진은 '바뀐 순간'만 보므로 중복은 문제가 없습니다.
 * - 운전대 각도: Pleos SteeringWheel(CAR_DRIVE). 깜빡이 없는 회전·차로 변경 판단에 씁니다.
 * - 안전띠·착석: Pleos Seat(CAR_SEATS), 좌석마다 등록합니다. 지원하지 않는 좌석은 등록 실패를 무시합니다.
 * 차량 제어 API·CONTROL 권한은 쓰지 않습니다(읽기 전용).
 */
class PleosCarSignals(private val context:Context,private val onError:(String)->Unit):CarSignals {
 private val flow=MutableSharedFlow<Signal>(extraBufferCapacity=256)
 override val signals=flow.asSharedFlow()
 private val main=Handler(Looper.getMainLooper())
 private val vehicle=Vehicle(context)
 private var car:Car?=null
 private var manager:CarPropertyManager?=null
 private var lastTurn:TurnSignal?=null

 private fun emitTurn(t:TurnSignal) { if(t!=lastTurn) { lastTurn=t; flow.tryEmit(Signal.Turn(t)) } }

 private val turnListener=object:TurnSignalStateListener {
  override fun onTurnSignalStateUpdated(state:TurnSignalValue?) { main.post { emitTurn(map(state)) } }
  override fun onFailed(exception:Exception) { Log.w(TAG,"Pleos 깜빡이 실패: ${exception.message}") }
 }

 private val steeringListener=object:SteeringWheelAngleListener {
  override fun onSteeringWheelAngleUpdated(value:Float?) { value?.let { v-> main.post { flow.tryEmit(Signal.Steering(v.toDouble())) } } }
  override fun onFailed(exception:Exception) { Log.w(TAG,"운전대 각도 실패: ${exception.message}") }
 }

 /** 좌석 번호 = 열×3+칸(0=1열 왼쪽=운전석, 한국 좌핸들). */
 private val seatValues=listOf(SeatValue.ROW_1_LEFT,SeatValue.ROW_1_RIGHT,SeatValue.ROW_2_LEFT,SeatValue.ROW_2_CENTER,SeatValue.ROW_2_RIGHT,SeatValue.ROW_3_LEFT,SeatValue.ROW_3_RIGHT)
 private fun seatIndex(v:SeatValue)=SeatValue.entries.indexOf(v)
 private val seatListeners=seatValues.map { v->
  val i=seatIndex(v)
  Triple(SeatArea(v),object:SeatBeltBuckledStateListener {
   override fun onSeatBeltBuckledStateUpdated(state:Boolean?) { state?.let { b-> main.post { flow.tryEmit(Signal.Seat(i,buckled=b)) } } }
   override fun onFailed(exception:Exception)=Unit
  },object:SeatOccupancyListener {
   override fun onSeatOccupancyUpdated(state:Boolean?) { state?.let { o-> main.post { flow.tryEmit(Signal.Seat(i,occupied=o)) } } }
   override fun onFailed(exception:Exception)=Unit
  })
 }

 private fun map(v:TurnSignalValue?)=when(v) { TurnSignalValue.RIGHT->TurnSignal.RIGHT; TurnSignalValue.LEFT->TurnSignal.LEFT; else->TurnSignal.OFF }

 private val callback=object:CarPropertyManager.CarPropertyEventCallback {
  override fun onChangeEvent(v:CarPropertyValue<*>?) {
   if(v==null || v.status!=CarPropertyValue.STATUS_AVAILABLE) return
   when(v.propertyId) {
    VehiclePropertyIds.PERF_VEHICLE_SPEED->(v.value as? Float)?.let { flow.tryEmit(Signal.Speed(it*3.6)) }
    VehiclePropertyIds.GEAR_SELECTION->(v.value as? Int)?.let { flow.tryEmit(Signal.Shift(Gear.fromAaos(it))) }
    VehiclePropertyIds.TURN_SIGNAL_STATE->(v.value as? Int)?.let { emitTurn(TurnSignal.fromAaos(it)) }
   }
  }
  override fun onErrorEvent(propertyId:Int,areaId:Int) { Log.w(TAG,"차량 신호 오류 ${VehiclePropertyIds.toString(propertyId)}") }
 }

 override fun start() {
  runCatching { vehicle.initialize() }.onFailure { Log.w(TAG,"Pleos Vehicle 초기화 실패: ${it.message}") }
  // Vehicle 서비스 바인딩 직후에는 API가 준비되지 않아(autolog 실측) 잠시 뒤 등록합니다.
  main.postDelayed({
   runCatching {
    vehicle.getTurnSignal().registerTurnSignalState(turnListener)
    vehicle.getTurnSignal().getCurrentTurnSignalState({ v-> main.post { emitTurn(map(v)) } },{ Log.w(TAG,"깜빡이 현재값 실패: ${it.message}") })
   }.onFailure { Log.w(TAG,"Pleos 깜빡이 구독 실패: ${it.message}") }
   runCatching { vehicle.getSteeringWheel().registerSteeringWheelAngle(steeringListener) }.onFailure { Log.w(TAG,"운전대 각도 구독 실패: ${it.message}") }
   val seat=vehicle.getSeat()
   seatListeners.forEach { (area,belt,occ)->
    runCatching { seat.registerSeatBeltBuckledState(area,belt) }.onFailure { Log.i(TAG,"안전띠 미지원 ${area.values.toList()}") }
    runCatching { seat.registerSeatOccupancy(area,occ) }.onFailure { Log.i(TAG,"착석 미지원 ${area.values.toList()}") }
   }
  },1500)
  car=Car.createCar(context,main,Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c,ready->
   if(!ready) { manager=null; return@createCar }
   val m=c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return@createCar
   manager=m
   subscribe(m,VehiclePropertyIds.PERF_VEHICLE_SPEED,CarPropertyManager.SENSOR_RATE_UI,"차속")
   subscribe(m,VehiclePropertyIds.GEAR_SELECTION,CarPropertyManager.SENSOR_RATE_ONCHANGE,"기어")
   subscribe(m,VehiclePropertyIds.TURN_SIGNAL_STATE,CarPropertyManager.SENSOR_RATE_ONCHANGE,"깜빡이",required=false)
   runCatching { flow.tryEmit(Signal.Shift(Gear.fromAaos(m.getIntProperty(VehiclePropertyIds.GEAR_SELECTION,0)))) }
   runCatching { flow.tryEmit(Signal.Speed(m.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED,0)*3.6)) }
  }
 }

 private fun subscribe(m:CarPropertyManager,id:Int,rate:Float,label:String,required:Boolean=true) {
  runCatching { m.registerCallback(callback,id,rate) }
   .onSuccess { ok-> if(!ok && required) onError("$label 신호 구독 거부") }
   .onFailure { Log.w(TAG,"$label 구독 실패: ${it.message}"); if(required) onError("$label 읽기 권한 확인 필요") }
 }

 override fun close() {
  runCatching { vehicle.getTurnSignal().unregisterTurnSignalState(turnListener) }
  runCatching { vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
  seatListeners.forEach { (area,belt,occ)-> runCatching { vehicle.getSeat().unregisterSeatBeltBuckledState(area,belt); vehicle.getSeat().unregisterSeatOccupancy(area,occ) } }
  runCatching { vehicle.release() }
  runCatching { manager?.unregisterCallback(callback) }
  runCatching { car?.disconnect() }
  main.removeCallbacksAndMessages(null)
 }
}
