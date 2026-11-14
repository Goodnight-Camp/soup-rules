package com.goodnight.souprules.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

/** Pleos 분할 화면 크기별 배치. */
class ScreenLayoutTest {
 @Test fun fullLandscapeIsTwoColumns() {
  val l=ScreenLayout.of(1920.dp,720.dp)
  assertTrue(l.twoColumns); assertFalse(l.narrow); assertFalse(l.compact)
 }
 @Test fun halfWidthSplitStacksIntoOneColumn() {
  val l=ScreenLayout.of(800.dp,720.dp)
  assertFalse(l.twoColumns); assertFalse(l.narrow); assertFalse(l.compact)
 }
 @Test fun narrowSplitMovesButtonsBelowTitle() {
  val l=ScreenLayout.of(520.dp,720.dp)
  assertFalse(l.twoColumns); assertTrue(l.narrow); assertFalse(l.compact)
 }
 @Test fun shortSplitIsCompact() {
  val l=ScreenLayout.of(1280.dp,360.dp)
  assertFalse(l.twoColumns); assertTrue(l.compact)
 }
 @Test fun minimumWindowIsCompactAndNarrow() {
  val l=ScreenLayout.of(320.dp,240.dp)
  assertTrue(l.narrow); assertTrue(l.compact); assertFalse(l.twoColumns)
 }
}
