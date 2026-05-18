package luma_fix_v.test_utils

import scala.collection.mutable

final class ProgramBuilder(startPc: Int = 0) extends Instructions {
  private val program = mutable.Map[Int, Int]()
  private var pc = startPc

  def setPc(nextPc: Int): ProgramBuilder = {
    pc = nextPc
    this
  }

  def emit(inst: Int): ProgramBuilder = {
    program(pc) = inst
    pc += 4
    this
  }

  def nops(count: Int): ProgramBuilder = {
    for (_ <- 0 until count) {
      emit(nop)
    }
    this
  }

  def currentPc: Int = pc

  def result: Map[Int, Int] = program.toMap
}
