package com.goodnight.souprules.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.goodnight.souprules.SoupApp

/**
 * 내용 없는 ContentProvider. 디버그 프로세스가 뜰 때 [DebugLink]를 자동으로 시작합니다.
 * ContentProvider.onCreate()는 Application.onCreate()보다 먼저 불리므로,
 * 메인 루퍼 큐로 미뤄 SoupApp 초기화가 끝난 뒤에 시작합니다.
 */
class DebugLinkProvider:ContentProvider() {
 override fun onCreate():Boolean {
  Handler(Looper.getMainLooper()).post {
   (context?.applicationContext as? SoupApp)?.let { DebugLink.start(it) }
  }
  return true
 }

 override fun query(uri:Uri,projection:Array<String>?,selection:String?,selectionArgs:Array<String>?,sortOrder:String?):Cursor?=null
 override fun getType(uri:Uri):String?=null
 override fun insert(uri:Uri,values:ContentValues?):Uri?=null
 override fun delete(uri:Uri,selection:String?,selectionArgs:Array<String>?):Int=0
 override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<String>?):Int=0
}
